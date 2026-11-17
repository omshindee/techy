package com.devsocial.exception;

import org.springframework.http.HttpStatus;

public class PostNotFoundException extends ApiException {
    public PostNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Post not found: " + id);
    }
}
