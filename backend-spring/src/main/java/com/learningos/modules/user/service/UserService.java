package com.learningos.modules.user.service;

import com.learningos.common.exception.AppException;
import com.learningos.modules.user.entity.User;
import com.learningos.modules.user.entity.UserProfile;
import com.learningos.modules.user.repository.UserProfileRepository;
import com.learningos.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public User createGuestUser() {
        User user = new User();
        user.setKind("guest");
        return userRepository.save(user);
    }

    @Transactional
    public User upgradeToUser(UUID userId) {
        User user = getUser(userId);
        user.setKind("user");
        return userRepository.save(user);
    }

    @Transactional
    public UserProfile saveProfile(UUID userId, String background, List<String> skills,
                                   String target, String learningStyle, int dailyTime,
                                   String analogyBasis) {
        getUser(userId); // 校验用户存在

        // 若已存在 profile 则更新
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElse(new UserProfile());
        profile.setUserId(userId);
        profile.setBackground(background);
        profile.setSkills(skills);
        profile.setTarget(target);
        profile.setLearningStyle(learningStyle);
        profile.setDailyTime(dailyTime);
        profile.setAnalogyBasis(analogyBasis);
        return userProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("用户不存在: " + userId));
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> AppException.notFound("用户画像不存在"));
    }
}
