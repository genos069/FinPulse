package in.expensetrackerapp.dashboard.service;

import in.expensetrackerapp.common.exception.BadRequestException;
import in.expensetrackerapp.dashboard.config.DashboardProperties;
import in.expensetrackerapp.dashboard.dto.DashboardSummaryResponse;
import in.expensetrackerapp.transaction.dto.TransactionPageResponse;
import in.expensetrackerapp.transaction.dto.TransactionResponse;
import in.expensetrackerapp.transaction.model.Transaction;
import in.expensetrackerapp.transaction.model.TransactionDirection;
import in.expensetrackerapp.transaction.service.TransactionFilter;
import in.expensetrackerapp.transaction.service.TransactionService;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardAggregations aggregations;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private TransactionService transactionService;

    private final DashboardProperties properties = new DashboardProperties();

    private DashboardService serviceAt(String instant) {
        return new DashboardService(aggregations, mongoTemplate, transactionService, properties,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @BeforeEach
    void emptyDefaults() {
        // lenient: not every test reaches these calls (e.g. the invalid-month tests fail earlier)
        lenient().when(transactionService.list(any(), any(TransactionFilter.class), any(Integer.class), any(Integer.class)))
                .thenReturn(new TransactionPageResponse(List.of(), 0, 5, 0, 0));
        lenient().when(mongoTemplate.count(any(Query.class), eq(Transaction.class))).thenReturn(0L);
    }

    private static Decimal128 d(String value) {
        return new Decimal128(new BigDecimal(value));
    }

    @Test
    void mapsAggregationResultsToExactMoneyAndComputesNetCashFlow() {
        when(aggregations.totals("user-1", "INR", "2026-10-01", "2026-10-31")).thenReturn(List.of(new Document()
                .append("totalIncome", d("70000.00")).append("totalRefunds", d("300.00")).append("totalExpenses", d("1780.65"))
                .append("monthIncome", d("50000.00")).append("monthRefunds", d("300.00")).append("monthExpenses", d("780.65"))));
        when(aggregations.categoryExpenses("user-1", "INR", "2026-10-01", "2026-10-31")).thenReturn(List.of(
                new Document("_id", "Food").append("total", d("570.55")).append("transactionCount", 2),
                new Document("_id", "Transport").append("total", d("200.1")).append("transactionCount", 1L)));

        DashboardSummaryResponse r = serviceAt("2026-10-15T00:00:00Z").summary("user-1", null);

        assertThat(r.month()).isEqualTo("2026-10");
        assertThat(r.currency()).isEqualTo("INR");
        assertThat(r.totalIncome()).isEqualByComparingTo("70000.00");
        assertThat(r.totalExpenses()).isEqualByComparingTo("1780.65");
        assertThat(r.totalRefunds()).isEqualByComparingTo("300.00");
        assertThat(r.netCashFlow()).isEqualByComparingTo("68519.35");          // 70000 + 300 - 1780.65
        assertThat(r.currentMonthNetCashFlow()).isEqualByComparingTo("49519.35");  // 50000 + 300 - 780.65
        assertThat(r.categoryExpenses()).hasSize(2);
        assertThat(r.categoryExpenses().get(0).category()).isEqualTo("Food");
        assertThat(r.categoryExpenses().get(0).total()).isEqualByComparingTo("570.55");
        assertThat(r.categoryExpenses().get(0).transactionCount()).isEqualTo(2);
        assertThat(r.categoryExpenses().get(1).total().scale()).isEqualTo(2);
    }

    @Test
    void emptyAccountGivesZerosNotNullsOrErrors() {
        when(aggregations.totals(any(), any(), any(), any())).thenReturn(List.of());
        when(aggregations.categoryExpenses(any(), any(), any(), any())).thenReturn(List.of());

        DashboardSummaryResponse r = serviceAt("2026-10-15T00:00:00Z").summary("user-1", null);

        for (BigDecimal value : List.of(r.totalIncome(), r.totalExpenses(), r.totalRefunds(), r.netCashFlow(),
                r.currentMonthIncome(), r.currentMonthExpenses(), r.currentMonthRefunds(), r.currentMonthNetCashFlow())) {
            assertThat(value).isEqualByComparingTo("0");
            assertThat(value.scale()).isEqualTo(2);
        }
        assertThat(r.categoryExpenses()).isEmpty();
        assertThat(r.recentTransactions()).isEmpty();
        assertThat(r.transactionCount()).isZero();
        assertThat(r.pendingReviewCount()).isZero();
    }

    @Test
    void defaultMonthFollowsTheConfiguredZoneNotUtc() {
        when(aggregations.totals(any(), any(), any(), any())).thenReturn(List.of());
        when(aggregations.categoryExpenses(any(), any(), any(), any())).thenReturn(List.of());

        // 2026-09-30 20:00 UTC is already 1 October 01:30 in Asia/Kolkata (UTC+05:30).
        DashboardSummaryResponse r = serviceAt("2026-09-30T20:00:00Z").summary("user-1", null);

        assertThat(r.month()).isEqualTo("2026-10");
        verify(aggregations).totals("user-1", "INR", "2026-10-01", "2026-10-31");
    }

    @Test
    void explicitMonthIsUsedAndEndOfMonthIsCalendarCorrect() {
        when(aggregations.totals(any(), any(), any(), any())).thenReturn(List.of());
        when(aggregations.categoryExpenses(any(), any(), any(), any())).thenReturn(List.of());

        serviceAt("2026-10-15T00:00:00Z").summary("user-1", "2028-02");     // leap year
        verify(aggregations).totals("user-1", "INR", "2028-02-01", "2028-02-29");
        verify(aggregations).categoryExpenses("user-1", "INR", "2028-02-01", "2028-02-29");
    }

    @Test
    void invalidMonthIsRejectedBeforeAnyQuery() {
        DashboardService service = serviceAt("2026-10-15T00:00:00Z");
        for (String bad : List.of("2026-13", "2026-1", "abc", "2026-10-01", "10-2026", "2026-00")) {
            assertThatThrownBy(() -> service.summary("user-1", bad)).isInstanceOf(BadRequestException.class);
        }
        verify(aggregations, never()).totals(any(), any(), any(), any());
    }

    @Test
    void everyCountQueryIsScopedToTheUserAndUsesTheRightRule() {
        when(aggregations.totals(any(), any(), any(), any())).thenReturn(List.of());
        when(aggregations.categoryExpenses(any(), any(), any(), any())).thenReturn(List.of());

        serviceAt("2026-10-15T00:00:00Z").summary("user-1", null);

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, org.mockito.Mockito.times(4)).count(queries.capture(), eq(Transaction.class));
        List<Document> filters = queries.getAllValues().stream().map(Query::getQueryObject).toList();

        assertThat(filters).allSatisfy(f -> assertThat(f.get("userId")).isEqualTo("user-1"));
        assertThat(filters).anySatisfy(f -> assertThat(f.keySet()).containsExactly("userId"));                // all rows
        assertThat(filters).anySatisfy(f -> assertThat(f.get("needsReview")).isEqualTo(true));                // pending
        assertThat(filters).anySatisfy(f -> {                                                                // transfers
            assertThat(f.get("category")).isEqualTo("Transfer");
            assertThat(f.get("needsReview")).isEqualTo(false);
        });
        assertThat(filters).anySatisfy(f -> assertThat(f.containsKey("currency")).isTrue());                 // other currency
    }

    @Test
    void recentTransactionsComeFromTheOwnerScopedTransactionService() {
        when(aggregations.totals(any(), any(), any(), any())).thenReturn(List.of());
        when(aggregations.categoryExpenses(any(), any(), any(), any())).thenReturn(List.of());
        TransactionResponse recent = new TransactionResponse("t1", "d", "j", "2026-10-03", new BigDecimal("1.00"),
                "INR", TransactionDirection.DEBIT, "M", "Food", "x", "s", null, null, false, null,
                Instant.EPOCH, Instant.EPOCH);
        when(transactionService.list(eq("user-1"), any(TransactionFilter.class), eq(0), eq(5)))
                .thenReturn(new TransactionPageResponse(List.of(recent), 0, 5, 1, 1));

        DashboardSummaryResponse r = serviceAt("2026-10-15T00:00:00Z").summary("user-1", null);

        assertThat(r.recentTransactions()).containsExactly(recent);
    }

    @Test
    void moneyConversionHandlesAllNumericTypesFromMongoDB() {
        assertThat(DashboardService.money(null)).isEqualByComparingTo("0");
        assertThat(DashboardService.money(0)).isEqualByComparingTo("0");
        assertThat(DashboardService.money(12L)).isEqualByComparingTo("12.00");
        assertThat(DashboardService.money(d("1234567.891"))).isEqualByComparingTo("1234567.89");
        assertThat(DashboardService.money(d("0.1")).add(DashboardService.money(d("0.2")))).isEqualByComparingTo("0.30");
    }
}
