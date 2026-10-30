package com.nfctool.app.data.update

import com.nfctool.app.core.bytes.Hex
import com.nfctool.app.core.update.ReleaseAsset
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 下载结果。失败原因直接展示，不做二次包装。 */
sealed interface DownloadResult {

    class Success(val file: File) : DownloadResult

    class Failed(val reason: String) : DownloadResult
}

/**
 * 下载 Release 里的 APK。
 *
 * 三条校验，缺一不可：
 *  1. 大小与发布信息一致（`size` 字段）；
 *  2. 发布信息带 `digest` 时校验 SHA-256；
 *  3. 安装前由 [ApkInstaller] 校验包名与签名。
 *
 * 只写应用私有缓存目录，不碰外部存储，因此不需要任何存储权限。
 */
class ApkDownloader(private val cacheDir: File) {

    fun download(asset: ReleaseAsset, onProgress: (downloaded: Long, total: Long) -> Unit): DownloadResult {
        val directory = File(cacheDir, UPDATE_DIR)
        if (!directory.isDirectory && !directory.mkdirs()) {
            return DownloadResult.Failed("无法创建下载目录：${directory.absolutePath}")
        }
        val target = File(directory, safeFileName(asset.name))
        // 同一时间只保留一个待安装包，避免缓存目录越堆越大
        directory.listFiles()?.forEach { existing ->
            if (existing.absolutePath != target.absolutePath) existing.delete()
        }

        val url = try {
            URL(asset.downloadUrl)
        } catch (e: IOException) {
            return DownloadResult.Failed("下载地址不合法：${e.message ?: asset.downloadUrl}")
        }

        return try {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    return DownloadResult.Failed("下载失败：HTTP $code")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.sizeBytes
                var downloaded = 0L
                var lastReported = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastReported >= PROGRESS_STEP_BYTES || downloaded == total) {
                                lastReported = downloaded
                                onProgress(downloaded, total)
                            }
                        }
                    }
                }
                onProgress(downloaded, total)

                if (asset.sizeBytes > 0 && target.length() != asset.sizeBytes) {
                    target.delete()
                    return DownloadResult.Failed(
                        "下载的文件大小与发布信息不符（期望 ${asset.sizeBytes} 字节，" +
                            "实际 ${target.length()} 字节），已删除文件",
                    )
                }
                val expected = asset.sha256
                if (expected != null) {
                    val actual = sha256Hex(target)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        target.delete()
                        return DownloadResult.Failed("下载内容校验失败（SHA-256 不匹配），已删除文件")
                    }
                }
                DownloadResult.Success(target)
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            target.delete()
            DownloadResult.Failed("下载中断：${e.message ?: e.javaClass.simpleName}。请检查网络后重试。")
        }
    }

    /** 文件名来自远端，必须过滤路径分隔符，防止写出缓存目录。 */
    private fun safeFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val sanitized = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (sanitized.endsWith(".apk", ignoreCase = true) && sanitized.length > 4) {
            sanitized
        } else {
            "update.apk"
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return Hex.encode(digest.digest())
    }

    private companion object {
        const val UPDATE_DIR = "updates"
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val BUFFER_BYTES = 16 * 1024
        const val PROGRESS_STEP_BYTES = 64 * 1024L
    }
}
