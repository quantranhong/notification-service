package com.notification.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

/**
 * Email via AWS SES (PaaS). Configure AWS credentials via env/profile.
 */
@Component
@ConditionalOnProperty(name = "notification.channels.email.provider", havingValue = "aws-ses")
public class AwsSesEmailProvider implements EmailProvider {

    private final SesClient sesClient;
    private final String fromAddress;

    public AwsSesEmailProvider(
            @Value("${notification.channels.email.from-address:noreply@example.com}") String fromAddress,
            @Value("${AWS_REGION:us-east-1}") String region) {
        this.fromAddress = fromAddress;
        this.sesClient = SesClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public DeliveryResult send(String to, String subject, String body) {
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(fromAddress)
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .text(Content.builder().data(body).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();
            SendEmailResponse response = sesClient.sendEmail(request);
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
