package com.notification.service;

import com.notification.channel.DeliveryResult;
import com.notification.channel.EmailProvider;
import com.notification.channel.PushProvider;
import com.notification.channel.SmsProvider;
import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import com.notification.document.NotificationEvent;
import com.notification.entity.InternalUser;
import com.notification.entity.NotificationSubscription;
import com.notification.entity.NotificationTemplate;
import com.notification.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEngineTest {

    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private NotificationSubscriptionRepository subscriptionRepository;
    @Mock private InternalUserRepository internalUserRepository;
    @Mock private NotificationEventRepository eventRepository;
    @Mock private EmailProvider emailProvider;
    @Mock private SmsProvider smsProvider;
    @Mock private PushProvider pushProvider;
    @Mock private IdempotencyService idempotencyService;

    private NotificationEngine engine;

    private NotificationTemplate emailTemplate;
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new NotificationEngine(
                templateRepository, subscriptionRepository, internalUserRepository,
                eventRepository, emailProvider, smsProvider, pushProvider, idempotencyService);
        emailTemplate = NotificationTemplate.builder()
                .id(TEMPLATE_ID)
                .channel(Channel.EMAIL)
                .name("Test")
                .bodyTemplate("Hello {{name}}")
                .subjectTemplate("Subject {{name}}")
                .build();
    }

    @Test
    void resolveRecipients_internal_returnsInternalUserEmails() {
        when(internalUserRepository.findAll()).thenReturn(List.of(
                new InternalUser(UUID.randomUUID(), "a@test.com", "OP"),
                new InternalUser(UUID.randomUUID(), "b@test.com", "SUPPORT")
        ));
        List<String> recipients = engine.resolveRecipients(Channel.EMAIL, AudienceType.INTERNAL, null);
        assertThat(recipients).containsExactly("a@test.com", "b@test.com");
    }

    @Test
    void resolveRecipients_mobileApp_returnsSubscriptionDestinations() {
        when(subscriptionRepository.findByAudienceTypeAndChannel(AudienceType.MOBILE_APP, Channel.PUSH))
                .thenReturn(List.of(
                        NotificationSubscription.builder().destination("token1").build(),
                        NotificationSubscription.builder().destination("token2").build()
                ));
        List<String> recipients = engine.resolveRecipients(Channel.PUSH, AudienceType.MOBILE_APP, null);
        assertThat(recipients).containsExactly("token1", "token2");
    }

    @Test
    void renderBody_replacesPlaceholders() {
        String body = engine.renderBody(emailTemplate, Map.of("name", "Alice"));
        assertThat(body).isEqualTo("Hello Alice");
    }

    @Test
    void renderBody_nullContext_returnsTemplateAsIs() {
        String body = engine.renderBody(emailTemplate, null);
        assertThat(body).isEqualTo("Hello {{name}}");
    }

    @Test
    void renderSubject_replacesPlaceholders() {
        String sub = engine.renderSubject(emailTemplate, Map.of("name", "Bob"));
        assertThat(sub).isEqualTo("Subject Bob");
    }

    @Test
    void sendOne_email_callsEmailProviderAndSavesEvent() {
        when(emailProvider.send(eq("u@test.com"), any(), any()))
                .thenReturn(DeliveryResult.builder().success(true).messageId("msg-1").build());

        DeliveryResult result = engine.sendOne(emailTemplate, "u@test.com", Map.of("name", "X"), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessageId()).isEqualTo("msg-1");
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getChannel()).isEqualTo("EMAIL");
        assertThat(eventCaptor.getValue().getRecipient()).isEqualTo("u@test.com");
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("SENT");
    }

    @Test
    void sendOne_idempotencyHit_returnsCachedResult() {
        DeliveryResult cached = DeliveryResult.builder().success(true).messageId("cached").build();
        when(idempotencyService.getCachedResult("key-1")).thenReturn(Optional.of(cached));

        DeliveryResult result = engine.sendOne(emailTemplate, "u@test.com", Map.of(), "key-1");

        assertThat(result).isSameAs(cached);
        verify(emailProvider, never()).send(any(), any(), any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void sendToAudience_resolvesRecipientsAndSends() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(emailTemplate));
        when(internalUserRepository.findAll()).thenReturn(List.of(
                new InternalUser(UUID.randomUUID(), "one@test.com", "OP")
        ));
        when(emailProvider.send(any(), any(), any()))
                .thenReturn(DeliveryResult.builder().success(true).messageId("m1").build());
        when(idempotencyService.getCachedResult(any())).thenReturn(Optional.empty());

        List<DeliveryResult> results = engine.sendToAudience(
                TEMPLATE_ID, Channel.EMAIL, AudienceType.INTERNAL, null,
                Map.of("name", "User"), "prefix");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isTrue();
        verify(emailProvider).send(eq("one@test.com"), eq("Subject User"), eq("Hello User"));
    }

    @Test
    void sendToAudience_templateNotFound_throws() {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.sendToAudience(
                TEMPLATE_ID, Channel.EMAIL, AudienceType.INTERNAL, null, null, null))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
