package com.notification.api;

import com.notification.service.ScheduledNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalProcessController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "notification.internal.api-key=required-secret")
class InternalProcessControllerApiKeyTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ScheduledNotificationService scheduledService;

    @Test
    void processDueScheduled_missingApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/internal/process-due-scheduled"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(scheduledService);
    }

    @Test
    void processDueScheduled_wrongApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/internal/process-due-scheduled")
                        .header("X-Internal-Api-Key", "wrong"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(scheduledService);
    }

    @Test
    void processDueScheduled_correctApiKey_accepts() throws Exception {
        mockMvc.perform(post("/api/v1/internal/process-due-scheduled")
                        .header("X-Internal-Api-Key", "required-secret"))
                .andExpect(status().isAccepted());
        verify(scheduledService).processDueScheduled();
    }
}
