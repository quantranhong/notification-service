package com.notification.api;

import com.notification.channel.DeliveryResult;
import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import com.notification.domain.NotificationStatus;
import com.notification.entity.NotificationTemplate;
import com.notification.entity.ScheduledNotification;
import com.notification.repository.NotificationTemplateRepository;
import com.notification.repository.ScheduledNotificationRepository;
import com.notification.service.NotificationEngine;
import com.notification.service.ScheduledNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationRestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private NotificationEngine notificationEngine;
    @MockBean private ScheduledNotificationService scheduledService;
    @MockBean private NotificationTemplateRepository templateRepository;
    @MockBean private ScheduledNotificationRepository scheduledRepository;

    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Test
    void send_notification_returns200AndResults() throws Exception {
        NotificationTemplate template = NotificationTemplate.builder()
                .id(TEMPLATE_ID).channel(Channel.EMAIL).name("T").bodyTemplate("Hi").build();
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(notificationEngine.sendToAudience(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(DeliveryResult.builder().success(true).messageId("m1").build()));

        String body = """
            {"channel":"EMAIL","audienceType":"INTERNAL","templateId":"%s","context":{}}
            """.formatted(TEMPLATE_ID);

        mockMvc.perform(post("/api/v1/notifications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.totalRecipients").value(1))
                .andExpect(jsonPath("$.successCount").value(1));
    }

    @Test
    void send_templateNotFound_returns404() throws Exception {
        when(templateRepository.findById(any())).thenReturn(Optional.empty());
        String body = """
            {"channel":"EMAIL","audienceType":"INTERNAL","templateId":"%s"}
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/notifications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createScheduled_returns200AndScheduled() throws Exception {
        NotificationTemplate template = NotificationTemplate.builder()
                .id(TEMPLATE_ID).channel(Channel.PUSH).name("T").bodyTemplate("B").build();
        ScheduledNotification saved = ScheduledNotification.builder()
                .id(UUID.randomUUID())
                .template(template)
                .channel(Channel.PUSH)
                .audienceType(AudienceType.MOBILE_APP)
                .scheduledAt(Instant.now())
                .status(NotificationStatus.PENDING)
                .build();
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(scheduledService.create(any())).thenReturn(saved);

        String body = """
            {"templateId":"%s","channel":"PUSH","audienceType":"MOBILE_APP","scheduledAt":"2025-12-01T10:00:00Z"}
            """.formatted(TEMPLATE_ID);

        mockMvc.perform(post("/api/v1/notifications/scheduled")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void listScheduled_returns200AndList() throws Exception {
        when(scheduledRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications/scheduled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void cancelScheduled_foundAndPending_returns204() throws Exception {
        when(scheduledService.cancel(any(UUID.class))).thenReturn(true);

        mockMvc.perform(delete("/api/v1/notifications/scheduled/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelScheduled_notFound_returns404() throws Exception {
        when(scheduledService.cancel(any(UUID.class))).thenReturn(false);

        mockMvc.perform(delete("/api/v1/notifications/scheduled/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
