package in.expensetrackerapp.document.repository;

import in.expensetrackerapp.document.model.UploadedDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentRepository extends MongoRepository<UploadedDocument, String> {

    Page<UploadedDocument> findByUserId(String userId, Pageable pageable);

    /** Every single-document lookup is scoped by owner; there is deliberately no findById-only access path. */
    Optional<UploadedDocument> findByIdAndUserId(String id, String userId);
}
