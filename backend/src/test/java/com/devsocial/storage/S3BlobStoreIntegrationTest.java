package com.devsocial.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talks to a real S3-compatible bucket. Skipped unless S3_TEST_ENDPOINT is set, e.g. against MinIO:
 *   docker run -d -p 9000:9000 -e MINIO_ROOT_USER=minio -e MINIO_ROOT_PASSWORD=minio-secret minio/minio server /data
 *   (create bucket "techy-test"), then
 *   S3_TEST_ENDPOINT=http://localhost:9000 S3_TEST_BUCKET=techy-test S3_TEST_KEY=minio S3_TEST_SECRET=minio-secret mvn test
 * or against a Cloudflare R2 test bucket.
 */
@EnabledIfEnvironmentVariable(named = "S3_TEST_ENDPOINT", matches = ".+")
class S3BlobStoreIntegrationTest {

    @Test
    void putGetDelete() throws Exception {
        S3BlobStore store = new S3BlobStore(System.getenv("S3_TEST_ENDPOINT"), System.getenv().getOrDefault("S3_TEST_REGION", "auto"),
                System.getenv("S3_TEST_BUCKET"), System.getenv("S3_TEST_KEY"), System.getenv("S3_TEST_SECRET"));
        String key = UUID.randomUUID().toString().replace("-", "") + ".png";
        byte[] data = ("\u0089PNG test " + key).getBytes(StandardCharsets.ISO_8859_1);

        assertTrue(store.get(key).isEmpty());
        store.put(key, data, "image/png");
        assertArrayEquals(data, store.get(key).orElseThrow().getContentAsByteArray());
        store.delete(key);
        assertTrue(store.get(key).isEmpty());
    }
}
