package com.leelen.voicewake

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.leelen.voicewake.service.WakeService
import com.leelen.voicewake.ui.WakeScreen
import com.leelen.voicewake.ui.WakeTheme
import com.leelen.voicewake.wake.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // UI 状态
    private val _uiStatus = mutableStateOf<UiStatus>(UiStatus.Downloading(0f))
    private var wakeService: WakeService? = null
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WakeService.WakeBinder
            wakeService = binder.getService()
            bound = true
            Log.d(TAG, "Service 已绑定")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            wakeService = null
            bound = false
            Log.d(TAG, "Service 断开")
        }
    }

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            _uiStatus.value = UiStatus.Error("需要麦克风权限才能使用语音唤醒")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WakeTheme {
                val status by remember { _uiStatus }
                val service = wakeService
                val serviceState = service?.state?.collectAsState()
                val count = service?.wakeCount?.collectAsState()

                WakeScreen(
                    status = status,
                    serviceState = serviceState?.value ?: WakeService.State.IDLE,
                    wakeCount = count?.value ?: 0,
                    onStop = { stopWakeDetection() }
                )
            }
        }

        // 先请求权限，再启动流程
        if (hasPermissions()) {
            onPermissionsGranted()
        } else {
            requestPermissions()
        }
    }

    /** 权限已获取，开始下载模型并启动 */
    private fun onPermissionsGranted() {
        CoroutineScope(Dispatchers.Main).launch {
            if (!ModelManager.isModelReady(this@MainActivity)) {
                _uiStatus.value = UiStatus.Downloading(0f)
                try {
                    val modelPath = ModelManager.getModelPath(this@MainActivity) { downloaded, total ->
                        if (total > 0) {
                            val progress = downloaded.toFloat() / total
                            _uiStatus.value = UiStatus.Downloading(progress)
                        }
                    }
                    startWakeDetection(modelPath)
                } catch (e: Exception) {
                    Log.e(TAG, "模型下载失败", e)
                    _uiStatus.value = UiStatus.Error("模型下载失败: ${e.message}")
                }
            } else {
                val modelPath = ModelManager.getModelPath(this@MainActivity)
                startWakeDetection(modelPath)
            }
        }
    }

    private fun startWakeDetection(modelPath: String? = null) {
        _uiStatus.value = UiStatus.Initializing

        // 启动前台服务
        val intent = Intent(this, WakeService::class.java).apply {
            action = WakeService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // 绑定服务
        bindService(
            Intent(this, WakeService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        // 初始化模型并开始检测
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 等服务绑定
                while (!bound) {
                    kotlinx.coroutines.delay(100)
                }

                if (modelPath != null) {
                    withContext(Dispatchers.IO) {
                        wakeService?.initModel(modelPath)
                    }
                }

                wakeService?.startDetection()
                _uiStatus.value = UiStatus.Running
            } catch (e: Exception) {
                Log.e(TAG, "启动唤醒检测失败", e)
                cleanupWakeService()
                stopService(Intent(this@MainActivity, WakeService::class.java))
                _uiStatus.value = UiStatus.Error("启动监听失败: ${e.message ?: "请查看日志"}")
            }
        }
    }

    private fun stopWakeDetection() {
        val intent = Intent(this, WakeService::class.java).apply {
            action = WakeService.ACTION_STOP
        }
        startService(intent)
        cleanupWakeService()
        _uiStatus.value = UiStatus.Stopped
    }

    private fun cleanupWakeService() {
        wakeService?.stopDetection()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        wakeService = null
    }

    private fun hasPermissions(): Boolean {
        val audio = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return audio && notification
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        super.onDestroy()
    }
}

/** UI 状态 */
sealed class UiStatus {
    data class Downloading(val progress: Float) : UiStatus()
    data object Initializing : UiStatus()
    data object RequestingPermission : UiStatus()
    data object Running : UiStatus()
    data object Stopped : UiStatus()
    data class Error(val message: String) : UiStatus()
}
