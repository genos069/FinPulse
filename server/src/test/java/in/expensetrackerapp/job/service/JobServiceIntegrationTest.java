package in.expensetrackerapp.job.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.job.config.JobProperties;
import in.expensetrackerapp.job.model.JobStatus;
import in.expensetrackerapp.job.model.ProcessingJob;
import in.expensetrackerapp.job.repository.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-MongoDB tests of the job state machine. Runs only when TEST_MONGODB_URI is set; everything
 * happens in a random throw-away database ("finpulse_it_...") that is dropped afterwards.
 */
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
class JobServiceIntegrationTest {

    /** Clock the tests can move forward to simulate lease expiry and backoff. */
    static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MongoClient client;
    private String databaseName;
    private MutableClock clock;
    private JobService service;
    private MongoTemplate template;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(System.getenv("TEST_MONGODB_URI"));
        databaseName = "finpulse_it_" + UUID.randomUUID().toString().replace("-", "");
        template = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, databaseName));
        JobRepository repository = new MongoRepositoryFactory(template).getRepository(JobRepository.class);
        JobProperties props = new JobProperties();
        props.setMaxAttempts(3);
        props.setLeaseSeconds(60);
        props.setBackoffBaseSeconds(30);
        props.setBackoffMaxSeconds(3600);
        clock = new MutableClock();
        service = new JobService(template, repository, props, clock);
    }

    @AfterEach
    void tearDown() {
        client.getDatabase(databaseName).drop();
        client.close();
    }

    private ProcessingJob reload(String id) {
        return template.findById(id, ProcessingJob.class);
    }

    @Test
    void twoWorkersNeverClaimTheSameJobConcurrently() throws Exception {
        ProcessingJob job = service.createJob("user-1", "doc-1", "gfs-1");
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<ProcessingJob>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String worker = "worker-" + i;
            futures.add(pool.submit(() -> {
                start.await();
                return service.claimNext(worker);
            }));
        }
        start.countDown();
        int claims = 0;
        for (Future<Optional<ProcessingJob>> f : futures) {
            if (f.get(30, TimeUnit.SECONDS).isPresent()) {
                claims++;
            }
        }
        pool.shutdown();

        assertThat(claims).isEqualTo(1);
        ProcessingJob stored = reload(job.getId());
        assertThat(stored.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(stored.getAttempts()).isEqualTo(1);
    }

    @Test
    void manyWorkersProcessEachOfManyJobsExactlyOnce() throws Exception {
        for (int i = 0; i < 25; i++) {
            service.createJob("user-1", "doc-" + i, "gfs-" + i);
        }
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<String> claimed = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String worker = "worker-" + i;
            futures.add(pool.submit(() -> {
                Optional<ProcessingJob> next;
                while ((next = service.claimNext(worker)).isPresent()) {
                    claimed.add(next.get().getId());
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Set<String> unique = new HashSet<>(claimed);
        assertThat(claimed).hasSize(25);
        assertThat(unique).hasSize(25);
    }

    @Test
    void jobsAreNotClaimableBeforeTheyAreDue() {
        ProcessingJob job = service.createJob("user-1", "doc-1", "gfs-1");
        template.updateFirst(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("id").is(job.getId())),
                new org.springframework.data.mongodb.core.query.Update().set("availableAt", clock.instant().plusSeconds(100)),
                ProcessingJob.class);

        assertThat(service.claimNext("worker-a")).isEmpty();
        clock.advance(Duration.ofSeconds(101));
        assertThat(service.claimNext("worker-a")).isPresent();
    }

    @Test
    void expiredLeaseIsRecoveredByAnotherWorkerAndOriginalWorkerLosesOwnership() {
        ProcessingJob job = service.createJob("user-1", "doc-1", "gfs-1");
        assertThat(service.claimNext("worker-a")).isPresent();

        // Lease still valid: nobody else can take it.
        clock.advance(Duration.ofSeconds(30));
        assertThat(service.claimNext("worker-b")).isEmpty();

        // Worker A "crashes"; lease expires; B takes over.
        clock.advance(Duration.ofSeconds(31));
        Optional<ProcessingJob> taken = service.claimNext("worker-b");
        assertThat(taken).isPresent();
        assertThat(taken.get().getId()).isEqualTo(job.getId());
        assertThat(taken.get().getAttempts()).isEqualTo(2);

        // The zombie worker A may not touch the job any more.
        assertThat(service.complete(job.getId(), "worker-a", 5, 0, false)).isFalse();
        assertThat(service.extendLease(job.getId(), "worker-a")).isFalse();
        assertThat(service.fail(job.getId(), "worker-a", "late")).isEqualTo(JobService.FailureOutcome.NOT_OWNER);

        assertThat(service.complete(job.getId(), "worker-b", 5, 1, false)).isTrue();
        ProcessingJob done = reload(job.getId());
        assertThat(done.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(done.getExtractedCount()).isEqualTo(5);
        assertThat(done.getDuplicateCount()).isEqualTo(1);
        assertThat(done.getLeaseOwner()).isNull();
    }

    @Test
    void extendingTheLeaseKeepsOtherWorkersOut() {
        service.createJob("user-1", "doc-1", "gfs-1");
        ProcessingJob claimed = service.claimNext("worker-a").orElseThrow();

        clock.advance(Duration.ofSeconds(50));
        assertThat(service.extendLease(claimed.getId(), "worker-a")).isTrue();
        clock.advance(Duration.ofSeconds(50));   // 100s since claim, but lease was renewed at 50s

        assertThat(service.claimNext("worker-b")).isEmpty();
    }

    @Test
    void failureSchedulesRetryWithExponentialBackoff() {
        ProcessingJob job = service.createJob("user-1", "doc-1", "gfs-1");
        service.claimNext("worker-a");

        assertThat(service.fail(job.getId(), "worker-a", "ocr failed")).isEqualTo(JobService.FailureOutcome.RETRY_SCHEDULED);
        ProcessingJob afterFirst = reload(job.getId());
        assertThat(afterFirst.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(afterFirst.getErrorMessage()).isEqualTo("ocr failed");
        assertThat(afterFirst.getAvailableAt()).isEqualTo(clock.instant().plusSeconds(30));

        assertThat(service.claimNext("worker-a")).isEmpty();             // still backing off
        clock.advance(Duration.ofSeconds(31));
        assertThat(service.claimNext("worker-a")).isPresent();

        service.fail(job.getId(), "worker-a", "again");
        assertThat(reload(job.getId()).getAvailableAt()).isEqualTo(clock.instant().plusSeconds(60));
    }

    @Test
    void jobBecomesFailedAfterMaxAttemptsAndIsNeverClaimedAgain() {
        ProcessingJob job = service.createJob("user-1", "doc-1", "gfs-1");

        for (int attempt = 1; attempt <= 3; attempt++) {
            clock.advance(Duration.ofHours(2));
            assertThat(service.claimNext("worker-a")).isPresent();
            JobService.FailureOutcome outcome = service.fail(job.getId(), "worker-a", "error " + attempt);
            assertThat(outcome).isEqualTo(attempt < 3 ? JobService.FailureOutcome.RETRY_SCHEDULED : JobService.FailureOutcome.FAILED);
        }

        ProcessingJob failed = reload(job.getId());
        assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(3);
        assertThat(failed.getCompletedAt()).isNotNull();
        clock.advance(Duration.ofDays(1));
        assertThat(service.claimNext("worker-b")).isEmpty();
    }

    @Test
    void workerCrashOnFinalAttemptEndsInFailedNotInEndlessProcessing() {
        ProcessingJob job = service.createJob("user-1", "doc-1", "gfs-1");
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(service.claimNext("worker-" + attempt)).isPresent();
            clock.advance(Duration.ofSeconds(61));          // crash: lease expires without completion
        }

        assertThat(service.claimNext("worker-4")).isEmpty();   // sweeps the exhausted job
        ProcessingJob stored = reload(job.getId());
        assertThat(stored.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(stored.getErrorMessage()).contains("no retry attempts remain");
    }

    @Test
    void completedOrFailedJobsCannotBeCompletedAgainOrReclaimed() {
        ProcessingJob job = service.createJob("user-1", "doc-1", "gfs-1");
        service.claimNext("worker-a");
        assertThat(service.complete(job.getId(), "worker-a", 1, 0, true)).isTrue();

        assertThat(reload(job.getId()).getStatus()).isEqualTo(JobStatus.PARTIALLY_COMPLETED);
        assertThat(service.complete(job.getId(), "worker-a", 9, 9, false)).isFalse();
        clock.advance(Duration.ofDays(1));
        assertThat(service.claimNext("worker-b")).isEmpty();
    }

    @Test
    void usersCanOnlyReadTheirOwnJobs() {
        ProcessingJob job = service.createJob("user-1", "doc-1", "gfs-1");

        assertThat(service.getJob("user-1", job.getId()).getId()).isEqualTo(job.getId());
        assertThatThrownBy(() -> service.getJob("user-2", job.getId())).isInstanceOf(ResourceNotFoundException.class);
    }
}
