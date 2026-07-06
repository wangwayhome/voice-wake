package com.leelen.voicewake.wake

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Vosk 模型下载管理器
 *
 * 首次启动时从 alphacephei.com 下载 vosk-model-small-cn-0.22（~46MB）
 * 解压到应用内部存储，后续启动直接使用
 */
object ModelManager {

    private const val TAG = "ModelManager"
    private const val MODEL_NAME = "vosk-model-small-cn-0.22"
    private const val MODEL_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"

    /**
     * 获取模型路径，如果不存在则下载并解压
     * @param onProgress 下载进度回调 (downloaded, total)，total 为 -1 时表示未知
     */
    suspend fun getModelPath(
        context: Context,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, MODEL_NAME)

        if (modelDir.exists() && File(modelDir, "conf").exists()) {
            Log.d(TAG, "模型已存在: ${modelDir.absolutePath}")
            return@withContext modelDir.absolutePath
        }

        // 下载
        val zipFile = File(context.cacheDir, "$MODEL_NAME.zip")
        downloadModel(zipFile, onProgress)

        // 解压
        Log.d(TAG, "解压模型...")
        unzipModel(zipFile, context.filesDir)
        zipFile.delete()

        Log.d(TAG, "模型就绪: ${modelDir.absolutePath}")
        modelDir.absolutePath
    }

    fun isModelReady(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_NAME)
        return modelDir.exists() && File(modelDir, "conf").exists()
    }

    private fun downloadModel(target: File, onProgress: (Long, Long) -> Unit) {
        Log.d(TAG, "下载模型: $MODEL_URL")

        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(MODEL_URL).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw RuntimeException("模型下载失败: HTTP ${response.code}")
        }

        val body = response.body ?: throw RuntimeException("模型下载失败: 空响应")
        val totalBytes = body.contentLength() // -1 if unknown
        var downloadedBytes = 0L

        body.byteStream().use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    onProgress(downloadedBytes, totalBytes)
                }
            }
        }

        Log.d(TAG, "下载完成: $downloadedBytes bytes")
    }

    private fun unzipModel(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (zis.read(buffer).also { len = it } != -1) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
