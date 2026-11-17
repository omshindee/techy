package com.devsocial.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Notifications for follows, likes and comments: created, deduplicated, undone, hidden when blocked. */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private record Acct(String name, String token) {}

    private Acct register(String prefix) throws Exception {
        String name = prefix + "_" + UUID.randomUUID().toString().substring(0, 6);
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", name, "email", name + "@example.com", "password", "password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Acct(name, "Bearer " + om.readTree(res).get("token").asText());
    }

    private JsonNode getJson(String path, Acct as) throws Exception {
        return om.readTree(mvc.perform(get(path).header("Authorization", as.token())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Acct as) throws Exception {
        String body = mvc.perform(req.header("Authorization", as.token())).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : om.readTree(body);
    }

    private long createPost(Acct as, String text) throws Exception {
        return call(post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("content", text, "category", "Java"))), as).get("id").asLong();
    }

    private long comment(Acct as, long postId, String text) throws Exception {
        return call(post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("content", text))), as).get("id").asLong();
    }

    private JsonNode items(Acct as) throws Exception {
        return getJson("/api/notifications", as).get("items");
    }

    private long unread(Acct as) throws Exception {
        return getJson("/api/notifications/unread-count", as).get("count").asLong();
    }

    @Test
    void followsLikesAndCommentsNotifyTheOwner() throws Exception {
        Acct alice = register("alice"), bob = register("bob");
        long postId = createPost(alice, "Shipped my first Spring Boot app");

        call(post("/api/users/" + alice.name() + "/follow"), bob);
        call(post("/api/posts/" + postId + "/like"), bob);
        call(post("/api/posts/" + postId + "/like"), bob); // liking twice doesn't notify twice
        comment(bob, postId, "Congrats!   Which   database did you use?");

        // Own actions never notify: Alice liking and commenting on her own post.
        call(post("/api/posts/" + postId + "/like"), alice);
        comment(alice, postId, "Thanks all");

        JsonNode list = items(alice);
        assertEquals(3, list.size());
        assertEquals("COMMENT", list.get(0).get("type").asText());
        assertEquals("Congrats! Which database did you use?", list.get(0).get("snippet").asText());
        assertEquals(postId, list.get(0).get("postId").asLong());
        assertEquals("Shipped my first Spring Boot app", list.get(0).get("postText").asText());
        assertEquals("LIKE", list.get(1).get("type").asText());
        assertEquals("FOLLOW", list.get(2).get("type").asText());
        assertEquals(bob.name(), list.get(2).get("actor").get("username").asText());
        assertFalse(list.get(2).get("followingActor").asBoolean());
        assertFalse(list.get(0).get("read").asBoolean());
        assertEquals(3, unread(alice));
        assertEquals(0, items(bob).size());

        // Marking read clears the badge but keeps the list.
        call(post("/api/notifications/read"), alice);
        assertEquals(0, unread(alice));
        assertTrue(items(alice).get(0).get("read").asBoolean());

        // Follow back shows on the follow notification.
        call(post("/api/users/" + bob.name() + "/follow"), alice);
        assertTrue(items(alice).get(2).get("followingActor").asBoolean());
    }

    @Test
    void undoingAnActionRemovesItsNotification() throws Exception {
        Acct alice = register("alice"), bob = register("bob");
        long postId = createPost(alice, "Rust or Go?");

        call(post("/api/users/" + alice.name() + "/follow"), bob);
        call(post("/api/posts/" + postId + "/like"), bob);
        long commentId = comment(bob, postId, "Go!");
        assertEquals(3, items(alice).size());

        call(delete("/api/users/" + alice.name() + "/follow"), bob);
        call(delete("/api/posts/" + postId + "/like"), bob);
        call(delete("/api/posts/" + postId + "/comments/" + commentId), bob);
        assertEquals(0, items(alice).size());
        assertEquals(0, unread(alice));

        // Deleting the post removes notifications about it (and doesn't fail on the reference).
        call(post("/api/posts/" + postId + "/like"), bob);
        comment(bob, postId, "Rust!");
        assertEquals(2, items(alice).size());
        call(delete("/api/posts/" + postId), alice);
        assertEquals(0, items(alice).size());
    }

    @Test
    void blockedPeoplesNotificationsAreHidden() throws Exception {
        Acct alice = register("alice"), bob = register("bob");
        call(post("/api/users/" + alice.name() + "/follow"), bob);
        assertEquals(1, unread(alice));

        call(post("/api/users/" + bob.name() + "/block"), alice);
        assertEquals(0, items(alice).size());
        assertEquals(0, unread(alice));

        call(delete("/api/users/" + bob.name() + "/block"), alice);
        // Blocking removes the follow, so there's nothing left to show.
        assertEquals(0, items(alice).size());
    }

    @Test
    void olderNotificationsLoadPageByPage() throws Exception {
        Acct alice = register("alice"), bob = register("bob");
        for (int i = 0; i < 32; i++) {
            long postId = createPost(alice, "post " + i);
            call(post("/api/posts/" + postId + "/like"), bob);
        }
        JsonNode first = getJson("/api/notifications", alice);
        assertEquals(30, first.get("items").size());
        assertFalse(first.get("nextBefore").isNull());
        JsonNode second = getJson("/api/notifications?before=" + first.get("nextBefore").asLong(), alice);
        assertEquals(2, second.get("items").size());
        assertTrue(second.get("nextBefore").isNull());
        assertEquals("post 0", second.get("items").get(1).get("postText").asText());
    }
}
