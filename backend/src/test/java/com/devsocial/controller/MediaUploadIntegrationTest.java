package com.devsocial.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MediaUploadIntegrationTest {

    /** Smallest valid-looking PNG header; the server only sniffs magic bytes. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String register() throws Exception {
        String name = "m_" + UUID.randomUUID().toString().substring(0, 8);
        String body = om.writeValueAsString(Map.of("username", name, "email", name + "@example.com", "password", "password123"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(res).get("token").asText();
    }

    private static MockMultipartFile image(byte[] bytes, String name) {
        return new MockMultipartFile("image", name, "image/png", bytes);
    }

    @Test
    void imagePostIsStoredServedPubliclyAndDeletedWithThePost() throws Exception {
        String token = register();
        String res = mvc.perform(multipart("/api/posts").file(image(PNG, "screenshot.png"))
                        .param("content", "My new setup").param("category", "Linux").header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("My new setup"))
                .andReturn().getResponse().getContentAsString();
        JsonNode post = om.readTree(res);
        String url = post.get("imageUrl").asText();
        assertTrue(url.matches("/api/media/[a-f0-9]{32}\\.png\\?exp=\\d+&sig=[A-Za-z0-9_-]+"), url);

        // Served without a token (img tags can't send one) because the link is signed; sniffed type.
        byte[] served = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(PNG, served);

        mvc.perform(delete("/api/posts/" + post.get("id").asLong()).header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void captionIsOptionalForImagePostsButSomethingIsRequired() throws Exception {
        String token = register();
        mvc.perform(multipart("/api/posts").file(image(JPEG, "a.jpg")).param("category", "DevOps").header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(""))
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.containsString(".jpg?exp=")));
        mvc.perform(multipart("/api/posts").param("content", "  ").param("category", "DevOps").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonImagesAndOversizedFilesAreRejected() throws Exception {
        String token = register();
        // Claims to be a PNG, but the bytes are HTML.
        mvc.perform(multipart("/api/posts").file(image("<script>alert(1)</script>".getBytes(), "x.png"))
                        .param("category", "Java").header("Authorization", token))
                .andExpect(status().isUnsupportedMediaType());

        byte[] big = Arrays.copyOf(PNG, 1024 * 1024 + 1); // test limit is 1 MB
        mvc.perform(multipart("/api/posts").file(image(big, "big.png")).param("category", "Java").header("Authorization", token))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void mediaKeysCannotEscapeTheStorageFolder() throws Exception {
        mvc.perform(get("/api/media/..%2Fapplication.properties")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/media/not-a-key.png")).andExpect(status().isForbidden());
    }

    @Test
    void imagesNeedAValidUnexpiredSignature() throws Exception {
        String token = register();
        String url = om.readTree(mvc.perform(multipart("/api/posts").file(image(PNG, "p.png"))
                        .param("category", "Linux").header("Authorization", token))
                .andReturn().getResponse().getContentAsString()).get("imageUrl").asText();
        String bare = url.substring(0, url.indexOf('?'));
        String exp = url.replaceAll(".*exp=(\\d+).*", "$1");
        mvc.perform(get(url)).andExpect(status().isOk());
        mvc.perform(get(bare)).andExpect(status().isForbidden());                                   // no signature
        mvc.perform(get(url.replaceAll("sig=[^&]+", "sig=" + "A".repeat(43)))).andExpect(status().isForbidden()); // forged
        mvc.perform(get(url.replace("exp=" + exp, "exp=" + (Long.parseLong(exp) + 3600))))       // extended
                .andExpect(status().isForbidden());
        mvc.perform(get(bare + "?exp=1000&sig=abc")).andExpect(status().isForbidden());           // expired
    }

    @Test
    void avatarUploadReplacesAndRemovesTheOldFile() throws Exception {
        String token = register();
        String first = om.readTree(mvc.perform(multipart(HttpMethod.PUT, "/api/users/me/avatar").file(image(PNG, "me.png"))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("avatarUrl").asText();
        mvc.perform(get(first)).andExpect(status().isOk());

        String second = om.readTree(mvc.perform(multipart(HttpMethod.PUT, "/api/users/me/avatar").file(image(JPEG, "me2.jpg"))
                        .header("Authorization", token))
                .andReturn().getResponse().getContentAsString()).get("avatarUrl").asText();
        assertNotEquals(first, second);
        mvc.perform(get(first)).andExpect(status().isNotFound());   // old one cleaned up
        mvc.perform(get(second)).andExpect(status().isOk());

        mvc.perform(delete("/api/users/me/avatar").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
        mvc.perform(get(second)).andExpect(status().isNotFound());
    }

    @Test
    void profileReportsPostCount() throws Exception {
        String token = register();
        mvc.perform(post("/api/posts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello\",\"category\":\"Java\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/users/me").header("Authorization", token))
                .andExpect(jsonPath("$.postCount").value(1));
    }
}
