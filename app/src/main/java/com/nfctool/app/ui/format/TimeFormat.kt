package com.nfctool.app.ui.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间展示格式。 */
object TimeFormat {

    /**
     * 格式化为「月-日 时:分」。
     *
     * SimpleDateFormat 每次调用时构造：一是成本可忽略（历史列表条目有限），
     * 二是 Locale 取的是当前值，用户在应用运行期间切换系统语言后能立刻生效。
     * 若把它缓存成静态字段，语言切换后不会更新（Lint 的 ConstantLocale 检查即为此）。
     */
    fun shortDateTime(millis: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}
