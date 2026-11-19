package in.expensetrackerapp.testsupport;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import java.util.UUID;

/**
 * Shared guard for every test that talks to a real MongoDB (enabled by TEST_MONGODB_URI).
 * Tests always work inside a random throw-away database; this additionally refuses a URI that is the
 * production connection string or that names the production database.
 */
public final class IntegrationTestSupport {

    public static final String ENV = "TEST_MONGODB_URI";
    public static final String PRODUCTION_DATABASE = "finpulse";

    private IntegrationTestSupport() {
    }

    /** Validated TEST_MONGODB_URI. Throws IllegalStateException when it could point at production. */
    public static String uri() {
        String uri = System.getenv(ENV);
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException(ENV + " is not set");
        }
        assertSafe(uri.trim(), System.getenv("MONGODB_URI"));
        return uri.trim();
    }

    public static MongoClient client() {
        return MongoClients.create(uri());
    }

    public static String randomDatabaseName() {
        return "finpulse_it_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** Package-visible for IntegrationTestSupportTest. */
    public static void assertSafe(String testUri, String productionUri) {
        if (productionUri != null && !productionUri.isBlank() && testUri.equals(productionUri.trim())) {
            throw new IllegalStateException(
                    ENV + " is identical to MONGODB_URI: refusing to run tests on it. Use a separate test cluster or a local MongoDB.");
        }
        String withoutQuery = testUri.split("\\?", 2)[0];
        String path = withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1);
        if (path.equals(PRODUCTION_DATABASE)) {
            throw new IllegalStateException(ENV + " names the production database '" + PRODUCTION_DATABASE + "'");
        }
    }
}
