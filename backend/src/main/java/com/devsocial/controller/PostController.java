package com.devsocial.controller;

import com.devsocial.dto.PostRequest;
import com.devsocial.dto.PostResponse;
import com.devsocial.model.Categories;
import com.devsocial.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping("/categories")
    public List<String> categories() {
        return Categories.ALL;
    }

    /** Text-only post as JSON: {@code { content, categories: [...] }} (or a single {@code category}). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@Valid @RequestBody PostRequest req, Principal principal) {
        return postService.create(principal.getName(), req);
    }

    /** Post with an optional image as multipart form data: fields content, categories (repeated) or category, image. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createWithImage(@RequestParam(required = false) String content,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) List<String> categories,
                                        @RequestPart(required = false) MultipartFile image,
                                        Principal principal) {
        return postService.create(principal.getName(), content, PostService.merge(category, categories), image);
    }

    @GetMapping
    public List<PostResponse> list(@RequestParam(required = false) String category,
                                   @RequestParam(required = false) String username,
                                   Principal principal) {
        return postService.list(category, username, principal.getName());
    }

    @GetMapping("/{id}")
    public PostResponse get(@PathVariable Long id, Principal principal) {
        return postService.get(id, principal.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Principal principal) {
        postService.delete(id, principal.getName());
    }
}
