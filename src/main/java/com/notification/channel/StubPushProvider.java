package com.notification.channel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(PushProvider.class)
public class StubPushProvider implements PushProvider {

    @Override
    public DeliveryResult send(String deviceToken, String title, String body) {
        return DeliveryResult.builder()
                .success(true)
                .messageId("stub-push-" + System.currentTimeMillis())
                .build();
    }
}
