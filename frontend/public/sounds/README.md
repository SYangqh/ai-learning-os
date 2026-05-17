# 反馈音效文件清单

Phase 9A 主题化正反馈演出系统需要以下音效文件。

## 文件位置

所有音效文件应放置在 `frontend/public/sounds/` 目录下。

## 必需音效文件

### cute 主题（可爱风）
- `cute-ding.mp3` — 回答正确的轻快提示音（answer_good）
- `cute-success.mp3` — 评审通过的庆祝音（review_pass）
- `cute-celebration.mp3` — 阶段完成的完整庆祝音（stage_complete）
- `cute-streak.mp3` — 连续学习达成音（streak_achieved）

### dark 主题（夜间风）
- `dark-tick.mp3` — 简短确认音（answer_good）
- `dark-confirm.mp3` — 低调确认音（review_pass）
- `dark-complete.mp3` — 完成提示音（stage_complete）
- `dark-streak.mp3` — 连续学习提示（streak_achieved）

### corporate 主题（国企风）
- `corporate-pass.mp3` — 正式通过音（review_pass）
- `corporate-complete.mp3` — 阶段完成音（stage_complete）

### cyber 主题（未来科技）
- `cyber-beep.mp3` — 电子短促音（answer_good）
- `cyber-success.mp3` — 系统确认音（review_pass）
- `cyber-complete.mp3` — 任务完成音（stage_complete）
- `cyber-streak.mp3` — 连续学习音（streak_achieved）

### botanical 主题（自然风）
- `botanical-chime.mp3` — 自然风铃音（answer_good）
- `botanical-success.mp3` — 舒缓成功音（review_pass）
- `botanical-complete.mp3` — 温暖完成音（stage_complete）
- `botanical-water.mp3` — 水滴音（streak_achieved）

### accessible 主题（无障碍）
- 无音效（配置中已设置为 `null`）

## 音效要求

1. **格式**：MP3（兼容性好）或 WebM（体积小）
2. **时长**：0.3-2 秒
3. **音量**：归一化到 -3dB 到 -6dB，避免爆音
4. **大小**：单文件 < 50KB
5. **采样率**：44.1kHz 或 48kHz

## 临时方案

在音效文件未准备好之前，系统会自动降级到纯文本提示（使用 `fallbackText`），不影响功能使用。

## 获取音效资源

推荐免费音效库：
- https://freesound.org/
- https://mixkit.co/free-sound-effects/
- https://www.zapsplat.com/

或使用 AI 音频生成工具：
- ElevenLabs Sound Effects
- https://elevenlabs.io/sound-effects

## 测试音效

```bash
cd frontend/public/sounds
# 测试播放（macOS）
afplay cute-ding.mp3

# 测试播放（Windows PowerShell）
(New-Object Media.SoundPlayer "cute-ding.mp3").PlaySync()
```

## 验收标准

1. 所有音效文件存在且可播放
2. 音量适中，不刺耳
3. 不同主题音效风格明显不同
4. 文件大小总和 < 500KB
