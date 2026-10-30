package com.nfctool.app.core.update

/**
 * 三段式版本号。
 *
 * 只保留 `major.minor.patch` 三个数字：本工程的 tag 与 `versionName` 都是这个形式，
 * 预发布后缀（`-beta1`）、构建后缀（debug 包的 `-debug`）与 `+` 元数据在解析时丢弃。
 * 这样 debug 包（`1.1.0-debug`）与正式包（`1.1.0`）比较出的结果是一致的。
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {

        /**
         * 解析版本号；无法解析时返回 null。
         *
         * 接受的输入：`1.1.0`、`v1.1.0`、`V1.1.0`、`1.1`（补 0）、`1`（补 0）、
         * `1.1.0-debug`、`1.1.0-beta.1`、`1.1.0+20261020`。
         * 拒绝：空串、非数字段、超过三段（`1.2.3.4`）、负数。
         */
        fun parse(raw: String): AppVersion? {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            if (trimmed.isEmpty()) return null

            // 后缀（- 之后）与元数据（+ 之后）不参与比较
            val core = trimmed.takeWhile { it != '-' && it != '+' }
            if (core.isEmpty()) return null

            val parts = core.split('.')
            if (parts.size > 3) return null

            val numbers = ArrayList<Int>(3)
            for (part in parts) {
                // 允许 "01" 这类写法，但拒绝空段与负号
                val value = part.toIntOrNull() ?: return null
                if (value < 0) return null
                numbers.add(value)
            }
            return AppVersion(
                major = numbers[0],
                minor = numbers.getOrElse(1) { 0 },
                patch = numbers.getOrElse(2) { 0 },
            )
        }
    }
}
