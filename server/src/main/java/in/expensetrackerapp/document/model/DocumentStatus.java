package in.expensetrackerapp.document.model;

/** Processing state of an uploaded document. The Python worker moves it along via the job lifecycle. */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    PROCESSED,
    FAILED
}
