package com.notification.service;

import com.notification.channel.*;
import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import com.notification.document.NotificationEvent;
import com.notification.entity.InternalUser;
import com.notification.entity.NotificationSubscription;
import com.notification.entity.NotificationTemplate;
import com.notification.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEngine {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final InternalUserRepository internalUserRepository;
    private final NotificationEventRepository eventRepository;
    private final EmailProvider emailProvider;
    private final SmsProvider smsProvider;
    private final PushProvider pushProvider;
    private final IdempotencyService idempotencyService;

    /**
     * Resolve recipients for the given channel and audience (e.g. INTERNAL = internal users, MOBILE_APP = subscribers).
     */
    public List<String> resolveRecipients(Channel channel, AudienceType audienceType, String audienceFilter) {
        if (audienceType == AudienceType.INTERNAL) {
            return internalUserRepository.findAll().stream()
                    .map(InternalUser::getEmail)
                    .collect(Collectors.toList());
        }
        List<NotificationSubscription> subs = subscriptionRepository.findByAudienceTypeAndChannel(audienceType, channel);
        return subs.stream().map(NotificationSubscription::getDestination).distinct().collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public String renderBody(NotificationTemplate template, Map<String, String> context) {
        String body = template.getBodyTemplate();
        if (context != null) {
            for (Map.Entry<String, String> e : context.entrySet()) {
                body = body.replace("{{" + e.getKey() + "}}", e.getValue() != null ? e.getValue() : "");
            }
        }
        return body;
    }

    public String renderSubject(NotificationTemplate template, Map<String, String> context) {
        String sub = template.getSubjectTemplate() != null ? template.getSubjectTemplate() : "";
        if (context != null) {
            for (Map.Entry<String, String> e : context.entrySet()) {
                sub = sub.replace("{{" + e.getKey() + "}}", e.getValue() != null ? e.getValue() : "");
            }
        }
        return sub;
    }

    /**
     * Send to a single recipient; used for immediate and scheduled sends. Logs to MongoDB.
     */
    public DeliveryResult sendOne(NotificationTemplate template, String recipient, Map<String, String> context, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<DeliveryResult> cached = idempotencyService.getCachedResult(idempotencyKey);
            if (cached.isPresent()) return cached.get();
        }

        String body = renderBody(template, context);
        String subject = renderSubject(template, context);
        DeliveryResult result;

        switch (template.getChannel()) {
            case EMAIL -> result = emailProvider.send(recipient, subject, body);
            case SMS -> result = smsProvider.send(recipient, body);
            case PUSH -> result = pushProvider.send(recipient, subject, body);
            default -> result = DeliveryResult.builder().success(false).errorMessage("Unknown channel").build();
        }

        NotificationEvent event = new NotificationEvent();
        event.setRequestId(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());
        event.setChannel(template.getChannel().name());
        event.setRecipient(recipient);
        event.setPayload(Map.of("subject", subject, "bodyLength", body.length()));
        event.setStatus(result.isSuccess() ? "SENT" : "FAILED");
        event.setSentAt(Instant.now());
        event.setProviderResponse(result.isSuccess() ? result.getMessageId() : result.getErrorMessage());
        eventRepository.save(event);

        if (idempotencyKey != null && !idempotencyKey.isBlank())
            idempotencyService.cacheResult(idempotencyKey, result);

        return result;
    }

    /**
     * Send to all resolved recipients for channel + audience (e.g. new mobile user → email to internal users).
     */
    public List<DeliveryResult> sendToAudience(UUID templateId, Channel channel, AudienceType audienceType, String audienceFilter,
                                               Map<String, String> context, String idempotencyKeyPrefix) {
        NotificationTemplate template = templateRepository.findById(templateId).orElseThrow();
        List<String> recipients = resolveRecipients(channel, audienceType, audienceFilter);
        List<DeliveryResult> results = new ArrayList<>();
        for (int i = 0; i < recipients.size(); i++) {
            String key = idempotencyKeyPrefix != null ? idempotencyKeyPrefix + "-" + i : null;
            results.add(sendOne(template, recipients.get(i), context, key));
        }
        return results;
    }
}
