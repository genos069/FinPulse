package in.expensetrackerapp.transaction.dto;

import in.expensetrackerapp.transaction.model.Transaction;
import in.expensetrackerapp.transaction.model.TransactionDirection;

import java.math.BigDecimal;
import java.time.Instant;

/** API view. userId and sourceHash are internal and never returned. */
public record TransactionResponse(
        String id,
        String documentId,
        String jobId,
        String date,
        BigDecimal amount,
        String currency,
        TransactionDirection direction,
        String merchant,
        String category,
        String description,
        String source,
        String referenceNumber,
        BigDecimal balance,
        boolean needsReview,
        String possibleDuplicateOf,
        Instant createdAt,
        Instant updatedAt) {

    public static TransactionResponse fromTransaction(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getDocumentId(), t.getJobId(), t.getDate(), t.getAmount(), t.getCurrency(),
                t.getDirection(), t.getMerchant(), t.getCategory(), t.getDescription(), t.getSource(),
                t.getReferenceNumber(), t.getBalance(), t.isNeedsReview(), t.getPossibleDuplicateOf(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
