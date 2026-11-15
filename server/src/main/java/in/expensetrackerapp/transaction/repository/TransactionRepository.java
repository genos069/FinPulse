package in.expensetrackerapp.transaction.repository;

import in.expensetrackerapp.transaction.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {

    /** Every single-row access is owner-scoped; there is deliberately no findById-only path for the API. */
    Optional<Transaction> findByIdAndUserId(String id, String userId);

    /** Returns the number of rows deleted (0 when the row does not exist or belongs to someone else). */
    long deleteByIdAndUserId(String id, String userId);
}
