package com.devsocial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // emails are sent in the background
public class DevSocialApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevSocialApplication.class, args);
    }
}
