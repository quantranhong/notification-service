package com.notification.repository;

import com.notification.document.NotificationEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationEventRepository extends MongoRepository<NotificationEvent, String> {
}
