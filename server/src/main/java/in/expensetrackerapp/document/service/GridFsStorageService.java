package in.expensetrackerapp.document.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Optional;

/** Thin wrapper over GridFsTemplate. Files are only reachable through application code, never by URL. */
@Service
public class GridFsStorageService {

    private static final Logger log = LoggerFactory.getLogger(GridFsStorageService.class);

    private final GridFsTemplate gridFsTemplate;

    public GridFsStorageService(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
    }

    /** Stores the stream and returns the GridFS file id (hex string). */
    public String store(InputStream content, String storedFilename, String contentType,
                        String userId, String documentId) {
        Document metadata = new Document("userId", userId)
                .append("documentId", documentId);
        ObjectId id = gridFsTemplate.store(content, storedFilename, contentType, metadata);
        return id.toHexString();
    }

    /** Opens the stored file, or empty when the id is malformed or the file does not exist. */
    public Optional<GridFsResource> open(String gridFsFileId) {
        return find(gridFsFileId).map(file -> gridFsTemplate.getResource(file));
    }

    public boolean exists(String gridFsFileId) {
        return find(gridFsFileId).isPresent();
    }

    /** Best-effort delete; failures are logged and never thrown so cleanup cannot mask the original error. */
    public void deleteQuietly(String gridFsFileId) {
        if (gridFsFileId == null || !ObjectId.isValid(gridFsFileId)) {
            return;
        }
        try {
            gridFsTemplate.delete(idQuery(gridFsFileId));
        } catch (RuntimeException e) {
            log.error("Failed to delete GridFS file {}: {}", gridFsFileId, e.getMessage());
        }
    }

    private Optional<GridFSFile> find(String gridFsFileId) {
        if (gridFsFileId == null || !ObjectId.isValid(gridFsFileId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(gridFsTemplate.findOne(idQuery(gridFsFileId)));
    }

    private static Query idQuery(String gridFsFileId) {
        return new Query(Criteria.where("_id").is(new ObjectId(gridFsFileId)));
    }
}
