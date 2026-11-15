package in.expensetrackerapp.transaction.model;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Python worker creates the same indexes on the same collection. This checks that the Java entity
 * declares exactly what pipelines/.../contracts/transaction_indexes.json says (the Python tests check
 * the same file), so the two services can never disagree about index names or uniqueness.
 */
class TransactionIndexContractTest {

    private static final Path CONTRACT = Path.of(
            "..", "pipelines", "financial_transaction_extraction", "contracts", "transaction_indexes.json");

    @Test
    void entityIndexesMatchTheSharedContract() throws Exception {
        Document contract = Document.parse(Files.readString(CONTRACT));
        List<Document> expected = contract.getList("indexes", Document.class);

        CompoundIndex[] declared = Transaction.class.getAnnotation(CompoundIndexes.class).value();

        assertThat(declared).hasSameSizeAs(expected);
        for (int i = 0; i < declared.length; i++) {
            Document want = expected.get(i);
            assertThat(declared[i].name()).isEqualTo(want.getString("name"));
            assertThat(declared[i].unique()).isEqualTo(want.getBoolean("unique"));

            Document def = Document.parse(declared[i].def());
            List<String> wantKeys = new ArrayList<>();
            List<Integer> wantDirections = new ArrayList<>();
            for (Object pair : want.getList("keys", Object.class)) {
                List<?> kv = (List<?>) pair;
                wantKeys.add((String) kv.get(0));
                wantDirections.add(((Number) kv.get(1)).intValue());
            }
            assertThat(new ArrayList<>(def.keySet())).as("key order of %s", declared[i].name()).isEqualTo(wantKeys);
            assertThat(def.values().stream().map(v -> ((Number) v).intValue()).toList()).isEqualTo(wantDirections);
        }
    }

    @Test
    void moneyFieldsAreStoredAsDecimal128() throws Exception {
        for (String field : new String[]{"amount", "balance"}) {
            var f = Transaction.class.getDeclaredField(field);
            assertThat(f.getType()).isEqualTo(BigDecimal.class);
            Field mapping = f.getAnnotation(Field.class);
            assertThat(mapping).as("@Field on %s", field).isNotNull();
            assertThat(mapping.targetType()).isEqualTo(FieldType.DECIMAL128);
        }
    }

    @Test
    void categoriesAreExactlyTheThirteenStandardOnes() {
        assertThat(TransactionCategory.values()).extracting(TransactionCategory::getValue).containsExactly(
                "Food", "Shopping", "Travel", "Transport", "Bills", "Entertainment", "Healthcare", "Education",
                "Rent", "Salary", "Investment", "Transfer", "Other");
        assertThat(TransactionCategory.fromValue("  healthcare ")).contains(TransactionCategory.HEALTHCARE);
        assertThat(TransactionCategory.fromValue("Groceries")).isEmpty();
        assertThat(TransactionCategory.fromValue(null)).isEmpty();
    }
}
