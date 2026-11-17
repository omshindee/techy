package com.devsocial.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Posts can have one to three categories, including Tech and Memes, and show up under each of them. */
@SpringBootTest
@AutoConfigureMockMvc
class CategoriesIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String register() throws Exception {
        String name = "cat_" + UUID.randomUUID().toString().substring(0, 6);
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", name, "email", name + "@example.com", "password", "password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(res).get("token").asText();
    }

    private JsonNode json(String body) throws Exception {
        return om.readTree(body);
    }

    @Test
    void severalCategoriesPerPostAndFilteringByEach() throws Exception {
        String token = register();
        String unique = "meme about " + UUID.randomUUID();
        JsonNode created = json(mvc.perform(post("/api/posts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("content", unique, "categories", List.of("Memes", "Tech", "Memes", "Java")))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertEquals("Memes", created.get("category").asText());
        assertEquals(List.of("Memes", "Tech", "Java"), om.convertValue(created.get("categories"), List.class)); // repeat dropped

        for (String c : List.of("Memes", "Tech", "Java")) {
            String list = mvc.perform(get("/api/posts").param("category", c).header("Authorization", token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(list.contains(unique), "post should be listed under " + c);
        }
        String linux = mvc.perform(get("/api/posts").param("category", "Linux").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        assertFalse(linux.contains(unique));
    }

    @Test
    void singleCategoryStillWorksAndBadInputIsRejected() throws Exception {
        String token = register();
        mvc.perform(post("/api/posts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("content", "old client", "category", "Tech"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.categories[0]").value("Tech"));

        mvc.perform(post("/api/posts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("content", "x", "categories", List.of("Java", "Tech", "Memes", "DSA")))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Pick up to 3 categories"));
        mvc.perform(post("/api/posts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("content", "x", "categories", List.of("Cooking")))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Unknown category: Cooking"));
        mvc.perform(post("/api/posts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("content", "x"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Pick at least one category"));
    }

    @Test
    void multipartPostsTakeRepeatedCategories() throws Exception {
        String token = register();
        mvc.perform(multipart("/api/posts").param("content", "photo-less multipart")
                        .param("categories", "Tech").param("categories", "Memes").header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categories[0]").value("Tech"))
                .andExpect(jsonPath("$.categories[1]").value("Memes"));
    }
}
