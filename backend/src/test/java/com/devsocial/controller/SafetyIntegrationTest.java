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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Search, display names, blocking, reporting and moderation. */
@SpringBootTest
@AutoConfigureMockMvc
class SafetyIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository users;

    private record Account(String username, String token) {}

    private Account register(String prefix, String displayName) throws Exception {
        String name = prefix + "_" + UUID.randomUUID().toString().substring(0, 6);
        String body = om.writeValueAsString(Map.of("username", name, "displayName", displayName,
                "email", name + "@example.com", "password", "password123"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account(name, "Bearer " + om.readTree(res).get("token").asText());
    }

    private long createPost(Account a, String text) throws Exception {
        String res = mvc.perform(post("/api/posts").header("Authorization", a.token()).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("content", text, "category", "Java"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("id").asLong();
    }

    private JsonNode json(String path, Account a) throws Exception {
        return om.readTree(mvc.perform(get(path).header("Authorization", a.token())).andReturn().getResponse().getContentAsString());
    }

    @Test
    void searchFindsPeopleByDisplayNameUsernameOrSkill() throws Exception {
        String unique = "Zephyrine" + UUID.randomUUID().toString().substring(0, 4);
        Account viewer = register("viewer", "Viewer");
        Account priya = register("priya", unique + " Sharma");
        mvc.perform(put("/api/users/me").header("Authorization", priya.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skills\":[\"Kubernetes\"]}")).andExpect(status().isOk());

        mvc.perform(get("/api/users").param("q", unique.toLowerCase()).header("Authorization", viewer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value(priya.username()))
                .andExpect(jsonPath("$.items[0].displayName").value(unique + " Sharma"))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/api/users").param("q", "@" + priya.username()).header("Authorization", viewer.token()))
                .andExpect(jsonPath("$.items[*].username", hasItem(priya.username())));
        mvc.perform(get("/api/users").param("q", "kubernetes").header("Authorization", viewer.token()))
                .andExpect(jsonPath("$.items[*].username", hasItem(priya.username())));
        // LIKE wildcards are matched literally, not as "everything".
        mvc.perform(get("/api/users").param("q", "%").header("Authorization", viewer.token()))
                .andExpect(jsonPath("$.items", hasSize(0)));
        // Your own account isn't in your search results; private fields only appear on your own account.
        mvc.perform(get("/api/users").param("q", viewer.username()).header("Authorization", viewer.token()))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/users/" + priya.username()).header("Authorization", viewer.token()))
                .andExpect(jsonPath("$.emailVerified").doesNotExist())
                .andExpect(jsonPath("$.admin").doesNotExist());
    }

    @Test
    void searchPagesThroughResults() throws Exception {
        String tag = "pg" + UUID.randomUUID().toString().substring(0, 5);
        Account viewer = register("pager", "Pager");
        for (int i = 0; i < 21; i++) register(tag, "Same Name");
        mvc.perform(get("/api/users").param("q", tag).header("Authorization", viewer.token()))
                .andExpect(jsonPath("$.items", hasSize(20))).andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/api/users").param("q", tag).param("page", "1").header("Authorization", viewer.token()))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void blockingHidesBothPeopleFromEachOther() throws Exception {
        Account bob = register("bob", "Bob");
        Account carol = register("carol", "Carol");
        long bobsPost = createPost(bob, "bob's post");
        long carolsPost = createPost(carol, "carol's post");
        mvc.perform(post("/api/users/" + carol.username() + "/follow").header("Authorization", bob.token())).andExpect(status().isOk());

        mvc.perform(post("/api/users/" + carol.username() + "/block").header("Authorization", bob.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedByMe").value(true))
                .andExpect(jsonPath("$.followedByMe").value(false)); // the follow was removed

        // Bob no longer sees Carol's posts anywhere, nor her in search or suggestions.
        mvc.perform(get("/api/feed").header("Authorization", bob.token()))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) carolsPost))));
        mvc.perform(get("/api/posts").header("Authorization", bob.token()))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) carolsPost))));
        mvc.perform(get("/api/users").param("q", carol.username()).header("Authorization", bob.token()))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/users/suggestions").param("limit", "20").header("Authorization", bob.token()))
                .andExpect(jsonPath("$[*].username", not(hasItem(carol.username()))));
        // Bob can still open Carol's profile (to unblock), but sees none of her posts.
        mvc.perform(get("/api/users/" + carol.username()).header("Authorization", bob.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blockedByMe").value(true));
        mvc.perform(get("/api/posts").param("username", carol.username()).header("Authorization", bob.token()))
                .andExpect(jsonPath("$", hasSize(0)));

        // Carol can't find Bob, see his profile or posts, follow, like or comment.
        mvc.perform(get("/api/users/" + bob.username()).header("Authorization", carol.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + bobsPost).header("Authorization", carol.token())).andExpect(status().isNotFound());
        mvc.perform(post("/api/users/" + bob.username() + "/follow").header("Authorization", carol.token())).andExpect(status().isNotFound());
        mvc.perform(post("/api/posts/" + bobsPost + "/like").header("Authorization", carol.token())).andExpect(status().isNotFound());
        mvc.perform(post("/api/posts/" + bobsPost + "/comments").header("Authorization", carol.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}")).andExpect(status().isNotFound());

        mvc.perform(get("/api/users/me/blocked").header("Authorization", bob.token()))
                .andExpect(jsonPath("$[0].username").value(carol.username()));

        mvc.perform(delete("/api/users/" + carol.username() + "/block").header("Authorization", bob.token()))
                .andExpect(jsonPath("$.blockedByMe").value(false));
        mvc.perform(get("/api/users/" + bob.username()).header("Authorization", carol.token())).andExpect(status().isOk());
        mvc.perform(get("/api/feed").header("Authorization", bob.token()))
                .andExpect(jsonPath("$[*].id", hasItem((int) carolsPost)));
    }

    @Test
    void commentsCanBeDeletedByTheirAuthorThePostOwnerOrAModerator() throws Exception {
        Account owner = register("owner", "Owner");
        Account commenter = register("commenter", "Commenter");
        Account stranger = register("stranger", "Stranger");
        long postId = createPost(owner, "discuss");
        String path = "/api/posts/" + postId + "/comments";
        long c1 = om.readTree(mvc.perform(post(path).header("Authorization", commenter.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"first\"}")).andReturn().getResponse().getContentAsString()).get("id").asLong();
        long c2 = om.readTree(mvc.perform(post(path).header("Authorization", commenter.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"second\"}")).andReturn().getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(delete(path + "/" + c1).header("Authorization", stranger.token())).andExpect(status().isForbidden());
        mvc.perform(delete(path + "/" + c1).header("Authorization", owner.token())).andExpect(status().isNoContent());
        mvc.perform(delete(path + "/" + c2).header("Authorization", commenter.token())).andExpect(status().isNoContent());
        mvc.perform(get(path).header("Authorization", owner.token())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void reportsGoToModeratorsWhoCanRemoveContentAndSuspendAccounts() throws Exception {
        Account spammer = register("spammer", "Spammer");
        Account reporter = register("reporter", "Reporter");
        Account mod = register("mod", "Moderator");
        User modUser = users.findByUsername(mod.username()).orElseThrow();
        modUser.setRole(User.ROLE_ADMIN);
        users.save(modUser);

        long spamPost = createPost(spammer, "BUY FOLLOWERS NOW");
        String report = om.writeValueAsString(Map.of("targetType", "POST", "targetId", spamPost, "reason", "SPAM", "details", "bot"));
        mvc.perform(post("/api/reports").header("Authorization", reporter.token()).contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/reports").header("Authorization", reporter.token()).contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/reports").header("Authorization", spammer.token()).contentType(MediaType.APPLICATION_JSON).content(report))
                .andExpect(status().isBadRequest()); // can't report yourself

        // Regular users can't see the moderation queue.
        mvc.perform(get("/api/admin/reports").header("Authorization", reporter.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me").header("Authorization", mod.token())).andExpect(jsonPath("$.admin").value(true));

        JsonNode queue = json("/api/admin/reports", mod);
        JsonNode mine = null;
        for (JsonNode r : queue) if (r.get("targetId").asLong() == spamPost) mine = r;
        org.junit.jupiter.api.Assertions.assertNotNull(mine);
        org.junit.jupiter.api.Assertions.assertEquals("BUY FOLLOWERS NOW", mine.get("target").get("text").asText());

        mvc.perform(post("/api/admin/reports/" + mine.get("id").asLong() + "/resolve").header("Authorization", mod.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REMOVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.target.exists").value(false));
        mvc.perform(get("/api/posts/" + spamPost).header("Authorization", reporter.token())).andExpect(status().isNotFound());

        // Report the account itself, then suspend it.
        long spammerId = users.findByUsername(spammer.username()).orElseThrow().getId();
        long laterPost = createPost(spammer, "still spamming");
        mvc.perform(post("/api/reports").header("Authorization", reporter.token()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("targetType", "USER", "targetId", spammerId, "reason", "SPAM"))))
                .andExpect(status().isCreated());
        long userReport = -1;
        for (JsonNode r : json("/api/admin/reports", mod)) if (r.get("targetId").asLong() == spammerId) userReport = r.get("id").asLong();
        mvc.perform(post("/api/admin/reports/" + userReport + "/resolve").header("Authorization", mod.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"SUSPEND\"}"))
                .andExpect(jsonPath("$.resolution").value("Suspended @" + spammer.username()));

        // Suspended: existing session stops working, can't sign in, posts and profile hidden.
        mvc.perform(get("/api/users/me").header("Authorization", spammer.token())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", spammer.username(), "password", "password123"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/feed").header("Authorization", reporter.token()))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) laterPost))));
        mvc.perform(get("/api/users/" + spammer.username()).header("Authorization", reporter.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/" + spammer.username()).header("Authorization", mod.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.suspended").value(true));

        // Moderators can lift it.
        mvc.perform(delete("/api/admin/users/" + spammer.username() + "/suspend").header("Authorization", mod.token()))
                .andExpect(jsonPath("$.suspended").value(false));
        mvc.perform(get("/api/users/" + spammer.username()).header("Authorization", reporter.token())).andExpect(status().isOk());
    }
}
