package com.notification.channel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Stub for local/dev when AWS SES is not configured.
 */
@Component
@ConditionalOnMissingBean(EmailProvider.class)
public class StubEmailProvider implements EmailProvider {

    @Override
    public DeliveryResult send(String to, String subject, String body) {
        return DeliveryResult.builder()
                .success(true)
                .messageId("stub-email-" + System.currentTimeMillis())
                .build();
    }
}
