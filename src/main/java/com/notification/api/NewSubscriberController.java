package com.notification.api;

import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import com.notification.service.NotificationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Use case: When a new mobile application user subscribes, send an email notification
 * to the designated internal user(s).
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class NewSubscriberController {

    private final NotificationEngine notificationEngine;
    private final com.notification.repository.NotificationTemplateRepository templateRepository;

    @PostMapping("/mobile-user-subscribed")
    public ResponseEntity<Void> onMobileUserSubscribed(
            @RequestBody MobileUserSubscribedPayload payload,
            @RequestParam UUID templateId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        // Resolve internal users and send email via template
        if (templateRepository.findById(templateId).isEmpty())
            return ResponseEntity.badRequest().build();
        String prefix = idempotencyKey != null ? idempotencyKey : "mobile-subscribed-" + payload.userId();
        Map<String, String> context = Map.of(
                "userId", payload.userId(),
                "email", payload.email() != null ? payload.email() : ""
        );
        notificationEngine.sendToAudience(
                templateId,
                Channel.EMAIL,
                AudienceType.INTERNAL,
                null,
                context,
                prefix);
        return ResponseEntity.accepted().build();
    }

    public record MobileUserSubscribedPayload(String userId, String email) {}
}
