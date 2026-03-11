package com.notification.api.dto;

import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateScheduledRequest {

    @NotNull
    private UUID templateId;
    @NotNull
    private Channel channel;
    @NotNull
    private AudienceType audienceType;
    private String audienceFilter;
    @NotNull
    private Instant scheduledAt;
    private String timezone;
    private Map<String, String> context;
}
