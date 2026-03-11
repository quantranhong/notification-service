package com.notification.api.dto;

import com.notification.domain.AudienceType;
import com.notification.domain.Channel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class SendNotificationRequest {

    @NotNull
    private Channel channel;
    @NotNull
    private AudienceType audienceType;
    @NotNull
    private UUID templateId;
    private Map<String, String> context;
    private String idempotencyKey;
}
