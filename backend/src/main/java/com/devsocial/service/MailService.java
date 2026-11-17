package com.devsocial.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends account emails (verification, password reset) over SMTP.
 * Without SMTP_HOST (local development) the email is written to the log instead, so you can
 * copy the link from `docker compose logs backend`. Sending runs in the background so a slow
 * mail server never delays a request, and response times don't reveal whether an account exists.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;
    private final String baseUrl;

    public MailService(ObjectProvider<JavaMailSender> sender,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${app.mail.from}") String from,
                       @Value("${app.base-url}") String baseUrl) {
        this.sender = sender;
        this.host = host;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** Absolute link into the web app, e.g. link("/verify-email?token=..."). */
    public String link(String pathAndQuery) {
        return baseUrl + pathAndQuery;
    }

    @Async
    public void send(String to, String subject, String body) {
        JavaMailSender mail = sender.getIfAvailable();
        if (host == null || host.isBlank() || mail == null) {
            log.warn("SMTP is not configured, so this email was NOT sent. To: {} | Subject: {}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mail.send(msg);
        } catch (MailException e) {
            log.error("Failed to send email '{}' to {}", subject, to, e);
        }
    }
}
