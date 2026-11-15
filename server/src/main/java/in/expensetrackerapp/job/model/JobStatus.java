package in.expensetrackerapp.job.model;

public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == PARTIALLY_COMPLETED || this == FAILED;
    }
}
