package com.notification.domain;

/**
 * Status of a notification or scheduled job.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
    CANCELLED
}
