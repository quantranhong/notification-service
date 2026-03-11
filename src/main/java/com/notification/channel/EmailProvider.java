package com.notification.channel;

/**
 * Email delivery via PaaS (e.g. AWS SES, Azure Communication Services).
 */
public interface EmailProvider {

    DeliveryResult send(String to, String subject, String body);
}
