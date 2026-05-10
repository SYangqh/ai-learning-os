package com.learningos.modules.auth.repository;

import com.learningos.modules.auth.entity.AuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {
    Optional<AuthIdentity> findByTypeAndEmail(String type, String email);
    List<AuthIdentity> findByUserId(UUID userId);
}
