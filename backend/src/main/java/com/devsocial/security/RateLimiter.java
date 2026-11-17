package com.devsocial.security;

import com.devsocial.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window rate limiter, e.g. "5 sign-ups per hour per IP".
 * Fine for one backend instance; if you ever run several, move the counters to Redis.
 */
@Component
public class RateLimiter {

    private record Window(long startMillis, int count) {}

    private static final long MAX_WINDOW_MILLIS = Duration.ofDays(1).toMillis();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final boolean enabled;

    public RateLimiter(@Value("${app.ratelimit.enabled}") boolean enabled) {
        this.enabled = enabled;
    }

    /** Counts one attempt; throws 429 once more than {@code limit} attempts happened within {@code window}. */
    public void check(String bucket, String key, int limit, Duration window) {
        if (!enabled || key == null) return;
        long now = System.currentTimeMillis();
        long size = window.toMillis();
        Window w = windows.compute(bucket + ':' + key.toLowerCase(), (k, old) ->
                old == null || now - old.startMillis() >= size ? new Window(now, 1) : new Window(old.startMillis(), old.count() + 1));
        if (windows.size() > 50_000) windows.values().removeIf(x -> now - x.startMillis() > MAX_WINDOW_MILLIS);
        if (w.count() > limit) {
            long seconds = Math.max(1, (w.startMillis() + size - now) / 1000);
            String wait = seconds < 90 ? seconds + " seconds" : (seconds + 59) / 60 + " minutes";
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please try again in " + wait + ".");
        }
    }
}
