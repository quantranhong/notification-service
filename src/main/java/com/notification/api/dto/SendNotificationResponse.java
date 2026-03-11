package com.notification.api.dto;

import com.notification.channel.DeliveryResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SendNotificationResponse {
    private boolean success;
    private int totalRecipients;
    private int successCount;
    private List<DeliveryResult> results;
}
