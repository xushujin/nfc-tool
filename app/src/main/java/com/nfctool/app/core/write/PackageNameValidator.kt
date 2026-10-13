package com.nfctool.app.core.write

/**
 * Android 应用包名校验。
 *
 * 用于「写入 Android 应用记录（AAR）」场景：包名写错的话，标签读出来只会
 * 静静地什么都不发生，用户完全看不出问题，所以必须在写入前拦住。
 *
 * 规则比 Java 的标识符规则更严，也更贴合 Android 的实际情况：
 * - 只允许 ASCII 字母、数字、下划线和点（Java 的 `isJavaIdentifierPart`
 *   会放过 `$` 与中文等 Unicode 字母，而 Android 包名必须是 ASCII）；
 * - 每段必须以**字母**开头（不允许 `_`/`$`/数字 开头）；
 * - 至少两段。
 *
 * 这里**不**校验应用是否已安装 —— 那是运行时才能知道的事，且未安装也是合法输入。
 */
object PackageNameValidator {

    private const val MAX_BYTES = 255

    /** 返回问题描述；null 表示通过。 */
    fun problem(packageName: String): String? {
        if (packageName.isBlank()) return "包名不能为空"
        if (packageName.length > MAX_BYTES) return "包名过长，最多 $MAX_BYTES 字节"
        if (packageName.any { it.isWhitespace() }) return "包名不能包含空格"

        val segments = packageName.split('.')
        if (segments.size < 2) return "包名至少需要两段，例如 com.example.app"

        for (segment in segments) {
            if (segment.isEmpty()) return "包名中存在空段（连续的「.」），请检查"
            if (!segment[0].isAsciiLetter()) {
                return "包名段「$segment」必须以字母开头（不允许数字、下划线或其它字符）"
            }
            for (ch in segment) {
                if (!(ch.isAsciiLetterOrDigit() || ch == '_')) {
                    return "包名包含非法字符「$ch」，只允许 ASCII 字母、数字、下划线和点"
                }
            }
        }
        return null
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private fun Char.isAsciiLetterOrDigit(): Boolean = isAsciiLetter() || this in '0'..'9'
}
