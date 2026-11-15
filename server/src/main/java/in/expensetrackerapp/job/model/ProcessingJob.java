package in.expensetrackerapp.job.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A unit of work for the Python worker. Claiming and lease changes are done with atomic
 * conditional updates (see JobService); this class is only the persisted shape.
 *
 * remainingAttempts == maxAttempts - attempts. It is stored so that "can this job still be
 * claimed?" is a plain field comparison, which both Java and the Python worker can use atomically.
 */
@Document(collection = "processing_jobs")
@CompoundIndexes({
        @CompoundIndex(name = "status_availableAt", def = "{'status': 1, 'availableAt': 1}"),
        @CompoundIndex(name = "userId_createdAt", def = "{'userId': 1, 'createdAt': -1}")
})
public class ProcessingJob {

    @Id
    private String id;

    private String userId;
    private String documentId;
    private String gridFsFileId;
    private JobStatus status;
    private int attempts;
    private int maxAttempts;
    private int remainingAttempts;
    private Instant availableAt;
    private String leaseOwner;

    @Indexed(name = "leaseUntil_idx")
    private Instant leaseUntil;

    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
    private int extractedCount;
    private int duplicateCount;

    public ProcessingJob() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getGridFsFileId() { return gridFsFileId; }
    public void setGridFsFileId(String gridFsFileId) { this.gridFsFileId = gridFsFileId; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public int getRemainingAttempts() { return remainingAttempts; }
    public void setRemainingAttempts(int remainingAttempts) { this.remainingAttempts = remainingAttempts; }

    public Instant getAvailableAt() { return availableAt; }
    public void setAvailableAt(Instant availableAt) { this.availableAt = availableAt; }

    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }

    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getExtractedCount() { return extractedCount; }
    public void setExtractedCount(int extractedCount) { this.extractedCount = extractedCount; }

    public int getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
}
