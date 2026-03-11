package com.notification.channel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(SmsProvider.class)
public class StubSmsProvider implements SmsProvider {

    @Override
    public DeliveryResult send(String toPhoneNumber, String message) {
        return DeliveryResult.builder()
                .success(true)
                .messageId("stub-sms-" + System.currentTimeMillis())
                .build();
    }
}
