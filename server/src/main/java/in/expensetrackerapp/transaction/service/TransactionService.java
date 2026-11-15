package in.expensetrackerapp.transaction.service;

import in.expensetrackerapp.common.exception.BadRequestException;
import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.transaction.dto.TransactionPageResponse;
import in.expensetrackerapp.transaction.dto.TransactionResponse;
import in.expensetrackerapp.transaction.dto.TransactionUpdateRequest;
import in.expensetrackerapp.transaction.model.Transaction;
import in.expensetrackerapp.transaction.model.TransactionCategory;
import in.expensetrackerapp.transaction.model.TransactionDirection;
import in.expensetrackerapp.transaction.repository.TransactionRepository;
import org.bson.types.Decimal128;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Every method takes the authenticated user's id and scopes every query by it. */
@Service
public class TransactionService {

    static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository repository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public TransactionService(TransactionRepository repository, MongoTemplate mongoTemplate, Clock clock) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public TransactionPageResponse list(String userId, TransactionFilter filter, int page, int size) {
        Query countQuery = buildQuery(userId, filter);
        long total = mongoTemplate.count(countQuery, Transaction.class);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // Newest first; createdAt and id make the order total, so pages never overlap or skip rows.
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "date", "createdAt", "id"));
        List<TransactionResponse> content = mongoTemplate
                .find(buildQuery(userId, filter).with(pageable), Transaction.class)
                .stream().map(TransactionResponse::fromTransaction).toList();

        int totalPages = (int) Math.ceil(total / (double) safeSize);
        return new TransactionPageResponse(content, safePage, safeSize, total, totalPages);
    }

    public TransactionResponse get(String userId, String id) {
        return TransactionResponse.fromTransaction(findOwned(userId, id));
    }

    public TransactionResponse update(String userId, String id, TransactionUpdateRequest request) {
        if (request.hasNoChanges()) {
            throw new BadRequestException("No fields to update");
        }
        Transaction existing = findOwned(userId, id);

        Update update = new Update();
        TransactionDirection direction = existing.getDirection();
        String date = existing.getDate();

        if (request.category() != null) {
            TransactionCategory category = TransactionCategory.fromValue(request.category())
                    .orElseThrow(() -> new BadRequestException("Unknown category"));
            update.set("category", category.getValue());
        }
        if (request.direction() != null) {
            if (request.direction() == TransactionDirection.UNKNOWN) {
                throw new BadRequestException("Direction must be DEBIT or CREDIT");
            }
            direction = request.direction();
            update.set("direction", direction);
        }
        if (request.date() != null) {
            try {
                date = LocalDate.parse(request.date()).toString();
            } catch (DateTimeParseException e) {
                throw new BadRequestException("date is not a valid calendar date");
            }
            update.set("date", date);
        }
        if (request.amount() != null) {
            // Stored as Decimal128 with scale 2. Bean validation already limits input to 2 decimals;
            // this guard keeps the service safe even when called without it (no silent rounding of money).
            BigDecimal amount;
            try {
                amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException e) {
                throw new BadRequestException("amount must have at most 2 decimal places");
            }
            update.set("amount", new Decimal128(amount));
        }
        if (request.merchant() != null) {
            String merchant = request.merchant().strip();
            if (merchant.isEmpty()) {
                throw new BadRequestException("merchant must not be blank");
            }
            update.set("merchant", merchant);
        }
        if (request.description() != null) {
            update.set("description", request.description());
        }
        if (request.needsReview() != null) {
            boolean unresolved = direction == TransactionDirection.UNKNOWN || date == null;
            if (!request.needsReview() && unresolved) {
                throw new BadRequestException(
                        "Set the direction and date before marking this transaction as reviewed");
            }
            update.set("needsReview", request.needsReview());
        }
        update.set("updatedAt", clock.instant());

        Transaction updated = mongoTemplate.findAndModify(
                ownedQuery(userId, id), update, FindAndModifyOptions.options().returnNew(true), Transaction.class);
        if (updated == null) {
            throw new ResourceNotFoundException("Transaction not found");
        }
        return TransactionResponse.fromTransaction(updated);
    }

    public void delete(String userId, String id) {
        if (repository.deleteByIdAndUserId(id, userId) == 0) {
            throw new ResourceNotFoundException("Transaction not found");
        }
    }

    private Transaction findOwned(String userId, String id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
    }

    private static Query ownedQuery(String userId, String id) {
        return new Query(Criteria.where("id").is(id).and("userId").is(userId));
    }

    private Query buildQuery(String userId, TransactionFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        Criteria criteria = Criteria.where("userId").is(userId);
        if (filter.from() != null && filter.to() != null) {
            criteria = criteria.and("date").gte(filter.from().toString()).lte(filter.to().toString());
        } else if (filter.from() != null) {
            criteria = criteria.and("date").gte(filter.from().toString());
        } else if (filter.to() != null) {
            criteria = criteria.and("date").lte(filter.to().toString());
        }
        if (filter.category() != null) {
            TransactionCategory category = TransactionCategory.fromValue(filter.category())
                    .orElseThrow(() -> new BadRequestException("Unknown category"));
            criteria = criteria.and("category").is(category.getValue());
        }
        if (filter.direction() != null) {
            criteria = criteria.and("direction").is(filter.direction());
        }
        if (filter.needsReview() != null) {
            criteria = criteria.and("needsReview").is(filter.needsReview());
        }
        return new Query(criteria);
    }
}
