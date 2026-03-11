package com.notification.entity;

import com.notification.domain.Channel;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "notification_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(nullable = false)
    private String name;

    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    @Column(name = "subject_template", columnDefinition = "TEXT")
    private String subjectTemplate; // for EMAIL

    @Column(name = "external_template_id")
    private String externalTemplateId; // PaaS template ID if any
}
