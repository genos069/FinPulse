package in.expensetrackerapp.document.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameSanitizerTest {

    @Test
    void keepsOrdinaryNames() {
        assertThat(FilenameSanitizer.sanitize("HDFC statement-2024.pdf")).isEqualTo("HDFC statement-2024.pdf");
    }

    @Test
    void stripsUnixAndWindowsPathTraversal() {
        assertThat(FilenameSanitizer.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FilenameSanitizer.sanitize("..\\..\\windows\\system32\\evil.pdf")).isEqualTo("evil.pdf");
        assertThat(FilenameSanitizer.sanitize("C:\\Users\\me\\bill.png")).isEqualTo("bill.png");
    }

    @Test
    void replacesUnsafeCharactersAndControlCodes() {
        String result = FilenameSanitizer.sanitize("a<b>c\"d|e\u0000f.pdf");
        assertThat(result).isEqualTo("a_b_c_d_e_f.pdf");
    }

    @Test
    void collapsesRepeatedDotsAndLeadingDots() {
        assertThat(FilenameSanitizer.sanitize("...hidden..file.pdf")).isEqualTo("hidden.file.pdf");
    }

    @Test
    void fallsBackWhenNothingUsableRemains() {
        assertThat(FilenameSanitizer.sanitize(null)).isEqualTo("document");
        assertThat(FilenameSanitizer.sanitize("   ")).isEqualTo("document");
        assertThat(FilenameSanitizer.sanitize("../")).isEqualTo("document");
    }

    @Test
    void limitsLength() {
        String result = FilenameSanitizer.sanitize("a".repeat(500) + ".pdf");
        assertThat(result.length()).isLessThanOrEqualTo(FilenameSanitizer.MAX_LENGTH);
    }
}
