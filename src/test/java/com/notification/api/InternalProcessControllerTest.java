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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalProcessController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "notification.internal.api-key=")
class InternalProcessControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ScheduledNotificationService scheduledService;

    @Test
    void processDueScheduled_noApiKeyConfigured_acceptsAndCallsService() throws Exception {
        mockMvc.perform(post("/api/v1/internal/process-due-scheduled"))
                .andExpect(status().isAccepted());
        verify(scheduledService).processDueScheduled();
    }

    @Test
    void processDueScheduled_withValidApiKey_accepts() throws Exception {
        mockMvc.perform(post("/api/v1/internal/process-due-scheduled")
                        .header("X-Internal-Api-Key", "secret"))
                .andExpect(status().isAccepted());
        verify(scheduledService).processDueScheduled();
    }
}
