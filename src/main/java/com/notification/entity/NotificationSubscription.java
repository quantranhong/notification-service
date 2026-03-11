package com.notification.entity;

import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_subscriptions", indexes = {
    @Index(name = "idx_sub_user_channel", columnList = "user_id, channel"),
    @Index(name = "idx_sub_audience", columnList = "audience_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false)
    private AudienceType audienceType;

    @Column(nullable = false)
    private String destination; // phone, email, or push token

    @Column(columnDefinition = "TEXT")
    private String preferences; // JSON optional

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
