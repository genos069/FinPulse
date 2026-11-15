package in.expensetrackerapp.job.dto;

import in.expensetrackerapp.job.model.JobStatus;
import in.expensetrackerapp.job.model.ProcessingJob;

import java.time.Instant;

/** API view of a job. userId, GridFS id and lease details are intentionally not exposed. */
public record JobResponse(
        String id,
        String documentId,
        JobStatus status,
        int attempts,
        int maxAttempts,
        Instant availableAt,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        int extractedCount,
        int duplicateCount) {

    public static JobResponse fromJob(ProcessingJob j) {
        return new JobResponse(
                j.getId(), j.getDocumentId(), j.getStatus(), j.getAttempts(), j.getMaxAttempts(),
                j.getAvailableAt(), j.getCreatedAt(), j.getStartedAt(), j.getCompletedAt(),
                j.getErrorMessage(), j.getExtractedCount(), j.getDuplicateCount());
    }
}
