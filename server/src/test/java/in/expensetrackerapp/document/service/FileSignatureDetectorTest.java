package in.expensetrackerapp.document.service;

import in.expensetrackerapp.document.model.DocumentType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileSignatureDetectorTest {

    @Test
    void detectsPdf() {
        byte[] header = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        assertThat(FileSignatureDetector.detect(header)).contains(DocumentType.PDF);
    }

    @Test
    void detectsJpeg() {
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'};
        assertThat(FileSignatureDetector.detect(header)).contains(DocumentType.JPEG);
    }

    @Test
    void detectsPng() {
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(FileSignatureDetector.detect(header)).contains(DocumentType.PNG);
    }

    @Test
    void rejectsExecutableDisguisedAsDocument() {
        byte[] header = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
        assertThat(FileSignatureDetector.detect(header)).isEqualTo(Optional.empty());
    }

    @Test
    void rejectsPlainTextAndHtml() {
        assertThat(FileSignatureDetector.detect("<html><scr".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileSignatureDetector.detect("hello wor".getBytes(StandardCharsets.US_ASCII))).isEmpty();
    }

    @Test
    void rejectsTruncatedAndNullHeaders() {
        assertThat(FileSignatureDetector.detect(new byte[0])).isEmpty();
        assertThat(FileSignatureDetector.detect(new byte[]{'%', 'P'})).isEmpty();
        assertThat(FileSignatureDetector.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G'})).isEmpty();
        assertThat(FileSignatureDetector.detect(null)).isEmpty();
    }
}
