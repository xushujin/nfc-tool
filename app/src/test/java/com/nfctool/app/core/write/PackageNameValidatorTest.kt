package com.nfctool.app.core.write

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class PackageNameValidatorTest {

    @Test
    fun `常见包名通过校验`() {
        for (name in listOf(
            "com.android.chrome",
            "com.tencent.mm",
            "com.nfctool.app",
            "com.example.app_debug",
            "a1.b2.c3",
        )) {
            assertAcceptable(name)
        }
    }

    @Test
    fun `空与超长被拒绝`() {
        assertProblem("", "不能为空")
        assertProblem("   ", "不能为空")
        assertProblem("a".repeat(256) + ".b", "最多 255 字节")
    }

    @Test
    fun `必须有至少两段`() {
        assertProblem("chrome", "至少需要两段")
        assertProblem("com.", "空段")
        assertProblem("com..app", "空段")
    }

    @Test
    fun `首段必须以字母开头`() {
        assertProblem("1com.example", "以字母开头")
        assertProblem("_com.example", "以字母开头")
    }

    @Test
    fun `不能含空格或非法字符`() {
        assertProblem("com example app", "空格")
        assertProblem("com.example/app", "非法字符")
        // 中文既不是 ASCII 字母、也不是合法段首，先被"段首"规则拦下，
        // 同样属于拒绝，这里断言被段首规则挡住即可
        assertProblem("com.例子", "必须以字母开头")
    }

    @Test
    fun `数字可以出现在非首段`() {
        assertAcceptable("com.a1b2")
        assertAcceptable("com.a_1")
    }

    private fun assertAcceptable(name: String) {
        assertWithMessage("包名 $name 应通过校验")
            .that(PackageNameValidator.problem(name))
            .isNull()
    }

    private fun assertProblem(name: String, fragment: String) {
        val problem = PackageNameValidator.problem(name)
        assertWithMessage("包名 $name 应被拒绝")
            .that(problem)
            .isNotNull()
        assertThat(problem).contains(fragment)
    }
}
