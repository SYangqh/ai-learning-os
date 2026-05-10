package com.learningos.modules.user.service;

import com.learningos.modules.user.entity.UserMastery;
import com.learningos.modules.user.entity.UserMasteryId;
import com.learningos.modules.user.repository.UserMasteryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 用户掌握度服务：追踪每个概念/技能的掌握程度，用于自适应教学难度调整。
 *
 * <h3>分数规则</h3>
 * <ul>
 *   <li>初始值：50（无历史记录时）</li>
 *   <li>通过评审（passed=true）：+15，上限 100</li>
 *   <li>评审未通过（passed=false）：-5，下限 0</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasteryService {

    private static final int INITIAL_SCORE  = 50;
    private static final int PASS_DELTA     = 15;
    private static final int FAIL_DELTA     = -5;

    private final UserMasteryRepository masteryRepository;

    /**
     * 记录阶段学习结果，更新掌握度分数。
     * conceptKey 通常使用 {@code stage.skillId}。
     */
    @Transactional
    public void recordStageResult(UUID userId, String conceptKey, boolean passed) {
        if (conceptKey == null || conceptKey.isBlank()) return;

        UserMasteryId pk = new UserMasteryId(userId, conceptKey);
        UserMastery mastery = masteryRepository.findById(pk).orElseGet(() -> {
            UserMastery m = new UserMastery();
            m.setId(pk);
            m.setMasteryScore(INITIAL_SCORE);
            return m;
        });

        int delta = passed ? PASS_DELTA : FAIL_DELTA;
        mastery.setMasteryScore(Math.clamp(mastery.getMasteryScore() + delta, 0, 100));
        mastery.setLastTestedAt(OffsetDateTime.now());
        masteryRepository.save(mastery);

        log.info("Mastery updated: user={} concept={} score={} ({})",
                userId, conceptKey, mastery.getMasteryScore(), passed ? "+" + PASS_DELTA : FAIL_DELTA);
    }

    /**
     * 返回适合注入 system prompt 的难度提示文字。
     * 若无历史记录，返回空字符串（不影响原有提示词）。
     */
    public String getDifficultyHint(UUID userId, String conceptKey) {
        if (conceptKey == null || conceptKey.isBlank()) return "";

        return masteryRepository.findById(new UserMasteryId(userId, conceptKey))
                .map(m -> {
                    int score = m.getMasteryScore();
                    if (score >= 70) {
                        return "【自适应难度】学员对该领域有较好基础（掌握度 " + score + "/100），" +
                               "可直接使用进阶示例，减少基础铺垫，鼓励独立思考。";
                    } else if (score <= 30) {
                        return "【自适应难度】学员对该领域较陌生（掌握度 " + score + "/100），" +
                               "请多用类比和循序渐进的方式，每个新概念都充分解释。";
                    } else {
                        return "【自适应难度】学员对该领域有基本认知（掌握度 " + score + "/100），" +
                               "保持正常教学节奏。";
                    }
                })
                .orElse("");
    }

    /**
     * 获取用户全部掌握度记录（用于前端展示学习画像）。
     */
    @Transactional(readOnly = true)
    public List<UserMastery> getMasteryList(UUID userId) {
        return masteryRepository.findByIdUserId(userId);
    }
}
