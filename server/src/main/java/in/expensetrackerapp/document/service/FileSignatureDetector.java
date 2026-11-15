package in.expensetrackerapp.document.service;

import in.expensetrackerapp.document.model.DocumentType;

import java.util.Optional;

/** Identifies supported formats from the leading bytes of a file. */
public final class FileSignatureDetector {

    /** Number of leading bytes needed to recognise every supported signature. */
    public static final int HEADER_LENGTH = 8;

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D};                        // %PDF-
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private FileSignatureDetector() {
    }

    public static Optional<DocumentType> detect(byte[] header) {
        if (header == null) {
            return Optional.empty();
        }
        if (startsWith(header, PDF)) {
            return Optional.of(DocumentType.PDF);
        }
        if (startsWith(header, JPEG)) {
            return Optional.of(DocumentType.JPEG);
        }
        if (startsWith(header, PNG)) {
            return Optional.of(DocumentType.PNG);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
