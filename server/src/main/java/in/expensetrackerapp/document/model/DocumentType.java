package in.expensetrackerapp.document.model;

/** Supported upload formats, identified by file signature (magic bytes), never by client-declared type. */
public enum DocumentType {
    PDF("application/pdf", "pdf"),
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png");

    private final String contentType;
    private final String extension;

    DocumentType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }
}
