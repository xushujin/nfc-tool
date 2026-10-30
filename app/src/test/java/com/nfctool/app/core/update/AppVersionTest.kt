package com.nfctool.app.core.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 版本号解析与比较。更新提示的「该不该弹」完全依赖这里，因此边界要钉死。 */
class AppVersionTest {

    @Test
    fun `解析标准三段式`() {
        assertThat(AppVersion.parse("1.1.0")).isEqualTo(AppVersion(1, 1, 0))
        assertThat(AppVersion.parse("10.20.30")).isEqualTo(AppVersion(10, 20, 30))
    }

    @Test
    fun `忽略前导 v 与首尾空白`() {
        assertThat(AppVersion.parse("v1.1.0")).isEqualTo(AppVersion(1, 1, 0))
        assertThat(AppVersion.parse("V2.0.0")).isEqualTo(AppVersion(2, 0, 0))
        assertThat(AppVersion.parse("  1.1.0  ")).isEqualTo(AppVersion(1, 1, 0))
    }

    @Test
    fun `缺省段补零`() {
        assertThat(AppVersion.parse("1.1")).isEqualTo(AppVersion(1, 1, 0))
        assertThat(AppVersion.parse("1")).isEqualTo(AppVersion(1, 0, 0))
    }

    @Test
    fun `丢弃预发布与构建后缀`() {
        // debug 包的 versionName 带 -debug，必须与正式包解析成同一个版本
        assertThat(AppVersion.parse("1.1.0-debug")).isEqualTo(AppVersion(1, 1, 0))
        assertThat(AppVersion.parse("1.1.0-beta.1")).isEqualTo(AppVersion(1, 1, 0))
        assertThat(AppVersion.parse("1.1.0+20261020")).isEqualTo(AppVersion(1, 1, 0))
    }

    @Test
    fun `非法输入返回 null`() {
        assertThat(AppVersion.parse("")).isNull()
        assertThat(AppVersion.parse("v")).isNull()
        assertThat(AppVersion.parse("abc")).isNull()
        assertThat(AppVersion.parse("1.x.0")).isNull()
        assertThat(AppVersion.parse("1..0")).isNull()
        assertThat(AppVersion.parse("1.2.3.4")).isNull()
        assertThat(AppVersion.parse("-1.0.0")).isNull()
    }

    @Test
    fun `按主次修订顺序比较`() {
        val ordered = listOf("1.0.0", "1.0.1", "1.1.0", "1.10.0", "2.0.0")
            .map { requireNotNull(AppVersion.parse(it)) }

        ordered.zipWithNext().forEach { (lower, higher) ->
            assertThat(lower < higher).isTrue()
            assertThat(higher > lower).isTrue()
        }
        assertThat(AppVersion.parse("1.1.0")).isEqualTo(AppVersion.parse("1.1.0"))
    }

    @Test
    fun `toString 与解析互逆`() {
        assertThat(AppVersion(1, 1, 0).toString()).isEqualTo("1.1.0")
        assertThat(AppVersion.parse(AppVersion(2, 3, 4).toString())).isEqualTo(AppVersion(2, 3, 4))
    }
}
