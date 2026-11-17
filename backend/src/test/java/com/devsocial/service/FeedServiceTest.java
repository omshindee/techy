package com.devsocial.service;

import com.devsocial.dto.PostResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private PostResponse post(Duration age, long likes, long comments) {
        return new PostResponse(1L, "c", "Java", null, NOW.minus(age),
                new PostResponse.Author(1L, "u", "u", null), likes, comments, false);
    }

    @Test
    void followedAuthorsOutrankStrangers() {
        PostResponse p = post(Duration.ofHours(1), 0, 0);
        assertTrue(FeedService.score(p, true, false, NOW) > FeedService.score(p, false, false, NOW));
    }

    @Test
    void interestMatchBoostsScore() {
        PostResponse p = post(Duration.ofHours(1), 0, 0);
        assertTrue(FeedService.score(p, false, true, NOW) > FeedService.score(p, false, false, NOW));
    }

    @Test
    void engagementAndRecencyMatter() {
        assertTrue(FeedService.score(post(Duration.ofHours(1), 5, 2), false, false, NOW)
                > FeedService.score(post(Duration.ofHours(1), 0, 0), false, false, NOW));
        assertTrue(FeedService.score(post(Duration.ofMinutes(5), 0, 0), false, false, NOW)
                > FeedService.score(post(Duration.ofDays(5), 0, 0), false, false, NOW));
    }
}
