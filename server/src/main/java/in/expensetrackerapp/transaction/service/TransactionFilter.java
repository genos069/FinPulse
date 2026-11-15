package in.expensetrackerapp.transaction.service;

import in.expensetrackerapp.transaction.model.TransactionDirection;

import java.time.LocalDate;

/** Optional list filters; null means "no filter". Dates are inclusive. */
public record TransactionFilter(
        LocalDate from,
        LocalDate to,
        String category,
        TransactionDirection direction,
        Boolean needsReview) {

    public static TransactionFilter none() {
        return new TransactionFilter(null, null, null, null, null);
    }
}
