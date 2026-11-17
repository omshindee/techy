package com.devsocial.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/** Picks where uploaded images are stored: STORAGE_TYPE=local (default) or s3 (Cloudflare R2, S3, MinIO). */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public BlobStore blobStore(@Value("${app.storage.type:local}") String type,
                               @Value("${app.storage.dir}") String dir,
                               @Value("${app.storage.s3.endpoint:}") String endpoint,
                               @Value("${app.storage.s3.region:auto}") String region,
                               @Value("${app.storage.s3.bucket:}") String bucket,
                               @Value("${app.storage.s3.access-key-id:}") String accessKeyId,
                               @Value("${app.storage.s3.secret-access-key:}") String secretAccessKey) throws IOException {
        BlobStore store = switch (type.trim().toLowerCase()) {
            case "s3", "r2" -> new S3BlobStore(endpoint, region, bucket, accessKeyId, secretAccessKey);
            case "local" -> new LocalBlobStore(dir);
            default -> throw new IllegalStateException("Unknown STORAGE_TYPE '" + type + "' (use local or s3)");
        };
        log.info("Uploaded images are stored in {}", store.describe());
        return store;
    }
}
