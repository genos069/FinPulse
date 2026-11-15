package in.expensetrackerapp.job.repository;

import in.expensetrackerapp.job.model.ProcessingJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JobRepository extends MongoRepository<ProcessingJob, String> {

    /** Owner-scoped lookup; there is no unscoped access path for API callers. */
    Optional<ProcessingJob> findByIdAndUserId(String id, String userId);
}
