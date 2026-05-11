package com.learningos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.modules.llm.service.DynamicChatService;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API 接口冒烟测试 — 覆盖核心 HTTP 接口的 "不报错" 验收。
 *
 * <p>目标：每条接口返回正确的 HTTP 状态码，响应体符合 {@code Result<T>} 结构。
 * 不验证业务正确性，只保证接口可访问、无 5xx、序列化无异常。</p>
 *
 * <p>使用 H2 内嵌数据库 + MockMvc，无需启动真实 HTTP 端口，也不依赖 Redis / SMTP / LLM。</p>
 *
 * <p>测试顺序：{@code @TestMethodOrder} 保证游客登录最先执行，后续用其返回的 token。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiTest {

    @MockBean
    RedissonClient redissonClient;

    @MockBean
    DynamicChatService chatService;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    /** 游客登录后共享给后续测试 */
    static String accessToken;

    /** generatePath 返回的第一个阶段 ID，供 startStage 测试使用 */
    static String firstStageId;

    /** startStage 返回的 session ID，供 artifact 测试使用 */
    static String testSessionId;

    // ─────────────────────────────────────────────────────────────────────────
    // 公开接口（无需认证）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/auth/guest → 200 + 返回 access_token")
    void guestLogin_returns200WithTokens() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isString())
                .andExpect(jsonPath("$.data.refresh_token").isString())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        accessToken = data.get("access_token").asText();
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/auth/magic-link/request → 200（防枚举，始终返回成功）")
    void magicLinkRequest_alwaysReturns200() throws Exception {
        mockMvc.perform(post("/api/auth/magic-link/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "email": "test@example.com", "deviceId": null }
                            """))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 需要认证的接口（使用 Order(1) 获取的 token）
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("GET /api/llm/providers → 200 + 返回 provider 列表")
    void getLlmProviders_returns200() throws Exception {
        mockMvc.perform(get("/api/llm/providers")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/llm/credentials → 200（空列表也正常）")
    void getLlmCredentials_returns200() throws Exception {
        mockMvc.perform(get("/api/llm/credentials")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(20)
    @DisplayName("POST /api/profile → 200（保存用户画像）")
    void saveProfile_returns200() throws Exception {
        mockMvc.perform(post("/api/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "background": "前端工程师",
                              "skills": ["JavaScript", "React"],
                              "target": "后端开发 (Node.js)",
                              "dailyTime": 60
                            }
                            """))
                .andExpect(status().isOk());
    }

    @Test
    @Order(21)
    @DisplayName("GET /api/me → 200（返回当前用户信息）")
    void getMe_returns200() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @Order(30)
    @DisplayName("GET /api/path → 200（无路径时返回空或正常结构）")
    void getPath_returns200WhenNoPath() throws Exception {
        mockMvc.perform(get("/api/path")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(31)
    @DisplayName("POST /api/path/generate → 200（mock LLM，生成路径并返回阶段列表）")
    void generatePath_returns200() throws Exception {
        // mock LLM 返回合法的路径规划 JSON
        Mockito.when(chatService.chat(any(), any(), any()))
               .thenReturn("""
                   {"title":"Node.js后端开发","stages":[
                     {"title":"Node.js基础","goal":"掌握 Node.js 核心模块","skill_id":"nodejs_basics"},
                     {"title":"Express框架","goal":"能独立构建 REST API","skill_id":"express_basics"}
                   ]}
                   """);

        MvcResult result = mockMvc.perform(post("/api/path/generate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path_id").isString())
                .andExpect(jsonPath("$.data.stages").isArray())
                .andReturn();

        // 捕获第一个阶段 ID，供 startStage 测试使用
        JsonNode stages = objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/stages");
        firstStageId = stages.get(0).get("id").asText();
        assertThat(firstStageId).isNotBlank();
    }

    @Test
    @Order(32)
    @DisplayName("POST /api/stage/{id}/start → 200（mock LLM，返回开场白消息）")
    void startStage_returns200() throws Exception {
        assertThat(firstStageId).as("需要先运行 generatePath 测试").isNotBlank();

        // mock LLM 返回开场白文本（第二次调用）
        Mockito.when(chatService.chat(any(), any(), any()))
               .thenReturn("欢迎来到 Node.js 基础阶段！我们先从模块系统开始……");

        MvcResult result = mockMvc.perform(post("/api/stage/" + firstStageId + "/start")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session_id").isString())
                .andExpect(jsonPath("$.data.messages").isArray())
                .andReturn();

        // 捕获 sessionId，供 artifact 测试使用
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        testSessionId = data.get("session_id").asText();
        assertThat(testSessionId).isNotBlank();
    }

    @Test
    @Order(33)
    @DisplayName("POST /api/artifact → 200（提交 CODE 产出并持久化）")
    void submitArtifact_returns200() throws Exception {
        assertThat(testSessionId).as("需要先运行 startStage 测试").isNotBlank();

        MvcResult result = mockMvc.perform(post("/api/artifact")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "sessionId": "%s",
                              "type": "CODE",
                              "content": "console.log('hello world');"
                            }
                            """.formatted(testSessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.type").value("CODE"))
                .andExpect(jsonPath("$.data.status").value("submitted"))
                .andReturn();

        // 验证 artifact 被持久化
        assertThat(result.getResponse().getContentAsString()).contains("submitted");
    }

    @Test
    @Order(34)
    @DisplayName("GET /api/session/{sessionId}/artifacts → 200（能查到刚提交的产出）")
    void listArtifacts_returns200() throws Exception {
        assertThat(testSessionId).as("需要先运行 startStage 测试").isNotBlank();

        mockMvc.perform(get("/api/session/" + testSessionId + "/artifacts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].type").value("CODE"))
                .andExpect(jsonPath("$.data[0].content").value("console.log('hello world');"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 安全边界测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(90)
    @DisplayName("无 token 访问受保护接口 → 401")
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/path"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(91)
    @DisplayName("非法 token 访问受保护接口 → 401")
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/path")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }
}
