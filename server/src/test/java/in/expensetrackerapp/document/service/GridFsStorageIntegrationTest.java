package in.expensetrackerapp.document.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real GridFS round trip. Runs only when TEST_MONGODB_URI points at a throw-away MongoDB
 * (e.g. a local container or a dedicated test cluster). It always works inside a randomly named
 * database "finpulse_it_<uuid>" which is dropped afterwards, so it can never touch "finpulse".
 */
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
class GridFsStorageIntegrationTest {

    private MongoClient client;
    private String databaseName;
    private GridFsStorageService storage;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(System.getenv("TEST_MONGODB_URI"));
        databaseName = "finpulse_it_" + UUID.randomUUID().toString().replace("-", "");
        SimpleMongoClientDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(client, databaseName);
        MongoTemplate template = new MongoTemplate(factory);
        storage = new GridFsStorageService(new GridFsTemplate(factory, template.getConverter(), "fs"));
    }

    @AfterEach
    void tearDown() {
        client.getDatabase(databaseName).drop();
        client.close();
    }

    @Test
    void storeOpenAndDeleteRoundTrip() throws Exception {
        byte[] content = "%PDF-1.4 integration".getBytes(StandardCharsets.US_ASCII);

        String id = storage.store(new ByteArrayInputStream(content), "a.pdf", "application/pdf", "user-1", "doc-1");

        assertThat(storage.exists(id)).isTrue();
        try (InputStream in = storage.open(id).orElseThrow().getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }

        storage.deleteQuietly(id);
        assertThat(storage.exists(id)).isFalse();
        assertThat(storage.open(id)).isEmpty();
    }
}
