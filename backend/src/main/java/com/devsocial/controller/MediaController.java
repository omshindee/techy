package com.devsocial.controller;

import com.devsocial.service.MediaStorageService;
import com.devsocial.service.MediaUrlSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Serves uploaded images. <img> tags can't send a login token, so every link the API hands out
 * is signed and expires instead (see MediaUrlSigner). Unsigned, tampered or expired links get 403.
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaStorageService storage;
    private final MediaUrlSigner signer;

    @GetMapping("/{key}")
    public ResponseEntity<?> get(@PathVariable String key,
                                 @RequestParam(required = false) Long exp,
                                 @RequestParam(required = false) String sig) {
        if (!signer.verify(key, exp, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "This image link is invalid or has expired"));
        }
        return storage.find(key)
                .<ResponseEntity<?>>map(resource -> ResponseEntity.ok()
                        .contentType(MediaStorageService.contentType(key))
                        // private: shared caches (CDNs, proxies) must not keep a copy
                        .cacheControl(CacheControl.maxAge(MediaUrlSigner.secondsLeft(exp), TimeUnit.SECONDS).cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Referrer-Policy", "no-referrer")
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
