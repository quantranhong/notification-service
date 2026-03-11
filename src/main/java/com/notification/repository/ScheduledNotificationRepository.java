package com.notification.repository;

import com.notification.domain.NotificationStatus;
import com.notification.entity.ScheduledNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScheduledNotificationRepository extends JpaRepository<ScheduledNotification, UUID> {

    @Query("SELECT s FROM ScheduledNotification s WHERE s.status = :status AND s.scheduledAt <= :before ORDER BY s.scheduledAt")
    List<ScheduledNotification> findDueScheduled(NotificationStatus status, Instant before);
}
