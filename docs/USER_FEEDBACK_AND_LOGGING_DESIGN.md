# 用户反馈与日志采集系统 — 设计文档

> **状态**: 📋 设计阶段（未实施）  
> **版本**: v1.0  
> **创建日期**: 2026-05-18  
> **预计实施**: Phase 11（Phase 10 完成后、正式上线前）

---

## 一、功能目标

为已上线的 AI Learning OS 提供用户反馈通道和可观测性支持，实现：

1. **用户自助反馈**：在遇到问题时直接提交反馈，无需离开系统
2. **上下文日志附带**：反馈自动携带用户最近操作日志和错误堆栈，方便复现问题
3. **敏感信息保护**：自动脱敏 API Key、token、密码等敏感字段
4. **开发者友好**：提供管理后台查看反馈、关联日志、标记处理状态
5. **性能优化**：前端日志采集不影响用户体验，后端日志存储策略合理

---

## 二、系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                         前端 (Next.js)                       │
├─────────────────────────────────────────────────────────────┤
│  FeedbackButton (全局悬浮按钮)                               │
│  FeedbackDialog (反馈表单: 描述 + 截图 + 类型)               │
│  FrontendLogger (日志采集服务)                               │
│    - 操作轨迹 (Navigation, Click, Input)                     │
│    - 错误捕获 (Error Boundary, unhandledrejection)           │
│    - API 调用记录 (成功/失败/耗时)                            │
│    - 本地缓存 (IndexedDB, 滚动窗口 30 分钟)                  │
└─────────────────────────────────────────────────────────────┘
                              │ POST /api/feedback/submit
                              │ (包含: 描述、截图 URL、类型、日志快照)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       后端 (Spring Boot)                      │
├─────────────────────────────────────────────────────────────┤
│  FeedbackController                                          │
│    - POST /api/feedback/submit (提交反馈)                    │
│    - GET /api/feedback/list (管理后台查询)                   │
│    - PUT /api/feedback/{id}/status (标记处理状态)            │
│                                                              │
│  FeedbackService                                             │
│    - saveFeedback() (持久化到 DB)                            │
│    - linkBackendLogs() (关联后端 trace_id)                   │
│    - sanitizeLogs() (敏感信息脱敏)                           │
│                                                              │
│  LogSanitizer (脱敏服务)                                      │
│    - maskApiKey() (API Key → sk-***xyz)                      │
│    - maskToken() (JWT → eyJ***xyz)                           │
│    - maskEmail() (user@example.com → u***@e***.com)          │
│    - maskSensitiveFields() (递归处理 JSON)                   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    PostgreSQL 数据库                          │
├─────────────────────────────────────────────────────────────┤
│  user_feedbacks 表                                            │
│    - id (UUID, PK)                                           │
│    - user_id (UUID, FK → users)                              │
│    - type (ENUM: 'BUG', 'FEATURE', 'OTHER')                  │
│    - description (TEXT, 用户描述)                             │
│    - screenshot_url (TEXT, 可选)                              │
│    - frontend_logs (JSONB, 前端日志快照)                      │
│    - backend_trace_ids (TEXT[], 关联后端 trace_id)           │
│    - status (ENUM: 'NEW', 'IN_PROGRESS', 'RESOLVED', ...)    │
│    - created_at (TIMESTAMPTZ)                                │
│    - resolved_at (TIMESTAMPTZ, 可空)                          │
│                                                              │
│  frontend_event_logs 表 (可选，用于存储原始前端日志)          │
│    - id (BIGSERIAL, PK)                                      │
│    - user_id (UUID)                                          │
│    - session_id (UUID, 可空)                                 │
│    - event_type (VARCHAR: 'navigation', 'click', ...)        │
│    - event_data (JSONB)                                      │
│    - timestamp (TIMESTAMPTZ)                                 │
│    - created_at (TIMESTAMPTZ)                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 三、数据库设计

### 3.1 Flyway 迁移脚本：V8__user_feedback_system.sql

```sql
-- ============================
-- Phase 11: 用户反馈与日志采集系统
-- ============================

-- 反馈类型枚举
CREATE TYPE feedback_type AS ENUM ('BUG', 'FEATURE', 'SUGGESTION', 'OTHER');

-- 反馈状态枚举
CREATE TYPE feedback_status AS ENUM ('NEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'WONT_FIX');

-- 用户反馈表
CREATE TABLE user_feedbacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- 反馈内容
    type feedback_type NOT NULL DEFAULT 'OTHER',
    description TEXT NOT NULL CHECK (char_length(description) >= 10 AND char_length(description) <= 2000),
    screenshot_url TEXT,  -- 截图 URL (可选，存储到 OSS/S3 后的链接)
    
    -- 上下文信息
    page_url TEXT,  -- 发生问题的页面 URL
    user_agent TEXT,  -- 浏览器信息
    viewport_size VARCHAR(50),  -- 视口尺寸 (如 "1920x1080")
    
    -- 日志快照
    frontend_logs JSONB,  -- 前端日志 JSON 数组（已脱敏）
    backend_trace_ids TEXT[],  -- 关联的后端 trace_id 列表
    
    -- 处理状态
    status feedback_status NOT NULL DEFAULT 'NEW',
    admin_notes TEXT,  -- 管理员备注
    resolved_at TIMESTAMPTZ,  -- 解决时间
    
    -- 时间戳
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 索引优化
CREATE INDEX idx_user_feedbacks_user_id ON user_feedbacks(user_id);
CREATE INDEX idx_user_feedbacks_status ON user_feedbacks(status);
CREATE INDEX idx_user_feedbacks_created_at ON user_feedbacks(created_at DESC);
CREATE INDEX idx_user_feedbacks_type ON user_feedbacks(type);

-- 前端事件日志表（可选，用于存储原始前端日志，供分析和调试）
CREATE TABLE frontend_event_logs (
    id BIGSERIAL PRIMARY KEY,
    
    -- 关联信息
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,  -- 允许匿名用户
    session_id UUID,  -- 学习会话 ID（可选）
    
    -- 事件信息
    event_type VARCHAR(50) NOT NULL,  -- 'navigation', 'click', 'api_call', 'error', etc.
    event_data JSONB NOT NULL,  -- 事件详细数据（已脱敏）
    
    -- 时间戳
    timestamp TIMESTAMPTZ NOT NULL,  -- 事件发生时间
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()  -- 入库时间
);

-- 前端日志索引
CREATE INDEX idx_frontend_event_logs_user_id ON frontend_event_logs(user_id);
CREATE INDEX idx_frontend_event_logs_session_id ON frontend_event_logs(session_id);
CREATE INDEX idx_frontend_event_logs_timestamp ON frontend_event_logs(timestamp DESC);
CREATE INDEX idx_frontend_event_logs_event_type ON frontend_event_logs(event_type);

-- 分区策略（可选，日志量大时启用）
-- 按月分区，自动归档超过 3 个月的日志
-- CREATE TABLE frontend_event_logs_2026_05 PARTITION OF frontend_event_logs 
--     FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

COMMENT ON TABLE user_feedbacks IS 'Phase 11: 用户反馈记录表';
COMMENT ON TABLE frontend_event_logs IS 'Phase 11: 前端事件日志表（可选，用于调试和分析）';
```

---

## 四、前端设计

### 4.1 前端日志采集服务 (FrontendLogger)

**文件位置**: `frontend/src/lib/logger.ts`

**职责**：
- 自动采集用户操作轨迹（页面导航、按钮点击、输入事件）
- 捕获前端错误（Error Boundary、全局错误监听）
- 记录 API 调用（成功/失败/耗时/trace_id）
- 本地缓存日志（IndexedDB，滚动窗口 30 分钟，最多 500 条）
- 提供日志导出接口（供反馈功能调用）

**核心 API**：

```typescript
// logger.ts
export class FrontendLogger {
  // 初始化（App 启动时调用）
  static init(): void
  
  // 记录操作
  static logNavigation(from: string, to: string): void
  static logClick(elementId: string, elementText: string): void
  static logApiCall(method: string, url: string, status: number, duration: number, traceId?: string): void
  static logError(error: Error, context?: Record<string, any>): void
  
  // 导出日志（用于反馈提交）
  static exportLogs(timeWindow: number = 30): Promise<LogEntry[]>  // 默认最近 30 分钟
  
  // 清理旧日志
  static cleanup(): void
}

// 日志条目类型
export interface LogEntry {
  id: string
  timestamp: number  // Unix timestamp (ms)
  type: 'navigation' | 'click' | 'api_call' | 'error'
  data: Record<string, any>  // 已脱敏
}
```

**敏感信息脱敏规则（前端）**：
- API Key: `sk-1234567890abcdef` → `sk-***cdef`
- JWT Token: `eyJhbGc...` → `eyJ***xyz`
- 密码字段: 完全移除
- 邮箱: `user@example.com` → `u***@e***.com`

---

### 4.2 反馈入口组件 (FeedbackButton)

**文件位置**: `frontend/src/components/FeedbackButton.tsx`

**UI 设计**：
- 全局悬浮按钮（右下角，类似客服按钮）
- 主题化样式（适配暗黑/正常/轻松三主题）
- 图标：💬 或聊天气泡 SVG
- 点击后弹出 FeedbackDialog

**位置**：
```tsx
// app/layout.tsx 或 learn/page.tsx
<FeedbackButton />  // 全局或单页面引入
```

---

### 4.3 反馈表单弹窗 (FeedbackDialog)

**文件位置**: `frontend/src/components/FeedbackDialog.tsx`

**表单字段**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 反馈类型 | 单选 | ✅ | Bug / 功能建议 / 其他 |
| 问题描述 | 文本域 | ✅ | 10-2000 字符 |
| 截图上传 | 文件 | ❌ | 支持 PNG/JPG，最大 2MB |
| 附带日志 | 复选框 | ❌ | 默认勾选，附带最近 30 分钟日志 |

**提交流程**：
1. 表单验证（描述长度、截图大小）
2. 截图上传（可选）→ OSS/S3 → 返回 URL
3. 导出前端日志 → 脱敏
4. 调用 `POST /api/feedback/submit`
5. 显示成功提示 + 自动关闭弹窗

**代码示例**：

```tsx
async function handleSubmit() {
  // 1. 上传截图（可选）
  let screenshotUrl = null
  if (screenshot) {
    screenshotUrl = await uploadScreenshot(screenshot)  // 上传到 OSS
  }
  
  // 2. 导出日志
  const logs = includeLogsChecked 
    ? await FrontendLogger.exportLogs(30)  // 最近 30 分钟
    : null
  
  // 3. 提交反馈
  await apiFetch('/feedback/submit', {
    method: 'POST',
    body: JSON.stringify({
      type: feedbackType,  // 'BUG' | 'FEATURE' | 'OTHER'
      description,
      screenshotUrl,
      pageUrl: window.location.href,
      userAgent: navigator.userAgent,
      viewportSize: `${window.innerWidth}x${window.innerHeight}`,
      frontendLogs: logs,
    }),
  })
  
  // 4. 成功提示
  toast.success('反馈已提交，感谢你的反馈！')
  onClose()
}
```

---

## 五、后端设计

### 5.1 FeedbackController

**文件位置**: `backend-spring/src/main/java/com/learningos/modules/feedback/controller/FeedbackController.java`

**接口清单**：

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 提交反馈 | POST | `/api/feedback/submit` | 用户提交反馈 |
| 查询反馈列表 | GET | `/api/feedback/list` | 管理员查询（分页、筛选） |
| 更新反馈状态 | PUT | `/api/feedback/{id}/status` | 管理员标记处理状态 |
| 查询反馈详情 | GET | `/api/feedback/{id}` | 查看单个反馈 + 关联日志 |

**请求 DTO**：

```java
// SubmitFeedbackRequest.java
public record SubmitFeedbackRequest(
    @NotNull FeedbackType type,
    
    @NotBlank @Size(min = 10, max = 2000) String description,
    
    String screenshotUrl,
    
    @NotBlank String pageUrl,
    @NotBlank String userAgent,
    String viewportSize,
    
    List<FrontendLogEntry> frontendLogs  // 前端日志快照
) {}

// FrontendLogEntry.java (内部类)
public record FrontendLogEntry(
    String id,
    Long timestamp,
    String type,  // 'navigation' | 'click' | 'api_call' | 'error'
    Map<String, Object> data
) {}
```

---

### 5.2 FeedbackService

**文件位置**: `backend-spring/src/main/java/com/learningos/modules/feedback/service/FeedbackService.java`

**核心方法**：

```java
@Service
@Slf4j
public class FeedbackService {
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private LogSanitizerService logSanitizer;
    @Autowired private AuditLogRepository auditLogRepository;  // Phase 8 已存在
    
    /**
     * 提交反馈
     */
    @Transactional
    public UUID submitFeedback(UUID userId, SubmitFeedbackRequest req) {
        // 1. 脱敏前端日志
        List<FrontendLogEntry> sanitizedLogs = req.frontendLogs().stream()
            .map(logSanitizer::sanitizeFrontendLog)
            .toList();
        
        // 2. 提取后端 trace_id（从前端日志中的 API 调用记录）
        List<String> traceIds = extractTraceIds(req.frontendLogs());
        
        // 3. 保存反馈
        UserFeedback feedback = UserFeedback.builder()
            .userId(userId)
            .type(req.type())
            .description(req.description())
            .screenshotUrl(req.screenshotUrl())
            .pageUrl(req.pageUrl())
            .userAgent(req.userAgent())
            .viewportSize(req.viewportSize())
            .frontendLogs(sanitizedLogs)  // 存储为 JSONB
            .backendTraceIds(traceIds)
            .status(FeedbackStatus.NEW)
            .build();
        
        feedback = feedbackRepository.save(feedback);
        
        // 4. 审计日志
        auditLogRepository.log(userId, "FEEDBACK_SUBMITTED", 
            Map.of("feedbackId", feedback.getId(), "type", req.type()));
        
        log.info("User {} submitted feedback: id={}, type={}", userId, feedback.getId(), req.type());
        return feedback.getId();
    }
    
    /**
     * 从前端日志中提取 trace_id
     */
    private List<String> extractTraceIds(List<FrontendLogEntry> logs) {
        if (logs == null) return List.of();
        return logs.stream()
            .filter(log -> "api_call".equals(log.type()))
            .map(log -> (String) log.data().get("traceId"))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }
}
```

---

### 5.3 LogSanitizerService（敏感信息脱敏）

**文件位置**: `backend-spring/src/main/java/com/learningos/modules/feedback/service/LogSanitizerService.java`

**脱敏规则**：

| 字段类型 | 原始值 | 脱敏后 |
|---------|--------|--------|
| API Key | `sk-1234567890abcdef` | `sk-***cdef` |
| JWT Token | `eyJhbGciOiJ...xyz` | `eyJ***xyz` |
| 邮箱 | `user@example.com` | `u***@e***.com` |
| 密码字段 | 任意值 | `<removed>` |
| 手机号 | `13812345678` | `138****5678` |

**实现示例**：

```java
@Service
public class LogSanitizerService {
    private static final Pattern API_KEY_PATTERN = Pattern.compile("sk-[a-zA-Z0-9]{20,}");
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+");
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "apiKey", "api_key", "token", "accessToken", "refreshToken"
    );
    
    public FrontendLogEntry sanitizeFrontendLog(FrontendLogEntry log) {
        Map<String, Object> sanitizedData = sanitizeMap(log.data());
        return new FrontendLogEntry(log.id(), log.timestamp(), log.type(), sanitizedData);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 移除敏感字段
            if (SENSITIVE_FIELDS.contains(key.toLowerCase())) {
                result.put(key, "<removed>");
                continue;
            }
            
            // 递归处理嵌套对象
            if (value instanceof Map) {
                result.put(key, sanitizeMap((Map<String, Object>) value));
            } else if (value instanceof String) {
                result.put(key, sanitizeString((String) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }
    
    private String sanitizeString(String value) {
        if (value == null) return null;
        
        // API Key 脱敏
        value = API_KEY_PATTERN.matcher(value).replaceAll(m -> {
            String key = m.group();
            if (key.length() < 10) return "sk-***";
            return "sk-***" + key.substring(key.length() - 4);
        });
        
        // JWT 脱敏
        value = JWT_PATTERN.matcher(value).replaceAll(m -> {
            String token = m.group();
            if (token.length() < 20) return "eyJ***";
            return "eyJ***" + token.substring(token.length() - 3);
        });
        
        // 邮箱脱敏
        if (value.contains("@") && value.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            String[] parts = value.split("@");
            String localPart = parts[0];
            String domainPart = parts[1];
            if (localPart.length() > 2) {
                localPart = localPart.charAt(0) + "***";
            }
            if (domainPart.length() > 4) {
                domainPart = domainPart.charAt(0) + "***." + domainPart.substring(domainPart.lastIndexOf('.') + 1);
            }
            return localPart + "@" + domainPart;
        }
        
        return value;
    }
}
```

---

## 六、管理后台（后续 Phase）

**路径**: `/admin/feedback` （管理员专用）

**功能清单**：
- 反馈列表（分页、按状态/类型筛选、搜索）
- 查看反馈详情（描述、截图、前端日志、关联后端 trace_id）
- 标记处理状态（NEW → IN_PROGRESS → RESOLVED → CLOSED）
- 添加管理员备注
- 导出反馈数据（CSV/Excel）

**权限控制**：
- 需要 `ROLE_ADMIN` 角色（Spring Security）
- 普通用户只能查看自己的反馈记录

---

## 七、性能与存储策略

### 7.1 前端日志存储策略

| 策略 | 说明 |
|------|------|
| **本地缓存** | IndexedDB，最多 500 条，滚动窗口 30 分钟 |
| **自动清理** | 每 10 分钟清理超过 30 分钟的旧日志 |
| **上传时机** | 仅在用户主动提交反馈时上传 |
| **批量上传** | 不实时上传，避免影响用户体验 |

### 7.2 后端日志存储策略

| 策略 | 说明 |
|------|------|
| **分区表** | `frontend_event_logs` 按月分区（可选） |
| **归档策略** | 超过 3 个月的日志归档到冷存储（如 S3 Glacier） |
| **索引优化** | user_id、timestamp、event_type 建立索引 |
| **定期清理** | 每月清理超过 6 个月的日志（CRON 任务） |

### 7.3 截图存储

| 策略 | 说明 |
|------|------|
| **存储位置** | 阿里云 OSS / AWS S3 / 本地文件系统（开发环境） |
| **文件命名** | `feedback/{userId}/{feedbackId}/{timestamp}.png` |
| **访问控制** | 仅管理员可访问（Presigned URL） |
| **过期策略** | 90 天后自动删除（对象生命周期规则） |

---

## 八、测试验收标准

### 8.1 前端测试

| 测试项 | 验收标准 |
|--------|---------|
| 日志采集 | 页面导航、按钮点击、API 调用、错误事件正确记录 |
| 脱敏功能 | API Key、JWT Token、邮箱等敏感信息正确脱敏 |
| 本地缓存 | IndexedDB 正常存储，超过 500 条或 30 分钟自动清理 |
| 反馈提交 | 表单验证、截图上传、日志导出、提交成功 |
| UI 适配 | 三主题正常显示，移动端响应式布局正确 |

### 8.2 后端测试

| 测试项 | 验收标准 |
|--------|---------|
| 反馈保存 | user_feedbacks 表正确保存所有字段 |
| 日志脱敏 | LogSanitizerService 正确脱敏所有敏感字段 |
| trace_id 提取 | 正确从前端日志中提取后端 trace_id |
| 管理接口 | 列表查询、状态更新、详情查看正常 |
| 权限控制 | 普通用户只能查看自己的反馈，管理员可查看全部 |

### 8.3 集成测试

| 测试项 | 验收标准 |
|--------|---------|
| 端到端流程 | 用户提交反馈 → 后端保存 → 管理员查看 → 标记已解决 |
| 日志关联 | 反馈中的 trace_id 能正确关联到后端 audit_log 表 |
| 截图上传 | 截图正确上传到 OSS，URL 可访问 |
| 性能测试 | 前端日志采集不影响页面性能（< 10ms） |
| 并发测试 | 100 并发提交反馈无数据丢失或重复 |

---

## 九、实施步骤

### 阶段一：前端日志采集（2 天）
- [ ] 实现 FrontendLogger 服务（logger.ts）
- [ ] 集成到 App（全局错误监听、API 拦截器）
- [ ] IndexedDB 缓存与清理逻辑
- [ ] 脱敏功能实现与单元测试

### 阶段二：反馈入口 UI（1 天）
- [ ] FeedbackButton 组件（悬浮按钮）
- [ ] FeedbackDialog 组件（表单弹窗）
- [ ] 截图上传功能（OSS SDK 集成）
- [ ] 三主题适配与移动端适配

### 阶段三：后端接口（2 天）
- [ ] Flyway V8 迁移脚本
- [ ] FeedbackController + FeedbackService
- [ ] LogSanitizerService（脱敏服务）
- [ ] FeedbackRepository（JPA）
- [ ] 单元测试与集成测试

### 阶段四：管理后台（3 天，可延后）
- [ ] 反馈列表页面（分页、筛选）
- [ ] 反馈详情页面（日志展示、trace_id 跳转）
- [ ] 状态更新接口与 UI
- [ ] 权限控制（ROLE_ADMIN）
- [ ] 导出功能（CSV）

### 阶段五：测试与上线（1 天）
- [ ] 端到端测试
- [ ] 性能测试
- [ ] 文档更新（TESTING.md、README.md）
- [ ] 上线发布

**总计**: 约 9 个工作日（管理后台延后则 6 天）

---

## 十、未来扩展方向

1. **智能问题分类**：使用 LLM 自动分类反馈类型（Bug / Feature / Question）
2. **自动回复**：常见问题自动生成回复建议
3. **问题趋势分析**：按周/月统计高频问题，生成分析报告
4. **用户行为回放**：集成 rrweb，记录用户操作视频（可选）
5. **实时通知**：新反馈提交时通知管理员（WebSocket / 邮件）

---

## 十一、参考资料

- [Sentry 文档](https://docs.sentry.io/) - 错误监控最佳实践
- [LogRocket](https://logrocket.com/) - 用户会话回放
- [rrweb](https://www.rrweb.io/) - 开源会话录制库
- [GDPR 合规指南](https://gdpr.eu/) - 日志采集隐私合规
- [OWASP 数据脱敏指南](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)

---

**文档版本**: v1.0  
**作者**: GitHub Copilot  
**审核状态**: 待审核  
**下次更新**: 实施时根据实际情况调整
