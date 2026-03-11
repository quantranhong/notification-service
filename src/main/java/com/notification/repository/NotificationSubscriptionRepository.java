package com.notification.repository;

import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import com.notification.entity.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, UUID> {

    List<NotificationSubscription> findByUserId(String userId);

    List<NotificationSubscription> findByUserIdAndChannel(String userId, Channel channel);

    List<NotificationSubscription> findByAudienceTypeAndChannel(AudienceType audienceType, Channel channel);
}
