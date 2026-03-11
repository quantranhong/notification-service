package com.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Internal users (operators, customer services) who receive notifications
 * e.g. when a new mobile user subscribes.
 */
@Entity
@Table(name = "internal_users", indexes = {
    @Index(name = "idx_internal_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String roles; // e.g. "OPERATOR,CUSTOMER_SERVICE"
}
