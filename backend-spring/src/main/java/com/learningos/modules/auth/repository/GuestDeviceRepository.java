package com.learningos.modules.auth.repository;

import com.learningos.modules.auth.entity.GuestDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GuestDeviceRepository extends JpaRepository<GuestDevice, UUID> {
    Optional<GuestDevice> findByDeviceId(UUID deviceId);
}
