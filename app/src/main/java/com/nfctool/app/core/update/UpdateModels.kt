package com.nfctool.app.core.update

/**
 * 一个 Release 附件。
 *
 * @param sha256 由 GitHub API 的 `digest` 字段解析而来（形如 `sha256:ab12…`）。
 *   该字段是可选返回的，拿不到时为 null —— 此时仍有「包名 + 签名一致性」校验兜底。
 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String? = null,
)

/** 从 GitHub Releases 解析出的一次发布信息。 */
data class ReleaseInfo(
    val tagName: String,
    val version: AppVersion,
    val title: String,
    val notes: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>,
)

/** 一次检查的结果。失败原因直接给用户看，不做二次包装。 */
sealed interface UpdateCheck {

    class Available(val info: ReleaseInfo, val asset: ReleaseAsset) : UpdateCheck

    /** 已是最新，或该版本已被用户跳过。 */
    class UpToDate(val latest: AppVersion?, val skipped: Boolean = false) : UpdateCheck

    class Failed(val reason: String) : UpdateCheck
}

/** 拉取 Release 的结果，与 [UpdateCheck] 分开：这一层只管网络与解析。 */
sealed interface ReleaseFetch {

    class Success(val info: ReleaseInfo) : ReleaseFetch

    class Failed(val reason: String) : ReleaseFetch
}
