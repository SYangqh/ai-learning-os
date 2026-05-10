package com.learningos;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke Test — 每次 Vibe Coding 结束后必须跑通。
 *
 * <p>目的：验证 Spring 上下文能正常启动，所有 Bean 注入无误，
 * 无编译错误，无循环依赖，数据库迁移脚本语法合法。</p>
 *
 * <p>使用 H2 内嵌数据库（test profile），Mock Redis，不依赖外部服务。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SmokeTest {

    /** Mock Redis，避免测试环境启动时尝试连接真实 Redis */
    @MockBean
    RedissonClient redissonClient;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // Spring 上下文能启动 = 所有 Bean 正常 = 基础配置无误
        // 方法体为空是故意的：只要不抛异常即为通过
    }

    @Test
    void criticalBeansPresent() {
        // 验证关键 Bean 都已注册，防止 @Service / @Component 被意外删除
        assertThat(applicationContext.containsBean("sessionService")).isTrue();
        assertThat(applicationContext.containsBean("authService")).isTrue();
        assertThat(applicationContext.containsBean("pathService")).isTrue();
        assertThat(applicationContext.containsBean("dynamicChatService")).isTrue();
    }
}
