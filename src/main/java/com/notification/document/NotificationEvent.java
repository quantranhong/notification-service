package com.notification.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Append-only delivery log in MongoDB for audit and analytics.
 */
@Document(collection = "notification_events")
public class NotificationEvent {

    @Id
    private String id;

    @Indexed
    private String requestId;

    private String channel;
    private String recipient;
    private Map<String, Object> payload;
    private String status;
    private Instant sentAt;
    private String providerResponse;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public String getProviderResponse() { return providerResponse; }
    public void setProviderResponse(String providerResponse) { this.providerResponse = providerResponse; }
}
