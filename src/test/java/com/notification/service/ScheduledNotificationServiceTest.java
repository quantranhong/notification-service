package com.notification.service;

import com.notification.domain.Channel;
import com.notification.domain.NotificationStatus;
import com.notification.domain.AudienceType;
import com.notification.entity.NotificationTemplate;
import com.notification.entity.ScheduledNotification;
import com.notification.repository.ScheduledNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledNotificationServiceTest {

    @Mock private ScheduledNotificationRepository scheduledRepository;
    @Mock private NotificationEngine notificationEngine;

    private ScheduledNotificationService service;

    private NotificationTemplate template;
    private ScheduledNotification scheduled;

    @BeforeEach
    void setUp() {
        service = new ScheduledNotificationService(scheduledRepository, notificationEngine);
        template = NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .channel(Channel.EMAIL)
                .name("T")
                .bodyTemplate("Body")
                .build();
        scheduled = ScheduledNotification.builder()
                .template(template)
                .channel(Channel.EMAIL)
                .audienceType(AudienceType.INTERNAL)
                .scheduledAt(Instant.now().minusSeconds(60))
                .status(NotificationStatus.PENDING)
                .build();
        scheduled.setId(UUID.randomUUID());
    }

    @Test
    void create_setsPendingAndSaves() {
        ScheduledNotification input = ScheduledNotification.builder()
                .template(template)
                .channel(Channel.EMAIL)
                .audienceType(AudienceType.INTERNAL)
                .scheduledAt(Instant.now())
                .build();
        when(scheduledRepository.save(any(ScheduledNotification.class))).thenAnswer(inv -> inv.getArgument(0));

        ScheduledNotification result = service.create(input);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
        verify(scheduledRepository).save(input);
    }

    @Test
    void getById_returnsOptionalFromRepository() {
        when(scheduledRepository.findById(scheduled.getId())).thenReturn(Optional.of(scheduled));

        assertThat(service.getById(scheduled.getId())).contains(scheduled);
    }

    @Test
    void listDue_returnsFindDueScheduledResult() {
        when(scheduledRepository.findDueScheduled(eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(scheduled));

        List<ScheduledNotification> due = service.listDue();

        assertThat(due).containsExactly(scheduled);
    }

    @Test
    void processDueScheduled_updatesStatusAndCallsEngine() {
        when(scheduledRepository.findDueScheduled(eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(scheduled));
        when(scheduledRepository.save(any(ScheduledNotification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processDueScheduled();

        ArgumentCaptor<ScheduledNotification> captor = ArgumentCaptor.forClass(ScheduledNotification.class);
        verify(scheduledRepository, atLeast(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(notificationEngine).sendToAudience(
                eq(template.getId()), eq(Channel.EMAIL), eq(AudienceType.INTERNAL),
                eq(scheduled.getAudienceFilter()), any(), any());
    }

    @Test
    void cancel_pending_returnsTrueAndUpdatesStatus() {
        when(scheduledRepository.findById(scheduled.getId())).thenReturn(Optional.of(scheduled));
        when(scheduledRepository.save(any(ScheduledNotification.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean cancelled = service.cancel(scheduled.getId());

        assertThat(cancelled).isTrue();
        verify(scheduledRepository).save(scheduled);
        assertThat(scheduled.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
    }

    @Test
    void cancel_notFound_returnsFalse() {
        when(scheduledRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        boolean cancelled = service.cancel(UUID.randomUUID());

        assertThat(cancelled).isFalse();
        verify(scheduledRepository, never()).save(any());
    }

    @Test
    void cancel_alreadySent_returnsFalse() {
        scheduled.setStatus(NotificationStatus.SENT);
        when(scheduledRepository.findById(scheduled.getId())).thenReturn(Optional.of(scheduled));

        boolean cancelled = service.cancel(scheduled.getId());

        assertThat(cancelled).isFalse();
        verify(scheduledRepository, never()).save(any());
    }
}
