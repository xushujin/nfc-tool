package com.nfctool.app.core.update

import java.net.URI

/**
 * 更新判定的纯逻辑。
 *
 * 全部是无副作用的函数，放在 core 层是为了能在 JVM 上直接单测 ——
 * 「该不该提示更新」「挑哪个附件」「这个下载地址可不可信」这三件事
 * 出错会直接影响用户装到什么东西，不该埋在界面或网络代码里。
 */
object UpdatePolicy {

    /**
     * 是否应当向用户提示新版本。
     *
     * 三个条件同时成立才提示：比当前版本新、不是用户已跳过的版本、版本号可比较。
     * 相等不提示（debug 包的 `1.1.0-debug` 解析后与 `1.1.0` 相等，因此不会自己提示自己）。
     */
    fun shouldOffer(current: AppVersion, latest: AppVersion, skipped: AppVersion?): Boolean =
        latest > current && latest != skipped

    /**
     * 从附件里挑出要下载的 APK。
     *
     * 只接受 `.apk` 且下载地址可信的附件；优先 `nfc-tool-*`（CI 的产物命名），
     * 否则退回到第一个合格的 APK。返回 null 表示这次发布没有可用安装包。
     */
    fun pickApk(assets: List<ReleaseAsset>): ReleaseAsset? {
        val candidates = assets.filter { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) && isTrustedDownloadUrl(asset.downloadUrl)
        }
        return candidates.firstOrNull { it.name.startsWith("nfc-tool", ignoreCase = true) }
            ?: candidates.firstOrNull()
    }

    /**
     * 下载地址是否可信。
     *
     * 只允许 HTTPS，且主机必须属于 GitHub 的发布域名。这条检查挡不住被投毒的
     * GitHub 账号，所以安装前还有一道「包名 + 签名必须与本应用一致」的校验。
     */
    fun isTrustedDownloadUrl(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return TRUSTED_DOWNLOAD_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * 解析 GitHub 的 `digest` 字段，返回小写十六进制 SHA-256；不是 sha256 或格式不对时返回 null。
     */
    fun sha256FromDigest(digest: String?): String? {
        val value = digest?.trim().orEmpty()
        if (!value.startsWith("sha256:", ignoreCase = true)) return null
        val hex = value.substringAfter(':').lowercase()
        val valid = hex.length == SHA256_HEX_LENGTH && hex.all { it in '0'..'9' || it in 'a'..'f' }
        return if (valid) hex else null
    }

    private const val SHA256_HEX_LENGTH = 64

    /**
     * 发布产物的下载域名。`browser_download_url` 在 github.com，
     * 实际字节由 objects.githubusercontent.com 提供（跳转后）。
     */
    private val TRUSTED_DOWNLOAD_HOSTS = listOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )
}
