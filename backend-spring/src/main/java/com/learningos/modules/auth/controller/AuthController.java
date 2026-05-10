package com.learningos.modules.auth.controller;

import com.learningos.common.Result;
import com.learningos.infrastructure.mail.MailService;
import com.learningos.modules.auth.entity.AuthIdentity;
import com.learningos.modules.auth.repository.AuthIdentityRepository;
import com.learningos.modules.auth.repository.UserSessionRepository;
import com.learningos.modules.auth.service.AuthService;
import com.learningos.modules.auth.service.AuthService.*;
import com.learningos.modules.user.entity.User;
import com.learningos.modules.user.repository.UserRepository;
import com.learningos.modules.user.service.UserMergeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "身份认证")
public class AuthController {

    private final AuthService authService;
    private final MailService mailService;
    private final UserMergeService userMergeService;

    @PostMapping("/guest")
    @Operation(summary = "游客登录（无需注册，直接获取临时身份）")
    public Result<Map<String, Object>> guestLogin(@RequestBody(required = false) GuestRequest req,
                                                  HttpServletRequest httpReq) {
        UUID deviceId = (req != null && req.deviceId() != null) ? req.deviceId() : null;
        String ua = httpReq.getHeader("User-Agent");
        GuestAuthResult result = authService.guestLogin(deviceId, ua);
        return Result.ok(Map.of(
                "user_id", result.userId(),
                "device_id", result.deviceId(),
                "access_token", result.tokens().accessToken(),
                "refresh_token", result.tokens().refreshToken()
        ));
    }

    @PostMapping("/magic-link/request")
    @Operation(summary = "请求魔法链接（发邮件）")
    public Result<Map<String, String>> requestMagicLink(@Valid @RequestBody MagicLinkRequest req) {
        // 防止邮箱枚举：无论邮箱是否已注册都返回相同文案
        String rawToken = authService.createMagicLinkToken(req.email(), req.deviceId());
        // 异步发送邮件，不阻塞响应
        mailService.sendMagicLink(req.email(), rawToken);
        return Result.ok(Map.of("message", "如果该邮箱存在，您将收到一封登录邮件，有效期 10 分钟"));
    }

    @PostMapping("/magic-link/verify")
    @Operation(summary = "验证魔法链接（完成登录）")
    public Result<Map<String, Object>> verifyMagicLink(@Valid @RequestBody MagicLinkVerifyRequest req) {
        MagicLinkVerifyResult result = authService.verifyMagicLink(req.token(), req.deviceId());
        var body = new java.util.HashMap<String, Object>();
        body.put("user_id", result.userId());
        body.put("access_token", result.tokens().accessToken());
        body.put("refresh_token", result.tokens().refreshToken());
        if (result.guestUserIdToMerge() != null) {
            body.put("pending_merge_guest_id", result.guestUserIdToMerge());
        }
        return Result.ok(body);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 Access Token")
    public Result<Map<String, String>> refresh(@Valid @RequestBody RefreshRequest req) {
        TokenPair tokens = authService.refreshTokens(req.refreshToken());
        return Result.ok(Map.of(
                "access_token", tokens.accessToken(),
                "refresh_token", tokens.refreshToken()
        ));
    }

    @PostMapping("/merge-guest")
    @Operation(summary = "将游客账号数据合并到当前已登录账号（登录后调用）")
    @SecurityRequirement(name = "bearerAuth")
    public Result<Map<String, String>> mergeGuest(@Valid @RequestBody MergeGuestRequest req,
                                                  @AuthenticationPrincipal UUID userId) {
        userMergeService.merge(req.guestUserId(), userId);
        return Result.ok(Map.of("message", "合并成功"));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出（撤销当前 Session）")
    @SecurityRequirement(name = "bearerAuth")
    public Result<Map<String, String>> logout(@Valid @RequestBody LogoutRequest req) {
        authService.logout(req.sessionId());
        return Result.ok(Map.of("message", "已登出"));
    }

    // ─── Request Records ────────────────────────────────────────────────────────

    record GuestRequest(UUID deviceId) {}

    record MagicLinkRequest(
            @NotBlank @Email String email,
            UUID deviceId
    ) {}

    record MagicLinkVerifyRequest(
            @NotBlank String token,
            UUID deviceId
    ) {}

    record RefreshRequest(@NotBlank String refreshToken) {}

    record MergeGuestRequest(@NotNull UUID guestUserId) {}

    record LogoutRequest(@NotNull UUID sessionId) {}
}
