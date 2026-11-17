package com.devsocial.service;

import com.devsocial.exception.ApiException;
import com.devsocial.storage.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates and stores uploaded images in the configured {@link BlobStore} (local disk or
 * S3-compatible storage such as Cloudflare R2).
 *
 * Files get random, unguessable names (the "key", e.g. {@code 3f2a...c1.jpg}) and are only served
 * through {@code MediaController} via signed, expiring links (see MediaUrlSigner).
 * The file type is decided from the file's magic bytes, never from the client's Content-Type.
 */
@Service
public class MediaStorageService {

    public static final String URL_PREFIX = "/api/media/";
    private static final Pattern KEY = Pattern.compile("^[a-f0-9]{32}\\.(jpg|png|gif|webp)$");
    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    private final BlobStore store;
    private final long maxBytes;

    public MediaStorageService(BlobStore store, @Value("${app.storage.max-image-bytes}") long maxBytes) {
        this.store = store;
        this.maxBytes = maxBytes;
    }

    /**
     * Validates and saves an image, returning its key. If the surrounding transaction rolls
     * back, the file is deleted again so failed requests don't leave orphans in storage.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Image is empty");
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must be at most " + (maxBytes / 1024 / 1024) + " MB");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read the uploaded image");
        }
        String ext = detectExtension(Arrays.copyOf(data, Math.min(12, data.length)))
                .orElseThrow(() -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG, PNG, GIF and WebP images are allowed"));
        String key = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            store.put(key, data, contentType(key).toString());
        } catch (IOException e) {
            log.error("Failed to save upload {}", key, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the image. Please try again.");
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) store.delete(key);
                }
            });
        }
        return key;
    }

    /** Deletes the file once the current transaction commits (immediately if there is none). */
    public void deleteAfterCommit(String key) {
        if (key == null || !KEY.matcher(key).matches()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { store.delete(key); }
            });
        } else {
            store.delete(key);
        }
    }

    /** The image's bytes, if the key is well-formed and exists. Invalid keys never reach the store. */
    public Optional<Resource> find(String key) {
        if (key == null || !KEY.matcher(key).matches()) return Optional.empty();
        return store.get(key);
    }

    public static MediaType contentType(String key) {
        return switch (key.substring(key.lastIndexOf('.') + 1)) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
    }

    public static String urlFor(String key) {
        return key == null ? null : URL_PREFIX + key;
    }

    /** The key behind a URL produced by {@link #urlFor}, or null for external/empty URLs. */
    public static String keyFromUrl(String url) {
        return url != null && url.startsWith(URL_PREFIX) ? url.substring(URL_PREFIX.length()) : null;
    }

    static Optional<String> detectExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return Optional.of("jpg");
        if (h.length >= 8 && Arrays.equals(Arrays.copyOf(h, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) return Optional.of("png");
        if (h.length >= 4 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') return Optional.of("gif");
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return Optional.of("webp");
        return Optional.empty();
    }
}
