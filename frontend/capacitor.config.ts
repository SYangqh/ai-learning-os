import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.ailearningos.app',
  appName: 'AI Learning OS',
  webDir: 'out',
  // 生产环境将 server.url 设置为你的实际后端域名
  // 本地开发时取消注释以热重载：
  // server: {
  //   url: 'http://192.168.x.x:3000',
  //   cleartext: true,
  // },
  ios: {
    contentInset: 'always',          // 支持 safe-area-inset-*
    preferredContentMode: 'mobile',
    backgroundColor: '#ffffff',
    // 在 Info.plist 中需声明 NSMicrophoneUsageDescription（如启用语音输入）
  },
  android: {
    backgroundColor: '#ffffff',
    // 允许混合内容（HTTP/HTTPS）仅限开发测试
    // allowMixedContent: true,
  },
  plugins: {
    // 推送通知（Firebase / APNs）
    // PushNotifications 插件需另行安装: npm install @capacitor/push-notifications
    // 若用户拒绝推送权限，应用应静默降级，不中断学习流程
    SplashScreen: {
      launchShowDuration: 1500,
      backgroundColor: '#1a1a2e',
      androidSplashResourceName: 'splash',
      showSpinner: false,
    },
    Keyboard: {
      // 键盘弹起时缩小 WebView 而非平移，配合 interactiveWidget: 'resizes-content'
      resize: 'body',
      resizeOnFullScreen: true,
    },
  },
}

export default config
