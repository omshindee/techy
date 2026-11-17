package com.devsocial.controller;

import com.devsocial.model.User;
import com.devsocial.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Direct messages: who can message whom, read state, polling, blocking, reporting. */
@SpringBootTest
@AutoConfigureMockMvc
class MessagingIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository users;

    private record Acct(String name, String token) {}

    private Acct register(String prefix) throws Exception {
        String name = prefix + "_" + UUID.randomUUID().toString().substring(0, 6);
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", name, "email", name + "@example.com", "password", "password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Acct(name, "Bearer " + om.readTree(res).get("token").asText());
    }

    private void follow(Acct who, Acct whom) throws Exception {
        mvc.perform(post("/api/users/" + whom.name() + "/follow").header("Authorization", who.token())).andExpect(status().isOk());
    }

    private ResultActions send(Acct from, Acct to, String text) throws Exception {
        return mvc.perform(post("/api/messages/with/" + to.name()).header("Authorization", from.token())
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("content", text))));
    }

    private JsonNode getJson(String path, Acct as) throws Exception {
        return om.readTree(mvc.perform(get(path).header("Authorization", as.token())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void followersCanStartChatsAndTheOtherPersonCanAlwaysReply() throws Exception {
        Acct alice = register("alice"), bob = register("bob"), carol = register("carol");
        follow(alice, bob);

        // Carol doesn't follow Bob: she can't start a chat.
        send(carol, bob, "hi").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Follow @" + bob.name() + " to send them a message."));
        // Bob doesn't follow Alice and she hasn't written yet: he can't start one either.
        send(bob, alice, "hello?").andExpect(status().isForbidden());

        long first = om.readTree(send(alice, bob, "Hey Bob, loved your post!").andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true)).andReturn().getResponse().getContentAsString()).get("id").asLong();

        // Bob still doesn't follow Alice, but he can reply now.
        assertEquals(1, getJson("/api/messages/unread-count", bob).get("count").asLong());
        JsonNode bobsView = getJson("/api/messages/with/" + alice.name(), bob);
        assertEquals(true, bobsView.get("canSend").asBoolean());
        assertEquals("Hey Bob, loved your post!", bobsView.get("messages").get(0).get("content").asText());
        send(bob, alice, "Thanks Alice!").andExpect(status().isCreated());

        // Opening the chat marked Alice's message read: no unread left for Bob, and Alice sees "Seen".
        assertEquals(0, getJson("/api/messages/unread-count", bob).get("count").asLong());
        JsonNode alicesView = getJson("/api/messages/with/" + bob.name(), alice);
        assertEquals(first, alicesView.get("seenUpToId").asLong());
        assertEquals(2, alicesView.get("messages").size());

        // Polling with ?after= returns only newer messages.
        send(alice, bob, "Want to pair on something?").andExpect(status().isCreated());
        JsonNode newer = getJson("/api/messages/with/" + bob.name() + "?after=" + alicesView.get("messages").get(1).get("id").asLong(), alice);
        assertEquals(1, newer.get("messages").size());

        // Inbox: newest first, with last message and unread count.
        JsonNode inbox = getJson("/api/messages/conversations", bob);
        assertEquals(alice.name(), inbox.get(0).get("other").get("username").asText());
        assertEquals("Want to pair on something?", inbox.get(0).get("lastMessage").asText());
        assertEquals(1, inbox.get(0).get("unread").asLong());

        // Someone else asking for "their chat with Bob" sees their own (empty) conversation, never Alice's.
        assertEquals(0, getJson("/api/messages/with/" + bob.name(), carol).get("messages").size());
    }

    @Test
    void blockingStopsMessagesBothWaysAndHidesTheChat() throws Exception {
        Acct alice = register("alice"), bob = register("bob");
        follow(alice, bob);
        follow(bob, alice);
        send(alice, bob, "hi").andExpect(status().isCreated());

        mvc.perform(post("/api/users/" + alice.name() + "/block").header("Authorization", bob.token())).andExpect(status().isOk());
        send(alice, bob, "are you there?").andExpect(status().isNotFound());   // Bob is invisible to Alice now
        send(bob, alice, "bye").andExpect(status().isForbidden());
        assertEquals(0, getJson("/api/messages/conversations", bob).size());
        assertEquals(0, getJson("/api/messages/unread-count", bob).get("count").asLong());
    }

    @Test
    void invalidMessagesAreRejected() throws Exception {
        Acct alice = register("alice"), bob = register("bob");
        follow(alice, bob);
        send(alice, bob, "   ").andExpect(status().isBadRequest());
        send(alice, bob, "x".repeat(2001)).andExpect(status().isBadRequest());
        send(alice, alice, "me").andExpect(status().isForbidden());
    }

    @Test
    void onlyTheRecipientCanReportAMessageAndModeratorsCanRemoveIt() throws Exception {
        Acct alice = register("alice"), bob = register("bob"), carol = register("carol"), mod = register("mod");
        User m = users.findByUsername(mod.name()).orElseThrow();
        m.setRole(User.ROLE_ADMIN);
        users.save(m);
        follow(alice, bob);
        long msg = om.readTree(send(alice, bob, "buy cheap followers here").andReturn().getResponse().getContentAsString()).get("id").asLong();

        String report = om.writeValueAsString(Map.of("targetType", "MESSAGE", "targetId", msg, "reason", "SPAM"));
        mvc.perform(post("/api/reports").header("Authorization", carol.token()).contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isNotFound());   // not in that conversation
        mvc.perform(post("/api/reports").header("Authorization", bob.token()).contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isCreated());

        JsonNode queue = getJson("/api/admin/reports", mod);
        JsonNode r = null;
        for (JsonNode x : queue) if ("MESSAGE".equals(x.get("targetType").asText()) && x.get("targetId").asLong() == msg) r = x;
        assertEquals("buy cheap followers here", r.get("target").get("text").asText());
        mvc.perform(post("/api/admin/reports/" + r.get("id").asLong() + "/resolve").header("Authorization", mod.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REMOVE\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/messages/with/" + alice.name()).header("Authorization", bob.token()))
                .andExpect(jsonPath("$.messages", hasSize(0)));
    }
}
