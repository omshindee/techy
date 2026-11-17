package com.devsocial.controller;

import com.devsocial.dto.PushDtos.PushConfig;
import com.devsocial.dto.PushDtos.SubscribeRequest;
import com.devsocial.dto.PushDtos.UnsubscribeRequest;
import com.devsocial.service.PushService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/** Turning phone/desktop notifications on and off for this device. */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    @GetMapping("/config")
    public PushConfig config() {
        return new PushConfig(pushService.publicKey());
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody SubscribeRequest req, Principal principal) {
        pushService.subscribe(principal.getName(), req);
    }

    /** Sends a test notification to all of your devices now, and says what each push service answered. */
    @PostMapping("/test")
    public java.util.List<PushService.DeviceResult> test(Principal principal) {
        return pushService.sendTest(principal.getName());
    }

    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody UnsubscribeRequest req, Principal principal) {
        pushService.unsubscribe(principal.getName(), req.endpoint());
    }
}
