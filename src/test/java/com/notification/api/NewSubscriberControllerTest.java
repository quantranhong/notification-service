package com.notification.api;

import com.notification.entity.NotificationTemplate;
import com.notification.repository.NotificationTemplateRepository;
import com.notification.service.NotificationEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewSubscriberController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class NewSubscriberControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private NotificationEngine notificationEngine;
    @MockBean private NotificationTemplateRepository templateRepository;

    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Test
    void mobileUserSubscribed_templateExists_returns202AndCallsEngine() throws Exception {
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(
                Optional.of(NotificationTemplate.builder().id(TEMPLATE_ID).channel(com.notification.domain.Channel.EMAIL).name("T").bodyTemplate("B").build()));

        mockMvc.perform(post("/api/v1/events/mobile-user-subscribed")
                        .param("templateId", TEMPLATE_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user-1\",\"email\":\"u@example.com\"}"))
                .andExpect(status().isAccepted());

        verify(notificationEngine).sendToAudience(
                eq(TEMPLATE_ID),
                eq(com.notification.domain.Channel.EMAIL),
                eq(com.notification.domain.AudienceType.INTERNAL),
                eq(null),
                any(),
                any());
    }

    @Test
    void mobileUserSubscribed_templateNotFound_returns400() throws Exception {
        when(templateRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/events/mobile-user-subscribed")
                        .param("templateId", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user-1\",\"email\":\"u@example.com\"}"))
                .andExpect(status().isBadRequest());
    }
}
