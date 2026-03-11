package com.notification.channel;

/**
 * SMS delivery via PaaS (e.g. AWS SNS, Azure Communication Services).
 */
public interface SmsProvider {

    DeliveryResult send(String toPhoneNumber, String message);
}
