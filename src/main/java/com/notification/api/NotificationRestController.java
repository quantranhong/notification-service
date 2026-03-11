package com.notification.api;

import com.notification.api.dto.CreateScheduledRequest;
import com.notification.api.dto.SendNotificationRequest;
import com.notification.api.dto.SendNotificationResponse;
import com.notification.channel.DeliveryResult;
import com.notification.domain.NotificationStatus;
import com.notification.entity.ScheduledNotification;
import com.notification.entity.NotificationTemplate;
import com.notification.service.NotificationEngine;
import com.notification.service.ScheduledNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationRestController {

    private final NotificationEngine notificationEngine;
    private final ScheduledNotificationService scheduledService;
    private final com.notification.repository.NotificationTemplateRepository templateRepository;
    private final com.notification.repository.ScheduledNotificationRepository scheduledRepository;

    @PostMapping
    public ResponseEntity<SendNotificationResponse> send(@Valid @RequestBody SendNotificationRequest request) {
        NotificationTemplate template = templateRepository.findById(request.getTemplateId())
                .orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        String prefix = request.getIdempotencyKey() != null ? request.getIdempotencyKey() : "immediate-" + UUID.randomUUID();
        List<DeliveryResult> results = notificationEngine.sendToAudience(
                request.getTemplateId(),
                request.getChannel(),
                request.getAudienceType(),
                null,
                request.getContext(),
                prefix);
        int successCount = (int) results.stream().filter(DeliveryResult::isSuccess).count();
        return ResponseEntity.ok(SendNotificationResponse.builder()
                .success(successCount == results.size())
                .totalRecipients(results.size())
                .successCount(successCount)
                .results(results)
                .build());
    }

    @PostMapping("/scheduled")
    public ResponseEntity<ScheduledNotification> createScheduled(@Valid @RequestBody CreateScheduledRequest request) {
        NotificationTemplate template = templateRepository.findById(request.getTemplateId()).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        ScheduledNotification scheduled = ScheduledNotification.builder()
                .template(template)
                .channel(request.getChannel())
                .audienceType(request.getAudienceType())
                .audienceFilter(request.getAudienceFilter())
                .scheduledAt(request.getScheduledAt())
                .timezone(request.getTimezone())
                .contextJson(toJson(request.getContext()))
                .status(NotificationStatus.PENDING)
                .build();
        return ResponseEntity.ok(scheduledService.create(scheduled));
    }

    @GetMapping("/scheduled")
    public List<ScheduledNotification> listScheduled(
            @RequestParam(required = false) NotificationStatus status) {
        if (status != null)
            return scheduledRepository.findAll().stream().filter(s -> s.getStatus() == status).collect(Collectors.toList());
        return scheduledRepository.findAll();
    }

    @DeleteMapping("/scheduled/{id}")
    public ResponseEntity<Void> cancelScheduled(@PathVariable UUID id) {
        return scheduledService.cancel(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private static String toJson(Map<String, String> context) {
        if (context == null || context.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(context);
        } catch (Exception e) {
            return "{}";
        }
    }
}
