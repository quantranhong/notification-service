package com.notification.api;

import com.notification.domain.Channel;
import com.notification.entity.NotificationTemplate;
import com.notification.repository.NotificationTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TemplateController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class TemplateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private NotificationTemplateRepository templateRepository;

    @Test
    void create_returns200AndTemplate() throws Exception {
        NotificationTemplate template = NotificationTemplate.builder()
                .channel(Channel.EMAIL)
                .name("Welcome")
                .bodyTemplate("Hello {{name}}")
                .subjectTemplate("Welcome")
                .build();
        template.setId(UUID.randomUUID());
        when(templateRepository.save(any(NotificationTemplate.class))).thenReturn(template);

        mockMvc.perform(post("/api/v1/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(template)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Welcome"))
                .andExpect(jsonPath("$.channel").value("EMAIL"));
    }

    @Test
    void list_returnsAll() throws Exception {
        when(templateRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void get_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        NotificationTemplate template = NotificationTemplate.builder()
                .id(id).channel(Channel.SMS).name("SMS").bodyTemplate("Hi").build();
        when(templateRepository.findById(id)).thenReturn(Optional.of(template));

        mockMvc.perform(get("/api/v1/templates/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("SMS"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(templateRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/templates/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
