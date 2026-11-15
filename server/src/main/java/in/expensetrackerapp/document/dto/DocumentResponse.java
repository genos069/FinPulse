package in.expensetrackerapp.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.expensetrackerapp.document.model.DocumentStatus;
import in.expensetrackerapp.document.model.UploadedDocument;

import java.time.Instant;

/** API view of a document. userId, GridFS ids and stored filenames are intentionally not exposed. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentResponse(
        String id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        Instant createdAt,
        Instant updatedAt,
        String jobId) {

    public static DocumentResponse fromDocument(UploadedDocument d) {
        return new DocumentResponse(
                d.getId(),
                d.getOriginalFilename(),
                d.getContentType(),
                d.getSizeBytes(),
                d.getStatus(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                null);
    }

    /** Upload responses carry the id of the processing job so clients can poll GET /api/jobs/{id}. */
    public DocumentResponse withJobId(String jobId) {
        return new DocumentResponse(id, originalFilename, contentType, sizeBytes, status, createdAt, updatedAt, jobId);
    }
}
