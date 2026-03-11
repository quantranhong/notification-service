package com.notification.repository;

import com.notification.domain.Channel;
import com.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    List<NotificationTemplate> findByChannel(Channel channel);
}
