package com.devsocial.controller;

import com.devsocial.push.WebPushCrypto;
import com.devsocial.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.interfaces.ECPublicKey;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Turning push on/off for a device, with VAPID keys configured. */
@SpringBootTest(properties = {
        "app.push.vapid.public-key=BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
        "app.push.vapid.private-key=yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
})
@AutoConfigureMockMvc
class PushIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired PushSubscriptionRepository subscriptions;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;

    private String register(String prefix) throws Exception {
        String name = prefix + "_" + UUID.randomUUID().toString().substring(0, 6);
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", name, "email", name + "@example.com", "password", "password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(res).get("token").asText();
    }

    private String body(String endpoint, String p256dh, String auth) throws Exception {
        return om.writeValueAsString(Map.of("endpoint", endpoint, "keys", Map.of("p256dh", p256dh, "auth", auth)));
    }

    @Test
    void subscribeUnsubscribeAndDeviceHandover() throws Exception {
        String alice = register("alice"), bob = register("bob");
        mvc.perform(get("/api/push/config").header("Authorization", alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicKey").value(org.hamcrest.Matchers.startsWith("BP4z9")));
        mvc.perform(get("/api/push/config")).andExpect(status().isUnauthorized());

        String p256dh = WebPushCrypto.b64(WebPushCrypto.encode((ECPublicKey) WebPushCrypto.generateKeyPair().getPublic()));
        String auth = WebPushCrypto.b64(new byte[16]);
        String endpoint = "https://push.example.test/send/" + UUID.randomUUID();

        mvc.perform(post("/api/push/subscribe").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                .content(body(endpoint, p256dh, auth))).andExpect(status().isNoContent());
        // Subscribing again is fine (same device); bob signing in on the same phone takes it over.
        mvc.perform(post("/api/push/subscribe").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                .content(body(endpoint, p256dh, auth))).andExpect(status().isNoContent());
        mvc.perform(post("/api/push/subscribe").header("Authorization", bob).contentType(MediaType.APPLICATION_JSON)
                .content(body(endpoint, p256dh, auth))).andExpect(status().isNoContent());
        assertTrue(subscriptions.findByEndpoint(endpoint).isPresent());
        assertTrue(tx.execute(st -> subscriptions.findByEndpoint(endpoint).get().getUser().getUsername()).startsWith("bob"));

        // Alice can't remove bob's device; bob can.
        mvc.perform(post("/api/push/unsubscribe").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("endpoint", endpoint)))).andExpect(status().isNoContent());
        assertTrue(subscriptions.findByEndpoint(endpoint).isPresent());
        mvc.perform(post("/api/push/unsubscribe").header("Authorization", bob).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("endpoint", endpoint)))).andExpect(status().isNoContent());
        assertFalse(subscriptions.findByEndpoint(endpoint).isPresent());
    }

    @Test
    void rejectsNonHttpsEndpointsAndBadKeys() throws Exception {
        String alice = register("alice");
        String p256dh = WebPushCrypto.b64(WebPushCrypto.encode((ECPublicKey) WebPushCrypto.generateKeyPair().getPublic()));
        String auth = WebPushCrypto.b64(new byte[16]);
        for (String bad : new String[]{"http://169.254.169.254/latest", "file:///etc/passwd", "not a url"}) {
            mvc.perform(post("/api/push/subscribe").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                    .content(body(bad, p256dh, auth))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/push/subscribe").header("Authorization", alice).contentType(MediaType.APPLICATION_JSON)
                .content(body("https://push.example.test/x", "AAAA", auth))).andExpect(status().isBadRequest());
    }
}
