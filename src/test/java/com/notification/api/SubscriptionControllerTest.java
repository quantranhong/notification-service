package com.notification.api;

import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import com.notification.entity.NotificationSubscription;
import com.notification.repository.NotificationSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class SubscriptionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private NotificationSubscriptionRepository subscriptionRepository;

    @Test
    void create_returns200AndSubscription() throws Exception {
        NotificationSubscription sub = NotificationSubscription.builder()
                .userId("u1")
                .channel(Channel.EMAIL)
                .audienceType(AudienceType.MOBILE_APP)
                .destination("u@test.com")
                .build();
        sub.setId(UUID.randomUUID());
        when(subscriptionRepository.save(any(NotificationSubscription.class))).thenReturn(sub);

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.destination").value("u@test.com"));
    }

    @Test
    void list_noParams_returnsAll() throws Exception {
        when(subscriptionRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void list_byUserId_returnsFiltered() throws Exception {
        NotificationSubscription sub = NotificationSubscription.builder()
                .userId("u1").channel(Channel.PUSH).audienceType(AudienceType.MOBILE_APP).destination("token1").build();
        when(subscriptionRepository.findByUserId("u1")).thenReturn(List.of(sub));

        mockMvc.perform(get("/api/v1/subscriptions").param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("u1"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/subscriptions/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
