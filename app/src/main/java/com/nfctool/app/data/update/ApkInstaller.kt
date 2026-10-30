package com.nfctool.app.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nfctool.app.core.bytes.Hex
import java.io.File
import java.security.MessageDigest

/** 拉起安装器的结果。 */
sealed interface InstallResult {

    /** 系统安装界面已打开，后续由用户在系统界面确认。 */
    data object Launched : InstallResult

    /** 缺少「安装未知来源应用」授权，已跳转到对应的系统设置页。 */
    data object NeedsPermission : InstallResult

    class Failed(val reason: String) : InstallResult
}

/**
 * 拉起系统安装器。
 *
 * 安装前做两道校验，且都在本机完成：
 *  1. APK 的**包名**必须与本应用一致；
 *  2. APK 的**签名证书**必须与本应用当前签名一致。
 *
 * 第 2 条是真正的安全边界：即使下载域名被劫持、或 GitHub 账号被投毒，
 * 签名对不上的包也装不上（Android 本身也会拒绝覆盖安装，这里提前给出可读原因）。
 *
 * 应用不做静默安装 —— Android 也不允许普通应用这么做。每次安装都要用户在系统界面点确认。
 */
class ApkInstaller(private val context: Context) {

    fun install(apk: File): InstallResult {
        val problem = verifyApk(apk)
        if (problem != null) return InstallResult.Failed(problem)

        if (!context.packageManager.canRequestPackageInstalls()) {
            openUnknownSourcesSettings()
            return InstallResult.NeedsPermission
        }

        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", apk)
        } catch (e: IllegalArgumentException) {
            return InstallResult.Failed("无法为安装包生成共享路径：${e.message ?: "FileProvider 未配置"}")
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            InstallResult.Launched
        } catch (_: ActivityNotFoundException) {
            InstallResult.Failed("系统没有可用的安装器，请到文件管理器手动打开下载的安装包")
        }
    }

    /** 返回拒绝安装的原因；通过校验时返回 null。 */
    private fun verifyApk(apk: File): String? {
        if (!apk.isFile || apk.length() == 0L) return "安装包不存在或为空"

        val manager = context.packageManager
        val archive = archiveInfo(manager, apk) ?: return "下载的文件不是有效的 APK"
        if (archive.packageName != context.packageName) {
            return "下载的安装包包名（${archive.packageName}）与本应用不一致，已拒绝安装"
        }

        val expected = certificatesOf(manager, context.packageName)
        if (expected.isEmpty()) return "无法读取本应用签名，已拒绝安装"
        if (certificatesOf(archive) != expected) {
            return "下载的安装包签名与本应用不一致，已拒绝安装"
        }
        return null
    }

    // GET_SIGNING_CERTIFICATES / GET_SIGNATURES 都是 int 常量，会被内联进字节码；
    // 低版本设备上这两个分支不会被执行，因此 InlinedApi 与 DEPRECATION 一并抑制。
    @Suppress("DEPRECATION", "InlinedApi")
    private fun archiveInfo(manager: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            manager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        }

    @Suppress("DEPRECATION", "InlinedApi")
    private fun certificatesOf(manager: PackageManager, packageName: String): Set<String> {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        return certificatesOf(info)
    }

    @Suppress("DEPRECATION", "InlinedApi")
    private fun certificatesOf(info: PackageInfo): Set<String> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            signing.apkContentsSigners
        } else {
            info.signatures ?: return emptySet()
        }
        return signatures.map { signature -> sha256Hex(signature.toByteArray()) }.toSet()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        Hex.encode(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** 打开「安装未知应用」授权页；部分 ROM 没有该页面时退回应用详情页。 */
    private fun openUnknownSourcesSettings() {
        val packageUri = Uri.parse("package:${context.packageName}")
        val direct = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
            return
        } catch (_: ActivityNotFoundException) {
            // 该 ROM 没有独立授权页，继续尝试应用详情页
        }
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(fallback)
        } catch (_: ActivityNotFoundException) {
            // 两个页面都打不开时只能让用户自己去系统设置里找，界面提示已经给出说明
        }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        /** 与 AndroidManifest 中 FileProvider 的 authorities 后缀保持一致。 */
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    }
}
