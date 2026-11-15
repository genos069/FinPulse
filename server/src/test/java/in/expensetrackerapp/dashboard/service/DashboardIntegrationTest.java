package in.expensetrackerapp.dashboard.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import in.expensetrackerapp.dashboard.config.DashboardProperties;
import in.expensetrackerapp.dashboard.dto.DashboardSummaryResponse;
import in.expensetrackerapp.transaction.model.Transaction;
import in.expensetrackerapp.transaction.model.TransactionDirection;
import in.expensetrackerapp.transaction.repository.TransactionRepository;
import in.expensetrackerapp.transaction.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard aggregation against a real MongoDB with transactions written through the Java entity
 * (Decimal128). Same scenario as the Python test_dashboard_pipelines.py. Runs only when TEST_MONGODB_URI is
 * set, in a random throw-away database.
 */
@EnabledIfEnvironmentVariable(named = "TEST_MONGODB_URI", matches = ".+")
class DashboardIntegrationTest {

    private MongoClient client;
    private String databaseName;
    private MongoTemplate template;
    private DashboardService service;
    private int sequence;

    @BeforeEach
    void setUp() {
        client = MongoClients.create(System.getenv("TEST_MONGODB_URI"));
        databaseName = "finpulse_it_" + UUID.randomUUID().toString().replace("-", "");
        template = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, databaseName));
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T06:00:00Z"), ZoneOffset.UTC);
        TransactionRepository repository = new MongoRepositoryFactory(template).getRepository(TransactionRepository.class);
        service = new DashboardService(new DashboardAggregations(template), template,
                new TransactionService(repository, template, clock), new DashboardProperties(), clock);
        seed();
    }

    @AfterEach
    void tearDown() {
        client.getDatabase(databaseName).drop();
        client.close();
    }

    private void add(String user, String date, String amount, TransactionDirection direction, String category,
                     String currency, boolean needsReview, String pipelineCategory) {
        Transaction t = new Transaction();
        t.setUserId(user);
        t.setSourceHash("hash-" + (++sequence));
        t.setDate(date);
        t.setAmount(new BigDecimal(amount));
        t.setDirection(direction);
        t.setCategory(category);
        t.setPipelineCategory(pipelineCategory);
        t.setCurrency(currency);
        t.setNeedsReview(needsReview);
        t.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
        t.setUpdatedAt(Instant.parse("2026-10-01T00:00:00Z"));
        template.insert(t);
    }

    private void seed() {
        String a = "user-A";
        add(a, "2026-10-01", "50000.00", TransactionDirection.CREDIT, "Salary", "INR", false, "Salary");
        add(a, "2026-10-03", "450.00", TransactionDirection.DEBIT, "Food", "INR", false, "Food & Dining");
        add(a, "2026-10-04", "120.55", TransactionDirection.DEBIT, "Food", "INR", false, "Food & Dining");
        add(a, "2026-10-05", "200.10", TransactionDirection.DEBIT, "Transport", "INR", false, "Transport");
        add(a, "2026-10-06", "5000.00", TransactionDirection.DEBIT, "Transfer", "INR", false, "Transfers");       // transfer
        add(a, "2026-10-07", "300.00", TransactionDirection.CREDIT, "Other", "INR", false, "Refund / Cashback"); // refund
        add(a, "2026-10-08", "999.00", TransactionDirection.UNKNOWN, "Shopping", "INR", true, "Shopping");       // review
        add(a, "2026-09-15", "1000.00", TransactionDirection.DEBIT, "Shopping", "INR", false, "Shopping");
        add(a, "2026-09-01", "20000.00", TransactionDirection.CREDIT, "Salary", "INR", false, "Salary");
        add(a, "2026-10-09", "50.00", TransactionDirection.DEBIT, "Shopping", "USD", false, "Shopping");          // USD
        add(a, "2026-10-10", "10.00", TransactionDirection.DEBIT, "Bills", null, false, "Bills & Utilities");     // no currency
        add(a, "2026-10-11", "77.00", TransactionDirection.DEBIT, "Food", "INR", true, "Food & Dining");          // review
        add("user-B", "2026-10-02", "123456.00", TransactionDirection.CREDIT, "Salary", "INR", false, "Salary");
        add("user-B", "2026-10-03", "654321.00", TransactionDirection.DEBIT, "Shopping", "INR", false, "Shopping");
    }

    @Test
    void totalsForAllTimeAndTheCurrentMonth() {
        DashboardSummaryResponse r = service.summary("user-A", null);          // clock says October 2026

        assertThat(r.month()).isEqualTo("2026-10");
        assertThat(r.totalIncome()).isEqualByComparingTo("70000.00");
        assertThat(r.totalRefunds()).isEqualByComparingTo("300.00");
        assertThat(r.totalExpenses()).isEqualByComparingTo("1780.65");
        assertThat(r.netCashFlow()).isEqualByComparingTo("68519.35");
        assertThat(r.currentMonthIncome()).isEqualByComparingTo("50000.00");
        assertThat(r.currentMonthRefunds()).isEqualByComparingTo("300.00");
        assertThat(r.currentMonthExpenses()).isEqualByComparingTo("780.65");
        assertThat(r.currentMonthNetCashFlow()).isEqualByComparingTo("49519.35");
    }

    @Test
    void monthFilterChangesOnlyTheMonthFigures() {
        DashboardSummaryResponse r = service.summary("user-A", "2026-09");

        assertThat(r.currentMonthIncome()).isEqualByComparingTo("20000.00");
        assertThat(r.currentMonthExpenses()).isEqualByComparingTo("1000.00");
        assertThat(r.totalExpenses()).isEqualByComparingTo("1780.65");
        assertThat(r.categoryExpenses()).extracting(c -> c.category()).containsExactly("Shopping");
    }

    @Test
    void categoryTotalsAreSortedAndExcludeTransfersReviewRowsAndOtherCurrencies() {
        DashboardSummaryResponse r = service.summary("user-A", "2026-10");

        assertThat(r.categoryExpenses()).extracting(c -> c.category()).containsExactly("Food", "Transport", "Bills");
        assertThat(r.categoryExpenses().get(0).total()).isEqualByComparingTo("570.55");
        assertThat(r.categoryExpenses().get(0).transactionCount()).isEqualTo(2);
    }

    @Test
    void countsExplainWhatWasLeftOut() {
        DashboardSummaryResponse r = service.summary("user-A", "2026-10");

        assertThat(r.transactionCount()).isEqualTo(12);
        assertThat(r.pendingReviewCount()).isEqualTo(2);
        assertThat(r.excludedTransferCount()).isEqualTo(1);
        assertThat(r.otherCurrencyCount()).isEqualTo(1);
        assertThat(r.recentTransactions()).hasSize(5);
        assertThat(r.recentTransactions().get(0).date()).isEqualTo("2026-10-11");     // newest first, includes review rows
    }

    @Test
    void usersAreIsolatedAndAnEmptyAccountGivesZeros() {
        DashboardSummaryResponse b = service.summary("user-B", null);
        assertThat(b.totalIncome()).isEqualByComparingTo("123456.00");
        assertThat(b.totalExpenses()).isEqualByComparingTo("654321.00");

        DashboardSummaryResponse nobody = service.summary("nobody", null);
        assertThat(nobody.totalIncome()).isEqualByComparingTo("0");
        assertThat(nobody.netCashFlow()).isEqualByComparingTo("0");
        assertThat(nobody.categoryExpenses()).isEmpty();
        assertThat(nobody.transactionCount()).isZero();
    }

    @Test
    void decimalSumsAreExact() {
        for (int i = 0; i < 10; i++) {
            add("user-C", "2026-10-05", "0.10", TransactionDirection.DEBIT, "Food", "INR", false, "Food & Dining");
        }
        assertThat(service.summary("user-C", null).totalExpenses()).isEqualByComparingTo("1.00");
    }
}
