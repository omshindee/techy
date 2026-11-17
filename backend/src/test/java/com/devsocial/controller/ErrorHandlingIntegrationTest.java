package com.devsocial.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs against a real servlet container (not MockMvc) so that Spring's /error forwarding
 * is exercised. Previously every 4xx/5xx raised that way came back as 401, which made the
 * frontend sign the user out.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorHandlingIntegrationTest {

    @Autowired TestRestTemplate rest;
    private HttpHeaders auth;

    @BeforeEach
    void registerUser() {
        String name = "err_" + UUID.randomUUID().toString().substring(0, 8);
        Map<?, ?> res = rest.postForObject("/api/auth/register",
                Map.of("username", name, "email", name + "@example.com", "password", "password123"), Map.class);
        auth = new HttpHeaders();
        auth.setBearerAuth((String) res.get("token"));
        auth.setContentType(MediaType.APPLICATION_JSON);
    }

    private HttpStatusCode call(HttpMethod method, String path, String body) {
        return rest.exchange(path, method, new HttpEntity<>(body, auth), String.class).getStatusCode();
    }

    @Test
    void malformedJsonIs400NotUnauthorized() {
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.PUT, "/api/users/me", "{bad"));
    }

    @Test
    void nonNumericIdIs400NotUnauthorized() {
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.GET, "/api/posts/abc", null));
    }

    @Test
    void unknownRouteIs404NotUnauthorized() {
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.GET, "/api/does-not-exist", null));
    }

    @Test
    void nullInterestOrSkillIsHandled() {
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.PUT, "/api/users/me", "{\"interests\":[null]}"));
        assertEquals(HttpStatus.OK, call(HttpMethod.PUT, "/api/users/me", "{\"skills\":[null,\"Java\"]}"));
    }

    @Test
    void stillUnauthorizedWithoutToken() {
        ResponseEntity<String> res = rest.getForEntity("/api/feed", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
    }

    @Test
    void usernamesAreUniqueIgnoringCaseAndLoginIgnoresCase() {
        String name = "Case_" + UUID.randomUUID().toString().substring(0, 8);
        rest.postForObject("/api/auth/register",
                Map.of("username", name, "email", name.toLowerCase() + "@example.com", "password", "password123"), Map.class);

        ResponseEntity<String> dupe = rest.postForEntity("/api/auth/register",
                Map.of("username", name.toLowerCase(), "email", "other-" + name.toLowerCase() + "@example.com",
                        "password", "password123"), String.class);
        assertEquals(HttpStatus.CONFLICT, dupe.getStatusCode());

        ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
                Map.of("username", name.toUpperCase(), "password", "password123"), Map.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertEquals(name, ((Map<?, ?>) login.getBody().get("user")).get("username"));
    }

    @Test
    void userListCountsAreBatchedCorrectly() {
        String target = "tgt_" + UUID.randomUUID().toString().substring(0, 8);
        rest.postForObject("/api/auth/register",
                Map.of("username", target, "email", target + "@example.com", "password", "password123"), Map.class);
        assertEquals(HttpStatus.OK, call(HttpMethod.POST, "/api/users/" + target + "/follow", null));

        Map<String, Object> page = rest.exchange("/api/users?q=" + target, HttpMethod.GET, new HttpEntity<>(auth),
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) page.get("items");
        Map<String, Object> t = users.stream().filter(u -> target.equals(u.get("username"))).findFirst().orElseThrow();
        assertEquals(1, ((Number) t.get("followerCount")).intValue());
        assertEquals(0, ((Number) t.get("followingCount")).intValue());
        assertEquals(true, t.get("followedByMe"));
    }
}
