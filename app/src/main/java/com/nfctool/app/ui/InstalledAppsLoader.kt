package com.nfctool.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** 手机上可启动的一个应用。 */
class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?,
)

/**
 * 列出手机上可启动的应用，供「写入应用」时挑选包名。
 *
 * 用 `queryIntentActivities` + MAIN/LAUNCHER 过滤，配合 manifest 里的 `<queries>`：
 * 这是 Android 11+ 包可见性规则下的合规做法，不需要 `QUERY_ALL_PACKAGES`
 * （那是需要重点审查的高敏权限）。
 *
 * 只有启动图标的应用才会出现；纯服务、系统组件等没有图标的仍可在写入页手动输入包名。
 */
object InstalledAppsLoader {

    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            queryLauncherActivities(pm, intent)
        } catch (_: Throwable) {
            // 个别 ROM 的 PackageManager 会抛异常。列不出来就退化成手动输入，不影响主流程。
            emptyList()
        }
        return resolved
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                InstalledApp(
                    label = info.loadLabel(pm).toString().ifBlank { packageName },
                    packageName = packageName,
                    icon = loadIcon(pm, info),
                )
            }
            // 一个包可能有多个启动入口，只保留第一个
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun queryLauncherActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            queryLauncherActivitiesLegacy(pm, intent)
        }

    @Suppress("DEPRECATION")
    private fun queryLauncherActivitiesLegacy(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        pm.queryIntentActivities(intent, 0)

    /** 统一按小尺寸加载图标，避免把各应用的原图整张拉进内存。 */
    private fun loadIcon(pm: PackageManager, info: ResolveInfo): ImageBitmap? = try {
        info.loadIcon(pm)?.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }

    private const val ICON_SIZE_PX = 96
}
