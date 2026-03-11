package com.notification.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * SMS via AWS SNS (PaaS). Configure AWS credentials via env/profile.
 */
@Component
@ConditionalOnProperty(name = "notification.channels.sms.provider", havingValue = "aws-sns")
public class AwsSnsSmsProvider implements SmsProvider {

    private final SnsClient snsClient;

    public AwsSnsSmsProvider(@Value("${AWS_REGION:us-east-1}") String region) {
        this.snsClient = SnsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public DeliveryResult send(String toPhoneNumber, String message) {
        try {
            PublishRequest request = PublishRequest.builder()
                    .phoneNumber(toPhoneNumber)
                    .message(message)
                    .build();
            PublishResponse response = snsClient.publish(request);
            return DeliveryResult.builder()
                    .success(true)
                    .messageId(response.messageId())
                    .build();
        } catch (Exception e) {
            return DeliveryResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}
