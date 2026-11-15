package in.expensetrackerapp.job.service;

import com.mongodb.client.result.UpdateResult;
import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.job.config.JobProperties;
import in.expensetrackerapp.job.model.JobStatus;
import in.expensetrackerapp.job.model.ProcessingJob;
import in.expensetrackerapp.job.repository.JobRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests check the shape of each atomic operation (filters and updates). The real
 * atomicity/concurrency guarantees are exercised by JobServiceIntegrationTest against MongoDB.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private JobRepository jobRepository;

    private JobProperties properties;
    private JobService service;

    @BeforeEach
    void setUp() {
        properties = new JobProperties();
        properties.setMaxAttempts(3);
        properties.setLeaseSeconds(60);
        properties.setBackoffBaseSeconds(30);
        properties.setBackoffMaxSeconds(3600);
        service = new JobService(mongoTemplate, jobRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UpdateResult matched(long count) {
        return UpdateResult.acknowledged(count, count, null);
    }

    private static ProcessingJob job(String id, String owner, JobStatus status, int attempts, int remaining) {
        ProcessingJob j = new ProcessingJob();
        j.setId(id);
        j.setUserId("user-1");
        j.setLeaseOwner(owner);
        j.setStatus(status);
        j.setAttempts(attempts);
        j.setMaxAttempts(3);
        j.setRemainingAttempts(remaining);
        return j;
    }

    private Document updateDoc(ArgumentCaptor<UpdateDefinition> captor) {
        return captor.getValue().getUpdateObject();
    }

    @Test
    void createJob_startsPendingWithConfiguredAttempts() {
        when(jobRepository.insert(any(ProcessingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcessingJob created = service.createJob("user-1", "doc-1", "gridfs-1");

        assertThat(created.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(created.getUserId()).isEqualTo("user-1");
        assertThat(created.getDocumentId()).isEqualTo("doc-1");
        assertThat(created.getGridFsFileId()).isEqualTo("gridfs-1");
        assertThat(created.getAttempts()).isZero();
        assertThat(created.getMaxAttempts()).isEqualTo(3);
        assertThat(created.getRemainingAttempts()).isEqualTo(3);
        assertThat(created.getAvailableAt()).isEqualTo(NOW);
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void getJob_isOwnerScoped() {
        when(jobRepository.findByIdAndUserId("job-1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob("user-2", "job-1")).isInstanceOf(ResourceNotFoundException.class);
        verify(jobRepository).findByIdAndUserId("job-1", "user-2");
    }

    @Test
    void claimNext_usesSingleAtomicFindAndModifyWithLeaseAndAttemptAccounting() {
        when(mongoTemplate.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(0));
        ProcessingJob claimed = job("job-1", "worker-a", JobStatus.PROCESSING, 1, 2);
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(ProcessingJob.class))).thenReturn(claimed);

        Optional<ProcessingJob> result = service.claimNext("worker-a");

        assertThat(result).containsSame(claimed);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        ArgumentCaptor<FindAndModifyOptions> options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), options.capture(), eq(ProcessingJob.class));

        String filter = query.getValue().getQueryObject().toString();
        assertThat(filter).contains("remainingAttempts").contains("$gt").contains("$or")
                .contains("PENDING").contains("availableAt")
                .contains("PROCESSING").contains("leaseUntil");
        assertThat(options.getValue().isReturnNew()).isTrue();

        Document set = (Document) updateDoc(update).get("$set");
        assertThat(set.get("leaseOwner")).isEqualTo("worker-a");
        assertThat(set.get("status")).isEqualTo(JobStatus.PROCESSING);
        assertThat(set.get("leaseUntil")).isEqualTo(NOW.plusSeconds(60));
        Document inc = (Document) updateDoc(update).get("$inc");
        assertThat(inc.get("attempts")).isEqualTo(1);
        assertThat(inc.get("remainingAttempts")).isEqualTo(-1);
    }

    @Test
    void claimNext_returnsEmptyWhenNothingRunnable() {
        when(mongoTemplate.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(0));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(ProcessingJob.class))).thenReturn(null);

        assertThat(service.claimNext("worker-a")).isEmpty();
    }

    @Test
    void claimNext_requiresWorkerId() {
        assertThatThrownBy(() -> service.claimNext(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.claimNext(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failExhaustedExpiredJobs_targetsOnlyExpiredProcessingJobsWithNoAttemptsLeft() {
        when(mongoTemplate.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(2));

        assertThat(service.failExhaustedExpiredJobs(NOW)).isEqualTo(2);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(ProcessingJob.class));
        assertThat(query.getValue().getQueryObject().toString())
                .contains("PROCESSING").contains("leaseUntil").contains("remainingAttempts").contains("$lte");
        assertThat(((Document) updateDoc(update).get("$set")).get("status")).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void complete_requiresOwnershipAndRecordsCounts() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(1));

        assertThat(service.complete("job-1", "worker-a", 10, 2, false)).isTrue();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(ProcessingJob.class));
        assertThat(query.getValue().getQueryObject().toString())
                .contains("worker-a").contains("PROCESSING").contains("leaseOwner");
        Document set = (Document) updateDoc(update).get("$set");
        assertThat(set.get("status")).isEqualTo(JobStatus.COMPLETED);
        assertThat(set.get("extractedCount")).isEqualTo(10);
        assertThat(set.get("duplicateCount")).isEqualTo(2);
    }

    @Test
    void complete_canMarkPartialCompletion() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(1));

        service.complete("job-1", "worker-a", 5, 0, true);

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(ProcessingJob.class));
        assertThat(((Document) updateDoc(update).get("$set")).get("status")).isEqualTo(JobStatus.PARTIALLY_COMPLETED);
    }

    @Test
    void complete_byNonOwnerReturnsFalse() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(0));

        assertThat(service.complete("job-1", "worker-b", 1, 0, false)).isFalse();
    }

    @Test
    void extendLease_pushesLeaseForwardOnlyForOwner() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(1), matched(0));

        assertThat(service.extendLease("job-1", "worker-a")).isTrue();
        assertThat(service.extendLease("job-1", "worker-b")).isFalse();
    }

    @Test
    void fail_withAttemptsLeftSchedulesRetryWithExponentialBackoff() {
        when(mongoTemplate.findOne(any(Query.class), eq(ProcessingJob.class)))
                .thenReturn(job("job-1", "worker-a", JobStatus.PROCESSING, 2, 1));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(1));

        assertThat(service.fail("job-1", "worker-a", "ocr failed")).isEqualTo(JobService.FailureOutcome.RETRY_SCHEDULED);

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(ProcessingJob.class));
        Document set = (Document) updateDoc(update).get("$set");
        assertThat(set.get("status")).isEqualTo(JobStatus.PENDING);
        assertThat(set.get("availableAt")).isEqualTo(NOW.plusSeconds(60));   // 30s * 2^(2-1)
        assertThat(set.get("errorMessage")).isEqualTo("ocr failed");
    }

    @Test
    void fail_withNoAttemptsLeftIsTerminal() {
        when(mongoTemplate.findOne(any(Query.class), eq(ProcessingJob.class)))
                .thenReturn(job("job-1", "worker-a", JobStatus.PROCESSING, 3, 0));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(1));

        assertThat(service.fail("job-1", "worker-a", "boom")).isEqualTo(JobService.FailureOutcome.FAILED);

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(ProcessingJob.class));
        Document set = (Document) updateDoc(update).get("$set");
        assertThat(set.get("status")).isEqualTo(JobStatus.FAILED);
        assertThat(set.get("completedAt")).isEqualTo(NOW);
    }

    @Test
    void fail_byWrongWorkerOrMissingJobChangesNothing() {
        when(mongoTemplate.findOne(any(Query.class), eq(ProcessingJob.class)))
                .thenReturn(job("job-1", "worker-a", JobStatus.PROCESSING, 1, 2))
                .thenReturn(null);

        assertThat(service.fail("job-1", "worker-b", "x")).isEqualTo(JobService.FailureOutcome.NOT_OWNER);
        assertThat(service.fail("job-1", "worker-a", "x")).isEqualTo(JobService.FailureOutcome.NOT_OWNER);
        verify(mongoTemplate, org.mockito.Mockito.never())
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class));
    }

    @Test
    void fail_whenJobWasReclaimedMeanwhileIsNotOwner() {
        when(mongoTemplate.findOne(any(Query.class), eq(ProcessingJob.class)))
                .thenReturn(job("job-1", "worker-a", JobStatus.PROCESSING, 1, 2));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(0));

        assertThat(service.fail("job-1", "worker-a", "x")).isEqualTo(JobService.FailureOutcome.NOT_OWNER);
    }

    @Test
    void fail_truncatesVeryLongErrorMessages() {
        when(mongoTemplate.findOne(any(Query.class), eq(ProcessingJob.class)))
                .thenReturn(job("job-1", "worker-a", JobStatus.PROCESSING, 1, 2));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(ProcessingJob.class)))
                .thenReturn(matched(1));

        service.fail("job-1", "worker-a", "x".repeat(5000));

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(ProcessingJob.class));
        String stored = (String) ((Document) updateDoc(update).get("$set")).get("errorMessage");
        assertThat(stored).hasSize(JobService.MAX_ERROR_LENGTH);
    }

    @Test
    void backoff_doublesAndIsCapped() {
        assertThat(service.backoff(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(service.backoff(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(service.backoff(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(service.backoff(10)).isEqualTo(Duration.ofSeconds(3600));
        assertThat(service.backoff(100_000)).isEqualTo(Duration.ofSeconds(3600));
        assertThat(service.backoff(0)).isEqualTo(Duration.ofSeconds(30));
    }
}
