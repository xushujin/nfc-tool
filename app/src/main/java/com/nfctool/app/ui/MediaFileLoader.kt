package com.nfctool.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * 从内容 URI 读取用户选中的音视频文件。
 *
 * 读取上限 [MAX_BYTES] 是刻意设的：标准 NDEF 标签可用容量只有 144~888 字节，
 * 把几十 MB 的文件读进内存既没意义也可能触发 OOM。
 */
object MediaFileLoader {

    /** 单次读取上限：1 MiB。超过即拒绝，并提示改用网络地址。 */
    const val MAX_BYTES = 1024 * 1024

    class LoadedFile(
        val displayName: String,
        val mimeType: String,
        val bytes: ByteArray,
    )

    sealed interface Result {
        class Success(val file: LoadedFile) : Result
        class Failure(val reason: String) : Result
    }

    fun load(context: Context, uri: Uri): Result {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri)
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val size = querySize(context, uri)
        if (size != null && size > MAX_BYTES) {
            return Result.Failure(
                "文件大小 ${size} 字节，超过本应用 ${MAX_BYTES} 字节的读取上限。" +
                    "标准 NDEF 标签最多只能容纳几百字节，音频/视频请改用网络地址写入。",
            )
        }
        val bytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: java.io.IOException) {
            return Result.Failure("读取文件失败：${e.message ?: "IO 错误"}")
        } ?: return Result.Failure("无法打开所选文件。")

        if (bytes.size > MAX_BYTES) {
            return Result.Failure(
                "文件实际大小 ${bytes.size} 字节，超过 ${MAX_BYTES} 字节上限。",
            )
        }
        return Result.Success(LoadedFile(displayName, mimeType, bytes))
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        return uri.lastPathSegment ?: "未命名文件"
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
                }
            }
        return null
    }
}
