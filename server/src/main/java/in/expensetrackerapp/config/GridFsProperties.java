package in.expensetrackerapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GridFS settings shared with the Python worker. Both sides must use the same bucket name.
 */
@ConfigurationProperties(prefix = "application.gridfs")
public class GridFsProperties {

    /** Bucket name; files live in "<bucket>.files" and "<bucket>.chunks". Default is "fs". */
    private String bucket = "fs";

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("application.gridfs.bucket (GRIDFS_BUCKET) must not be blank");
        }
        this.bucket = bucket.trim();
    }
}
