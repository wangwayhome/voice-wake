package com.leelen.voicewake.wake

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * 基于 Vosk 的唤醒词检测器
 *
 * 使用 vosk-model-small-cn-0.22 中文模型，
 * 持续识别音频流，检测到 "小立管家" 时触发回调
 */
class WakeWordDetector(private val context: Context) {

    companion object {
        private const val TAG = "WakeWordDetector"
        // 容错：常见同音误识别
        private val WAKE_WORD_VARIANTS = listOf(
            "小立管家",
            "小李管家",
            "小力管家",
            "小丽管家",
            "小立管理",
            "小里管家",
            "小理管家",
            "小林管家",
        )
        private val WAKE_WORD_GRAMMAR_PHRASES = listOf(
            "小立 管家",
            "小李 管家",
            "小力 管家",
            "小丽 管家",
            "小立 管理",
            "小里 管家",
            "小理 管家",
            "小林 管家",
            "小 立 管 家",
            "小 李 管 家",
            "小 力 管 家",
            "小 丽 管 家",
            "小 里 管 家",
            "小 理 管 家",
            "小 林 管 家",
        )
        private val IGNORED_CHARS = Regex("[\\s，。！？、,.!?]+")
        private val WAKE_WORD_GRAMMAR =
            WAKE_WORD_GRAMMAR_PHRASES.joinToString(
                prefix = "[",
                postfix = ", \"[unk]\"]",
                separator = ", "
            ) { JSONObject.quote(it) }
    }

    private var model: Model? = null
    @Volatile
    private var recognizer: Recognizer? = null
    private var currentModelPath: String? = null
    private var lastRecognizedText = ""

    /** 初始化模型，必须在子线程调用 */
    fun init(modelPath: String) {
        if (recognizer != null && currentModelPath == modelPath) {
            Log.d(TAG, "模型已加载，跳过重复初始化: $modelPath")
            return
        }

        release()
        Log.d(TAG, "加载模型: $modelPath")
        val loadedModel = Model(modelPath)
        val loadedRecognizer = Recognizer(loadedModel, 16000f, WAKE_WORD_GRAMMAR)
        model = loadedModel
        recognizer = loadedRecognizer
        currentModelPath = modelPath
        Log.d(TAG, "模型加载完成")
    }

    /**
     * 处理一帧音频数据
     * @return true 表示检测到唤醒词
     */
    fun process(pcm: ShortArray): Boolean {
        val rec = recognizer ?: return false
        if (pcm.isEmpty()) return false

        return try {
            val hasFinalResult = rec.acceptWaveForm(pcm, pcm.size)
            val resultJson = if (hasFinalResult) rec.result else rec.partialResult
            val text = parseRecognizedText(resultJson, hasFinalResult)

            if (text.isNotBlank() && text != lastRecognizedText) {
                lastRecognizedText = text
                Log.d(TAG, if (hasFinalResult) "识别结果: $text" else "识别中: $text")
            }

            val detected = containsWakeWord(text)
            if (detected) {
                Log.d(TAG, "匹配到唤醒词，原始识别文本: $text")
            }
            detected
        } catch (e: Exception) {
            Log.e(TAG, "处理音频帧失败: samples=${pcm.size}", e)
            false
        }
    }

    /** 重置识别状态（唤醒后调用，避免重复触发） */
    fun reset() {
        lastRecognizedText = ""
        recognizer?.reset()
    }

    /** 释放资源 */
    fun release() {
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
        currentModelPath = null
    }

    private fun parseRecognizedText(resultJson: String?, hasFinalResult: Boolean): String {
        if (resultJson.isNullOrEmpty()) return ""
        val json = JSONObject(resultJson)
        val primaryKey = if (hasFinalResult) "text" else "partial"
        val fallbackKey = if (hasFinalResult) "partial" else "text"
        return json.optString(primaryKey, json.optString(fallbackKey, ""))
    }

    private fun containsWakeWord(text: String): Boolean {
        val normalizedText = normalize(text)
        if (normalizedText.isBlank()) return false
        return WAKE_WORD_VARIANTS.any { normalizedText.contains(normalize(it)) }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(IGNORED_CHARS, "")
}
