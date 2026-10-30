package com.nfctool.app.data.update

import com.nfctool.app.core.update.AppVersion
import com.nfctool.app.core.update.ReleaseAsset
import com.nfctool.app.core.update.ReleaseFetch
import com.nfctool.app.core.update.ReleaseInfo
import com.nfctool.app.core.update.UpdatePolicy
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

/**
 * 从 GitHub Releases 读取最新版本。
 *
 * 只用 `HttpURLConnection` + 平台自带的 `org.json`，不引第三方 HTTP / JSON 库 ——
 * 与「第三方库只有 AndroidX」的约束一致，也为更新功能单独引一个网络栈不划算。
 *
 * 匿名调用 `releases/latest`，不携带任何用户标识；GitHub 对同一出口 IP 限流 60 次/小时，
 * 本应用只在用户主动点「检查更新」时请求一次，正常使用远达不到上限。
 */
class GitHubReleaseClient(
    private val repo: String,
    private val userAgent: String,
) {

    fun fetchLatest(): ReleaseFetch {
        if (repo.isBlank()) return ReleaseFetch.Failed("未配置更新源，无法检查更新")

        val url = try {
            URL("https://api.github.com/repos/$repo/releases/latest")
        } catch (e: MalformedURLException) {
            return ReleaseFetch.Failed("更新源地址不合法：${e.message ?: repo}")
        }

        return try {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    ReleaseFetch.Failed(describeHttpFailure(code, body))
                } else {
                    ReleaseFetch.Success(parse(body))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            ReleaseFetch.Failed(
                "无法连接 GitHub：${e.message ?: e.javaClass.simpleName}。请检查网络后重试。",
            )
        } catch (e: JSONException) {
            ReleaseFetch.Failed("更新信息格式无法解析：${e.message ?: "JSON 结构不符"}")
        } catch (e: IllegalArgumentException) {
            ReleaseFetch.Failed("更新信息不完整：${e.message ?: "缺少版本号"}")
        }
    }

    private fun parse(body: String): ReleaseInfo {
        val root = JSONObject(body)
        val tag = root.optString("tag_name").trim()
        val version = AppVersion.parse(tag)
            ?: throw IllegalArgumentException("tag「$tag」不是可比较的版本号")

        val array = root.optJSONArray("assets") ?: JSONArray()
        val assets = ArrayList<ReleaseAsset>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val downloadUrl = item.optString("browser_download_url").trim()
            if (name.isEmpty() || downloadUrl.isEmpty()) continue
            assets.add(
                ReleaseAsset(
                    name = name,
                    downloadUrl = downloadUrl,
                    sizeBytes = item.optLong("size", 0L),
                    sha256 = UpdatePolicy.sha256FromDigest(item.optString("digest")),
                ),
            )
        }

        return ReleaseInfo(
            tagName = tag,
            version = version,
            title = root.optString("name").ifBlank { tag },
            notes = root.optString("body"),
            htmlUrl = root.optString("html_url"),
            assets = assets,
        )
    }

    /** 把 HTTP 状态码翻译成用户能据此行动的说明，而不是只丢一个数字。 */
    private fun describeHttpFailure(code: Int, body: String): String = when (code) {
        403 -> "GitHub 拒绝了本次请求（HTTP 403），可能是访问频率超限，请稍后再试。"
        404 -> "找不到发布信息（HTTP 404）：仓库 $repo 还没有发布过 Release，或仓库地址不正确。"
        in 500..599 -> "GitHub 服务暂时不可用（HTTP $code），请稍后再试。"
        else -> "检查更新失败：HTTP $code" + body.trim().take(120).let { if (it.isEmpty()) "" else "（$it）" }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 15_000
    }
}
