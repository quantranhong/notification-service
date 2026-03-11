package com.notification.entity;

import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import com.notification.domain.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduled_notifications", indexes = {
    @Index(name = "idx_scheduled_status_at", columnList = "status, scheduled_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private NotificationTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false)
    private AudienceType audienceType;

    @Column(name = "audience_filter", columnDefinition = "TEXT")
    private String audienceFilter; // JSON: segment, user ids, etc.

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson; // template variables

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = NotificationStatus.PENDING;
    }
}
