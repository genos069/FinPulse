package in.expensetrackerapp.transaction.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.transaction.dto.TransactionPageResponse;
import in.expensetrackerapp.transaction.dto.TransactionUpdateRequest;
import in.expensetrackerapp.transaction.model.Transaction;
import in.expensetrackerapp.transaction.model.TransactionDirection;
import in.expensetrackerapp.transaction.repository.TransactionRepository;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-MongoDB tests (decimal storage, per-user uniqueness, filters, pagination, isolation).
 * Runs only when TEST_MONGODB_URI is set; uses a random throw-away database that is dropped afterwards.
 */
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
class TransactionIntegrationTest {

    private MongoClient client;
    private String databaseName;
    private MongoTemplate template;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(System.getenv("TEST_MONGODB_URI"));
        databaseName = "finpulse_it_" + UUID.randomUUID().toString().replace("-", "");
        template = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, databaseName));
        TransactionRepository repository = new MongoRepositoryFactory(template).getRepository(TransactionRepository.class);
        service = new TransactionService(repository, template,
                Clock.fixed(Instant.parse("2026-02-01T10:00:00Z"), ZoneOffset.UTC));

        // Create the indexes the entity declares (a plain MongoTemplate does not auto-create them).
        for (CompoundIndex index : Transaction.class.getAnnotation(CompoundIndexes.class).value()) {
            CompoundIndexDefinition definition = new CompoundIndexDefinition(Document.parse(index.def()));
            definition.named(index.name());
            if (index.unique()) {
                definition.unique();
            }
            template.indexOps(Transaction.class).createIndex(definition);
        }
    }

    @AfterEach
    void tearDown() {
        client.getDatabase(databaseName).drop();
        client.close();
    }

    private Transaction save(String user, String hash, String date, String amount, TransactionDirection direction,
                             String category) {
        Transaction t = new Transaction();
        t.setUserId(user);
        t.setSourceHash(hash);
        t.setDate(date);
        t.setAmount(new BigDecimal(amount));
        t.setDirection(direction);
        t.setCategory(category);
        t.setMerchant("M-" + hash);
        t.setCurrency("INR");
        t.setNeedsReview(false);
        t.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        t.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return template.insert(t);
    }

    @Test
    void moneyIsStoredAsDecimal128AndKeepsExactPrecision() {
        Transaction saved = save("user-1", "h1", "2026-01-05", "1234567.89", TransactionDirection.DEBIT, "Food");

        Document raw = client.getDatabase(databaseName).getCollection("transactions").find().first();
        assertThat(raw.get("amount")).isInstanceOf(Decimal128.class);
        assertThat(((Decimal128) raw.get("amount")).bigDecimalValue()).isEqualByComparingTo("1234567.89");
        assertThat(service.get("user-1", saved.getId()).amount()).isEqualByComparingTo("1234567.89");
    }

    @Test
    void sourceHashIsUniquePerUserNotGlobally() {
        save("user-1", "same", "2026-01-05", "10.00", TransactionDirection.DEBIT, "Food");
        save("user-2", "same", "2026-01-05", "10.00", TransactionDirection.DEBIT, "Food");   // other user: fine

        assertThatThrownBy(() -> save("user-1", "same", "2026-01-06", "11.00", TransactionDirection.DEBIT, "Food"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void listFiltersByDateCategoryDirectionAndReviewFlagForTheOwnerOnly() {
        save("user-1", "a", "2026-01-05", "10.00", TransactionDirection.DEBIT, "Food");
        save("user-1", "b", "2026-01-15", "20.00", TransactionDirection.CREDIT, "Salary");
        save("user-1", "c", "2026-02-10", "30.00", TransactionDirection.DEBIT, "Food");
        Transaction flagged = save("user-1", "d", "2026-01-20", "40.00", TransactionDirection.UNKNOWN, "Other");
        template.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("id").is(flagged.getId())),
                new org.springframework.data.mongodb.core.query.Update().set("needsReview", true), Transaction.class);
        save("user-2", "x", "2026-01-05", "99.00", TransactionDirection.DEBIT, "Food");

        assertThat(service.list("user-1", TransactionFilter.none(), 0, 20).totalElements()).isEqualTo(4);
        assertThat(service.list("user-1", new TransactionFilter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                null, null, null), 0, 20).totalElements()).isEqualTo(3);
        assertThat(service.list("user-1", new TransactionFilter(null, null, "food", null, null), 0, 20).totalElements())
                .isEqualTo(2);
        assertThat(service.list("user-1", new TransactionFilter(null, null, null, TransactionDirection.CREDIT, null), 0, 20)
                .content()).hasSize(1);
        assertThat(service.list("user-1", new TransactionFilter(null, null, null, null, true), 0, 20).content())
                .extracting(r -> r.id()).containsExactly(flagged.getId());
        assertThat(service.list("user-2", TransactionFilter.none(), 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void paginationIsNewestFirstWithoutOverlap() {
        for (int day = 1; day <= 7; day++) {
            save("user-1", "h" + day, String.format("2026-01-%02d", day), "1.00", TransactionDirection.DEBIT, "Food");
        }

        TransactionPageResponse first = service.list("user-1", TransactionFilter.none(), 0, 3);
        TransactionPageResponse second = service.list("user-1", TransactionFilter.none(), 1, 3);
        TransactionPageResponse third = service.list("user-1", TransactionFilter.none(), 2, 3);

        assertThat(first.totalElements()).isEqualTo(7);
        assertThat(first.totalPages()).isEqualTo(3);
        List<String> dates = java.util.stream.Stream.of(first, second, third)
                .flatMap(p -> p.content().stream()).map(r -> r.date()).toList();
        assertThat(dates).containsExactly("2026-01-07", "2026-01-06", "2026-01-05", "2026-01-04", "2026-01-03",
                "2026-01-02", "2026-01-01");
    }

    @Test
    void updateChangesOnlyRequestedFieldsAndKeepsDecimalPrecision() {
        Transaction saved = save("user-1", "a", "2026-01-05", "10.00", TransactionDirection.UNKNOWN, "Other");

        service.update("user-1", saved.getId(), new TransactionUpdateRequest(
                null, new BigDecimal("12.50"), TransactionDirection.DEBIT, null, "transport", null, false));

        var updated = service.get("user-1", saved.getId());
        assertThat(updated.amount()).isEqualByComparingTo("12.50");
        assertThat(updated.direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(updated.category()).isEqualTo("Transport");
        assertThat(updated.needsReview()).isFalse();
        assertThat(updated.date()).isEqualTo("2026-01-05");
        assertThat(updated.updatedAt()).isEqualTo(Instant.parse("2026-02-01T10:00:00Z"));
        Document raw = client.getDatabase(databaseName).getCollection("transactions").find().first();
        assertThat(raw.get("amount")).isInstanceOf(Decimal128.class);
    }

    @Test
    void usersCannotReadEditOrDeleteEachOthersTransactions() {
        Transaction mine = save("user-1", "a", "2026-01-05", "10.00", TransactionDirection.DEBIT, "Food");

        assertThatThrownBy(() -> service.get("user-2", mine.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update("user-2", mine.getId(), new TransactionUpdateRequest(
                null, null, null, "Hacked", null, null, null))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete("user-2", mine.getId())).isInstanceOf(ResourceNotFoundException.class);

        assertThat(service.get("user-1", mine.getId()).merchant()).isEqualTo("M-a");
        service.delete("user-1", mine.getId());
        assertThatThrownBy(() -> service.get("user-1", mine.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void malformedIdsAreSimplyNotFound() {
        assertThatThrownBy(() -> service.get("user-1", "not-an-object-id")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete("user-1", "not-an-object-id")).isInstanceOf(ResourceNotFoundException.class);
    }
}
