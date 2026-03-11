package com.notification.channel;

/**
 * Push notification via PaaS (e.g. Firebase FCM, AWS SNS Mobile Push).
 */
public interface PushProvider {

    DeliveryResult send(String deviceToken, String title, String body);
}
