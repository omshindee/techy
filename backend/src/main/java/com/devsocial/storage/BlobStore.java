package com.devsocial.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Optional;

/** Where uploaded image bytes live. Keys are already validated, random file names like "3f2a...c1.jpg". */
public interface BlobStore {
    void put(String key, byte[] data, String contentType) throws IOException;

    Optional<Resource> get(String key);

    void delete(String key);

    /** For startup logs, e.g. "local disk /app/uploads" or "S3 bucket techy-media". */
    String describe();
}
