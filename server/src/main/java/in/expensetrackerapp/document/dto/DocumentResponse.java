package in.expensetrackerapp.document.dto;

import in.expensetrackerapp.document.model.DocumentStatus;
import in.expensetrackerapp.document.model.UploadedDocument;

import java.time.Instant;

/** API view of a document. userId, GridFS ids and stored filenames are intentionally not exposed. */
public record DocumentResponse(
        String id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentResponse fromDocument(UploadedDocument d) {
        return new DocumentResponse(
                d.getId(),
                d.getOriginalFilename(),
                d.getContentType(),
                d.getSizeBytes(),
                d.getStatus(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}
