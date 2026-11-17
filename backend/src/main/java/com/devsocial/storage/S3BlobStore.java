package com.devsocial.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * S3-compatible object storage: Cloudflare R2 (recommended next to Cloudflare Pages), AWS S3, MinIO...
 * The bucket stays private; images are only served through the backend's signed /api/media links.
 *
 * For R2: endpoint https://ACCOUNT_ID.r2.cloudflarestorage.com, region "auto", and an R2 API token's
 * Access Key ID / Secret Access Key with "Object Read & Write" on the bucket.
 */
public class S3BlobStore implements BlobStore {

    private static final Logger log = LoggerFactory.getLogger(S3BlobStore.class);
    private final S3Client s3;
    private final String bucket;

    public S3BlobStore(String endpoint, String region, String bucket, String accessKeyId, String secretAccessKey) {
        if (isBlank(endpoint) || isBlank(bucket) || isBlank(accessKeyId) || isBlank(secretAccessKey)) {
            throw new IllegalStateException("STORAGE_TYPE=s3 needs S3_ENDPOINT, S3_BUCKET, S3_ACCESS_KEY_ID and S3_SECRET_ACCESS_KEY");
        }
        this.bucket = bucket.trim();
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint.trim()))
                .region(Region.of(isBlank(region) ? "auto" : region.trim()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())))
                // Path-style URLs (endpoint/bucket/key) work with R2 and MinIO alike.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClient(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(20)).build())
                .build();
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws IOException {
        try {
            s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType).contentLength((long) data.length),
                    RequestBody.fromBytes(data));
        } catch (RuntimeException e) {
            throw new IOException("Upload to object storage failed", e);
        }
    }

    @Override
    public Optional<Resource> get(String key) {
        try {
            byte[] bytes = s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
            return Optional.of(new ByteArrayResource(bytes));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            log.error("Could not read media {} from object storage", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (RuntimeException e) {
            log.warn("Could not delete media {} from object storage", key, e);
        }
    }

    @Override
    public String describe() {
        return "S3-compatible bucket " + bucket;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
