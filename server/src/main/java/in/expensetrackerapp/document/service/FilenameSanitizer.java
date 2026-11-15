package in.expensetrackerapp.document.service;

import java.text.Normalizer;

/**
 * Produces a safe display name from a client-supplied filename. The result is never used as a
 * filesystem path; stored files are named with server-generated identifiers.
 */
public final class FilenameSanitizer {

    static final String FALLBACK = "document";
    static final int MAX_LENGTH = 100;

    private FilenameSanitizer() {
    }

    public static String sanitize(String original) {
        if (original == null || original.isBlank()) {
            return FALLBACK;
        }
        String name = Normalizer.normalize(original, Normalizer.Form.NFKC);
        // Keep only the last path segment (blocks ../ and C:\ style traversal).
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        name = name.replaceAll("\\.{2,}", ".");
        name = name.replaceAll("^[.\\s]+", "").trim();
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH);
        }
        return name.isBlank() ? FALLBACK : name;
    }
}
