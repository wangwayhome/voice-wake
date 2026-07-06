package com.leelen.voicewake.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leelen.voicewake.UiStatus
import com.leelen.voicewake.service.WakeService

/**
 * 唤醒词 Demo 主界面
 */
@Composable
fun WakeScreen(
    status: UiStatus,
    serviceState: WakeService.State,
    wakeCount: Int,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0A1628) // 深色背景，壁挂设备省电
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 标题
            Text(
                text = "小立管家",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "语音唤醒 Demo",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 状态指示器
            StatusIndicator(status, serviceState)

            Spacer(modifier = Modifier.height(32.dp))

            // 唤醒词展示
            when (status) {
                is UiStatus.Running -> {
                    WakeWordDisplay(serviceState)
                }
                is UiStatus.Downloading -> {
                    DownloadProgress(status.progress)
                }
                is UiStatus.Error -> {
                    Text(
                        text = status.message,
                        color = Color(0xFFFF6B6B),
                        fontSize = 16.sp
                    )
                }
                else -> {
                    Text(
                        text = "正在初始化...",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // 唤醒次数
            if (status is UiStatus.Running) {
                Text(
                    text = "唤醒次数: $wakeCount",
                    color = Color(0xFF8899AA),
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 停止按钮
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334455)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("停止监听", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: UiStatus, serviceState: WakeService.State) {
    val (color, text) = when {
        status is UiStatus.Downloading -> Color(0xFFFFA726) to "下载模型中"
        status is UiStatus.Initializing -> Color(0xFFFFA726) to "初始化中"
        status is UiStatus.Error -> Color(0xFFFF6B6B) to "错误"
        serviceState == WakeService.State.WAKE_DETECTED -> Color(0xFF4CAF50) to "已唤醒"
        serviceState == WakeService.State.LISTENING -> Color(0xFF2196F3) to "监听中"
        else -> Color.Gray to "待机"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun WakeWordDisplay(serviceState: WakeService.State) {
    val isWake = serviceState == WakeService.State.WAKE_DETECTED

    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isWake) 1.2f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isWake) 300 else 2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isWake) 200 else 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .scale(scale)
    ) {
        // 外圈光晕
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    if (isWake) Color(0x334CAF50) else Color(0x1A2196F3)
                )
                .alpha(alpha)
        )

        // 内圈
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    if (isWake) Color(0xFF4CAF50) else Color(0xFF1565C0)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isWake) "我在！" else "小立管家",
                color = Color.White,
                fontSize = if (isWake) 28.sp else 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    AnimatedVisibility(
        visible = isWake,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Text(
            text = "✅ 唤醒词已检测到",
            color = Color(0xFF4CAF50),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun DownloadProgress(progress: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.8f)
    ) {
        Text(
            text = "正在下载语音模型...",
            color = Color.White,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF2196F3),
            trackColor = Color(0xFF1A2A3A),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${(progress * 100).toInt()}%  (约 46MB)",
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}
