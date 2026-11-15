package in.expensetrackerapp.transaction.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Canonical transaction. Rows are written by the Python worker and edited through the REST API.
 *
 * - amount is always positive; direction carries the sign.
 * - amount and balance are stored as BSON Decimal128 (never floating point).
 * - date is an ISO "yyyy-MM-dd" string: it sorts chronologically and cannot be shifted by the JVM's
 *   timezone (Spring Data converts LocalDate to a BSON date in the system zone).
 * - Index names/options must equal contracts/transaction_indexes.json (the worker creates the same ones).
 */
@Document(collection = "transactions")
@CompoundIndexes({
        @CompoundIndex(name = "userId_sourceHash_unique", def = "{'userId': 1, 'sourceHash': 1}", unique = true),
        @CompoundIndex(name = "userId_date", def = "{'userId': 1, 'date': -1}"),
        @CompoundIndex(name = "userId_category_date", def = "{'userId': 1, 'category': 1, 'date': -1}"),
        @CompoundIndex(name = "userId_direction_date", def = "{'userId': 1, 'direction': 1, 'date': -1}"),
        @CompoundIndex(name = "userId_needsReview", def = "{'userId': 1, 'needsReview': 1}")
})
public class Transaction {

    @Id
    private String id;

    private String userId;
    private String documentId;
    private String jobId;
    private String date;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    private String currency;
    private TransactionDirection direction;
    private String merchant;
    private String category;
    private String pipelineCategory;
    private String description;
    private String source;
    private String referenceNumber;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal balance;

    private String sourceHash;
    private boolean needsReview;
    private String possibleDuplicateOf;
    private Instant createdAt;
    private Instant updatedAt;

    public Transaction() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public TransactionDirection getDirection() { return direction; }
    public void setDirection(TransactionDirection direction) { this.direction = direction; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPipelineCategory() { return pipelineCategory; }
    public void setPipelineCategory(String pipelineCategory) { this.pipelineCategory = pipelineCategory; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }

    public boolean isNeedsReview() { return needsReview; }
    public void setNeedsReview(boolean needsReview) { this.needsReview = needsReview; }

    public String getPossibleDuplicateOf() { return possibleDuplicateOf; }
    public void setPossibleDuplicateOf(String possibleDuplicateOf) { this.possibleDuplicateOf = possibleDuplicateOf; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
