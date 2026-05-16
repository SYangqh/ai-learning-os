package com.learningos.infrastructure.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

/**
 * 用户级限流拦截器（基于 Redis INCR + EXPIRE 滑动窗口）。
 * 每用户每分钟最多 maxRequests 次请求，超出返回 429。
 */
@Component
@Slf4j
public class UserRateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "rl:user:";

    private final StringRedisTemplate redis;
    private final int maxRequests;
    private final boolean enabled;

    @Autowired
    public UserRateLimitInterceptor(StringRedisTemplate redis,
                                    @Value("${app.rate-limit.max-requests-per-minute:60}") int maxRequests,
                                    @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.redis       = redis;
        this.maxRequests = maxRequests;
        this.enabled     = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (!enabled) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return true;
        }

        String userId = auth.getPrincipal().toString();
        String key = KEY_PREFIX + userId;
        Long count = redis.opsForValue().increment(key);
        if (count == null) return true;

        if (count == 1) {
            redis.expire(key, Duration.ofMinutes(1));
        }

        if (count > maxRequests) {
            log.warn("Rate limit exceeded for user={} count={}/{}", userId, count, maxRequests);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"\u8bf7\u6c42\u8fc7\u4e8e\u9891\u7e41\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\"}");
            return false;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(maxRequests - count));
        return true;
    }
}