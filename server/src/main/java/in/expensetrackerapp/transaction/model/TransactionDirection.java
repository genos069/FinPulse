package in.expensetrackerapp.transaction.model;

/**
 * DEBIT = money leaving an account (expense), CREDIT = money entering (income).
 * UNKNOWN is written by the extraction worker when the statement does not make the direction clear;
 * such rows are always flagged needsReview until a user resolves them.
 */
public enum TransactionDirection {
    DEBIT,
    CREDIT,
    UNKNOWN
}
