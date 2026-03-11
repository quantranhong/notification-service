package com.notification.api;

import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import com.notification.domain.NotificationStatus;
import com.notification.entity.NotificationTemplate;
import com.notification.entity.ScheduledNotification;
import com.notification.entity.NotificationSubscription;
import com.notification.repository.NotificationTemplateRepository;
import com.notification.repository.ScheduledNotificationRepository;
import com.notification.repository.NotificationSubscriptionRepository;
import com.notification.service.NotificationEngine;
import com.notification.service.ScheduledNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Controller
@RequiredArgsConstructor
public class NotificationGraphQLController {

    private final NotificationEngine notificationEngine;
    private final ScheduledNotificationService scheduledService;
    private final NotificationTemplateRepository templateRepository;
    private final ScheduledNotificationRepository scheduledRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;

    @MutationMapping
    public SendNotificationPayload sendNotification(@Argument("input") SendNotificationInput input) {
        Map<String, String> context = input.context() != null
                ? input.context().stream().collect(Collectors.toMap(KeyValueInput::key, KeyValueInput::value))
                : Map.of();
        List<com.notification.channel.DeliveryResult> results = notificationEngine.sendToAudience(
                UUID.fromString(input.templateId()),
                Channel.valueOf(input.channel()),
                AudienceType.valueOf(input.audienceType()),
                null,
                context,
                input.idempotencyKey());
        int successCount = (int) results.stream().filter(com.notification.channel.DeliveryResult::isSuccess).count();
        return new SendNotificationPayload(successCount == results.size(), results.size(), successCount);
    }

    @MutationMapping
    public ScheduledNotification createScheduledNotification(@Argument("input") CreateScheduledInput input) {
        NotificationTemplate template = templateRepository.findById(UUID.fromString(input.templateId()))
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        Map<String, String> context = input.context() != null
                ? input.context().stream().collect(Collectors.toMap(KeyValueInput::key, KeyValueInput::value))
                : Map.of();
        String contextJson = context.isEmpty() ? null : toJson(context);
        ScheduledNotification s = ScheduledNotification.builder()
                .template(template)
                .channel(Channel.valueOf(input.channel()))
                .audienceType(AudienceType.valueOf(input.audienceType()))
                .audienceFilter(input.audienceFilter())
                .scheduledAt(Instant.parse(input.scheduledAt()))
                .timezone(input.timezone())
                .contextJson(contextJson)
                .status(NotificationStatus.PENDING)
                .build();
        return scheduledService.create(s);
    }

    @MutationMapping
    public Boolean cancelScheduledNotification(@Argument("id") String id) {
        return scheduledService.cancel(UUID.fromString(id));
    }

    @QueryMapping
    public List<ScheduledNotification> scheduledNotifications(@Argument("status") String status) {
        if (status != null) {
            NotificationStatus s = NotificationStatus.valueOf(status);
            return StreamSupport.stream(scheduledRepository.findAll().spliterator(), false)
                    .filter(n -> n.getStatus() == s)
                    .toList();
        }
        return scheduledRepository.findAll();
    }

    @QueryMapping
    public List<NotificationSubscription> subscriptions(
            @Argument("userId") String userId,
            @Argument("channel") String channel,
            @Argument("audienceType") String audienceType) {
        if (userId != null && channel != null)
            return subscriptionRepository.findByUserIdAndChannel(userId, Channel.valueOf(channel));
        if (userId != null)
            return subscriptionRepository.findByUserId(userId);
        if (audienceType != null && channel != null)
            return subscriptionRepository.findByAudienceTypeAndChannel(
                    AudienceType.valueOf(audienceType), Channel.valueOf(channel));
        return subscriptionRepository.findAll();
    }

    private static String toJson(Map<String, String> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    public record SendNotificationInput(String channel, String audienceType, String templateId,
                                        List<KeyValueInput> context, String idempotencyKey) {}
    public record KeyValueInput(String key, String value) {}
    public record CreateScheduledInput(String templateId, String channel, String audienceType,
                                       String audienceFilter, String scheduledAt, String timezone,
                                       List<KeyValueInput> context) {}
    public record SendNotificationPayload(boolean success, int totalRecipients, int successCount) {}
}
