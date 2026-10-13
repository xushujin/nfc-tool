import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.util.Properties

plugins {
    // AGP 9.x 内置 Kotlin 支持：不再应用 org.jetbrains.kotlin.android 插件。
    // 内置 Kotlin 版本由 AGP 决定（9.4.1 → KGP 2.2.10），见 gradle/libs.versions.toml 注释。
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// 签名：两条通道
//   1) 本地：项目根目录 keystore.properties（不入库，见 keystore.properties.example）
//   2) CI  ：环境变量 NFC_TOOL_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD
// 两通道都没有时，release 变体不会被静默签成 debug 包，而是直接失败（见文末守卫）。
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

val releaseStoreFilePath = signingValue("storeFile", "NFC_TOOL_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "NFC_TOOL_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "NFC_TOOL_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "NFC_TOOL_KEY_PASSWORD")

val releaseKeystoreFile = releaseStoreFilePath?.let { path ->
    val candidates = listOf(rootProject.file(path), file(path))
    candidates.firstOrNull { it.isFile }
}

val hasReleaseSigning = releaseKeystoreFile != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "com.nfctool.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nfctool.app"
        // minSdk 26：VibrationEffect 等 API 从 26 起可用，取 26 可避免为 24/25 写已废弃的兼容分支
        minSdk = 26
        targetSdk = 37
        // 1.0.0：首个发布版本。
        // 发版约束：versionCode 只能升不能降；versionName 必须与所打的 tag 一致
        // （CI 的 release 任务会校验 tag == "v" + versionName，不一致直接失败）。
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module",
            )
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        // 被刻意关掉的检查连同理由写在 app/lint.xml 里，不在这里无差别屏蔽
        lintConfig = file("lint.xml")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnit()
                // 失败时输出完整堆栈：CI 上只看一行断言消息无法定位问题
                it.testLogging {
                    events("failed", "skipped")
                    exceptionFormat = TestExceptionFormat.FULL
                    showStackTraces = true
                    showExceptions = true
                    showCauses = true
                }
                // 受限网络下的离线逃生口：预先下载 Robolectric 的 android-all 运行库后，
                // 用 -ProbolectricDependencyDir=<目录> 指定，测试 JVM 就不再联网拉取。
                // 不传该参数时保持默认行为（首次运行自动下载）。
                val offlineDir = providers.gradleProperty("robolectricDependencyDir").orNull
                if (!offlineDir.isNullOrBlank()) {
                    it.systemProperty("robolectric.offline", "true")
                    it.systemProperty("robolectric.dependency.dir", offlineDir)
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        // jvmTarget 不显式设置：内置 Kotlin 下其默认值跟随 android.compileOptions.targetCompatibility
        allWarningsAsErrors.set(true)
        // 显式开严格模式，避免把「以后再说」的警告留在构建日志里
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// ---------------------------------------------------------------------------
// release 守卫：没有签名材料时明确失败，不产出装不上去的包
// ---------------------------------------------------------------------------
val guardedReleaseTasks = setOf(
    "assembleRelease",
    "bundleRelease",
    "installRelease",
    "packageRelease",
    "packageReleaseBundle",
)

tasks.matching { it.name in guardedReleaseTasks }.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                """
                |release 构建缺少签名材料，已中止。
                |
                |本地构建：在项目根目录创建 keystore.properties（参考 keystore.properties.example），
                |          需要 storeFile / storePassword / keyAlias / keyPassword 四项。
                |CI 构建：注入 NFC_TOOL_STORE_FILE / NFC_TOOL_STORE_PASSWORD /
                |          NFC_TOOL_KEY_ALIAS / NFC_TOOL_KEY_PASSWORD 四个环境变量。
                |
                |当前状态：keystore.properties 存在=${keystorePropertiesFile.isFile}，
                |          keystore 文件可用=${releaseKeystoreFile != null}
                """.trimMargin(),
            )
        }
    }
}
