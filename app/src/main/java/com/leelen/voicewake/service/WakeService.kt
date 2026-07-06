package com.leelen.voicewake.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.leelen.voicewake.MainActivity
import com.leelen.voicewake.R
import com.leelen.voicewake.WakeApplication
import com.leelen.voicewake.audio.AudioCapture
import com.leelen.voicewake.wake.WakeWordDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台服务：持续运行唤醒词检测
 *
 * 架构：
 *   WakeService (前台 Service)
 *     ├── AudioCapture (麦克风采集)
 *     └── WakeWordDetector (Vosk 识别)
 *         ↓ 检测到 "小立管家"
 *     回调 → Activity UI 更新
 */
class WakeService : Service() {

    companion object {
        private const val TAG = "WakeService"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.leelen.voicewake.START"
        const val ACTION_STOP = "com.leelen.voicewake.STOP"
    }

    // Service 状态
    enum class State {
        IDLE,           // 未启动
        LISTENING,      // 正在监听唤醒词
        WAKE_DETECTED,  // 检测到唤醒词
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _wakeCount = MutableStateFlow(0)
    val wakeCount: StateFlow<Int> = _wakeCount

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var detectJob: Job? = null
    @Volatile
    private var isModelInitialized = false

    private val audioCapture = AudioCapture()
    private val wakeDetector by lazy { WakeWordDetector(this) }

    private val binder = WakeBinder()

    inner class WakeBinder : Binder() {
        fun getService(): WakeService = this@WakeService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopDetection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification("正在监听..."))
                startDetection()
            }
        }
        return START_STICKY
    }

    /** 初始化 Vosk 模型（耗时操作，在协程中调用） */
    suspend fun initModel(modelPath: String) {
        if (isModelInitialized) {
            Log.d(TAG, "模型已初始化，跳过重复加载")
            return
        }
        wakeDetector.init(modelPath)
        isModelInitialized = true
    }

    /** 开始唤醒词检测 */
    fun startDetection() {
        if (detectJob?.isActive == true) return
        if (!isModelInitialized) {
            Log.w(TAG, "模型尚未初始化，暂不启动唤醒词检测")
            return
        }

        _state.value = State.LISTENING
        updateNotification("正在监听「小立管家」...")

        detectJob = serviceScope.launch(Dispatchers.IO) {
            try {
                audioCapture.start(
                    bufferSize = 1280 // ~80ms at 16kHz, Vosk 推荐
                ) { pcm ->
                    if (!isActive) return@start

                    if (wakeDetector.process(pcm)) {
                        onWakeWordDetected()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "唤醒词检测异常退出", e)
                _state.value = State.IDLE
                updateNotification("监听异常：${e.message ?: "请查看日志"}")
            }
        }

        Log.d(TAG, "唤醒词检测已启动")
    }

    /** 停止检测 */
    fun stopDetection() {
        detectJob?.cancel()
        detectJob = null
        audioCapture.stop()
        _state.value = State.IDLE
        Log.d(TAG, "唤醒词检测已停止")
    }

    private fun onWakeWordDetected() {
        Log.d(TAG, "🎉 检测到唤醒词: 小立管家")
        _wakeCount.value += 1
        _state.value = State.WAKE_DETECTED
        updateNotification("✨ 已唤醒！")

        // 重置检测器，3秒后恢复监听
        wakeDetector.reset()
        serviceScope.launch {
            delay(3000)
            _state.value = State.LISTENING
            updateNotification("正在监听「小立管家」...")
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WakeApplication.WAKE_CHANNEL_ID)
            .setContentTitle("小立管家")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopDetection()
        wakeDetector.release()
        isModelInitialized = false
        serviceScope.cancel()
        super.onDestroy()
    }
}
