package com.learningos.modules.auth.service;

import com.learningos.common.exception.AppException;
import com.learningos.modules.auth.entity.*;
import com.learningos.modules.auth.repository.*;
import com.learningos.modules.user.entity.User;
import com.learningos.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final GuestDeviceRepository guestDeviceRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final MagicLinkTokenRepository magicLinkTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final JwtService jwtService;

    @Value("${app.magic-link.token-expiry-minutes:10}")
    private int magicLinkExpiryMinutes;

    @Value("${app.jwt.refresh-token-expiry-days:30}")
    private int refreshTokenExpiryDays;

    // ─── 游客登录 ──────────────────────────────────────────────────────────────

    @Transactional
    public GuestAuthResult guestLogin(UUID deviceId, String userAgent) {
        if (deviceId != null) {
            GuestDevice device = guestDeviceRepository.findByDeviceId(deviceId).orElse(null);
            if (device != null) {
                User user = userRepository.findById(device.getUserId()).orElse(null);
                if (user != null && "guest".equals(user.getKind()) && "active".equals(user.getStatus())) {
                    device.setLastSeenAt(OffsetDateTime.now());
                    guestDeviceRepository.save(device);
                    TokenPair tokens = issueTokens(user.getId(), device.getDeviceId());
                    return new GuestAuthResult(user.getId(), deviceId, tokens);
                }
            }
        }

        // 新游客
        User user = new User();
        user.setKind("guest");
        userRepository.save(user);

        UUID newDeviceId = (deviceId != null) ? deviceId : UUID.randomUUID();
        GuestDevice device = new GuestDevice();
        device.setDeviceId(newDeviceId);
        device.setUserId(user.getId());
        device.setLastSeenAt(OffsetDateTime.now());
        device.setUserAgent(userAgent);
        guestDeviceRepository.save(device);

        TokenPair tokens = issueTokens(user.getId(), newDeviceId);
        return new GuestAuthResult(user.getId(), newDeviceId, tokens);
    }

    // ─── 魔法链接：生成 token（返回明文，调用方发邮件）────────────────────────────

    @Transactional
    public String createMagicLinkToken(String email, UUID deviceId) {
        // 生成高熵 token
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        MagicLinkToken token = new MagicLinkToken();
        token.setEmail(email.toLowerCase().trim());
        token.setTokenHash(sha256Hex(rawToken));
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(magicLinkExpiryMinutes));
        token.setDeviceId(deviceId);
        magicLinkTokenRepository.save(token);

        return rawToken;    // 调用方组装邮件 URL
    }

    // ─── 魔法链接：验证并登录 ──────────────────────────────────────────────────

    @Transactional
    public MagicLinkVerifyResult verifyMagicLink(String rawToken, UUID deviceId) {
        String hash = sha256Hex(rawToken);
        MagicLinkToken token = magicLinkTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> AppException.badRequest("无效的登录链接"));

        if (token.isExpired())   throw AppException.badRequest("登录链接已过期，请重新申请");
        if (token.isConsumed())  throw AppException.badRequest("登录链接已使用，请重新申请");

        // 消费 token
        token.setConsumedAt(OffsetDateTime.now());
        magicLinkTokenRepository.save(token);

        // 找或创建真实用户
        String normalEmail = token.getEmail();
        AuthIdentity identity = authIdentityRepository
                .findByTypeAndEmail("email", normalEmail)
                .orElse(null);

        User user;
        if (identity != null) {
            user = userRepository.findById(identity.getUserId())
                    .orElseThrow(() -> AppException.internal("用户数据异常"));
        } else {
            user = new User();
            user.setKind("user");
            userRepository.save(user);

            identity = new AuthIdentity();
            identity.setUserId(user.getId());
            identity.setType("email");
            identity.setEmail(normalEmail);
            identity.setEmailVerifiedAt(OffsetDateTime.now());
            authIdentityRepository.save(identity);
        }

        // 游客合并（在调用方执行，这里只返回 guestUserId 供外部决策）
        UUID guestUserIdToMerge = null;
        UUID effectiveDeviceId = deviceId != null ? deviceId : token.getDeviceId();
        if (effectiveDeviceId != null) {
            GuestDevice gd = guestDeviceRepository.findByDeviceId(effectiveDeviceId).orElse(null);
            if (gd != null) {
                User guestUser = userRepository.findById(gd.getUserId()).orElse(null);
                if (guestUser != null && "guest".equals(guestUser.getKind())
                        && !guestUser.getId().equals(user.getId())) {
                    guestUserIdToMerge = guestUser.getId();
                }
            }
        }

        TokenPair tokens = issueTokens(user.getId(), effectiveDeviceId);
        return new MagicLinkVerifyResult(user.getId(), tokens, guestUserIdToMerge);
    }

    // ─── Refresh Token 轮换 ────────────────────────────────────────────────────

    @Transactional
    public TokenPair refreshTokens(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);
        UserSession session = userSessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> AppException.badRequest("无效的刷新令牌"));

        if (!session.isValid()) throw AppException.badRequest("刷新令牌已失效，请重新登录");

        // 撤销旧 session
        session.setRevokedAt(OffsetDateTime.now());
        userSessionRepository.save(session);

        // 发新 token
        return issueTokens(session.getUserId(), session.getDeviceId());
    }

    // ─── 登出 ──────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(UUID sessionId) {
        userSessionRepository.findById(sessionId).ifPresent(s -> {
            s.setRevokedAt(OffsetDateTime.now());
            userSessionRepository.save(s);
        });
    }

    // ─── 内部：颁发 Access+Refresh Token ────────────────────────────────────────

    private TokenPair issueTokens(UUID userId, UUID deviceId) {
        // Access Token (JWT)
        String accessToken = jwtService.generateAccessToken(userId);

        // Refresh Token（高熵随机，只存 hash）
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setDeviceId(deviceId);
        session.setRefreshTokenHash(sha256Hex(rawRefresh));
        session.setExpiresAt(OffsetDateTime.now().plusDays(refreshTokenExpiryDays));
        session.setLastSeenAt(OffsetDateTime.now());
        userSessionRepository.save(session);

        return new TokenPair(accessToken, rawRefresh);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ─── Result Records ────────────────────────────────────────────────────────

    public record TokenPair(String accessToken, String refreshToken) {}
    public record GuestAuthResult(UUID userId, UUID deviceId, TokenPair tokens) {}
    public record MagicLinkVerifyResult(UUID userId, TokenPair tokens, UUID guestUserIdToMerge) {}
}
