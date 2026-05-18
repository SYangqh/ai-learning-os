# AI Learning OS — 移动端 Capacitor 打包指南

> 本文档覆盖 iOS / Android 真机构建步骤、权限配置、声音权限处理、触感反馈和推送降级策略。

---

## 目录

1. [前置条件](#前置条件)
2. [安装 Capacitor 依赖](#安装-capacitor-依赖)
3. [构建 Next.js 静态导出](#构建-nextjs-静态导出)
4. [iOS 构建与真机验证](#ios-构建与真机验证)
5. [Android 构建与真机验证](#android-构建与真机验证)
6. [声音权限处理](#声音权限处理)
7. [触感反馈降级策略](#触感反馈降级策略)
8. [推送通知降级策略](#推送通知降级策略)
9. [安全区适配验证](#安全区适配验证)
10. [软键盘适配验证](#软键盘适配验证)
11. [已知问题与解决方案](#已知问题与解决方案)

---

## 前置条件

| 平台   | 要求                                                    |
|--------|-------------------------------------------------------|
| iOS    | macOS + Xcode 15+（Apple 开发者账号，或使用模拟器）     |
| Android| Android Studio（Windows/macOS/Linux 均可）             |
| 共同   | Node.js 20+、npm 10+、Java 17+（Android only）         |

---

## 安装 Capacitor 依赖

```bash
cd frontend
npm install @capacitor/core @capacitor/cli
npm install @capacitor/ios @capacitor/android

# 可选：触感反馈插件
npm install @capacitor/haptics

# 可选：推送通知插件
npm install @capacitor/push-notifications

# 初始化（如首次设置）
npx cap init "AI Learning OS" "com.ailearningos.app" --web-dir=out
```

---

## 构建 Next.js 静态导出

Capacitor 需要将 Next.js 导出为静态文件（`out/` 目录）。

1. 在 `next.config.js` 中添加：

```js
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',          // 静态导出
  trailingSlash: true,       // Capacitor 路由需要
  images: { unoptimized: true },
}
module.exports = nextConfig
```

2. 构建：

```bash
npm run build   # 生成 out/ 目录
npx cap sync    # 同步到 iOS/Android 平台目录
```

> ⚠️ 静态导出模式下，Next.js Server Actions 和 API Routes 不可用。所有 API 请求应指向独立部署的 Spring Boot 后端。
> 在 `.env.production.local` 中设置：`NEXT_PUBLIC_API_BASE=https://your-backend.example.com/api`

---

## iOS 构建与真机验证

```bash
# 首次添加平台
npx cap add ios

# 构建 + 同步
npm run build && npx cap sync ios

# 用 Xcode 打开
npx cap open ios
```

### Xcode 中的操作

1. 选择开发团队（Signing & Capabilities）
2. 选择真机目标设备
3. **Product → Run**（⌘R）

### 验证清单（iOS）

- [ ] 顶部刘海/灵动岛区域不遮挡内容（safe-area-inset-top 生效）
- [ ] 底部 Home Indicator 区域不遮挡输入框（safe-area-inset-bottom 生效）
- [ ] 旋转为横屏后布局正常，输入框不被裁切
- [ ] 软键盘弹起时输入框上移并可见（Keyboard resize=body 生效）
- [ ] 侧边栏抽屉手势滑动正常（touch 事件无延迟）
- [ ] ArtifactPanel 底部 Sheet 上滑/下划正常

---

## Android 构建与真机验证

```bash
# 首次添加平台
npx cap add android

# 构建 + 同步
npm run build && npx cap sync android

# 用 Android Studio 打开
npx cap open android
```

### Android Studio 中的操作

1. 等待 Gradle 同步完成
2. 选择真机（或 API 34 模拟器）
3. **Run → Run 'app'**（⇧F10）

### 验证清单（Android）

- [ ] 状态栏不遮挡顶部内容（`WindowCompat.setDecorFitsSystemWindows(false)` + padding）
- [ ] 底部导航栏区域有底部 padding（safe-area-inset-bottom 生效）
- [ ] 软键盘弹起时 WebView 高度正确缩小（`adjustResize` 生效）
- [ ] 返回手势不误触发侧边栏关闭
- [ ] 折叠屏展开/折叠时布局重新计算正常

---

## 声音权限处理

### iOS

iOS 中 Web Audio API 在 WebView 内需要**用户手势**才能播放音频：

```typescript
// 在 useMobileCapabilities.ts 中，requestHapticsTest() 同时激活音频上下文
// 第一次用户点击任意按钮时调用：
const audioCtx = new AudioContext()
await audioCtx.resume()   // 必须在用户手势中调用
```

在 `capacitor.config.ts` 的 iOS 配置中无需额外权限（Web Audio API 无需 Info.plist 声明）。

### Android

Android WebView 默认允许 Web Audio API。无需额外配置。

### 降级策略

- 若 `AudioContext` 创建失败（低版本设备）或用户系统静音，`useMobileCapabilities` 的 `hasHaptics` 返回 `false`
- 音效播放失败时 `FeedbackToast` 仍然显示文案和动画，不报错（`try/catch` 静默处理）
- 用户设置中的"声音开关"（`soundEnabled`）优先于一切硬件状态

---

## 触感反馈降级策略

```typescript
// 使用 @capacitor/haptics（需安装）
import { Haptics, ImpactStyle } from '@capacitor/haptics'

async function vibrate() {
  try {
    await Haptics.impact({ style: ImpactStyle.Light })
  } catch {
    // 降级：使用 Web Vibration API（Android 支持，iOS 不支持）
    navigator.vibrate?.(20)
    // 若两者都不支持，静默忽略
  }
}
```

- iOS：Haptics API 可用，`navigator.vibrate()` 无效（Safari 不支持）
- Android：两种方式都可用，优先 Haptics 插件
- Web（非 Capacitor）：仅 `navigator.vibrate()`，部分浏览器需用户手势

**触感反馈不影响学习主流程，失败时静默降级。**

---

## 推送通知降级策略

### 权限请求时机

- **不在应用启动时立即请求**推送权限（避免被用户拒绝）
- 在用户**完成第一个学习阶段后**，展示一个非阻塞的提示（Toast 样式，不是系统弹窗）
- 用户点击"开启提醒"后再调用 `Notification.requestPermission()`

```typescript
// 在 useMobileCapabilities.ts 中
const requestNotificationPermission = async () => {
  if (!('Notification' in window)) {
    return 'unsupported'
  }
  try {
    const result = await Notification.requestPermission()
    setNotificationPermission(result)
    return result
  } catch {
    setNotificationPermission('denied')
    return 'denied'
  }
}
```

### 降级行为

| 场景 | 处理 |
|------|------|
| 用户拒绝推送权限 | `hasNotifications=false`，不再提示，不中断学习 |
| 浏览器不支持 Notification API | 同上 |
| Capacitor 环境（需 `@capacitor/push-notifications`） | 使用插件的 `PushNotifications.requestPermissions()`，失败降级 |
| 网络断开时推送失败 | 静默处理，离线期间的学习进度正常保存（IndexedDB 草稿） |

---

## 安全区适配验证

### 关键 CSS 变量（在 `globals.css` 中定义）

```css
:root {
  --safe-inset-top: env(safe-area-inset-top, 0px);
  --safe-inset-bottom: env(safe-area-inset-bottom, 0px);
  --safe-inset-left: env(safe-area-inset-left, 0px);
  --safe-inset-right: env(safe-area-inset-right, 0px);
}
```

### 要求

1. `layout.tsx` 的 Viewport 必须包含 `viewportFit: 'cover'`，否则 `env()` 返回 0
2. `position: fixed` 的元素（Sidebar, ArtifactPanel Bottom Sheet）必须使用 `env()` 直接计算，不能通过 CSS 变量中转（部分 WebView 不支持嵌套 CSS 变量）
3. iOS 横屏时 `safe-area-inset-left/right` 约为 44px，需确保 Sidebar 和按钮不被遮挡

---

## 软键盘适配验证

### 检测原理

```typescript
// 在 useMobileCapabilities.ts 中
window.visualViewport?.addEventListener('resize', () => {
  const keyboard = Math.max(
    0,
    window.innerHeight - (window.visualViewport?.height ?? window.innerHeight) - (window.visualViewport?.offsetTop ?? 0)
  )
  setKeyboardHeight(keyboard)
  document.documentElement.style.setProperty('--keyboard-height', `${keyboard}px`)
})
```

### ChatPanel 适配

- 输入区底部 padding = `keyboardHeight`（当键盘弹起时）
- `interactiveWidget: 'resizes-content'`（在 `layout.tsx` Viewport 中设置）让浏览器缩小 viewport 而非平移
- Capacitor iOS 需要在 `capacitor.config.ts` 设置 `Keyboard.resize: 'body'`

---

## 已知问题与解决方案

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| iOS Safari 输入框 auto-zoom | 输入字体 < 16px | 全局设置 `font-size: 16px`（已在 globals.css 的 media query 中处理） |
| Android 软键盘弹起后页面空白区域 | `adjustPan` 默认行为 | `capacitor.config.ts` 设置 `Keyboard.resize: 'body'` |
| IndexedDB 在 iOS 私密浏览无法使用 | Safari 隐私限制 | `useOfflineDraft.ts` 中所有 IDB 操作均有 try/catch 静默降级 |
| Capacitor iOS WebView 首次白屏 | 首屏 JS 体积过大 | 确认 `next.config.js` 开启代码分割；SplashScreen 延迟 1500ms 遮盖加载过程 |
| `env(safe-area-inset-*)` 返回 0 | 缺少 `viewport-fit=cover` | 确认 `layout.tsx` 的 Viewport export 含 `viewportFit: 'cover'` |
