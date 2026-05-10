package com.learningos.modules.user.repository;

import com.learningos.modules.user.entity.UserMastery;
import com.learningos.modules.user.entity.UserMasteryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserMasteryRepository extends JpaRepository<UserMastery, UserMasteryId> {

    List<UserMastery> findByIdUserId(UUID userId);
}
