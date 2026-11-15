package in.expensetrackerapp.job.service;

import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.job.config.JobProperties;
import in.expensetrackerapp.job.model.JobStatus;
import in.expensetrackerapp.job.model.ProcessingJob;
import in.expensetrackerapp.job.repository.JobRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Job lifecycle with MongoDB-atomic transitions.
 *
 * <pre>
 * PENDING --claim--> PROCESSING --complete--> COMPLETED | PARTIALLY_COMPLETED
 *    ^                   |
 *    |   fail (retries left, backoff)
 *    +-------------------+
 *                        | fail (no retries left) / lease expired with no retries left
 *                        v
 *                      FAILED
 * </pre>
 *
 * Every state change is a single conditional update (compare-and-set on status, lease owner and,
 * where needed, attempts), so two workers can never both succeed on the same job.
 * A job whose lease has expired is claimable again, which is how crashed workers are recovered.
 */
@Service
public class JobService {

    public enum FailureOutcome { RETRY_SCHEDULED, FAILED, NOT_OWNER }

    static final int MAX_ERROR_LENGTH = 500;

    private final MongoTemplate mongoTemplate;
    private final JobRepository jobRepository;
    private final JobProperties properties;
    private final Clock clock;

    public JobService(MongoTemplate mongoTemplate, JobRepository jobRepository,
                      JobProperties properties, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.jobRepository = jobRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public ProcessingJob createJob(String userId, String documentId, String gridFsFileId) {
        Instant now = clock.instant();
        ProcessingJob job = new ProcessingJob();
        job.setUserId(userId);
        job.setDocumentId(documentId);
        job.setGridFsFileId(gridFsFileId);
        job.setStatus(JobStatus.PENDING);
        job.setAttempts(0);
        job.setMaxAttempts(properties.getMaxAttempts());
        job.setRemainingAttempts(properties.getMaxAttempts());
        job.setAvailableAt(now);
        job.setCreatedAt(now);
        return jobRepository.insert(job);
    }

    public ProcessingJob getJob(String userId, String jobId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
    }

    /**
     * Atomically claims the oldest runnable job: either PENDING and due, or PROCESSING with an
     * expired lease (its worker is presumed dead). Jobs without remaining attempts are never claimed.
     */
    public Optional<ProcessingJob> claimNext(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId is required");
        }
        Instant now = clock.instant();
        failExhaustedExpiredJobs(now);

        Criteria runnable = new Criteria().orOperator(
                Criteria.where("status").is(JobStatus.PENDING).and("availableAt").lte(now),
                Criteria.where("status").is(JobStatus.PROCESSING).and("leaseUntil").lte(now));
        Query query = new Query(new Criteria().andOperator(Criteria.where("remainingAttempts").gt(0), runnable))
                .with(Sort.by(Sort.Direction.ASC, "availableAt"));

        Update update = new Update()
                .set("status", JobStatus.PROCESSING)
                .set("leaseOwner", workerId)
                .set("leaseUntil", now.plus(leaseDuration()))
                .set("startedAt", now)
                .inc("attempts", 1)
                .inc("remainingAttempts", -1);

        ProcessingJob claimed = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), ProcessingJob.class);
        return Optional.ofNullable(claimed);
    }

    /** Extends the lease. Returns false when the caller does not currently own the job. */
    public boolean extendLease(String jobId, String workerId) {
        Instant now = clock.instant();
        Update update = new Update().set("leaseUntil", now.plus(leaseDuration()));
        return ownedByWorker(jobId, workerId, update);
    }

    /** Marks the job finished. Returns false (and changes nothing) unless workerId owns the PROCESSING job. */
    public boolean complete(String jobId, String workerId, int extractedCount, int duplicateCount, boolean partial) {
        Update update = new Update()
                .set("status", partial ? JobStatus.PARTIALLY_COMPLETED : JobStatus.COMPLETED)
                .set("completedAt", clock.instant())
                .set("extractedCount", extractedCount)
                .set("duplicateCount", duplicateCount)
                .unset("leaseOwner")
                .unset("leaseUntil")
                .unset("errorMessage");
        return ownedByWorker(jobId, workerId, update);
    }

    /**
     * Records a failed attempt. With attempts left the job returns to PENDING after an exponential
     * backoff; otherwise it becomes FAILED. Only the current lease owner may do this.
     */
    public FailureOutcome fail(String jobId, String workerId, String errorMessage) {
        ProcessingJob job = mongoTemplate.findOne(
                Query.query(Criteria.where("id").is(jobId)), ProcessingJob.class);
        if (job == null || job.getStatus() != JobStatus.PROCESSING || !workerId.equals(job.getLeaseOwner())) {
            return FailureOutcome.NOT_OWNER;
        }

        boolean exhausted = job.getRemainingAttempts() <= 0;
        Instant now = clock.instant();
        Update update = new Update()
                .set("errorMessage", truncate(errorMessage))
                .unset("leaseOwner")
                .unset("leaseUntil");
        if (exhausted) {
            update.set("status", JobStatus.FAILED).set("completedAt", now);
        } else {
            update.set("status", JobStatus.PENDING).set("availableAt", now.plus(backoff(job.getAttempts())));
        }

        // Compare-and-set on attempts: if the job was re-claimed meanwhile, nothing matches.
        Query query = Query.query(Criteria.where("id").is(jobId)
                .and("status").is(JobStatus.PROCESSING)
                .and("leaseOwner").is(workerId)
                .and("attempts").is(job.getAttempts()));
        if (mongoTemplate.updateFirst(query, update, ProcessingJob.class).getMatchedCount() != 1) {
            return FailureOutcome.NOT_OWNER;
        }
        return exhausted ? FailureOutcome.FAILED : FailureOutcome.RETRY_SCHEDULED;
    }

    /**
     * Fails PROCESSING jobs whose lease expired and that have no attempts left (the worker died on its
     * last attempt). Jobs with attempts left are simply claimable again. Returns the number failed.
     */
    public long failExhaustedExpiredJobs(Instant now) {
        Query query = Query.query(Criteria.where("status").is(JobStatus.PROCESSING)
                .and("leaseUntil").lte(now)
                .and("remainingAttempts").lte(0));
        Update update = new Update()
                .set("status", JobStatus.FAILED)
                .set("completedAt", now)
                .set("errorMessage", "Worker stopped responding and no retry attempts remain")
                .unset("leaseOwner")
                .unset("leaseUntil");
        return mongoTemplate.updateMulti(query, update, ProcessingJob.class).getModifiedCount();
    }

    /** Delay before the next attempt after the given (1-based) number of attempts has failed. */
    public Duration backoff(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 20);
        long seconds = properties.getBackoffBaseSeconds() * (1L << exponent);
        return Duration.ofSeconds(Math.min(seconds, properties.getBackoffMaxSeconds()));
    }

    private boolean ownedByWorker(String jobId, String workerId, Update update) {
        Query query = Query.query(Criteria.where("id").is(jobId)
                .and("status").is(JobStatus.PROCESSING)
                .and("leaseOwner").is(workerId));
        return mongoTemplate.updateFirst(query, update, ProcessingJob.class).getMatchedCount() == 1;
    }

    private Duration leaseDuration() {
        return Duration.ofSeconds(properties.getLeaseSeconds());
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Processing failed";
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
