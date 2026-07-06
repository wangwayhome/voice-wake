package com.leelen.voicewake.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * 持续采集麦克风音频，16kHz 单声道 16bit PCM
 * 专为 Vosk 语音识别设计
 */
class AudioCapture {

    companion object {
        private const val TAG = "AudioCapture"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val AUDIO_SOURCES = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
    }

    private var audioRecord: AudioRecord? = null
    @Volatile
    var isCapturing = false
        private set

    /**
     * 开始采集，每次读取 [bufferSize] 个 sample 后回调
     */
    fun start(bufferSize: Int, onAudioData: (ShortArray) -> Unit) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            throw IllegalStateException("AudioRecord 不支持 ${SAMPLE_RATE}Hz 单声道 16bit PCM: $minBuf")
        }
        val bufSize = maxOf(minBuf, bufferSize * 2)

        var selectedSource = -1
        audioRecord = null
        for (source in AUDIO_SOURCES) {
            val candidate = AudioRecord(
                source,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufSize
            )
            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord = candidate
                selectedSource = source
                break
            }
            candidate.release()
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord 初始化失败，请检查麦克风权限")
        }

        isCapturing = true
        audioRecord?.startRecording()
        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw IllegalStateException("AudioRecord 未进入录音状态，请检查麦克风是否被占用")
        }

        Log.d(
            TAG,
            "开始采集: source=$selectedSource, minBuf=$minBuf, bufBytes=$bufSize, frameSamples=$bufferSize"
        )

        val buffer = ShortArray(bufferSize)
        while (isCapturing) {
            val read = audioRecord?.read(buffer, 0, bufferSize) ?: -1
            if (read > 0) {
                val data = if (read == bufferSize) buffer else buffer.copyOf(read)
                onAudioData(data)
            } else if (read < 0) {
                Log.w(TAG, "AudioRecord read 返回错误: $read")
            }
        }
    }

    fun stop() {
        isCapturing = false
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "停止 AudioRecord 失败", e)
        }
        audioRecord?.release()
        audioRecord = null
    }
}
