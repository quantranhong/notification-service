package com.notification;

import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import com.notification.entity.InternalUser;
import com.notification.entity.NotificationSubscription;
import com.notification.entity.NotificationTemplate;
import com.notification.repository.InternalUserRepository;
import com.notification.repository.NotificationEventRepository;
import com.notification.repository.NotificationSubscriptionRepository;
import com.notification.repository.NotificationTemplateRepository;
import com.notification.repository.ScheduledNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests: full Spring context, Testcontainers (Postgres, MongoDB, Redis), stub channel providers.
 * Exercises REST APIs and persistence.
 */
class NotificationServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private NotificationTemplateRepository templateRepository;
    @Autowired
    private InternalUserRepository internalUserRepository;
    @Autowired
    private NotificationSubscriptionRepository subscriptionRepository;
    @Autowired
    private NotificationEventRepository eventRepository;
    @Autowired
    private ScheduledNotificationRepository scheduledRepository;

    private TestRestTemplate rest;
    private NotificationTemplate savedTemplate;

    @BeforeEach
    void setUp() {
        rest = restTemplate();
        internalUserRepository.deleteAll();
        subscriptionRepository.deleteAll();
        templateRepository.deleteAll();
        scheduledRepository.deleteAll();
        eventRepository.deleteAll();

        savedTemplate = templateRepository.save(NotificationTemplate.builder()
                .channel(Channel.EMAIL)
                .name("Welcome")
                .bodyTemplate("Hello {{userId}}")
                .subjectTemplate("New user {{userId}}")
                .build());
    }

    @Test
    void templates_api_createAndList() {
        NotificationTemplate newTemplate = NotificationTemplate.builder()
                .channel(Channel.SMS)
                .name("SMS Alert")
                .bodyTemplate("Alert: {{msg}}")
                .build();

        ResponseEntity<NotificationTemplate> createResp = rest.postForEntity(
                baseUrl() + "/api/v1/templates",
                jsonEntity(newTemplate),
                NotificationTemplate.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResp.getBody()).isNotNull();
        assertThat(createResp.getBody().getName()).isEqualTo("SMS Alert");

        ResponseEntity<List<NotificationTemplate>> listResp = rest.exchange(
                baseUrl() + "/api/v1/templates",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void sendNotification_toInternalAudience_returns200AndLogsEvent() {
        internalUserRepository.save(new InternalUser(null, "ops@test.com", "OPERATOR"));
        internalUserRepository.save(new InternalUser(null, "support@test.com", "SUPPORT"));

        Map<String, Object> request = Map.of(
                "channel", "EMAIL",
                "audienceType", "INTERNAL",
                "templateId", savedTemplate.getId().toString(),
                "context", Map.of("userId", "user-123"));

        ResponseEntity<Map> resp = rest.postForEntity(
                baseUrl() + "/api/v1/notifications",
                jsonEntity(request),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("success")).isEqualTo(true);
        assertThat(resp.getBody().get("totalRecipients")).isEqualTo(2);
        assertThat(resp.getBody().get("successCount")).isEqualTo(2);

        List<?> events = eventRepository.findAll();
        assertThat(events).hasSize(2);
    }

    @Test
    void subscriptions_api_createAndList() {
        NotificationSubscription sub = NotificationSubscription.builder()
                .userId("mobile-user-1")
                .channel(Channel.PUSH)
                .audienceType(AudienceType.MOBILE_APP)
                .destination("fcm-token-abc")
                .build();

        ResponseEntity<NotificationSubscription> createResp = rest.postForEntity(
                baseUrl() + "/api/v1/subscriptions",
                jsonEntity(sub),
                NotificationSubscription.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResp.getBody()).isNotNull();
        assertThat(createResp.getBody().getUserId()).isEqualTo("mobile-user-1");

        ResponseEntity<List<NotificationSubscription>> listResp = rest.exchange(
                baseUrl() + "/api/v1/subscriptions?userId=mobile-user-1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(1);
    }

    @Test
    void mobileUserSubscribed_sendsEmailToInternal_returns202() {
        internalUserRepository.save(new InternalUser(null, "internal@test.com", "OP"));

        Map<String, String> body = Map.of("userId", "new-user-1", "email", "newuser@example.com");

        ResponseEntity<Void> resp = rest.postForEntity(
                baseUrl() + "/api/v1/events/mobile-user-subscribed?templateId=" + savedTemplate.getId(),
                jsonEntity(body),
                Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        List<?> events = eventRepository.findAll();
        assertThat(events).hasSize(1);
    }

    @Test
    void scheduledNotification_createAndProcessDue() {
        internalUserRepository.save(new InternalUser(null, "sched@test.com", "OP"));

        Map<String, Object> createBody = Map.of(
                "templateId", savedTemplate.getId().toString(),
                "channel", "EMAIL",
                "audienceType", "INTERNAL",
                "scheduledAt", Instant.now().minusSeconds(60).toString());

        ResponseEntity<Map> createResp = rest.postForEntity(
                baseUrl() + "/api/v1/notifications/scheduled",
                jsonEntity(createBody),
                Map.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResp.getBody().get("status")).isEqualTo("PENDING");

        Object idObj = createResp.getBody().get("id");
        UUID scheduledId = idObj instanceof String ? UUID.fromString((String) idObj) : UUID.fromString(idObj.toString());

        ResponseEntity<Void> processResp = rest.postForEntity(
                baseUrl() + "/api/v1/internal/process-due-scheduled",
                jsonEntity(null),
                Void.class);

        assertThat(processResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        assertThat(scheduledRepository.findById(scheduledId))
                .isPresent()
                .get()
                .extracting(s -> s.getStatus().name())
                .isEqualTo("SENT");
    }

    @Test
    void cancelScheduled_returns204() {
        var template = templateRepository.save(NotificationTemplate.builder()
                .channel(Channel.EMAIL).name("T").bodyTemplate("B").build());
        var scheduled = com.notification.entity.ScheduledNotification.builder()
                .template(template)
                .channel(Channel.EMAIL)
                .audienceType(AudienceType.INTERNAL)
                .scheduledAt(Instant.now().plusSeconds(3600))
                .status(com.notification.domain.NotificationStatus.PENDING)
                .build();
        scheduled = scheduledRepository.save(scheduled);

        ResponseEntity<Void> resp = rest.exchange(
                baseUrl() + "/api/v1/notifications/scheduled/" + scheduled.getId(),
                HttpMethod.DELETE,
                null,
                Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(scheduledRepository.findById(scheduled.getId()))
                .isPresent()
                .get()
                .extracting(s -> s.getStatus().name())
                .isEqualTo("CANCELLED");
    }
}
