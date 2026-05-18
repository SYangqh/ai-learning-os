# Phase 11: 用户反馈与日志采集系统 — 实施检查清单

> **状态**: 📋 待实施  
> **设计文档**: [USER_FEEDBACK_AND_LOGGING_DESIGN.md](USER_FEEDBACK_AND_LOGGING_DESIGN.md)  
> **预计工期**: 9 个工作日（管理后台延后则 6 天）  
> **实施优先级**: Phase 10 完成后、正式上线前

---

## 前置准备（必须先完成）

- [ ] 确认 Phase 9C/10A/10B/10C 已全部完成
- [ ] 阅读 `docs/USER_FEEDBACK_AND_LOGGING_DESIGN.md`
- [ ] 确认技术选型：IndexedDB / OSS SDK / 分区表策略
- [ ] 确认管理后台是否同步实施（可延后）
- [ ] 在 ARCHITECTURE.md 中补充 Phase 11 章节

---

## 阶段一：前端日志采集（2 天）

### Day 1: FrontendLogger 服务核心实现
- [ ] 创建 `frontend/src/lib/logger.ts`
  - [ ] LogEntry 类型定义
  - [ ] FrontendLogger 类（Singleton 模式）
  - [ ] IndexedDB 初始化（数据库名 `ai-learning-logs`，表 `event_logs`）
  - [ ] logNavigation() - 页面导航记录
  - [ ] logClick() - 按钮点击记录
  - [ ] logApiCall() - API 调用记录（含 trace_id）
  - [ ] logError() - 错误捕获
  - [ ] exportLogs(timeWindow) - 导出日志（默认 30 分钟）
  - [ ] cleanup() - 清理旧日志（超过 30 分钟或超过 500 条）

### Day 2: 集成与脱敏
- [ ] 前端全局错误监听
  - [ ] `app/layout.tsx` 集成 Error Boundary
  - [ ] window.addEventListener('error') 全局错误监听
  - [ ] window.addEventListener('unhandledrejection') Promise 错误监听
- [ ] API 拦截器改造
  - [ ] `lib/api.ts` apiFetch 增加日志记录
  - [ ] 记录请求方法、URL、状态码、耗时、trace_id
- [ ] 敏感信息脱敏实现
  - [ ] sanitizeString() - 正则替换 API Key/Token/邮箱
  - [ ] sanitizeObject() - 递归处理 JSON 对象
  - [ ] 单元测试（vitest 或 jest）
- [ ] 自动清理定时器
  - [ ] setInterval 每 10 分钟调用 cleanup()
  - [ ] 页面卸载时清理定时器

---

## 阶段二：反馈入口 UI（1 天）

### Day 3: 反馈组件实现
- [ ] 创建 `frontend/src/components/FeedbackButton.tsx`
  - [ ] 悬浮按钮（右下角，z-index 9999）
  - [ ] 三主题适配（暗黑/正常/轻松）
  - [ ] 图标：💬 或 SVG 聊天气泡
  - [ ] 点击打开 FeedbackDialog
- [ ] 创建 `frontend/src/components/FeedbackDialog.tsx`
  - [ ] 表单字段：反馈类型（单选）、问题描述（文本域）、截图上传（可选）
  - [ ] 表单验证：描述 10-2000 字符、截图 < 2MB
  - [ ] "附带日志"复选框（默认勾选）
  - [ ] 提交按钮（loading 状态）
  - [ ] 成功提示 toast
- [ ] 截图上传功能
  - [ ] OSS SDK 集成（阿里云 OSS / AWS S3）
  - [ ] 上传进度显示
  - [ ] 错误处理（文件过大、网络失败）
- [ ] 集成到主页面
  - [ ] `app/learn/page.tsx` 引入 FeedbackButton
  - [ ] 移动端响应式布局（按钮位置自适应）

---

## 阶段三：后端接口（2 天）

### Day 4: 数据库与 Entity
- [ ] 创建 Flyway 迁移脚本
  - [ ] `V8__user_feedback_system.sql`
  - [ ] feedback_type ENUM
  - [ ] feedback_status ENUM
  - [ ] user_feedbacks 表（14 个字段）
  - [ ] frontend_event_logs 表（可选，6 个字段）
  - [ ] 索引创建（user_id, status, created_at, type）
- [ ] 创建 Entity 类
  - [ ] `UserFeedback.java` (JPA Entity)
  - [ ] `FrontendEventLog.java` (可选)
  - [ ] 枚举类：FeedbackType、FeedbackStatus
- [ ] 创建 Repository
  - [ ] `FeedbackRepository.java` (extends JpaRepository)
  - [ ] 自定义查询方法（findByStatus, findByUserId, etc.）

### Day 5: Service 与 Controller
- [ ] 创建 DTO
  - [ ] `SubmitFeedbackRequest.java`
  - [ ] `FeedbackListResponse.java`
  - [ ] `FrontendLogEntry.java` (内部类)
- [ ] 创建 LogSanitizerService
  - [ ] sanitizeFrontendLog() - 前端日志脱敏
  - [ ] sanitizeString() - 正则脱敏（API Key/Token/邮箱/手机号）
  - [ ] sanitizeMap() - 递归处理嵌套对象
  - [ ] 单元测试（JUnit 5）
- [ ] 创建 FeedbackService
  - [ ] submitFeedback() - 保存反馈
  - [ ] extractTraceIds() - 从日志中提取 trace_id
  - [ ] updateStatus() - 更新反馈状态
  - [ ] getFeedbackList() - 分页查询
  - [ ] getFeedbackDetail() - 查询详情
- [ ] 创建 FeedbackController
  - [ ] POST /api/feedback/submit
  - [ ] GET /api/feedback/list (管理员)
  - [ ] PUT /api/feedback/{id}/status (管理员)
  - [ ] GET /api/feedback/{id} (管理员)
  - [ ] 权限控制（@PreAuthorize）

---

## 阶段四：集成测试（1 天）

### Day 6: 端到端测试
- [ ] 前端测试
  - [ ] FrontendLogger 采集测试（导航/点击/API/错误）
  - [ ] 脱敏功能测试（API Key/Token/邮箱）
  - [ ] IndexedDB 存储与清理测试
  - [ ] FeedbackDialog 表单提交测试
- [ ] 后端测试
  - [ ] 单元测试（LogSanitizerService, FeedbackService）
  - [ ] 集成测试（Controller → Service → Repository）
  - [ ] 权限测试（普通用户 vs 管理员）
- [ ] 端到端测试
  - [ ] 用户提交反馈 → 后端保存 → 数据库验证
  - [ ] trace_id 提取与关联测试
  - [ ] 截图上传与访问测试
- [ ] 性能测试
  - [ ] FrontendLogger 采集性能（< 10ms）
  - [ ] 并发提交测试（100 并发）
  - [ ] IndexedDB 查询性能（500 条记录）

---

## 阶段五：管理后台（3 天，可延后）

### Day 7: 反馈列表页面
- [ ] 创建 `/admin/feedback` 路由
- [ ] 反馈列表组件
  - [ ] 分页（Page 组件）
  - [ ] 筛选（按状态、类型、日期范围）
  - [ ] 搜索（描述关键词）
  - [ ] 表格展示（ID、用户、类型、描述摘要、状态、创建时间）
  - [ ] 点击行跳转到详情页
- [ ] 权限控制
  - [ ] 路由守卫（仅管理员可访问）
  - [ ] 后端接口权限验证

### Day 8: 反馈详情页面
- [ ] 创建 `/admin/feedback/[id]` 动态路由
- [ ] 详情展示
  - [ ] 用户信息（邮箱、ID、注册时间）
  - [ ] 反馈内容（类型、描述、截图）
  - [ ] 上下文信息（页面 URL、浏览器、视口尺寸）
  - [ ] 前端日志（折叠面板，JSON 格式化展示）
  - [ ] 后端 trace_id（点击跳转到 audit_log 查询）
- [ ] 状态管理
  - [ ] 状态下拉选择（NEW → IN_PROGRESS → RESOLVED → CLOSED）
  - [ ] 管理员备注输入框
  - [ ] 保存按钮（调用 PUT /api/feedback/{id}/status）

### Day 9: 导出与优化
- [ ] 导出功能
  - [ ] 导出当前筛选结果为 CSV
  - [ ] 包含所有字段（用户、类型、描述、状态、时间）
- [ ] UI 优化
  - [ ] 加载状态（Skeleton）
  - [ ] 空状态提示
  - [ ] 错误边界
  - [ ] 三主题适配
- [ ] 性能优化
  - [ ] 虚拟滚动（react-window，日志量大时）
  - [ ] 懒加载截图（Intersection Observer）

---

## 阶段六：文档与上线（1 天）

### Day 10: 文档更新与发布
- [ ] 更新 ARCHITECTURE.md
  - [ ] 补充 Phase 11 章节（架构图、数据流、安全设计）
- [ ] 更新 TESTING.md
  - [ ] 补充 Phase 11 验收标准
  - [ ] 前端测试用例清单
  - [ ] 后端测试用例清单
- [ ] 更新 README.md
  - [ ] Phase 11 状态标记为 ✅
  - [ ] 补充交付说明
- [ ] 更新 docs/vibe-issues/2026-05-XX.md
  - [ ] 记录实施过程中的问题与决策
  - [ ] 根因分析与改进建议
- [ ] 运行验收脚本
  - [ ] `verify.bat` / `verify.sh` 全部通过
  - [ ] 端到端测试通过
- [ ] 上线发布
  - [ ] 合并到主分支
  - [ ] 标记 Git Tag: `v1.11.0`
  - [ ] 部署到生产环境
  - [ ] 监控日志与错误（Sentry / CloudWatch）

---

## 后续优化（Phase 12 候选）

- [ ] 智能问题分类（使用 LLM 自动分类反馈类型）
- [ ] 自动回复（常见问题自动生成回复建议）
- [ ] 问题趋势分析（按周/月统计高频问题）
- [ ] 用户行为回放（集成 rrweb，录制用户操作视频）
- [ ] 实时通知（新反馈提交时 WebSocket 通知管理员）
- [ ] GDPR 合规（用户数据导出、删除请求）

---

**检查清单版本**: v1.0  
**最后更新**: 2026-05-18  
**下次更新**: 实施时逐项打勾并补充实际耗时
