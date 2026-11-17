package com.devsocial.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String register(String username) throws Exception {
        String body = om.writeValueAsString(Map.of(
                "username", username, "email", username + "@example.com", "password", "password123"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(res).get("token").asText();
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/feed")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateUsernameAndBadLoginAreRejected() throws Exception {
        register("dupe_user");
        String body = om.writeValueAsString(Map.of(
                "username", "dupe_user", "email", "other@example.com", "password", "password123"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        String bad = om.writeValueAsString(Map.of("username", "dupe_user", "password", "wrong-password"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isUnauthorized());
        String ok = om.writeValueAsString(Map.of("username", "dupe_user", "password", "password123"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("dupe_user"));
    }

    @Test
    void fullSocialFlow() throws Exception {
        String alice = register("alice_flow");
        String bob = register("bob_flow");

        // Alice sets interests; Bob posts.
        mvc.perform(put("/api/users/me").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("bio", "hi", "skills", List.of("Java"), "interests", List.of("Linux")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests[0]").value("Linux"));

        String created = mvc.perform(post("/api/posts").header("Authorization", bob).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("content", "Learned about epoll today", "category", "Linux"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long postId = om.readTree(created).get("id").asLong();

        // Invalid category is rejected.
        mvc.perform(post("/api/posts").header("Authorization", bob).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("content", "x", "category", "Cooking"))))
                .andExpect(status().isBadRequest());

        // Follow / unfollow.
        mvc.perform(post("/api/users/bob_flow/follow").header("Authorization", alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.followedByMe").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));
        mvc.perform(post("/api/users/alice_flow/follow").header("Authorization", alice))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/bob_flow/followers").header("Authorization", alice))
                .andExpect(jsonPath("$[0].username").value("alice_flow"));

        // Like + comment.
        mvc.perform(post("/api/posts/" + postId + "/like").header("Authorization", alice))
                .andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(post("/api/posts/" + postId + "/like").header("Authorization", alice))
                .andExpect(jsonPath("$.likeCount").value(1)); // idempotent
        mvc.perform(post("/api/posts/" + postId + "/comments").header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Nice!\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/posts/" + postId + "/comments").header("Authorization", bob))
                .andExpect(jsonPath("$[0].content").value("Nice!"))
                .andExpect(jsonPath("$[0].author.username").value("alice_flow"));

        // Feed shows Bob's post with counts for Alice.
        mvc.perform(get("/api/feed").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(postId))
                .andExpect(jsonPath("$[0].likeCount").value(1))
                .andExpect(jsonPath("$[0].commentCount").value(1))
                .andExpect(jsonPath("$[0].likedByMe").value(true));

        // Only the author can delete.
        mvc.perform(delete("/api/posts/" + postId).header("Authorization", alice)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/posts/" + postId).header("Authorization", bob)).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId).header("Authorization", bob)).andExpect(status().isNotFound());
    }
}
