package com.notification.repository;

import com.notification.entity.InternalUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InternalUserRepository extends JpaRepository<InternalUser, UUID> {

    InternalUser findByEmail(String email);
}
