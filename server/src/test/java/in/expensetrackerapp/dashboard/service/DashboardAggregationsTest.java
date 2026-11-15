package in.expensetrackerapp.dashboard.service;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static in.expensetrackerapp.dashboard.service.DashboardAggregations.CATEGORIES_RESOURCE;
import static in.expensetrackerapp.dashboard.service.DashboardAggregations.TOTALS_RESOURCE;
import static in.expensetrackerapp.dashboard.service.DashboardAggregations.bind;
import static in.expensetrackerapp.dashboard.service.DashboardAggregations.loadResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pipeline binding checks (no database). The pipelines' numeric behaviour is verified by
 * DashboardIntegrationTest (real MongoDB) and by the Python test_dashboard_pipelines.py, which run the same files.
 */
class DashboardAggregationsTest {

    private static Map<String, String> tokens(String userId) {
        return Map.of("@@USER_ID@@", userId, "@@CURRENCY@@", "INR",
                "@@MONTH_START@@", "2026-10-01", "@@MONTH_END@@", "2026-10-31");
    }

    @Test
    void bothPipelinesStartWithAnOwnerMatchBoundToTheUser() {
        for (String resource : List.of(TOTALS_RESOURCE, CATEGORIES_RESOURCE)) {
            List<Document> stages = bind(loadResource(resource), tokens("user-42"));

            Document match = (Document) stages.get(0).get("$match");
            assertThat(match.get("userId")).as(resource).isEqualTo("user-42");
            assertThat(match.get("needsReview")).isEqualTo(false);
            assertThat(match.get("category")).isEqualTo(new Document("$ne", "Transfer"));
        }
    }

    @Test
    void everyPlaceholderIsReplaced() {
        for (String resource : List.of(TOTALS_RESOURCE, CATEGORIES_RESOURCE)) {
            String rendered = bind(loadResource(resource), tokens("user-42")).toString();
            assertThat(rendered).doesNotContain("@@");
            assertThat(rendered).contains("2026-10-01").contains("2026-10-31").contains("INR");
        }
    }

    @Test
    void aUserIdIsAlwaysBoundAsALiteralStringNeverAsPipelineStructure() {
        String hostile = "{\"$ne\": null}";
        List<Document> stages = bind(loadResource(TOTALS_RESOURCE), tokens(hostile));

        Object bound = ((Document) stages.get(0).get("$match")).get("userId");
        assertThat(bound).isInstanceOf(String.class).isEqualTo(hostile);
    }

    @Test
    void aValueThatLooksLikeAPlaceholderIsStillJustAValue() {
        List<Document> stages = bind(loadResource(TOTALS_RESOURCE), tokens("@@weird@@"));
        assertThat(((Document) stages.get(0).get("$match")).get("userId")).isEqualTo("@@weird@@");
    }

    @Test
    void eachBindReturnsAnIndependentCopy() {
        String json = loadResource(TOTALS_RESOURCE);
        List<Document> first = bind(json, tokens("user-1"));
        ((Document) first.get(0).get("$match")).put("userId", "tampered");

        List<Document> second = bind(json, tokens("user-2"));
        assertThat(((Document) second.get(0).get("$match")).get("userId")).isEqualTo("user-2");
    }

    @Test
    void aTemplateUsingAnUnknownPlaceholderIsRejected() {
        String json = "{\"pipeline\":[{\"$match\":{\"userId\":\"@@USER_ID@@\",\"x\":\"@@NOT_BOUND@@\"}}]}";
        assertThatThrownBy(() -> bind(json, tokens("u"))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@@NOT_BOUND@@");
    }

    @Test
    void totalsPipelineDefinesEverySumTheServiceReads() {
        Document group = (Document) bind(loadResource(TOTALS_RESOURCE), tokens("u")).get(1).get("$group");
        assertThat(group.keySet()).containsExactlyInAnyOrder("_id", "totalIncome", "totalRefunds", "totalExpenses",
                "monthIncome", "monthRefunds", "monthExpenses");
    }
}
