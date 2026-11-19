package in.expensetrackerapp.testsupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationTestSupportTest {

    @Test
    void identicalTestAndProductionUrisAreRefused() {
        String uri = "mongodb+srv://u:p@cluster.mongodb.net/?retryWrites=true";
        assertThatThrownBy(() -> IntegrationTestSupport.assertSafe(uri, uri))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("identical");
    }

    @Test
    void aUriNamingTheProductionDatabaseIsRefused() {
        assertThatThrownBy(() -> IntegrationTestSupport.assertSafe("mongodb://localhost:27017/finpulse", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("production database");
        assertThatThrownBy(() -> IntegrationTestSupport.assertSafe("mongodb://localhost:27017/finpulse?retryWrites=true", "x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aSeparateLocalOrTestClusterIsAccepted() {
        assertThatCode(() -> IntegrationTestSupport.assertSafe("mongodb://localhost:27017", "mongodb+srv://u:p@prod/finpulse"))
                .doesNotThrowAnyException();
        assertThatCode(() -> IntegrationTestSupport.assertSafe("mongodb://localhost:27017/finpulse_test", ""))
                .doesNotThrowAnyException();
    }

    @Test
    void randomDatabaseNamesNeverCollideWithProduction() {
        String a = IntegrationTestSupport.randomDatabaseName();
        String b = IntegrationTestSupport.randomDatabaseName();
        org.assertj.core.api.Assertions.assertThat(a).startsWith("finpulse_it_").isNotEqualTo(b).isNotEqualTo("finpulse");
    }
}
