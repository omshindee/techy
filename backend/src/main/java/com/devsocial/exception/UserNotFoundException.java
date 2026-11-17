package com.devsocial.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {
    public UserNotFoundException(String username) {
        super(HttpStatus.NOT_FOUND, "User not found: " + username);
    }
}
