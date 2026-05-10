package com.learningos.modules.llm.repository;

import com.learningos.modules.llm.entity.UserLlmPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserLlmPreferenceRepository extends JpaRepository<UserLlmPreference, UUID> {}
