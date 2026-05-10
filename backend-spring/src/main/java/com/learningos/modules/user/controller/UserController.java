package com.learningos.modules.user.controller;

import com.learningos.common.Result;
import com.learningos.modules.user.entity.UserProfile;
import com.learningos.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "User", description = "用户画像管理")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @PostMapping("/profile")
    @Operation(summary = "保存用户画像")
    public Result<Map<String, Object>> saveProfile(@Valid @RequestBody ProfileRequest req,
                                                   @AuthenticationPrincipal UUID userId) {
        UserProfile profile = userService.saveProfile(
                userId, req.background(), req.skills(),
                req.target(), req.learningStyle(), req.dailyTime(), req.analogyBasis()
        );
        return Result.ok(Map.of("profile_id", profile.getId()));
    }

    @GetMapping("/profile")
    @Operation(summary = "获取当前用户画像")
    public Result<UserProfile> getMyProfile(@AuthenticationPrincipal UUID userId) {
        return Result.ok(userService.getProfile(userId));
    }

    // ─── Request Records ─────────────────────────────────────────────────────

    record ProfileRequest(
            @NotBlank String background,
            List<String> skills,
            @NotBlank String target,
            String learningStyle,
            int dailyTime,
            String analogyBasis    // 可选：用于 AI 类比的领域背景
    ) {}
}
