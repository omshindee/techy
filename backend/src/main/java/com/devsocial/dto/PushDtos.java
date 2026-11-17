package com.devsocial.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request/response shapes for push notifications. */
public final class PushDtos {
    private PushDtos() {}

    /** {@code publicKey}: the server's VAPID key for {@code pushManager.subscribe}; null when push isn't set up. */
    public record PushConfig(String publicKey) {}

    /** The shape of the browser's {@code PushSubscription.toJSON()}. */
    public record SubscribeRequest(@NotBlank @Size(max = 1024) String endpoint, @NotNull @Valid Keys keys) {
        public record Keys(@NotBlank @Size(max = 128) String p256dh, @NotBlank @Size(max = 64) String auth) {}
    }

    public record UnsubscribeRequest(@NotBlank @Size(max = 1024) String endpoint) {}
}
