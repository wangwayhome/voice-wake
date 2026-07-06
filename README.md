# 小立管家语音唤醒 Demo

Android 离线语音唤醒 Demo。应用启动后以前台服务持续采集麦克风音频，使用 Vosk 中文模型在本地进行流式识别，命中“小立管家”或常见同音变体后更新通知和界面状态。

## 功能

- 离线唤醒词检测，不依赖云端识别。
- 前台服务持续监听麦克风。
- 使用 `AudioRecord` 采集 16kHz 单声道 16bit PCM。
- 使用 `vosk-model-small-cn-0.22` 中文模型。
- 通过 Vosk grammar 和同音变体提升“小立管家”命中率。
- Compose UI 展示下载、初始化、监听、唤醒和错误状态。

## 工程结构

```text
app/src/main/java/com/leelen/voicewake/
├── MainActivity.kt
├── WakeApplication.kt
├── audio/AudioCapture.kt
├── service/WakeService.kt
├── ui/WakeScreen.kt
└── wake/
    ├── ModelManager.kt
    └── WakeWordDetector.kt
docs/
└── voice-wake-design.md
```

## 构建

```bash
./gradlew :app:assembleDebug
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装与启动

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.leelen.voicewake android.permission.RECORD_AUDIO
adb shell pm grant com.leelen.voicewake android.permission.POST_NOTIFICATIONS
adb shell am start -n com.leelen.voicewake/.MainActivity
```

## 日志排查

```bash
adb logcat -s MainActivity WakeService AudioCapture WakeWordDetector ModelManager VoskAPI AndroidRuntime
```

成功唤醒时可看到类似日志：

```text
WakeWordDetector: 识别中: 小丽 管家
WakeWordDetector: 匹配到唤醒词，原始识别文本: 小丽 管家
WakeService: 检测到唤醒词: 小立管家
```

## 设计文档

概要设计、架构图、启动时序图、唤醒检测流程图和状态机见：

[docs/voice-wake-design.md](docs/voice-wake-design.md)
