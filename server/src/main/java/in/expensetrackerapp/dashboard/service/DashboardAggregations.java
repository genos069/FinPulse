package in.expensetrackerapp.dashboard.service;

import in.expensetrackerapp.transaction.model.Transaction;
import org.bson.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the dashboard aggregation pipelines. The pipelines live in src/main/resources/dashboard/*.json,
 * the same files the Python test suite runs against MongoDB. Values are bound by replacing whole
 * placeholder strings inside the parsed document tree (never by concatenating text), so a user id can
 * never change the shape of a pipeline.
 */
@Component
public class DashboardAggregations {

    static final String TOTALS_RESOURCE = "dashboard/totals_pipeline.json";
    static final String CATEGORIES_RESOURCE = "dashboard/category_expenses_pipeline.json";
    private static final Pattern PLACEHOLDER = Pattern.compile("@@[A-Z_]+@@");

    private final MongoTemplate mongoTemplate;
    private final String totalsJson;
    private final String categoriesJson;

    public DashboardAggregations(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.totalsJson = loadResource(TOTALS_RESOURCE);
        this.categoriesJson = loadResource(CATEGORIES_RESOURCE);
    }

    /** One document with the sums, or an empty list when the user has no countable transactions. */
    public List<Document> totals(String userId, String currency, String monthStart, String monthEnd) {
        return run(bind(totalsJson, tokens(userId, currency, monthStart, monthEnd)));
    }

    public List<Document> categoryExpenses(String userId, String currency, String monthStart, String monthEnd) {
        return run(bind(categoriesJson, tokens(userId, currency, monthStart, monthEnd)));
    }

    private List<Document> run(List<Document> stages) {
        String collection = mongoTemplate.getCollectionName(Transaction.class);
        return mongoTemplate.getCollection(collection).aggregate(stages).into(new ArrayList<>());
    }

    private static Map<String, String> tokens(String userId, String currency, String monthStart, String monthEnd) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        return Map.of(
                "@@USER_ID@@", userId,
                "@@CURRENCY@@", currency,
                "@@MONTH_START@@", monthStart,
                "@@MONTH_END@@", monthEnd);
    }

    /** Parses a fresh copy of the pipeline and substitutes the tokens. Package-private for tests. */
    static List<Document> bind(String pipelineJson, Map<String, String> tokens) {
        // Every placeholder the template uses must be one we bind: checked on the TEMPLATE text, so a
        // bound value that merely looks like a placeholder is never mistaken for an unbound one.
        Matcher matcher = PLACEHOLDER.matcher(pipelineJson);
        while (matcher.find()) {
            if (!tokens.containsKey(matcher.group())) {
                throw new IllegalStateException("Unbound pipeline placeholder: " + matcher.group());
            }
        }
        List<Document> stages = Document.parse(pipelineJson).getList("pipeline", Document.class);
        substitute(stages, tokens);
        return stages;
    }

    @SuppressWarnings("unchecked")
    private static Object substitute(Object node, Map<String, String> tokens) {
        if (node instanceof String s) {
            return tokens.getOrDefault(s, s);
        }
        if (node instanceof Document d) {
            d.replaceAll((key, value) -> substitute(value, tokens));
            return d;
        }
        if (node instanceof List<?> list) {
            ((List<Object>) list).replaceAll(value -> substitute(value, tokens));
            return list;
        }
        return node;
    }

    static String loadResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + path, e);
        }
    }
}
