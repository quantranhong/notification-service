package com.notification.api;

import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import com.notification.entity.NotificationSubscription;
import com.notification.repository.NotificationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final NotificationSubscriptionRepository subscriptionRepository;

    @PostMapping
    public ResponseEntity<NotificationSubscription> create(@RequestBody NotificationSubscription subscription) {
        return ResponseEntity.ok(subscriptionRepository.save(subscription));
    }

    @GetMapping
    public List<NotificationSubscription> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Channel channel,
            @RequestParam(required = false) AudienceType audienceType) {
        if (userId != null && channel != null)
            return subscriptionRepository.findByUserIdAndChannel(userId, channel);
        if (userId != null)
            return subscriptionRepository.findByUserId(userId);
        if (audienceType != null && channel != null)
            return subscriptionRepository.findByAudienceTypeAndChannel(audienceType, channel);
        return subscriptionRepository.findAll();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        subscriptionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
