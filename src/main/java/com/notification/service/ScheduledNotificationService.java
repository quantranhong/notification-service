package com.notification.service;

import com.notification.domain.NotificationStatus;
import com.notification.entity.ScheduledNotification;
import com.notification.repository.ScheduledNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledNotificationService {

    private final ScheduledNotificationRepository scheduledRepository;
    private final NotificationEngine notificationEngine;

    @Transactional
    public ScheduledNotification create(ScheduledNotification scheduled) {
        scheduled.setStatus(NotificationStatus.PENDING);
        return scheduledRepository.save(scheduled);
    }

    @Transactional(readOnly = true)
    public Optional<ScheduledNotification> getById(UUID id) {
        return scheduledRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ScheduledNotification> listDue() {
        return scheduledRepository.findDueScheduled(NotificationStatus.PENDING, Instant.now());
    }

    @Transactional
    public void processDueScheduled() {
        List<ScheduledNotification> due = listDue();
        for (ScheduledNotification s : due) {
            try {
                s.setStatus(NotificationStatus.SENT);
                scheduledRepository.save(s);
                notificationEngine.sendToAudience(
                        s.getTemplate().getId(),
                        s.getChannel(),
                        s.getAudienceType(),
                        s.getAudienceFilter(),
                        parseContext(s.getContextJson()),
                        "scheduled-" + s.getId());
            } catch (Exception e) {
                log.warn("Failed to process scheduled notification {}", s.getId(), e);
            }
        }
    }

    @Transactional
    public boolean cancel(UUID id) {
        return scheduledRepository.findById(id)
                .map(s -> {
                    if (s.getStatus() == NotificationStatus.PENDING) {
                        s.setStatus(NotificationStatus.CANCELLED);
                        scheduledRepository.save(s);
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    private static java.util.Map<String, String> parseContext(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) return java.util.Collections.emptyMap();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = mapper.readValue(contextJson, java.util.Map.class);
            java.util.Map<String, String> out = new java.util.HashMap<>();
            map.forEach((k, v) -> out.put(k, v != null ? v.toString() : null));
            return out;
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }
}
