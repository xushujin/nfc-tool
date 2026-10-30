package com.nfctool.app.core.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 更新判定：是否提示、挑哪个包、地址是否可信。 */
class UpdatePolicyTest {

    private val current = requireNotNull(AppVersion.parse("1.0.1"))

    @Test
    fun `有新版本时提示`() {
        assertThat(UpdatePolicy.shouldOffer(current, requireNotNull(AppVersion.parse("1.1.0")), null)).isTrue()
        assertThat(UpdatePolicy.shouldOffer(current, requireNotNull(AppVersion.parse("2.0.0")), null)).isTrue()
    }

    @Test
    fun `同版本或更旧版本不提示`() {
        assertThat(UpdatePolicy.shouldOffer(current, current, null)).isFalse()
        assertThat(UpdatePolicy.shouldOffer(current, requireNotNull(AppVersion.parse("1.0.0")), null)).isFalse()
    }

    @Test
    fun `已跳过的版本不提示`() {
        val latest = requireNotNull(AppVersion.parse("1.1.0"))
        assertThat(UpdatePolicy.shouldOffer(current, latest, latest)).isFalse()
        // 跳过的是旧版本，不影响新版本提示
        assertThat(
            UpdatePolicy.shouldOffer(
                current,
                requireNotNull(AppVersion.parse("1.2.0")),
                requireNotNull(AppVersion.parse("1.1.0")),
            ),
        ).isTrue()
    }

    @Test
    fun `优先挑选 nfc-tool 命名的 APK`() {
        val assets = listOf(
            ReleaseAsset("notes.txt", "https://github.com/o/r/releases/download/v1.1.0/notes.txt", 10),
            ReleaseAsset("app-release.apk", "https://github.com/o/r/releases/download/v1.1.0/app-release.apk", 20),
            ReleaseAsset("nfc-tool-v1.1.0.apk", "https://github.com/o/r/releases/download/v1.1.0/nfc-tool-v1.1.0.apk", 30),
        )
        assertThat(UpdatePolicy.pickApk(assets)?.name).isEqualTo("nfc-tool-v1.1.0.apk")
    }

    @Test
    fun `没有 nfc-tool 命名时退回第一个 APK`() {
        val assets = listOf(
            ReleaseAsset("notes.txt", "https://github.com/o/r/releases/download/v1.1.0/notes.txt", 10),
            ReleaseAsset("app-release.apk", "https://github.com/o/r/releases/download/v1.1.0/app-release.apk", 20),
        )
        assertThat(UpdatePolicy.pickApk(assets)?.name).isEqualTo("app-release.apk")
    }

    @Test
    fun `跳过不可信地址与非 APK 附件`() {
        val assets = listOf(
            ReleaseAsset("evil.apk", "https://example.com/evil.apk", 10),
            ReleaseAsset("plain.apk", "http://github.com/o/r/releases/download/v1.1.0/plain.apk", 10),
            ReleaseAsset("readme.md", "https://github.com/o/r/releases/download/v1.1.0/readme.md", 10),
        )
        assertThat(UpdatePolicy.pickApk(assets)).isNull()
        assertThat(UpdatePolicy.pickApk(emptyList())).isNull()
    }

    @Test
    fun `只接受 GitHub 的 HTTPS 域名`() {
        assertThat(UpdatePolicy.isTrustedDownloadUrl("https://github.com/o/r/releases/download/v1/a.apk")).isTrue()
        assertThat(
            UpdatePolicy.isTrustedDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset/x"),
        ).isTrue()
        assertThat(UpdatePolicy.isTrustedDownloadUrl("http://github.com/o/r/a.apk")).isFalse()
        assertThat(UpdatePolicy.isTrustedDownloadUrl("https://example.com/a.apk")).isFalse()
        assertThat(UpdatePolicy.isTrustedDownloadUrl("https://github.com.evil.com/a.apk")).isFalse()
        assertThat(UpdatePolicy.isTrustedDownloadUrl("不是地址")).isFalse()
    }

    @Test
    fun `解析 digest 中的 sha256`() {
        val hex = "a".repeat(64)
        assertThat(UpdatePolicy.sha256FromDigest("sha256:$hex")).isEqualTo(hex)
        assertThat(UpdatePolicy.sha256FromDigest("SHA256:${hex.uppercase()}")).isEqualTo(hex)
        assertThat(UpdatePolicy.sha256FromDigest("sha512:$hex")).isNull()
        assertThat(UpdatePolicy.sha256FromDigest("sha256:abc")).isNull()
        assertThat(UpdatePolicy.sha256FromDigest("sha256:${"z".repeat(64)}")).isNull()
        assertThat(UpdatePolicy.sha256FromDigest(null)).isNull()
        assertThat(UpdatePolicy.sha256FromDigest("")).isNull()
    }
}
