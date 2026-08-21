# 抖音TV (DouyinTV)

为 Amlogic S905X3 等低端电视盒子优化的抖音网页版客户端。

## 特性

### 🎮 遥控器适配
- **菜单键** → 切换光标模式（开/关）
- **方向键** → 光标移动（光标模式下）
- **OK键** → 鼠标左键单击
- **长按OK键** → 鼠标右键单击（长按500ms触发）
- **返回键** → 退出光标模式 / 返回上一页 / 双击退出
- **OK键单击** → 在页面元素间导航（非光标模式）

### ⚡ 性能优化
- **资源拦截**：自动屏蔽 50+ 个分析/监控/追踪请求（约节省 500KB+ 带宽）
- **DOM清理**：自动隐藏下载弹窗、APP推广横幅等无关UI
- **CSS优化**：禁用动画、隐藏滚动条、强制暗色模式
- **视频优化**：仅预加载元数据，禁用画中画
- **WebView优化**：启用硬件加速、DOM存储、数据库

### 🔒 资源拦截详情
屏蔽以下域名的请求：
- `mcs.snssdk.com` / `mcs.zijieapi.com` - 数据分析
- `mon.zijieapi.com` - 监控上报
- `mssdk.bytedance.com` - SDK追踪
- `security.zijieapi.com` - 安全检测
- `vcs.zijieapi.com` - 视频追踪
- `privacy.zijieapi.com` - 隐私追踪
- `lf-static.applogcdn.com` - 日志上报
- `lf3-short.ibytedapm.com` - APM监控
- `tnc0-aliec2.zijieapi.com` - 追踪
- `frontier.zijieapi.com` - 推送追踪
- 以及 Sentry/Slardar 错误上报路径

### 📱 登录方式
1. 启动APP后，按 **菜单键** 开启光标模式
2. 用方向键移动光标到右上角「登录」按钮
3. 按 **OK键** 点击登录
4. 用光标移动到二维码区域，用手机抖音扫码登录
5. 登录成功后，cookie 会自动保存，下次无需重复登录

## 构建

### GitHub Actions（推荐）
1. Fork 本仓库
2. Push 到 `main` 分支或创建 tag (`v1.0.0`)
3. 在 Actions 页面等待构建完成
4. 下载 APK artifact

### 本地构建
需要 JDK 17+ 和 Android SDK 34

```bash
./gradlew assembleDebug   # Debug 版本
./gradlew assembleRelease  # Release 版本
```

## 系统要求
- Android 5.0+ (API 21+)
- 推荐 Android 9+ (API 28+)
- ARM / ARM64 / x86 架构
- 最低 1GB RAM

## 文件结构
```
douyin-tv/
├── .github/workflows/build.yml  # GitHub Actions 构建配置
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/douyin/tv/
│       │   ├── MainActivity.java          # 主界面
│       │   ├── CursorController.java      # 遥控器→光标映射
│       │   ├── JavaScriptInterface.java   # JS桥接接口
│       │   └── TvWebViewClient.java       # 资源拦截
│       ├── assets/
│       │   ├── inject.js    # 注入JS（光标、DOM清理、资源优化）
│       │   └── inject.css   # 注入CSS（TV适配样式）
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/strings.xml
│           ├── values/colors.xml
│           ├── values/styles.xml
│           └── drawable/status_bg.xml
├── build.gradle
├── settings.gradle
├── gradle.properties
└── README.md
```

## 遥控器键位对照

| 遥控器按键 | Android KeyCode | 功能 |
|-----------|----------------|------|
| ↑↓←→ | DPAD_UP/DOWN/LEFT/RIGHT | 光标移动 |
| OK | DPAD_CENTER | 左键单击 |
| 长按OK | DPAD_CENTER (500ms) | 右键单击 |
| 菜单 | MENU | 切换光标模式 |
| 返回 | BACK | 退出光标/返回/退出APP |

## 注意事项
- 首次加载可能较慢（需下载JS/CSS资源约3MB）
- 建议连接稳定的WiFi网络
- 如果页面显示异常，可尝试清除APP数据后重试
- 登录状态会保存在Cookie中，卸载APP会丢失

## License
MIT
