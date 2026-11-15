package in.expensetrackerapp.transaction.service;

import in.expensetrackerapp.common.exception.BadRequestException;
import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.transaction.dto.TransactionPageResponse;
import in.expensetrackerapp.transaction.dto.TransactionResponse;
import in.expensetrackerapp.transaction.dto.TransactionUpdateRequest;
import in.expensetrackerapp.transaction.model.Transaction;
import in.expensetrackerapp.transaction.model.TransactionDirection;
import in.expensetrackerapp.transaction.repository.TransactionRepository;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T10:00:00Z");

    @Mock
    private TransactionRepository repository;

    @Mock
    private MongoTemplate mongoTemplate;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(repository, mongoTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Transaction tx(String id, String userId, TransactionDirection direction, String date) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(userId);
        t.setSourceHash("secret-hash");
        t.setAmount(new BigDecimal("450.00"));
        t.setDirection(direction);
        t.setDate(date);
        t.setCategory("Food");
        t.setNeedsReview(direction == TransactionDirection.UNKNOWN);
        return t;
    }

    private static TransactionUpdateRequest patch(String date, BigDecimal amount, TransactionDirection direction,
                                                  String merchant, String category, String description,
                                                  Boolean needsReview) {
        return new TransactionUpdateRequest(date, amount, direction, merchant, category, description, needsReview);
    }

    private Document capturedSet() {
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class),
                eq(Transaction.class));
        return (Document) update.getValue().getUpdateObject().get("$set");
    }

    // ------------------------------------------------------------------ get / delete / isolation
    @Test
    void get_returnsOwnTransactionWithoutInternalFields() {
        when(repository.findByIdAndUserId("t1", "user-1")).thenReturn(Optional.of(tx("t1", "user-1", TransactionDirection.DEBIT, "2026-01-05")));

        TransactionResponse response = service.get("user-1", "t1");

        assertThat(response.id()).isEqualTo("t1");
        assertThat(response.amount()).isEqualByComparingTo("450.00");
        assertThat(response.toString()).doesNotContain("secret-hash");
    }

    @Test
    void get_otherUsersTransactionIsNotFound() {
        when(repository.findByIdAndUserId("t1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("user-2", "t1")).isInstanceOf(ResourceNotFoundException.class);
        verify(repository).findByIdAndUserId("t1", "user-2");
    }

    @Test
    void delete_isOwnerScopedAndReportsNotFound() {
        when(repository.deleteByIdAndUserId("t1", "user-1")).thenReturn(1L);
        when(repository.deleteByIdAndUserId("t1", "user-2")).thenReturn(0L);

        service.delete("user-1", "t1");
        assertThatThrownBy(() -> service.delete("user-2", "t1")).isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ list / filters / pagination
    @Test
    void list_alwaysFiltersByUserAndAppliesAllFilters() {
        when(mongoTemplate.count(any(Query.class), eq(Transaction.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(List.of(tx("t1", "user-1", TransactionDirection.DEBIT, "2026-01-05")));

        TransactionPageResponse page = service.list("user-1", new TransactionFilter(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "food", TransactionDirection.DEBIT, true), 0, 20);

        assertThat(page.content()).hasSize(1);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Transaction.class));
        Document filter = query.getValue().getQueryObject();
        assertThat(filter.get("userId")).isEqualTo("user-1");
        Document date = (Document) filter.get("date");
        assertThat(date.get("$gte")).isEqualTo("2026-01-01");
        assertThat(date.get("$lte")).isEqualTo("2026-01-31");
        assertThat(filter.get("category")).isEqualTo("Food");          // canonicalised from "food"
        assertThat(filter.get("direction")).isEqualTo(TransactionDirection.DEBIT);
        assertThat(filter.get("needsReview")).isEqualTo(true);
    }

    @Test
    void list_withoutFiltersOnlyConstrainsByUser() {
        when(mongoTemplate.count(any(Query.class), eq(Transaction.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Transaction.class))).thenReturn(List.of());

        service.list("user-1", TransactionFilter.none(), 0, 20);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Transaction.class));
        assertThat(query.getValue().getQueryObject().keySet()).containsExactly("userId");
    }

    @Test
    void list_paginatesWithStableSortAndCapsSize() {
        when(mongoTemplate.count(any(Query.class), eq(Transaction.class))).thenReturn(250L);
        when(mongoTemplate.find(any(Query.class), eq(Transaction.class))).thenReturn(List.of());

        TransactionPageResponse page = service.list("user-1", TransactionFilter.none(), -3, 100_000);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(TransactionService.MAX_PAGE_SIZE);
        assertThat(page.totalElements()).isEqualTo(250);
        assertThat(page.totalPages()).isEqualTo(3);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Transaction.class));
        assertThat(query.getValue().getLimit()).isEqualTo(100);
        assertThat(query.getValue().getSortObject().keySet()).containsExactly("date", "createdAt", "id");
    }

    @Test
    void list_rejectsInvertedDateRangeAndUnknownCategory() {
        assertThatThrownBy(() -> service.list("u", new TransactionFilter(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null, null), 0, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list("u", new TransactionFilter(null, null, "Groceries2", null, null), 0, 20))
                .isInstanceOf(BadRequestException.class);
    }

    // ------------------------------------------------------------------ update
    @Test
    void update_setsOnlyProvidedFieldsAndStampsUpdatedAt() {
        Transaction existing = tx("t1", "user-1", TransactionDirection.DEBIT, "2026-01-05");
        when(repository.findByIdAndUserId("t1", "user-1")).thenReturn(Optional.of(existing));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Transaction.class))).thenReturn(existing);

        service.update("user-1", "t1", patch(null, null, null, null, "transport", null, null));

        Document set = capturedSet();
        assertThat(set.keySet()).containsExactlyInAnyOrder("category", "updatedAt");
        assertThat(set.get("category")).isEqualTo("Transport");
        assertThat(set.get("updatedAt")).isEqualTo(NOW);
    }

    @Test
    void update_storesAmountAsExactDecimal128() {
        Transaction existing = tx("t1", "user-1", TransactionDirection.DEBIT, "2026-01-05");
        when(repository.findByIdAndUserId("t1", "user-1")).thenReturn(Optional.of(existing));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Transaction.class))).thenReturn(existing);

        service.update("user-1", "t1", patch(null, new BigDecimal("1234567.5"), null, null, null, null, null));

        Object amount = capturedSet().get("amount");
        assertThat(amount).isInstanceOf(Decimal128.class);
        assertThat(((Decimal128) amount).bigDecimalValue()).isEqualByComparingTo("1234567.50");
        assertThat(((Decimal128) amount).bigDecimalValue().scale()).isEqualTo(2);
    }

    @Test
    void update_refusesAmountsThatWouldNeedRounding() {
        when(repository.findByIdAndUserId("t1", "user-1"))
                .thenReturn(Optional.of(tx("t1", "user-1", TransactionDirection.DEBIT, "2026-01-05")));

        assertThatThrownBy(() -> service.update("user-1", "t1",
                patch(null, new BigDecimal("10.005"), null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_validatesCategoryDateDirectionAndMerchant() {
        when(repository.findByIdAndUserId("t1", "user-1"))
                .thenReturn(Optional.of(tx("t1", "user-1", TransactionDirection.DEBIT, "2026-01-05")));

        assertThatThrownBy(() -> service.update("user-1", "t1", patch(null, null, null, null, "Nonsense", null, null)))
                .isInstanceOf(BadRequestException.class).hasMessage("Unknown category");
        assertThatThrownBy(() -> service.update("user-1", "t1", patch("2026-02-30", null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("user-1", "t1", patch(null, null, TransactionDirection.UNKNOWN, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("user-1", "t1", patch(null, null, null, "   ", null, null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Transaction.class));
    }

    @Test
    void update_withNoFieldsIsRejected() {
        assertThatThrownBy(() -> service.update("user-1", "t1", patch(null, null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class).hasMessage("No fields to update");
        verify(repository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void update_ofAnotherUsersTransactionIsNotFoundAndWritesNothing() {
        when(repository.findByIdAndUserId("t1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("user-2", "t1", patch(null, null, null, "X", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Transaction.class));
    }

    @Test
    void update_updateQueryIsOwnerScoped() {
        Transaction existing = tx("t1", "user-1", TransactionDirection.DEBIT, "2026-01-05");
        when(repository.findByIdAndUserId("t1", "user-1")).thenReturn(Optional.of(existing));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Transaction.class))).thenReturn(existing);

        service.update("user-1", "t1", patch(null, null, null, "Zomato", null, null, null));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Transaction.class));
        assertThat(query.getValue().getQueryObject().get("userId")).isEqualTo("user-1");
        assertThat(query.getValue().getQueryObject().get("id")).isEqualTo("t1");
    }

    @Test
    void update_rowDeletedConcurrentlyIsNotFound() {
        when(repository.findByIdAndUserId("t1", "user-1"))
                .thenReturn(Optional.of(tx("t1", "user-1", TransactionDirection.DEBIT, "2026-01-05")));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Transaction.class))).thenReturn(null);

        assertThatThrownBy(() -> service.update("user-1", "t1", patch(null, null, null, "X", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ review flag rules
    @Test
    void review_unknownDirectionCanBeResolvedAndMarkedReviewedInOneEdit() {
        Transaction existing = tx("t1", "user-1", TransactionDirection.UNKNOWN, "2026-01-05");
        when(repository.findByIdAndUserId("t1", "user-1")).thenReturn(Optional.of(existing));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Transaction.class))).thenReturn(existing);

        service.update("user-1", "t1", patch(null, null, TransactionDirection.CREDIT, null, null, null, false));

        Document set = capturedSet();
        assertThat(set.get("direction")).isEqualTo(TransactionDirection.CREDIT);
        assertThat(set.get("needsReview")).isEqualTo(false);
    }

    @Test
    void review_cannotBeClearedWhileDirectionIsStillUnknown() {
        when(repository.findByIdAndUserId("t1", "user-1"))
                .thenReturn(Optional.of(tx("t1", "user-1", TransactionDirection.UNKNOWN, "2026-01-05")));

        assertThatThrownBy(() -> service.update("user-1", "t1", patch(null, null, null, null, "Food", null, false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void review_cannotBeClearedWhileDateIsMissing() {
        when(repository.findByIdAndUserId("t1", "user-1"))
                .thenReturn(Optional.of(tx("t1", "user-1", TransactionDirection.DEBIT, null)));

        assertThatThrownBy(() -> service.update("user-1", "t1", patch(null, null, null, null, null, null, false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void review_flagCanAlwaysBeRaised() {
        Transaction existing = tx("t1", "user-1", TransactionDirection.DEBIT, null);
        when(repository.findByIdAndUserId("t1", "user-1")).thenReturn(Optional.of(existing));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Transaction.class))).thenReturn(existing);

        service.update("user-1", "t1", patch(null, null, null, null, null, null, true));

        assertThat(capturedSet().get("needsReview")).isEqualTo(true);
    }

    @Test
    void editingOtherFieldsDoesNotSilentlyChangeTheReviewFlag() {
        Transaction existing = tx("t1", "user-1", TransactionDirection.DEBIT, "2026-01-05");
        when(repository.findByIdAndUserId("t1", "user-1")).thenReturn(Optional.of(existing));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Transaction.class))).thenReturn(existing);

        service.update("user-1", "t1", patch(null, null, null, "Zomato", "Food", "note", null));

        assertThat(capturedSet().containsKey("needsReview")).isFalse();
    }
}
