package com.learningos.modules.user.controller;

import com.learningos.common.Result;
import com.learningos.common.exception.AppException;
import com.learningos.modules.auth.entity.AuthIdentity;
import com.learningos.modules.auth.repository.AuthIdentityRepository;
import com.learningos.modules.user.entity.User;
import com.learningos.modules.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Me", description = "当前用户信息")
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户信息")
    public Result<Map<String, Object>> me(@AuthenticationPrincipal UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("用户不存在"));

        List<AuthIdentity> identities = authIdentityRepository.findByUserId(userId);
        List<String> emails = identities.stream()
                .filter(i -> "email".equals(i.getType()))
                .map(AuthIdentity::getEmail)
                .toList();

        return Result.ok(Map.of(
            "user_id", user.getId(),
            "kind", user.getKind(),
            "status", user.getStatus(),
            "emails", emails,
            "created_at", user.getCreatedAt()
        ));
    }
}
