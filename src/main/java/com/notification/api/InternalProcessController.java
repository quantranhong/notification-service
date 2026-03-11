package com.notification.api;

import com.notification.service.ScheduledNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for the separate Scheduler Service to trigger processing of due scheduled notifications.
 * Secured by optional API key (X-Internal-Api-Key). When key is configured, request must supply it.
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalProcessController {

    private final ScheduledNotificationService scheduledService;

    @Value("${notification.internal.api-key:}")
    private String internalApiKey;

    private static final String HEADER_API_KEY = "X-Internal-Api-Key";

    @PostMapping("/process-due-scheduled")
    public ResponseEntity<Void> processDueScheduled(HttpServletRequest request) {
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            String provided = request.getHeader(HEADER_API_KEY);
            if (!internalApiKey.equals(provided)) {
                return ResponseEntity.status(401).build();
            }
        }
        scheduledService.processDueScheduled();
        return ResponseEntity.accepted().build();
    }
}
