package com.devsocial.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Files in a local folder. Fine with a persistent volume (docker-compose, a VPS); lost on hosts with ephemeral disks. */
public class LocalBlobStore implements BlobStore {

    private static final Logger log = LoggerFactory.getLogger(LocalBlobStore.class);
    private final Path dir;

    public LocalBlobStore(String dir) throws IOException {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.dir);
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws IOException {
        Files.write(dir.resolve(key), data);
    }

    @Override
    public Optional<Resource> get(String key) {
        Path p = dir.resolve(key);
        return Files.isRegularFile(p) ? Optional.of(new FileSystemResource(p)) : Optional.empty();
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(dir.resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete media {}", key, e);
        }
    }

    @Override
    public String describe() {
        return "local disk " + dir;
    }
}
