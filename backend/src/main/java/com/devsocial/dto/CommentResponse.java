package com.devsocial.dto;

import java.time.Instant;

public record CommentResponse(Long id, String content, Instant createdAt, PostResponse.Author author) {}
