package in.expensetrackerapp.transaction.model;

import java.util.Arrays;
import java.util.Optional;

/** The application's 13 standard categories. The worker maps its own pipeline categories onto these. */
public enum TransactionCategory {
    FOOD("Food"),
    SHOPPING("Shopping"),
    TRAVEL("Travel"),
    TRANSPORT("Transport"),
    BILLS("Bills"),
    ENTERTAINMENT("Entertainment"),
    HEALTHCARE("Healthcare"),
    EDUCATION("Education"),
    RENT("Rent"),
    SALARY("Salary"),
    INVESTMENT("Investment"),
    TRANSFER("Transfer"),
    OTHER("Other");

    private final String value;

    TransactionCategory(String value) {
        this.value = value;
    }

    /** The exact string stored in MongoDB and returned by the API. */
    public String getValue() {
        return value;
    }

    /** Case-insensitive lookup; returns the canonical category. */
    public static Optional<TransactionCategory> fromValue(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        return Arrays.stream(values()).filter(c -> c.value.equalsIgnoreCase(trimmed)).findFirst();
    }
}
