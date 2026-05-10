package com.learningos.modules.user.service;

import com.learningos.common.exception.AppException;
import com.learningos.modules.path.repository.LearningPathRepository;
import com.learningos.modules.session.repository.LearningSessionRepository;
import com.learningos.modules.user.entity.User;
import com.learningos.modules.user.repository.UserProfileRepository;
import com.learningos.modules.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 游客账号合并服务。
 *
 * <p>当游客完成魔法链接验证后，前端携带 {@code pending_merge_guest_id} 调用此接口，
 * 将游客产生的所有数据（画像、学习路径、会话）迁移到真实账号，随后软删除游客账号。</p>
 *
 * <p>并发安全：使用 PostgreSQL advisory lock（按 guestUserId 的 hashCode 加锁），
 * 确保同一游客账号不会被并发合并两次。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserMergeService {

    private final EntityManager em;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final LearningPathRepository learningPathRepository;
    private final LearningSessionRepository learningSessionRepository;

    /**
     * 将 guestUserId 的数据合并到 targetUserId。
     *
     * @param guestUserId  待合并的游客账号
     * @param targetUserId 已登录的真实账号（魔法链接验证成功后的用户）
     */
    @Transactional
    public void merge(UUID guestUserId, UUID targetUserId) {
        if (guestUserId.equals(targetUserId)) {
            return; // 相同账号，跳过
        }

        // ── Advisory Lock（事务级）────────────────────────────────────────────
        // 用 guestUserId 低 32 位作为锁 key，防止并发双写
        long lockKey = (long) guestUserId.hashCode() & 0xFFFFFFFFL;
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
          .setParameter("key", lockKey)
          .getSingleResult();

        // ── 校验游客账号 ──────────────────────────────────────────────────────
        User guest = userRepository.findById(guestUserId)
                .orElseThrow(() -> AppException.notFound("游客账号不存在"));

        if (!"guest".equals(guest.getKind())) {
            throw AppException.badRequest("指定账号不是游客账号，无法合并");
        }
        if (guest.getMergedIntoUserId() != null) {
            log.info("Guest {} already merged into {}, skip", guestUserId, guest.getMergedIntoUserId());
            return; // 已合并，幂等返回
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> AppException.notFound("目标账号不存在"));

        // ── 迁移 user_profiles ────────────────────────────────────────────────
        // 目标账号若没有画像，则将游客画像迁移过去；否则丢弃游客画像
        userProfileRepository.findByUserId(guestUserId).ifPresent(guestProfile -> {
            if (userProfileRepository.findByUserId(targetUserId).isEmpty()) {
                guestProfile.setUserId(targetUserId);
                userProfileRepository.save(guestProfile);
                log.debug("Migrated user_profile {} to user {}", guestProfile.getId(), targetUserId);
            } else {
                userProfileRepository.delete(guestProfile);
                log.debug("Discarded guest profile {} (target already has profile)", guestProfile.getId());
            }
        });

        // ── 迁移 learning_paths（批量 UPDATE）─────────────────────────────────
        int pathsMoved = em.createNativeQuery(
                "UPDATE learning_paths SET user_id = :targetId WHERE user_id = :guestId")
            .setParameter("targetId", targetUserId)
            .setParameter("guestId", guestUserId)
            .executeUpdate();
        log.debug("Migrated {} learning_paths to user {}", pathsMoved, targetUserId);

        // ── 迁移 learning_sessions（批量 UPDATE）──────────────────────────────
        int sessionsMoved = em.createNativeQuery(
                "UPDATE learning_sessions SET user_id = :targetId WHERE user_id = :guestId")
            .setParameter("targetId", targetUserId)
            .setParameter("guestId", guestUserId)
            .executeUpdate();
        log.debug("Migrated {} learning_sessions to user {}", sessionsMoved, targetUserId);

        // ── 软删除游客账号 ────────────────────────────────────────────────────
        guest.setMergedIntoUserId(targetUserId);
        guest.setStatus("disabled");
        userRepository.save(guest);

        log.info("Merged guest {} into user {}: {} paths, {} sessions",
                guestUserId, targetUserId, pathsMoved, sessionsMoved);
    }
}
