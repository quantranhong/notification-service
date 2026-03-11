package com.notification.channel;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Push via Firebase Cloud Messaging (PaaS). Initialize FirebaseApp with credentials (e.g. from env/file).
 */
@Component
@ConditionalOnProperty(name = "notification.channels.push.provider", havingValue = "firebase-fcm")
public class FirebasePushProvider implements PushProvider {

    @Override
    public DeliveryResult send(String deviceToken, String title, String body) {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                return DeliveryResult.builder()
                        .success(false)
                        .errorMessage("Firebase not initialized; set GOOGLE_APPLICATION_CREDENTIALS or Firebase options")
                        .build();
            }
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();
            String messageId = FirebaseMessaging.getInstance().send(message);
            return DeliveryResult.builder()
                    .success(true)
                    .messageId(messageId)
                    .build();
        } catch (Exception e) {
            return DeliveryResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}
