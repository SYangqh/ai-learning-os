# Phase 9A 音效文件创建指南

## 📋 概览

本指南帮助你为 AI Learning OS 的主题化反馈系统创建 20 个音效文件。这些音效文件是**可选的**——系统已实现优雅降级，即使没有音频文件也不会影响核心功能，只是会展示纯文本反馈。

---

## 🎯 目标

为 6 个主题 × 4 个事件类型创建共 20 个音效文件：

| 主题 | 音效数量 | 风格要求 |
|------|---------|----------|
| **Cute（可爱）** | 4 | 轻快、俏皮、明亮（钢琴、铃铛、八音盒） |
| **Dark（暗黑）** | 4 | 低沉、克制、神秘（低频鼓点、钟声、管风琴） |
| **Corporate（正式）** | 2 | 简洁、专业（系统提示音） |
| **Cyber（未来）** | 4 | 科技、电子（数字 beep、扫描音、合成器） |
| **Botanical（自然）** | 4 | 舒缓、有机（风铃、流水、鸟鸣、木琴） |
| **Accessible（无障碍）** | 0 | 无音效（强制静音，仅依赖视觉反馈） |

每个主题需要覆盖的 4 个事件：
1. **answer_good** — 回答正确/理解到位
2. **review_pass** — 作品评审通过
3. **stage_complete** — 阶段完成
4. **streak_achieved** — 连续学习达成

---

## 📐 文件规格要求

| 参数 | 要求 | 说明 |
|------|------|------|
| **格式** | MP3 | 浏览器兼容性最好 |
| **时长** | 0.3-2s | 太短听不清，太长打断学习流程 |
| **文件大小** | <50KB | 确保快速加载 |
| **采样率** | 44.1kHz 或 48kHz | 标准音质 |
| **比特率** | 128kbps | 平衡音质与文件大小 |
| **响度** | -3dB 至 -6dB | 归一化处理，避免音量不一致 |
| **声道** | 单声道或立体声 | 单声道文件更小 |

---

## 🎵 文件清单与推荐描述

### 1. Cute 主题（可爱风）

| 文件名 | 事件类型 | 推荐描述 | 时长 |
|--------|---------|---------|------|
| `cute-answer-good.mp3` | answer_good | 短促钢琴琶音上行（C-E-G） | 0.5s |
| `cute-review-pass.mp3` | review_pass | 欢快铃铛 + 轻柔鼓掌 | 1.2s |
| `cute-stage-complete.mp3` | stage_complete | 完整八音盒旋律（胜利感） | 1.8s |
| `cute-streak.mp3` | streak_achieved | 连续快速钟声（ding-ding-ding） | 0.8s |

**关键词**：cheerful, bright, playful, piano, bells, music box, xylophone

---

### 2. Dark 主题（暗黑风）

| 文件名 | 事件类型 | 推荐描述 | 时长 |
|--------|---------|---------|------|
| `dark-answer-good.mp3` | answer_good | 单次低频鼓点 + 短促合成器和弦 | 0.6s |
| `dark-review-pass.mp3` | review_pass | 深沉钟声 + 渐强弦乐 | 1.5s |
| `dark-stage-complete.mp3` | stage_complete | 管风琴和弦渐强 | 2s |
| `dark-streak.mp3` | streak_achieved | 三次低频脉冲（boom-boom-boom） | 1s |

**关键词**：deep, low-frequency, mysterious, bass drum, organ, dark ambient, strings

---

### 3. Corporate 主题（正式风）

| 文件名 | 事件类型 | 推荐描述 | 时长 |
|--------|---------|---------|------|
| `corporate-answer-good.mp3` | answer_good | 清脆单音（像邮件送达） | 0.3s |
| `corporate-stage-complete.mp3` | stage_complete | 短促上行三音（系统通知） | 0.8s |

> **注意**：Corporate 主题只需 2 个音效，`review_pass` 和 `streak_achieved` 会复用 `stage_complete`

**关键词**：professional, crisp, notification, email, system beep, concise

---

### 4. Cyber 主题（未来风）

| 文件名 | 事件类型 | 推荐描述 | 时长 |
|--------|---------|---------|------|
| `cyber-answer-good.mp3` | answer_good | 数字化 beep + 快速扫描音 | 0.5s |
| `cyber-review-pass.mp3` | review_pass | 合成器上升音阶 + 数据确认音 | 1.2s |
| `cyber-stage-complete.mp3` | stage_complete | 完整系统启动音效 | 1.8s |
| `cyber-streak.mp3` | streak_achieved | 快速脉冲序列（zap-zap-zap） | 0.7s |

**关键词**：digital, futuristic, electronic, synthesizer, scanning, data confirmation, tech

---

### 5. Botanical 主题（自然风）

| 文件名 | 事件类型 | 推荐描述 | 时长 |
|--------|---------|---------|------|
| `botanical-answer-good.mp3` | answer_good | 单次风铃音 + 短促鸟鸣 | 0.6s |
| `botanical-review-pass.mp3` | review_pass | 流水声 + 舒缓木琴 | 1.5s |
| `botanical-stage-complete.mp3` | stage_complete | 自然氛围音（风+水+鸟） | 2s |
| `botanical-streak.mp3` | streak_achieved | 连续风铃声 | 0.9s |

**关键词**：nature, wind chimes, water, birdsong, organic, calm, soothing, bamboo

---

## 🛠️ 获取音效的三种方式

### 方式 A：从免费资源库下载（推荐新手）

**优点**：快速、无需音频编辑技能  
**缺点**：需要筛选匹配的音效

#### 推荐站点

1. **[Freesound](https://freesound.org)** ⭐ 推荐
   - 免费、需注册
   - 搜索关键词：`success`, `notification`, `achievement`, `chime`, `bell`, `piano`
   - 筛选：License → CC0（公共领域）或 CC BY（需署名）
   - 下载 MP3 或 WAV（WAV 需转换）

2. **[Mixkit](https://mixkit.co/free-sound-effects/)**
   - 完全免费、无需注册
   - 分类：Success & Notification
   - 直接下载 MP3

3. **[Zapsplat](https://www.zapsplat.com)**
   - 免费注册（每天下载限制）
   - 分类：UI/UX → Notifications
   - 音效质量高

4. **[Pixabay Sound Effects](https://pixabay.com/sound-effects/)**
   - 完全免费、CC0 许可
   - 搜索：success, notification, game

#### 操作步骤

1. 访问 Freesound.org 并注册
2. 搜索 `piano arpeggio success` → 找到适合 cute-answer-good 的音效
3. 点击下载 → 选择 MP3 格式（或 WAV 后续转换）
4. 重命名为 `cute-answer-good.mp3`
5. 重复以上步骤完成其他 19 个文件

---

### 方式 B：使用 AI 生成音效（推荐快速完成）

**优点**：精确控制描述，快速生成  
**缺点**：需要免费额度或付费

#### 推荐工具

1. **[ElevenLabs Sound Effects](https://elevenlabs.io/sound-effects)** ⭐ 推荐
   - 免费额度：10,000 字符/月（约可生成 20-30 个音效）
   - 操作简单：输入描述 → 生成 → 下载

#### 操作步骤（以 cute-answer-good 为例）

1. 访问 https://elevenlabs.io/sound-effects
2. 输入描述：
   ```
   A short cheerful piano arpeggio, ascending notes C-E-G, 
   bright and playful, 0.5 seconds duration, suitable for 
   success notification in a cute UI theme
   ```
3. 点击 **Generate**
4. 试听生成结果，不满意可重新生成
5. 点击 **Download** → 保存为 `cute-answer-good.mp3`
6. 重复以上步骤完成其他 19 个文件

#### 示例 Prompts

**Cute 主题**：
```
cute-answer-good: A short cheerful piano arpeggio, ascending C-E-G, bright, 0.5s
cute-review-pass: Cheerful bells with soft clapping, joyful, 1.2s
cute-stage-complete: Complete music box melody, victorious, 1.8s
cute-streak: Quick succession of chimes, ding-ding-ding, playful, 0.8s
```

**Dark 主题**：
```
dark-answer-good: Deep bass drum with short synthesizer chord, mysterious, 0.6s
dark-review-pass: Deep bell with gradually intensifying strings, atmospheric, 1.5s
dark-stage-complete: Organ chord gradually building intensity, grand, 2s
dark-streak: Three low-frequency pulses, boom-boom-boom, powerful, 1s
```

---

### 方式 C：使用 Audacity 编辑（适合进阶用户）

**优点**：完全自定义，专业质量  
**缺点**：需要音频编辑技能

#### 工具准备

1. 下载 [Audacity](https://www.audacityteam.org/)（免费开源）
2. 下载基础音效素材（从 Freesound 获取）

#### 编辑步骤

1. 导入音频文件
2. 选择需要的片段（效果 → 选择 → 裁剪）
3. 调整音量（效果 → 归一化 → 设置为 -3dB）
4. 裁剪到目标时长（选择工具 → Delete）
5. 导出为 MP3（文件 → 导出 → 导出为 MP3）
   - 比特率：128kbps
   - 声道：单声道（节省空间）
6. 重命名文件

---

## 📂 文件放置位置

将所有 20 个 MP3 文件放入：

```
frontend/public/sounds/
```

最终目录结构应为：

```
frontend/public/sounds/
├── cute-answer-good.mp3
├── cute-review-pass.mp3
├── cute-stage-complete.mp3
├── cute-streak.mp3
├── dark-answer-good.mp3
├── dark-review-pass.mp3
├── dark-stage-complete.mp3
├── dark-streak.mp3
├── corporate-answer-good.mp3
├── corporate-stage-complete.mp3
├── cyber-answer-good.mp3
├── cyber-review-pass.mp3
├── cyber-stage-complete.mp3
├── cyber-streak.mp3
├── botanical-answer-good.mp3
├── botanical-review-pass.mp3
├── botanical-stage-complete.mp3
├── botanical-streak.mp3
└── README.md (已存在)
```

> **注意**：文件名必须与 `frontend/src/lib/feedbackManifest.ts` 中的 `sound` 字段**完全一致**，包括大小写和连字符。

---

## ✅ 验收测试

### 1. 文件检查

在 PowerShell 中运行：

```powershell
cd frontend/public/sounds
dir *.mp3
```

确认看到 20 个 MP3 文件。

### 2. 功能测试

1. 启动前端：`npm run dev`
2. 进入任意学习阶段，确保设置面板中**音效开关已打开**
3. 完成一个 REVIEW 节点（触发 `review_pass`）
4. 确认听到对应主题的音效（如 cute 主题应听到欢快铃铛）
5. 切换到 cyber 主题，再次触发，确认音效变为科技风格
6. 打开浏览器开发者工具 → **Network** 标签
7. 筛选 `mp3` → 确认音频文件成功加载（状态 200）

### 3. 主题差异验证

| 主题 | 预期音效特征 |
|------|------------|
| Cute | 明亮、轻快、钢琴/铃铛声 |
| Dark | 低沉、神秘、鼓点/管风琴声 |
| Corporate | 简洁、单音、专业提示音 |
| Cyber | 电子、合成器、数字化音效 |
| Botanical | 舒缓、自然、风铃/流水声 |

---

## 🚀 快速验证方式（跳过真实音效）

如果只想验证**播放逻辑**而不关心音效质量，可以：

1. 从任意来源下载 1 个短音效文件（如 0.5s 的 beep）
2. 复制 20 份并重命名为所需文件名
3. 这样可以验证播放功能，但所有主题音效都一样

> **警告**：这种方式不满足最终交付标准，仅用于开发测试。

---

## ⏱️ 时间估算

| 方式 | 预估时间 | 难度 |
|------|---------|------|
| 方式 A（资源库下载） | 2-3 小时 | ⭐ 简单 |
| 方式 B（AI 生成） | 1 小时 | ⭐⭐ 中等 |
| 方式 C（Audacity 编辑） | 3-4 小时 | ⭐⭐⭐ 进阶 |

---

## 📜 许可证说明

如果从 Freesound 等站点下载音效，请注意许可证要求：

- **CC0（公共领域）**：可自由使用，无需署名
- **CC BY**：需在项目文档中署名原作者
- **CC BY-NC**：仅限非商业用途

建议优先选择 **CC0 许可**的音效，避免后续许可证问题。

---

## 🔗 参考资源

- [Freesound](https://freesound.org) — 最大的免费音效库
- [Mixkit](https://mixkit.co/free-sound-effects/) — UI/UX 音效精选
- [Zapsplat](https://www.zapsplat.com) — 专业音效库
- [ElevenLabs Sound Effects](https://elevenlabs.io/sound-effects) — AI 生成音效
- [Audacity](https://www.audacityteam.org/) — 免费音频编辑器
- [Pixabay Sound Effects](https://pixabay.com/sound-effects/) — CC0 音效

---

## 💡 提示

1. **优先完成 Cute 和 Corporate 主题**：这两个主题最常用，可先创建这 6 个音效文件快速验证
2. **使用 ElevenLabs 批量生成**：输入精确描述后，可快速生成所有 20 个音效
3. **音效不是必需的**：系统已实现优雅降级，即使没有音效文件也不会影响核心功能

---

**祝你创建愉快！** 🎉
