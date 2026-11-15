package in.expensetrackerapp.dashboard.service;

import in.expensetrackerapp.common.exception.BadRequestException;
import in.expensetrackerapp.dashboard.config.DashboardProperties;
import in.expensetrackerapp.dashboard.dto.DashboardSummaryResponse;
import in.expensetrackerapp.dashboard.dto.DashboardSummaryResponse.CategoryExpense;
import in.expensetrackerapp.transaction.dto.TransactionResponse;
import in.expensetrackerapp.transaction.model.Transaction;
import in.expensetrackerapp.transaction.service.TransactionFilter;
import in.expensetrackerapp.transaction.service.TransactionService;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/** Sums are computed inside MongoDB (aggregation); no transaction list is loaded into memory. */
@Service
public class DashboardService {

    private static final Pattern MONTH_FORMAT = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final DashboardAggregations aggregations;
    private final MongoTemplate mongoTemplate;
    private final TransactionService transactionService;
    private final DashboardProperties properties;
    private final Clock clock;
    private final ZoneId zone;

    public DashboardService(DashboardAggregations aggregations, MongoTemplate mongoTemplate,
                            TransactionService transactionService, DashboardProperties properties, Clock clock) {
        this.aggregations = aggregations;
        this.mongoTemplate = mongoTemplate;
        this.transactionService = transactionService;
        this.properties = properties;
        this.clock = clock;
        this.zone = ZoneId.of(properties.getZone());       // fails fast on an invalid zone id
    }

    public DashboardSummaryResponse summary(String userId, String monthParam) {
        YearMonth month = resolveMonth(monthParam);
        String start = month.atDay(1).toString();
        String end = month.atEndOfMonth().toString();
        String currency = properties.getCurrency();

        List<Document> totals = aggregations.totals(userId, currency, start, end);
        Document row = totals.isEmpty() ? new Document() : totals.get(0);

        BigDecimal income = money(row.get("totalIncome"));
        BigDecimal expenses = money(row.get("totalExpenses"));
        BigDecimal refunds = money(row.get("totalRefunds"));
        BigDecimal monthIncome = money(row.get("monthIncome"));
        BigDecimal monthExpenses = money(row.get("monthExpenses"));
        BigDecimal monthRefunds = money(row.get("monthRefunds"));

        List<CategoryExpense> categories = aggregations.categoryExpenses(userId, currency, start, end).stream()
                .map(d -> new CategoryExpense(d.getString("_id"), money(d.get("total")),
                        d.get("transactionCount") instanceof Number n ? n.longValue() : 0L))
                .toList();

        List<TransactionResponse> recent = transactionService
                .list(userId, TransactionFilter.none(), 0, properties.getRecentCount()).content();

        return new DashboardSummaryResponse(
                month.toString(), currency,
                income, expenses, refunds, income.add(refunds).subtract(expenses),
                monthIncome, monthExpenses, monthRefunds, monthIncome.add(monthRefunds).subtract(monthExpenses),
                categories, recent,
                count(Criteria.where("userId").is(userId)),
                count(Criteria.where("userId").is(userId).and("needsReview").is(true)),
                count(Criteria.where("userId").is(userId).and("needsReview").is(false).and("category").is("Transfer")),
                count(Criteria.where("userId").is(userId).and("currency").nin(currency, null)));
    }

    private YearMonth resolveMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            return YearMonth.now(clock.withZone(zone));
        }
        String trimmed = monthParam.trim();
        if (!MONTH_FORMAT.matcher(trimmed).matches()) {
            throw new BadRequestException("month must be in yyyy-MM format");
        }
        try {
            return YearMonth.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("month must be in yyyy-MM format");
        }
    }

    private long count(Criteria criteria) {
        return mongoTemplate.count(new Query(criteria), Transaction.class);
    }

    static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal decimal = value instanceof Decimal128 d ? d.bigDecimalValue() : new BigDecimal(value.toString());
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }
}
