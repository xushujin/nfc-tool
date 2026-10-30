package com.nfctool.app.data.update

import com.nfctool.app.core.update.AppVersion
import com.nfctool.app.core.update.ReleaseAsset
import com.nfctool.app.core.update.ReleaseFetch
import com.nfctool.app.core.update.ReleaseInfo
import com.nfctool.app.core.update.UpdateCheck
import com.nfctool.app.core.update.UpdatePolicy
import com.nfctool.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 在线更新的编排层：检查 → 下载 → 安装。
 *
 * 所有阻塞调用（HTTP、文件 IO、PackageManager）都在这层切到 [Dispatchers.IO]，
 * ViewModel 只管状态流转，界面只管渲染。
 */
class UpdateRepository(
    private val client: GitHubReleaseClient,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun check(current: AppVersion): UpdateCheck = withContext(Dispatchers.IO) {
        when (val fetched = client.fetchLatest()) {
            is ReleaseFetch.Failed -> UpdateCheck.Failed(fetched.reason)
            is ReleaseFetch.Success -> evaluate(fetched.info, current)
        }
    }

    private fun evaluate(info: ReleaseInfo, current: AppVersion): UpdateCheck {
        val skipped = AppVersion.parse(settingsRepository.settings.value.skippedUpdateVersion)
        if (!UpdatePolicy.shouldOffer(current, info.version, skipped)) {
            return UpdateCheck.UpToDate(info.version, skipped = skipped == info.version)
        }
        val asset = UpdatePolicy.pickApk(info.assets)
            ?: return UpdateCheck.Failed("${info.version} 的发布里没有可用的 APK 附件")
        return UpdateCheck.Available(info, asset)
    }

    suspend fun download(asset: ReleaseAsset, onProgress: (Long, Long) -> Unit): DownloadResult =
        withContext(Dispatchers.IO) { downloader.download(asset, onProgress) }

    suspend fun install(file: File): InstallResult =
        withContext(Dispatchers.IO) { installer.install(file) }

    /** 跳过某个版本：之后检查到该版本不再提示，可在设置里取消。 */
    suspend fun skip(version: AppVersion) =
        settingsRepository.setSkippedUpdateVersion(version.toString())

    suspend fun clearSkipped() = settingsRepository.setSkippedUpdateVersion("")
}
