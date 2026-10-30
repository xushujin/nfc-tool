# NFC工具

一个用 Kotlin + Jetpack Compose 写的原生 Android NFC 工具，做三件事：

1. **复制卡片** —— 读一张卡，把内容写到另一张卡
2. **写 WiFi** —— 把 WiFi 凭据按 Wi-Fi Simple Configuration 规范写进标签，贴卡即可联网
3. **写音视频 / 网页** —— 把网页链接、音视频地址写进标签

界面为拟态（Neumorphism）风格，内置 5 套配色主题。

本项目以 [Apache License 2.0](LICENSE) 开源。

> 文档原则：本文所写内容与代码逐项对应。凡是**没有实际验证过**的部分，
> 都集中列在[「未验证项与已知限制」](#未验证项与已知限制)一节，不做模糊表述。

---

## 目录

- [功能与支持范围](#功能与支持范围)
- [技术栈](#技术栈)
- [关键设计决策](#关键设计决策)
- [工程结构](#工程结构)
- [环境要求](#环境要求)
- [构建](#构建)
- [签名](#签名)
- [发版流程](#发版流程)
- [测试与验证](#测试与验证)
- [外部依据](#外部依据)
- [权限清单](#权限清单)
- [数据与隐私](#数据与隐私)
- [未验证项与已知限制](#未验证项与已知限制)
- [许可](#许可)

---

## 功能与支持范围

| 能力 | 实现方式 | 说明 |
| --- | --- | --- |
| 读取标签 | `NfcAdapter.enableReaderMode` | 支持 NFC-A / B / F / V；读取 UID、技术类型、NDEF 记录、容量 |
| 复制 NDEF | 读取源卡 NDEF 记录 → 写入目标卡 | 适用于 NTAG 系列等标准标签；源卡未格式化时目标卡会先格式化 |
| 写入 WiFi | WSC（Wi-Fi Simple Configuration）MIME 记录 | 支持开放 / WPA-Personal / WPA2-Personal；企业级认证不支持；**可一键填入当前连接的 WiFi 名称** |
| 写入文本 | NDEF Text 记录 | 可选语言代码；超长文本在写入前拦截 |
| 写入图片 | MIME 记录内嵌文件字节 | 按后缀推断 MIME 类型；容量不足时明确报错，不截断 |
| 写入网页 | NDEF URI 记录 | 支持 `http` / `https` / `tel` / `mailto`，无协议头时自动补 `https://` |
| 写入音视频 | 网络地址：URI 记录；本地文件：MIME 记录 | 本地文件受标签容量限制，见下文 |
| 拉起指定应用 | Android 应用记录（外部类型 `android.com:pkg`） | 贴卡时唤起指定应用；包名写错会静默无反应，因此写入前强校验。包名可手动输入，也可从**已安装应用列表**里挑选 |
| MIFARE Classic | 逐扇区认证 + 整卡转储 + 数据块回写 | 需要常见默认密钥；密钥未知的扇区无法读取 |
| 读卡历史 | 本地 SQLite | 摘要 + 标签原文（用于「用历史记录再次写入新卡」），可在设置中关闭自动记录 |
| 主题 | 5 套拟态配色，全局生效 | 极光 / 沙丘 / 薄荷 / 石墨 / 午夜 |
| 应用内更新 | GitHub Releases API + 应用内下载安装 | 手动检查；校验大小、SHA-256 与签名；需允许安装未知来源应用 |

### 明确不支持

- **UID 级完全克隆**：MIFARE Classic 的厂商块（块 0，含 UID）出厂只读，任何应用都改不了。
  本应用会把 UID 读出来展示，但**不会**假装能写进去。
- **扇区尾块写入**：尾块含访问位与密钥，写错会让扇区永久不可读，一律跳过。
- **加密卡片**：银行卡、部分门禁卡、公交卡使用加密认证或安全芯片，读不到内容。
  本应用会明确报错，不会返回伪造的成功。
- **完整音视频文件**：标准 NDEF 标签可用容量通常只有 144~888 字节，装不下音视频。
  本地文件内嵌模式只适合极小的片段；容量不足时会明确报出「需要多少 / 可用多少」，不截断数据。
- **企业级 WiFi**：WPA-Enterprise 需要账号与证书，不支持写入。
- **静默安装**：Android 不允许普通应用静默安装 APK。应用内更新在下载完成后拉起系统安装器，
  必须由用户在系统界面点确认；未开启「安装未知应用」时会跳转到系统设置页。

---

## 技术栈

| 项目 | 取值 | 说明 |
| --- | --- | --- |
| 语言 | Kotlin 2.2.10 | 由 AGP 9.4.1 的**内置 Kotlin** 提供，不单独应用 `kotlin-android` 插件 |
| UI | Jetpack Compose（Compose BOM 2026.09.00，即 Compose 1.12.1）+ Material 3 | 拟态组件为自实现，Material 3 只用于 `Text` / `TextField` 等基础件的取色兜底 |
| 构建 | Android Gradle Plugin 9.4.1 / Gradle 9.8.1 | |
| 编译 / 目标 SDK | API 37 | |
| 最低系统 | Android 8.0（API 26） | 取 26 可避免为 24/25 写已废弃的兼容分支 |
| 存储 | 平台自带 SQLite（`SQLiteOpenHelper`） | 未使用 ORM |
| 依赖注入 | 无框架，手工装配 | 见 `data/AppContainer.kt` |
| 测试 | JUnit 4 + Truth + Robolectric 4.17 | Robolectric 固定在 API 34 |
| 网络 | `HttpURLConnection` + 平台 `org.json` | 只用于检查更新与下载 APK，未引入 OkHttp / Retrofit 等第三方网络库 |
| 第三方库 | **仅 AndroidX 官方库** | 未引入任何第三方 UI 库或 DI 框架 |

### 核心能力为自实现的部分

以下能力**没有使用现成库**，全部自己实现，代码位于 `app/src/main/java/com/nfctool/app/core/`：

| 模块 | 内容 |
| --- | --- |
| `ndef/NdefCodec` | NDEF 二进制编解码：记录头标志位（MB/ME/CF/SR/IL/TNF）、短/长长度字段、**分块记录合并** |
| `ndef/NdefRecords` | 文本记录（`T`）、URI 记录（`U`）、MIME 记录、外部类型记录、绝对 URI 记录、智能海报（`Sp`）、Android 应用记录 |
| `ndef/UriPrefix` | URI 前缀缩写表（36 项）与最长前缀压缩 |
| `ndef/NdefTlv` | NDEF TLV 解析（含 `0xFF` 扩展长度、NULL 填充、终止符） |
| `wsc/WscCodec` | WSC 属性 TLV 编解码（Credential 嵌套结构） |
| `mifare/*` | MIFARE Classic 扇区/块布局换算、厂商块与扇区尾块解析、转储文本编解码 |
| `media/MediaPayloads` | 网页/音视频写入方案计算与容量决策 |
| `capacity/CapacityCheck` | 写入前容量校验 |
| `update/UpdatePolicy` | 版本号解析与比较、APK 附件挑选、下载域名白名单、`digest` 解析 |
| `ui/component/NeuSurface` | 拟态双向阴影：分层扩散绘制，不依赖 `Modifier.shadow`（它只能画单侧）也不依赖 `Paint.setShadowLayer`（硬件加速画布上不可靠） |
| `ui/component/NeuIcon` | 图标全部用画布几何图元自绘，不引图标库 |

---

## 关键设计决策

### 存储方案：平台 SQLite + 显式迁移

**选择**：直接使用 `android.database.sqlite.SQLiteOpenHelper`，不引入 Room。

**理由**：

- 本工程只有两张表（读卡历史、设置），手写 SQL 的成本很低；
- 与「核心能力自己实现」的约束一致；
- **零注解处理器依赖**。AGP 9 起内置 Kotlin，KSP 与内置 Kotlin 的源集规则冲突
  （需要额外的 `android.disallowKotlinSourceSets` 开关压制），
  去掉 KSP 等于去掉一整类工具链版本耦合。

**代价**：没有编译期 schema 校验，SQL 写错只能在运行期暴露。
**补偿**：用 Robolectric 在 JVM 上跑真实 SQLite 覆盖数据层（见 `AppDatabaseTest`），
把「运行期才发现」提前到「测试期发现」。如果将来表结构复杂到需要编译期校验，Room 是自然的升级路径。

**迁移约束**：

- 当前 `AppDatabase.VERSION = 2`，`Migrations.ALL` 里注册了 v1 → v2 一条迁移
  （给读卡历史表加 `ndef_message` / `mifare_dump` 两列，用于「历史记录再次写入新卡」）；
- 迁移机制被测试守卫：
  - `MigrationRunner` 逐版本推进，**缺脚本直接抛错**，绝不回退到删表重建；
  - `MigrationRunnerTest.已注册的迁移不含破坏性语句` 会拒绝任何含
    `DROP TABLE` / `DELETE FROM` / `TRUNCATE` 的迁移；
  - `MigrationRunnerTest.注册表覆盖当前版本之前的每一步` 保证版本号升上去时不会漏迁移。
- **`onCreate` 也要跑一遍迁移**：`SQLiteOpenHelper` 的 `onCreate` 是按**当前版本**建库的，
  若只建 v1 表就完事，新装用户的库版本号是 v2、列却还是 v1 的，要到运行期才报「没有该列」。
  因此 `onCreate` 先建 v1 表、再 `MigrationRunner.run(db, 1, VERSION)`，
  由 `AppDatabaseTest.全新库的结构与迁移后的库一致` 守住。
- 新增 schema 变更的做法（三步）：改 `Schema`/加迁移 → `AppDatabase.VERSION` 加 1 →
  在 `Migrations.ALL` 里注册 `SqlMigration(旧版本, 旧版本+1, listOf(...))` 并补迁移测试。

### 设置只有一套实现

所有设置项统一由 `data/SettingsRepository` 读写，界面不允许直接碰 DAO，也不允许自己缓存一份。
主题切换走的是同一个 `StateFlow`，因此不存在「某个页面改了主题、另一个页面没变」的情况。

### 历史记录会保存标签原文

读卡历史保存**摘要**（UID、技术类型、记录类别、条数、字节数、时间）**加上标签原文**
（NDEF 原始编码 / MIFARE 转储），这样历史里的卡片可以直接「再次写入新卡」。

这是一个**有意识的取舍**：原文里可能含 WiFi 密码，落盘会扩大泄露面；换来的是历史不再只能看。
应用为此留了两条退路 —— 设置里可关闭自动记录，历史页可单条删除或整体清空。
相关说明在「关于」页与本文「数据与隐私」章节逐条对应。

> 早期版本只存摘要，理由写的是「不落盘敏感内容」；加入历史复用后该结论已不成立，故本节改写。

### 不抢占其他应用的标签处理权

没有注册 `NDEF_DISCOVERED` / `TECH_DISCOVERED` 意图过滤器，也不使用已在 API 33 废弃的
`enableForegroundDispatch`。只在自身处于前台时通过 reader mode 读卡。

### 在线更新：只做「用户主动触发」的联网

**选择**：检查更新走 GitHub Releases API（`/repos/{owner}/{repo}/releases/latest`），
下载与安装都在应用内完成，不引入第三方更新 SDK。

**理由**：

- 发布流程本来就在 GitHub Actions 上（推 tag → 构建 → 发 Release），更新源与发布源天然一致；
- 不引第三方库，符合「第三方依赖只有 AndroidX」的约束：HTTP 用 `HttpURLConnection`，
  JSON 用平台自带的 `org.json`；
- 只在用户点「检查更新」时发一次请求，不做启动自动检查、不轮询、不上报任何使用数据。

**代价**：需要 `INTERNET` 与 `REQUEST_INSTALL_PACKAGES` 两个权限，早期版本「完全不联网」
的表述不再成立。为此「关于」页与本文权限清单逐条改写，而不是含糊过去。

**安全**：下载的包要过三道校验才交给系统安装器 —— 大小与 Release 元数据一致、
发布方提供 `digest` 时校验 SHA-256、包名与签名证书必须与本应用完全一致。
最后一道是硬边界：即使下载域名被劫持，签名不符的包也装不上（Android 本身也会拒绝覆盖安装，
这里只是提前给出可读原因）。

**跳过语义**：选择「跳过此版本」会把版本号存进设置，之后检查到该版本只提示一句
「已跳过」，不再弹更新窗；设置页提供「取消跳过」把提示恢复，不会出现「跳过之后再也更新不了」。

---

## 工程结构

```
nfc-tool/
├── app/
│   ├── build.gradle.kts                 # 含 release 签名双通道与守卫
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/nfctool/app/
│       │   │   ├── NfcToolApplication.kt
│       │   │   ├── MainActivity.kt              # 读卡回调、振动、主题挂载
│       │   │   ├── core/                        # 纯 Kotlin，可在 JVM 单测
│       │   │   │   ├── bytes/                   # Hex、字节序
│       │   │   │   ├── ndef/                    # NDEF 编解码、记录构造、TLV
│       │   │   │   ├── wsc/                     # WiFi Simple Configuration
│       │   │   │   ├── mifare/                  # 扇区布局、转储编解码
│       │   │   │   ├── media/                   # 网页/音视频写入方案
│       │   │   │   ├── capacity/                # 容量校验
│       │   │   │   ├── update/                  # 版本比较与更新判定
│       │   │   │   └── model/                   # 快照与摘要
│       │   │   ├── data/                        # SQLite 持久层 + 仓库 + 手工容器
│       │   │   │   └── update/                  # GitHub 客户端、APK 下载与安装
│       │   │   ├── nfc/                         # 平台 NFC 适配层
│       │   │   └── ui/                          # Compose 界面
│       │   └── res/                             # 图标、颜色、主题、FileProvider 路径
│       └── test/
│           ├── java/com/nfctool/app/            # 单元测试
│           └── resources/
│               ├── ndef/                        # 14 个真实 NDEF 二进制样本 + 期望值表
│               └── mifare/                      # MIFARE 1K 转储样本
├── tools/                                       # 测试样本生成脚本（Python）
├── .github/workflows/android-ci.yml             # CI：校验 + 打 tag 发布
├── keystore.properties.example
├── LICENSE                                      # Apache License 2.0 全文
└── README.md
```

---

## 环境要求

- JDK 17 或更高（本工程用 JDK 21 验证；AGP 9.4.1 要求 JDK 17+）
- Android SDK，需包含：
  - `platforms;android-37.0`（compileSdk = 37）
  - `build-tools;37.0.0` 与 `build-tools;36.0.0`（AGP 默认使用的版本）
  - `platform-tools`

  > CI 上这些组件由工作流用 runner 自带的 `sdkmanager` 自动补齐，无需手动准备。
- 在项目根目录创建 `local.properties` 指向 SDK：

  ```properties
  sdk.dir=/path/to/Android/sdk
  ```

---

## 构建

```bash
# debug 包（不需要签名配置）
./gradlew :app:assembleDebug

# 单元测试
./gradlew :app:testDebugUnitTest

# Lint（已配置 warningsAsErrors + abortOnError）
./gradlew :app:lintDebug

# release 包（需要签名材料，见下一节）
./gradlew :app:assembleRelease
```

产物位置：

- debug：`app/build/outputs/apk/debug/app-debug.apk`
- release：`app/build/outputs/apk/release/app-release.apk`
- 测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint 报告：`app/build/reports/lint-results-debug.html`

### 编译警告策略

- Kotlin：`allWarningsAsErrors = true`（见 `app/build.gradle.kts` 的 `kotlin.compilerOptions`）
- Lint：`warningsAsErrors = true`、`abortOnError = true`

因此构建日志里出现任何 `w:` 或 lint 警告都会直接失败，不会「先合进去以后再说」。

---

## 签名

release 签名支持两条通道，**任一条配齐即可**，两条同时存在时以 `keystore.properties` 为准。

### 通道一：本地 `keystore.properties`

1. 生成密钥库（一次性）：

   ```bash
   keytool -genkeypair -v -keystore nfc-tool-release.jks \
     -alias nfc-tool -keyalg RSA -keysize 2048 -validity 10950 \
     -storetype JKS
   ```

2. 复制模板并填写：

   ```bash
   cp keystore.properties.example keystore.properties
   ```

   ```properties
   storeFile=/absolute/path/to/nfc-tool-release.jks
   storePassword=你的库口令
   keyAlias=nfc-tool
   keyPassword=你的密钥口令
   ```

3. `./gradlew :app:assembleRelease`

`keystore.properties` 与 `*.jks` 都已在 `.gitignore` 中，不会入库。

### 通道二：CI 环境变量

注入以下四个环境变量（名称固定）：

| 环境变量 | 对应 Secret | 含义 |
| --- | --- | --- |
| `NFC_TOOL_STORE_FILE` | 由 `KEYSTORE_BASE64` 解码得到 | keystore 文件路径 |
| `NFC_TOOL_STORE_PASSWORD` | `NFC_TOOL_STORE_PASSWORD` | 库口令 |
| `NFC_TOOL_KEY_ALIAS` | `NFC_TOOL_KEY_ALIAS` | 密钥别名 |
| `NFC_TOOL_KEY_PASSWORD` | `NFC_TOOL_KEY_PASSWORD` | 密钥口令 |

在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 中添加：

```bash
# 把密钥库转成 base64 后整段粘贴为 KEYSTORE_BASE64 的值
base64 -i nfc-tool-release.jks | pbcopy     # macOS
base64 -w0 nfc-tool-release.jks             # Linux
```

再加三个普通 Secret：`NFC_TOOL_STORE_PASSWORD`、`NFC_TOOL_KEY_ALIAS`、`NFC_TOOL_KEY_PASSWORD`。

### 没有签名材料时的行为

**直接构建失败**，不会用临时 debug 密钥兜底。

原因：用临时密钥签出来的包和已发布包签名不同，用户升级安装会报
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`，而构建阶段看起来一切正常。
失败信息会明确告诉你缺哪一项、两条通道分别怎么配。

### 密钥库备份

`.jks` 文件一旦丢失，**已发布的包将永远无法覆盖升级**（Android 不允许换签名升级）。
请把 `.jks` 与口令离线备份到至少两个地方。

---

## 发版流程

前置：仓库需要一个远端（`git remote add origin <你的仓库地址>`），
并配好四个签名 Secret（见上一节）。

```bash
# 1. 改版本号（versionCode 只能升不能降，versionName 必须与即将打的 tag 一致）
#    app/build.gradle.kts 中：
#      versionCode = 3
#      versionName = "1.1.0"

# 2. 提交并推送（本仓库主分支名为 main）
git add -A && git commit -m "chore: bump version to 1.1.0"
git push origin main

# 3. 打 tag 并推送 —— 这一步才会触发构建与发布
git tag v1.1.0
git push origin v1.1.0
```

**触发条件：只有推 `v*` tag 才会跑。** 推普通分支、提 PR 都不触发任何构建；
需要手动跑一次时到 Actions 页面点 "Run workflow"（`workflow_dispatch`）。

推 tag 后会跑两个任务：

**`verify`**：准备 Android SDK 组件 → 单元测试 → Lint → 上传报告。

**`release`**（仅 `v*` tag，且带 **`needs: verify`** —— 测试或 Lint 不过就不会发版）：

1. 校验 tag 与 `versionName` 是否一致（不一致直接失败）；
2. 校验四个签名 Secret 是否齐全（缺任何一个直接失败）；
3. 解码 keystore 并用 `keytool -list` 验证别名存在；
4. `./gradlew :app:assembleRelease`；
5. 用 `apksigner verify --print-certs` 打印签名指纹；
6. 把 APK 重命名为 `nfc-tool-<tag>.apk`（**纯 ASCII**，避免 GitHub Release 丢文件名），
   并生成 `.sha256` 校验文件；
7. `gh release create` 创建 Release 并附上产物；**同一个 tag 重跑时**改为覆盖上传
   （`gh release upload --clobber`），因此重新打 tag 不会因为 Release 已存在而失败。

推送普通分支不会触发任何任务。

### 几个容易踩的坑（本工程已规避）

- **`secrets` 不能出现在 `if:` 表达式里**（会报 `Unrecognized named-value: 'secrets'`）。
  本工程把密钥放在 job 级 `env`，判断写在 shell 脚本里。
- **不使用 `android-actions/setup-android`**：GitHub 的 ubuntu runner 已预装完整 Android SDK
  （含 cmdline-tools / build-tools / apksigner），装那一套反而容易踩到已下线的 legacy 包。
  工作流只用 runner 自带的 `sdkmanager` 补齐本工程需要的组件
  （`platforms;android-37.0`、`build-tools;37.0.0`、`build-tools;36.0.0`、`platform-tools`），
  已装过就是空操作；AGP 在缺失时也会自动下载，这一步是显式兜底。
- **Release 附件名必须纯 ASCII**：中文文件名会被 GitHub 处理掉，只剩前导横线。
  工作流里用 `tr -cd 'A-Za-z0-9._-'` 过滤 tag 名。
- **Actions 用的是 tag 指向的 commit 上的工作流**：如果只推 `main` 而不重新打 tag，
  工作流改动不会生效。改了工作流要 `git tag -f v1.1.0 && git push -f origin v1.1.0`。
- **解析 `versionName` 不能用 `grep -m1 versionName`**：`build.gradle.kts` 的注释里
  出现这个词也会被匹配到，于是把注释文本当成版本号，tag 校验直接失败（本工程踩过一次）。
  工作流改用锚定「行首赋值」的 `sed -nE`，并加了「解析不出来就报错」的兜底。

---

## 测试与验证

### 怎么跑

```bash
./gradlew :app:testDebugUnitTest          # 全部单元测试
./gradlew :app:lintDebug                  # Lint
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug   # 一次跑完
```

首次运行测试时，Robolectric 会联网拉取它需要的 `android-all` 运行库（体积较大，
约 80 MB）与 native runtime，因此第一次会明显偏慢；之后走本地缓存。

如果所在网络无法访问 Maven Central，可以预先下载 `android-all-instrumented` 运行库到某个目录，
再用 Gradle 属性指定离线目录，测试 JVM 就不会再联网：

```bash
# 以 API 34 为例，版本号见 Robolectric 的 SdkProvider 映射
curl -LO https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/\
14-robolectric-10818077-i7/android-all-instrumented-14-robolectric-10818077-i7.jar

./gradlew :app:testDebugUnitTest -ProbolectricDependencyDir=/path/to/deps
```

该开关只在显式传入时生效，不传即保持默认的自动下载行为。

### 测试分层

共 26 个测试类、211 个用例，全部通过。1.1.0 新增 `AppVersionTest` / `UpdatePolicyTest` 共 15 个用例。
这 26 个类分布在 23 个文件里 ——
`HexTest.kt`、`NdefRecordsTest.kt`、`MifareClassicLayoutTest.kt` 各含两个类
（`BytesTest`、`UriPrefixTest`、`MifareClassicPartsTest`），因此下表按**类名**索引，
不能按文件名去找。各测试类覆盖内容如下：

| 测试类 | 覆盖内容 |
| --- | --- |
| `NdefFixtureTest` | **用真实二进制样本验证 NDEF 解码器**（见下） |
| `NdefCodecTest` | 记录头标志位、短/长长度、分块记录、带 ID 记录、各类畸形数据必须报错 |
| `NdefRecordsTest` / `UriPrefixTest` | 文本/URI/MIME/外部类型/绝对 URI/智能海报/AAR 的构造与解析；URI 前缀缩写表压缩与还原 |
| `NdefTlvTest` | TLV 短格式、扩展长度、NULL 填充、截断容错 |
| `NdefBridgeTest` | **用真实 Android `NdefRecord` / `NdefMessage` 验证复制的编码环节**：核心层与平台层编码逐字节一致、平台编码可被核心层解码、空记录两侧差异已实测记录 |
| `WscCodecTest` | WSC 属性 ID 与取值回归、TLV 结构、黄金字节串、往返 |
| `WifiCredentialValidatorTest` | SSID 字节长度、WPA/WEP 口令规则、开放网络约束、企业级不支持 |
| `MifareClassicLayoutTest` | 1K/2K/4K 扇区块数、块号换算、尾块与厂商块判定、可写块集合 |
| `MifareClassicPartsTest` | 尾块与厂商块拆分、BCC 计算、密钥长度 |
| `MifareDumpTextCodecTest` | **用转储样本验证**两种格式的解析、往返、异常输入 |
| `MifareCopyAvailabilityTest` | 整卡复制不可用的三种原因（开关没开 / 卡不是 MIFARE Classic / 扇区密钥未知）必须分别判定，且**先判开关再判卡类型** |
| `MediaPayloadsTest` | 网址补全与校验、内嵌媒体的容量决策与降级 |
| `CapacityCheckTest` | Fits / Exceeds / Unknown 三种结果 |
| `RecordSummariesTest` | 各记录类型的摘要文案、WiFi 摘要不泄露密码、超长截断 |
| `HexTest` / `BytesTest` | 十六进制与字节序工具 |
| `CurrentWifiTest` | SSID 规范化（去引号、去空白、识别系统占位符）、按系统版本选择权限 |
| `WifiReadPresentationTest` | 读 WiFi 的各类结果 → 提示文案与补救动作的映射（含"永久拒绝才给去设置按钮"） |
| `PackageNameValidatorTest` | Android 包名校验（ASCII 限制、段首字母、空段、非法字符） |
| `MigrationRunnerTest` | 缺迁移报错、降级报错、按序执行、破坏性语句守卫 |
| `AppDatabaseTest` | 真实 SQLite 上的建表、增删查改、字段往返、重开不迁移；**全新库的结构与迁移后的库一致**；**从 v1 升级到 v2 保留数据**；历史原文按需存取 |
| `SettingsRepositoryTest` | 设置落库与内存状态一致、损坏值回退默认 |
| `NfcToolUiStateTest` | 写卡后自动回读必须**保留**结果提示、主动读卡必须清空（复制"没反应"的回归测试） |
| `AppVersionTest` | 版本号解析（`v` 前缀、缺省段、`-debug` 等后缀）与三段式比较；debug 包与正式包解析结果一致 |
| `UpdatePolicyTest` | 是否提示更新、已跳过版本不提示、APK 附件挑选、下载域名白名单、`digest` 的 SHA-256 解析 |

### 本次实际验证过的项

以下是在开发机上真实执行并通过的（不只是"应该能过"）：

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 编译零警告 | `./gradlew :app:compileDebugKotlin` | 通过，`allWarningsAsErrors = true` 下无任何 `w:` 输出 |
| 单元测试 | `./gradlew :app:testDebugUnitTest` | 26 个测试类、211 个用例全部通过（1.1.0 实跑） |
| Lint | `./gradlew :app:lintDebug` | 通过，`warningsAsErrors = true` 下 0 error 0 warning |
| debug 包 | `./gradlew :app:assembleDebug` | 产出 `app-debug.apk` |
| release 包 + 签名 | `./gradlew :app:assembleRelease` | 产出 `app-release.apk`，`apksigner verify` 通过 |
| 缺签名材料时拒绝出包 | 删除 `keystore.properties` 后构建 | 构建失败并打印两条通道的配置指引 |

> 1.1.0 已在本机实跑：debug / release 编译、26 个测试类 211 个用例、Lint（0 error 0 warning）、
> debug 包与 R8 压缩均通过。release APK 未在本机出包（缺签名材料），由 CI 完成签名与发布。

### Lint 被降级的检查

`app/lint.xml` 里降级了四条检查，每条都写了理由（该文件内可见）：

| 检查 | 理由 |
| --- | --- |
| `NewerVersionAvailable` | Kotlin / Compose 编译器插件被 AGP 9 内置 Kotlin 锁死在 2.2.10，不能升级 |
| `AndroidGradlePluginVersion` | 同上：AGP / Gradle 版本被内置 Kotlin 约束锁住；该检查是 error 级，上游发版就会让构建失败，与代码质量无关 |
| `UseKtx` | Lint 建议的 `androidx.core.database.transaction` 在 androidx.core 1.19.1 中不存在（已验证：引入即编译失败） |
| `UnnecessaryRequiredFeature` | NFC 是本应用的核心前提，`required="true"` 是有意的 |

### 写入反馈的三段状态

写卡流程容易让用户困惑：点完"写入"后应用只是在等一个物理动作，看起来和卡死一样。
因此把状态显式拆成三段，集中在一个状态卡里（`ui/component/NeuProgress.kt`）：

| 状态 | 界面表现 |
| --- | --- |
| 等待贴卡（`pending != null, busy == false`） | NFC 波纹呼吸动画 + "请将目标标签贴近手机背面" + 「取消写入」按钮 |
| 正在写入（`busy == true`） | 进度条（不确定式）+ "正在写入…，拿稳卡片" |
| 有结果 | 全局弹窗显示成功 / 部分成功 / 失败及具体原因（读卡也会弹一条结果摘要） |

另外加了两道保护：

1. **等待超时**：点完写入后 60 秒内没贴到卡，自动取消并提示，不会无限期停在等待态。
2. **异常兜底**：写入链路上任何未预期的异常都必须把 `busy`/`pending` 复位，
   否则界面会永久停在"复制中"，既看不到成功也看不到失败。

结果**统一用居中弹窗展示**（`ui/component/NeuDialog.kt`）：有标题（成功 / 注意 / 失败）、
正文与一个关闭按钮，点按钮或弹窗外都能关。此前是底部横幅，容易被忽略也不够醒目，
读卡的反馈更是只能从页面内容里自己看 —— 现在读卡也会弹一条结果摘要
（UID / 技术 / NDEF 条数 / MIFARE 容量 / 提示数）。

弹窗会**按级别自动消失**，不必每次都手动点掉：成功 / 过程说明 3 秒，
需要留意的警告 6 秒，带具体原因的错误 8 秒。
判定用的是**引用相等**而不是值相等 —— 连续两次操作可能产生内容完全相同的提示
（例如重复写同一张卡），按值比较会认为"没变化"而不重新计时，第二条会沿用
上一条的倒计时提前消失。「等待贴卡 / 正在写入」是持久状态，不受这套定时影响。

> 「写入」页的 WiFi 表单是唯一的例外：它的提示与「去设置授权 / 授予位置信息」按钮
> 绑在同一处、属于表单内的上下文操作，因此仍以内联提示呈现，不弹窗打断。

> 第 2 条不是凭空加的。初版确实有这个 bug：`executePending` 置好 `busy=true` 后才执行写入，
> 而 `NfcTagWriter` 只捕获了 `IOException` / `FormatException` / `TagLostException`，
> 编码阶段抛出的 `NdefFormatException`（`IllegalArgumentException` 子类）会直接穿透，
> 导致状态永不复位 —— 用户看到的就是"一直停在复制中，没有成功也没有失败"。

#### 第二个 bug：写卡结果被自动回读抹掉

修完上面那条之后，真机上仍然"复制没反应"，但性质变了 —— **写入其实成功了，提示被自己清掉**。

写入成功后会自动回读一次做校验，而回读复用了「用户主动读卡」的状态更新分支，
那份分支里有 `status = null`（主动读卡时清空上一条提示是对的）。
于是 `executePending` 刚设好的「已写入 N 字节」被紧随其后的回读清空，
用户看到的就是：贴卡 → 什么提示都没有 → 既不像成功也不像失败。

修法是把这条合并规则抽成显式的纯函数 `NfcToolUiState.afterRead(snapshot, clearStatus)`：

- 用户主动读卡 → `clearStatus = true`，清空提示；
- 写卡后的自动回读 → `clearStatus = false`，**保留**刚生成的成功 / 失败提示。

该规则由 `NfcToolUiStateTest` 锁定，避免再退化。

另外把 NFC 读写搬到 `Dispatchers.IO`：原先同步跑在主线程，界面根本来不及绘制
"正在写入"这个中间态，而且主线程被 IO 阻塞有触发 ANR 的风险。
同时加了 `writeInFlight` 去重 —— 标签停留在感应区时系统会连续多次分发同一个 Tag，
第二次分发会在写入途中被当成"读卡"处理，把刚写完的提示清掉。

#### 第三个 bug：「使用当前连接的 WiFi」点了没反应

真机反馈从"闪退"变成"点了没有任何反应"。根因是**读取跑在主线程**：
点击回调里同步调用 `CurrentWifiReader.read()`，而它访问的是
`ConnectivityManager` / `WifiManager` 这些系统服务，个别 ROM 上会**阻塞甚至卡住**。
主线程被占住时，界面既画不出"正在读取"，也走不到结果分支，表现就是"点了像没点"。

修法（`ui/screen/WriteScreen.kt` 的 `WifiForm`）：

- 读取放到 `Dispatchers.IO`，读完 `withContext(Dispatchers.Main)` 再更新界面状态；
- 点击后**立即**同步置一个"正在读取当前连接的 WiFi…"提示，
  保证任何一次点击都马上有可见反馈，不依赖读取是否成功；
- 读取结果仍然按 5 类原因分别给出可执行的下一步（见「能力边界」一节）。

这与写卡的处理是同一原则：**任何触碰系统服务的操作都不放在主线程**，
且**任何一次用户点击都必须在同一帧里产生可见反馈**。

#### 第三个 bug 的二次定位：状态与协程必须跨 Activity 重建存活

上一条修完后，真机现象变成"点一下、**布局跳一下**，然后什么都没有"。
"跳一下"正是提示条插入引起的布局变化，说明代码确实执行到了；
随后什么都不剩，说明**状态被丢掉了**。

根因是**授权弹窗触发 Activity 重建**：写在 `remember` 里的状态会被重置，
挂在界面作用域（`rememberCoroutineScope`）上的协程会被取消 ——
于是"弹窗阶段"的提示刚出现就消失，读取结果也永远送不回界面。

修法是把「读取当前 WiFi」的状态与协程**搬进 ViewModel**：

- 新增 `WifiReadState`（`Idle / Reading / Notice / Result`），随 `NfcToolUiState` 一起暴露；
- 读取在 `viewModelScope.launch(Dispatchers.IO)` 里跑，结果写回状态 ——
  `viewModelScope` 跨 Activity 重建存活，结果一定送得到；
- 界面只做两件事：查/要权限，把 `WifiReadState` 渲染成提示；
  措辞由纯函数 `wifiReadPresentation(result, permanentlyDenied)` 决定（可单测）；
- 表单输入（SSID / 密码 / 认证方式）改用 `rememberSaveable`，重建后不丢。

**教训**：凡是"用户触发 → 可能弹系统界面（权限 / 分享 / 相机）→ 再回填结果"的流程，
中间态和异步结果都不能只放在 Compose 的 `remember` 里。
系统的这类交互会重建 Activity，界面作用域随之销毁；状态得放到 ViewModel
（进程被杀还要用 `SavedStateHandle`）。

#### 第四个 bug：「不能复制门禁卡」没有说明原因

复制页的 MIFARE 按钮在不可用时只是一个灰掉的按钮，页面不解释为什么。
而"不能复制"实际有三种完全不同的原因，用户要做的事也完全不同：

| 判定 | 含义 | 用户该做什么 |
| --- | --- | --- |
| `DumpNotRequested` | 读卡时没打开「读取 MIFARE 整卡转储」开关 | 去读卡页打开开关重读 |
| `NotMifareClassic` | 卡不是 MIFARE Classic，或本机 NFC 芯片不支持该类卡 | 换卡或换设备，本机做不到 |
| `IncompleteDump` | 识别到了，但有扇区密钥未知，转储不完整 | 该卡无法复制（厂商自定义密钥） |

判定抽成纯函数 `MifareCopyAvailability.of(info, readDumpRequested)`（`core/mifare/`），
由 `MifareCopyAvailabilityTest` 锁定分支顺序 —— 特别是**先判开关、后判卡类型**：
开关没开时 `info` 必为 `null`，若先判 `info == null` 就会把"开关没开"误报成"卡不对"，
把用户引向错误的方向。界面只负责把判定结果措辞成提示。

另外，读卡时若打开了 MIFARE 开关却没能识别成 MIFARE Classic，
`NfcTagReader` 也会补一条 warning，读卡页与复制页都能看到。

### 历史记录：可再次写入新卡

历史原本只存摘要，只能看不能用。现在每条记录额外保存**标签原文**
（NDEF 原始编码 / MIFARE 转储），列表上出现「用这条写入新卡」按钮，
点一下即进入等待贴卡，把内容写进一张新卡 —— 门禁卡一类没有 NDEF 的走 MIFARE 整卡回写。

数据库因此从 v1 升到 v2（`ALTER TABLE` 新增两列，见 `Migrations`）。两点注意：

- **迁移必须显式**：`MigrationRunnerTest` 会检查「注册表覆盖当前版本之前的每一步」，
  缺一步就直接失败，杜绝悄悄删表重建。
- **新建的库也要跑一遍迁移**：`onCreate` 先建 v1 表、再执行迁移，
  否则新装用户的库版本号是 v2、列却还是 v1 的，要到运行期才报「没有该列」。
  这一点由 `AppDatabaseTest` 的「全新库的结构与迁移后的库一致」守住。

### 二进制解析用真实文件验证

这是本工程测试的重点，做法是**引入独立第三方实现产出样本，再用本工程解码器解析并逐项比对**：

- **NDEF**：`app/src/test/resources/ndef/` 下有 14 个样本，其中 11 个由
  **ndeflib 0.3.3**（Python 生态的独立 NDEF 实现）编码产出，
  3 个按 NFC Forum 规范手工构造（分块记录、带 ID 记录、截断数据）。
  期望值表 `expectations.tsv` 与样本由同一个脚本生成，
  测试会逐项核对记录数、TNF、类型、语言、文本、URI、WiFi 凭据字段，
  并对非分块样本要求**重新编码后与原字节完全一致**。
- **MIFARE**：`app/src/test/resources/mifare/classic_1k_sector_text.txt` 是按公开布局构造的
  1K 转储，其扇区 1 内嵌了 ndeflib 产出的真实 NDEF 消息，
  测试会顺着「转储 → NDEF TLV → NDEF 记录 → 文本内容」整条链路核对到
  `Hello NFC` 这个字符串，并验证该字节串与 `text_en.ndef` 完全一致。

**为什么不用「自己编码再自己解码」的往返测试**：那样只能证明编码器和解码器自洽，
两者同时偏离规范时照样通过。用独立实现产出的样本才能真正发现问题。

样本可用脚本重新生成：

```bash
python3 tools/generate_ndef_fixtures.py     # 需要 pip install ndeflib
python3 tools/generate_mifare_fixtures.py
python3 tools/generate_icons.py             # 需要 pip install Pillow
```

### 黄金字节串的验证方式

`WscCodecTest.编码结果与已验证的黄金字节串一致` 里固化的字节串，是先用本工程编码器产出，
再用 ndeflib 0.3.3 解码回验过的：解出的 SSID、密码、认证类型、加密类型与构造输入一致。
换言之这个字节串经过了两套独立实现的交叉确认，而不是「跑一遍看看对不对」。

---

## 外部依据

本工程中所有常量都来自可核查的来源，未凭记忆填写：

| 内容 | 来源 | 核对方式 |
| --- | --- | --- |
| NDEF 记录头标志位、SR/IL/CF 语义 | NFC Forum NDEF 1.0 规范 3.2 节 | 与 ndeflib 编码结果逐字节比对 |
| URI 前缀缩写表（36 项） | NFC Forum NDEF 1.0 规范 3.2.6.2 节 | 与 ndeflib 的 `UriRecord` 编码结果比对 |
| WSC 属性 ID（`0x1045` SSID、`0x1027` Network Key、`0x100E` Credential 等） | hostap / wpa_supplicant `src/wps/wps_defs.h` 的 `wps_attribute` 枚举 | 与 ndeflib `ndef/wifi.py` 的 `_key` 逐条比对，两套实现一致 |
| WSC 认证类型（`0x0001` Open、`0x0020` WPA2-Personal 等） | hostap `WPS_AUTH_*` 宏 | 与 ndeflib `AuthenticationType._bitmap` 逐条比对 |
| WSC 加密类型（`0x0001` None、`0x0008` AES 等） | hostap `WPS_ENCR_*` 宏 | 与 ndeflib `EncryptionType._bitmap` 逐条比对 |
| MIFARE Classic 扇区/块布局 | MIFARE Classic 公开规格（每块 16 字节，4K 前 32 扇区 4 块 / 后 8 扇区 16 块） | 布局自洽性由单测校验（块数 × 16 = 容量） |
| 扇区尾块与厂商块字段布局 | MIFARE Classic 公开规格（KeyA 6 + 访问位 4 + KeyB 6；UID 4 + BCC 1 + SAK 1 + ATQA 2 + 厂商数据 8） | 与转储样本逐字节比对 |
| `+Sector: N` 文本转储格式 | 读卡工具社区事实约定，**无规范性文档可引用** | 只按最小共识实现（扇区号从 0 连续递增、每行一个块），其余 `+` 行忽略 |
| GitHub Releases API 字段（`tag_name` / `assets[].browser_download_url` / `digest`） | GitHub REST API 文档（`X-GitHub-Api-Version: 2022-11-28`） | 按文档字段名解析；`digest` 缺失时降级为「大小 + 包名 + 签名」校验 |

WSC 的混合模式取值（认证 `0x0022`、加密 `0x000C`）在 hostap 与 ndeflib 中**都没有**
以具名常量形式出现，本工程因此**不采纳**这两个组合值，只支持上表中的单一取值。

---

## 权限清单

| 权限 | 级别 | 用途 | 缺权限后果 |
| --- | --- | --- | --- |
| `android.permission.NFC` | 普通权限 | 前台读卡与写卡 | 核心功能不可用 |
| `android.permission.VIBRATE` | 普通权限 | 读卡成功时的振动反馈（可在设置中关闭） | 无振动反馈，其余功能正常 |
| `android.permission.INTERNET` | 普通权限（安装时获得） | 检查更新与下载新版本 APK（GitHub Releases） | 无法检查更新，其余功能正常 |
| `android.permission.REQUEST_INSTALL_PACKAGES` | 特殊权限（用户手动开启） | 下载完成后拉起系统安装器 | 无法在应用内安装，可手动下载安装 |
| `android.permission.ACCESS_NETWORK_STATE` | 普通权限（安装时获得） | 判断当前网络类型 | 无法判断是否连着 WiFi |
| `android.permission.ACCESS_WIFI_STATE` | 普通权限（安装时获得） | 读取「当前连接的 WiFi 名称」的兜底路径 | 兜底路径失效；部分 ROM 会直接抛 `SecurityException` |
| `android.permission.NEARBY_WIFI_DEVICES` | 运行时权限（Android 13+） | 读取「当前连接的 WiFi 名称」 | WiFi 名需手动输入，其余功能正常 |
| `android.permission.ACCESS_FINE_LOCATION` | 运行时权限 | 读取「当前连接的 WiFi 名称」。Android 8~12 必需；Android 13+ 仅部分 ROM（如 MIUI）仍以它为前置，**只在读不到时引导用户补授** | WiFi 名需手动输入，其余功能正常 |
| `android.permission.ACCESS_COARSE_LOCATION` | 运行时权限 | 与 FINE 成对申请（lint 要求：精确权限缺失时系统至少能给出粗略值） | 同上 |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | signature 级 | **不是本应用申请的**，由 androidx.core 在 manifest 合并阶段自动注入 | 不适用 |

其中几条补充说明：

- `INTERNET` 只为更新而申请：应用不会访问写进标签的网址，也没有任何统计、广告或崩溃上报。
- `REQUEST_INSTALL_PACKAGES` **不授予静默安装能力**。它只让应用能拉起系统安装器，
  每次安装仍需用户在系统界面确认；用户未开启「安装未知应用」时，应用跳转到系统设置页。
- `NEARBY_WIFI_DEVICES` 已声明 `neverForLocation`，表示**不用于定位**；它只在 Android 13+ 存在，`usesPermissionFlags` 属性在低版本会被解析器忽略（无害，manifest 内已用 `tools:ignore="UnusedAttribute"` 说明）。
- 位置相关权限**不再限制 `maxSdkVersion`**：Android 13+ 的主路径用 `NEARBY_WIFI_DEVICES`，
  但部分厂商 ROM（如 MIUI）仍以定位为前置，因此保留声明；只有在读不到 SSID 时才**引导用户补授**，
  不会默认索取定位。
- `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 来自 AndroidX 内部的动态广播接收器机制，属于 signature 级权限，只有本应用自身能持有，不向任何外部组件授予能力。本应用没有编写任何广播接收器。用 `aapt2 dump badging` 检查打包产物时能看到它，属预期行为。

manifest 里还有一个 `<queries>` 声明（不是权限）：

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

Android 11+ 的**包可见性**规则下，不声明它，`queryIntentActivities` 只会返回极少数应用。
「写入应用」要从手机上挑应用，因此必须声明这一条 —— 它只放开 MAIN/LAUNCHER 一种意图，
**不申请 `QUERY_ALL_PACKAGES`**（那是需要重点审查的高敏权限）。列表只含**有启动图标**的应用，
其它组件仍可手动输入包名。

### 「使用当前连接的 WiFi」的能力边界

这一步**只填 SSID，不填密码**，原因不是没实现而是系统层面不允许：

| 问题 | 实际情况 |
| --- | --- |
| 能拿到已保存网络的密码吗 | **不能。** Android 从不向第三方应用提供已保存 WiFi 的密码，这是系统安全边界，加任何权限都不行 |
| 能列出所有已连过的网络让用户选吗 | **Android 10 起不能。** 系统不再向普通应用提供「已保存网络」列表 |
| 那能做什么 | 只读**当前正在连接**的那一个网络的 SSID，一键回填，密码仍需手动输入 |

实现在 `nfc/CurrentWifiReader.kt`，**两条路径都试，第一条拿到就算成功**：

- `ConnectivityManager.getNetworkCapabilities().transportInfo`（Android 12+ 首选），
  有 `NEARBY_WIFI_DEVICES` 权限时**不要求用户开启定位服务**；
- `WifiManager.connectionInfo` 兜底（该 API 自 API 31 起废弃，已加 `@Suppress`）。

> 初版是按版本**二选一**：Android 12+ 只走第一条、11 及以下只走第二条。
> 这在部分机型上会失效 —— 第一条路径被系统脱敏返回 `<unknown ssid>` 时，
> 第二条明明能拿到却根本没被调用。改成两条都试之后不再依赖这个假设。

读不到时按具体原因分别提示，并给出可点的补救动作，而不是一句笼统的"读取失败"：

| 原因 | 提示与动作 |
| --- | --- |
| 权限未授予 | 说明需要哪个权限（「附近 Wi-Fi 设备」/「位置信息」），引导再点一次按钮 |
| 权限被永久拒绝（勾了「不再询问」） | 说明系统不会再弹窗，提供「去系统设置授权」按钮直达应用详情页 |
| 定位服务关闭 | 说明必须开定位才允许读 SSID（Android 8~12 是硬性前置，部分 ROM 在 13+ 上亦然），提供「打开定位设置」按钮 |
| Android 13+ 只给了「附近设备」仍读不到 | 说明该 ROM 仍以定位为前置，提供「授予位置信息权限」按钮 —— **用户主动点了才申请** |
| 权限与定位都正常但仍拿到占位值 | 说明是厂商 ROM 限制，请手动输入 |
| 系统服务抛出异常 | 返回可展示的原因，提示手动输入；**绝不把异常冒到调用方** |

> Android 13+ 上「附近设备」（`NEARBY_WIFI_DEVICES`）在多数机型已足够，但 MIUI 一类 ROM
> 仍会脱敏 SSID，要求「位置信息」权限 + 定位服务开启。因此设计成**两级**：
> 先用最小权限读，读不到再引导补授定位；既不默认索取定位，也不至于在一部分机型上永远读不到。

判定「是否永久拒绝」依赖 `Activity.shouldShowRequestPermissionRationale`；
从 Context 里找不到 Activity 时保守返回 `false`，宁可让用户再试一次弹窗，
也不要错误地把人推去设置页。

> 读取整条链路跑在 `Dispatchers.IO`，点击后**立刻**显示「正在读取当前连接的 WiFi…」。
> 触碰系统服务的调用一律不放在主线程，且每次点击都必须在同一帧里产生可见反馈 ——
> 否则主线程被系统调用卡住时，用户看到的就是"点了没反应"。详见上文「第三个 bug」。

- `INTERNET` 只用于检查更新与下载 APK：写入的网址只是被写进标签，应用不会去访问。
- 未申请存储、相机、通讯录等权限；定位权限只服务于「读取当前 WiFi 名称」，且只在用户主动点击时申请。
- 申请了 `uses-feature android:hardware.nfc required="true"`，无 NFC 硬件的设备不会在应用市场看到本应用。
- 「关于」页内的权限说明与本节内容逐项对应（见 `ui/screen/AppInfo.kt` 的 `PERMISSIONS`）。

---

## 数据与隐私

| 项目 | 做法 |
| --- | --- |
| 读卡历史 | 存摘要（UID、技术类型、记录类别、条数、字节数、时间）**加上标签原文**（NDEF 编码 / MIFARE 转储），用于「用历史记录再次写入新卡」。**代价是 WiFi 密码一类内容会落到本地库**，可在设置里关闭自动记录或随时删除 |
| WiFi 密码 | 写入标签时按 WSC 规范编码进标签；若该标签被读卡并记入历史，密码会随 NDEF 原文一起落库（同上，可关闭/删除） |
| 完整卡片快照 | 读卡后既用于「读卡 → 写卡」的内存传递，也（按需）随历史落库 |
| 云备份 | `android:allowBackup="false"`，数据不会随系统备份离开设备 |
| 网络 | 只有「检查更新」会访问 `https://api.github.com`，不携带账号、设备标识或使用数据；下载走 GitHub 的发布域名。写入标签的网址与音视频地址不会被应用访问 |
| 更新包 | 下载到应用私有缓存 `cacheDir/updates`，通过 FileProvider 交给系统安装器；下次下载时清理旧包，不写入外部存储 |

数据库文件：`/data/data/com.nfctool.app/databases/nfc_tool.db`（debug 包为 `.debug` 后缀的包名）。

---

## 未验证项与已知限制

以下是**没有实际验证过**的部分，如实列出，请勿当作已验证结论：

1. **未在真实 Android 设备上运行过。**
   本次交付完成了编译（零警告）、单元测试、Lint 与签名构建，
   但没有在带 NFC 硬件的手机上装包实测。因此以下行为只经过代码审查与逻辑测试，
   未经真机确认：读卡回调时序、写卡成功率、MIFARE 扇区认证在各厂商机型上的表现、
   拟态阴影在真实屏幕上的观感、`enableReaderMode` 与各厂商 ROM 的兼容性。
2. **MIFARE 转储样本不是真实卡片抓包。**
   样本按公开布局构造（脚本内有说明），用于验证解析器的结构与字段处理。
   没有真实卡片数据意味着：非标准布局的卡片、真实密钥探测成功率、真实访问位组合
   都未经验证。
3. **`+Sector: N` 格式的出处未逐字比对到上游源码。**
   该格式是社区约定，没有规范性文档。本工程只按「扇区号从 0 连续递增、每行一个块」
   这一最小共识实现，因此可能无法导入所有工具导出的转储文件。
4. **WSC 写入的真实兼容性未验证。**
   编码结构已与两套独立实现比对一致，但「手机贴卡后是否真的弹出联网提示」
   依赖具体机型的 WSC 实现，未经真机验证。
5. **`embeddedNdef`（从 MIFARE 转储解析 NDEF）是启发式实现。**
   做法是把所有数据块拼起来找 NDEF TLV，对标准 NDEF 格式化的卡片有效，
   数据块内容非标准的卡片可能解析失败或误判。界面文案已标注「尽力解析」。
6. **CI 工作流已在 GitHub 上实跑通过。**
   推 `v1.0.0` tag 后 `verify` 与 `release` 两个任务都跑通，
   Release 里生成了 `nfc-tool-v1.0.0.apk`（1.34 MB）与 `.sha256` 两个附件。
   action 版本已按实际告警从 v4 升到 `actions/checkout@v7`、`actions/setup-java@v6`、
   `actions/upload-artifact@v7`（三者都是 Node 24 运行时），
   消除 "Node.js 20 is deprecated" 与 setup-java v4 的弃用告警。
   尚未覆盖：SDK 组件在 runner 上的安装耗时、以及「Release 已存在时走
   `gh release upload --clobber`」这条分支还没有实际走到过。
7. **Robolectric 固定在 API 34。**
   应用 targetSdk 为 37，但数据层测试在 API 34 下运行。
   这只影响测试环境，不影响产物，但意味着 API 35+ 的行为差异未被测试覆盖。
8. **Release 包可以构建并通过签名校验，但未做真机安装运行验证。**
   `apksigner verify` 已确认签名有效（V2 方案，AGP 按 minSdk 26 的默认选择），
   但 R8 混淆后的 release 包没有在真机上启动过；
   如遇混淆相关问题，请优先检查 `app/proguard-rules.pro`。`。
9. **未做无障碍（TalkBack）适配验证。**
   界面提供了 `contentDescription`，但没有用 TalkBack 实际走查过。
10. **未接入 Crash 上报 / 埋点**：应用只访问 GitHub Releases 接口，没有统计或崩溃上报，
    因此线上问题无法远程定位。
11. **「使用当前连接的 WiFi」这条链路没有真机验证过。**
   SSID 规范化逻辑有用例覆盖，但以下都由运行时环境决定，无法在 JVM 上验证：
   - 权限弹窗的实际表现（授予 / 拒绝 / 仅本次使用时 / 永久拒绝的判定）；
   - Android 12+ `ConnectivityManager.transportInfo` 路径能否真的拿到名称；
   - Android 10~12 在定位服务关闭时系统是否返回占位符；
   - `NEARBY_WIFI_DEVICES` 的 `neverForLocation` 在实际安装时是否被系统接受；
   - 读取已改为放到 IO 线程并加了"正在读取…"即时反馈，但**主线程阻塞**这一根因
     判断（见上文第三个 bug）是基于代码与现象推断，未经真机 logcat 证实。
   真机上如果拿不到 SSID，界面会明确说明原因并保留手动输入。
12. **Android 10+ 无法列出「已保存网络」列表**，这不是实现取舍而是系统限制，
   因此本功能只能选「当前正在连接」的那一个。
13. **`WifiManager.getConnectionInfo()` 的权限要求未从源码逐字证实。**
    本工程据此补了 `ACCESS_WIFI_STATE` 声明。查证时 AOSP 镜像与 `android.jar`
    都取不到可核查的注解（`android.jar` 是抛 `RuntimeException` 的桩类），
    因此这条依据是 Android 文档的通行说法与实测崩溃现象，而非源码比对。
    无论原因为何，防护都已到位：整个读取过程包了 try/catch，
    系统异常只会变成界面上的一条提示，不会再顶掉应用。
14. **空记录的编码在平台与核心层之间存在已实测的差异。**
   Android `NdefRecord` 编码空记录为 4 字节 `D8 00 00 00`（多置 IL 位并多写一个
   取值 0 的 ID 长度字节），核心层按 NDEF 1.0 §3.2 编码为 3 字节 `D0 00 00`。
   试过把空 ID 传成 `null`，结果不变，属平台自身行为。复制走平台编码器，
   同一台设备上的读写闭环不受影响；仅在与严格按规范的第三方读卡器互通时
   （且卡片上恰好有空记录）才可能出问题。差异已由 `NdefBridgeTest` 钉住。
15. **写卡反馈的三段状态没有真机验证过。**
   进度条与波纹动画的视觉节奏是否舒服、60 秒超时是否合适，
   只能上机看；异常兜底逻辑本身有代码路径保障，但无法用单测覆盖（依赖真实 NFC 硬件）。
16. **「不能复制门禁卡」的具体表现未在任何真机上确认过。**
   复制页现在会区分三种原因并就地给出提示，判定逻辑由 `MifareCopyAvailabilityTest` 锁定；
   但**具体某张门禁卡属于哪一种**、以及本机 NFC 芯片是否支持 MIFARE Classic，
   只能贴卡实测才知道。需要特别说明：即便转储完整、回写成功，
   本应用也**无法克隆 UID**（厂商块出厂只读），
   因此靠 UID 认证的门禁卡即使复制成功也刷不开门 —— 这是 MIFARE Classic 的硬件限制，
   不是实现问题。若需要 UID 级克隆，只能用可写 UID 的特殊卡片配合专用工具。
17. **在线更新链路未在真机验证过。**
   编译、单元测试与 Lint 已在本地通过，但没有带 NFC 硬件的设备可装包实测。
   以下行为只有代码审查与纯逻辑单测支撑：
   - GitHub API 的实际返回，尤其 `digest` 字段是否随附件提供（缺失时降级为签名校验）；
   - 真实网络下的下载进度节奏与断网中断表现；
   - FileProvider 授权、系统安装器、以及各厂商 ROM 上「安装未知应用」设置页的跳转；
   - 签名一致性校验在 release 包上的表现。**debug 包因包名带 `.debug` 后缀，
     无法安装 release 包**，这是预期行为，不是缺陷；
   - 跳过 / 取消跳过的状态在各版本间的持久化。

---

## 许可

本项目以 **Apache License 2.0** 授权，许可证全文见仓库根目录的 [`LICENSE`](LICENSE)。

```
Copyright 2026 xushujin
```

### 你可以做什么

在遵守下述条件的前提下，Apache 2.0 允许你：商用、私用、修改、再分发，
以源码或二进制形式分发，并获得贡献者授予的**专利许可**（第 3 条），
该专利许可不可撤回——除非你主动对作品发起专利诉讼。

### 你需要做什么

- 分发本作品或其衍生作品时，**附带一份许可证副本**（第 4(a) 条）；
- **修改过的文件要标注你做过修改**（第 4(b) 条）；
- 保留源码中的**版权、专利、商标与署名声明**（第 4(c) 条）；
- 若本仓库含 `NOTICE` 文件（当前**没有**），衍生作品需一并保留其中的署名信息（第 4(d) 条）。

### 不授予的权利

- **商标**：本许可证不授予使用作者姓名、商标或产品名的权利（第 6 条），
  衍生作品不得暗示由原作者背书。
- **担保**：作品按"现状"提供，不附带任何明示或默示担保（第 7 条）；
  作者不对使用本作品造成的任何损失承担责任（第 8 条）。

### 第三方组件

随 APK 一起分发的第三方库同样以 Apache 2.0 授权，与本许可证兼容。
下表是按构建配置核对后的实际清单 —— 来源是 `app/build.gradle.kts` 的
`dependencies` 块，以及各依赖 POM 中的 `<license>` 元数据：

| 组件 | 作用域 | 许可证 |
| --- | --- | --- |
| AndroidX（Core KTX / Lifecycle / Activity Compose） | 进入 APK | Apache License 2.0 |
| Jetpack Compose（UI / Foundation / Material3） | 进入 APK | Apache License 2.0 |
| Kotlin 标准库、kotlinx-coroutines | 进入 APK | Apache License 2.0 |
| JUnit 4 | 仅测试，不进入 APK | Eclipse Public License 1.0 |
| Truth | 仅测试，不进入 APK | Apache License 2.0 |
| Robolectric | 仅测试，不进入 APK | MIT License |
| androidx.test:core | 仅测试，不进入 APK | Apache License 2.0 |

`tools/` 下的 Python 脚本用于生成测试样本，依赖第三方库 **ndeflib**；
它只在开发机上运行，**不随应用分发**。

### 许可与使用是两件事

许可证解决的是**代码**的授权问题，不改变**使用**层面的边界：

- 本应用**不破解任何加密**。MIFARE Classic 只尝试公开的默认扇区密钥，
  厂商自定义密钥读不到就是读不到。
- 本应用**无法克隆 UID**（厂商块出厂只读），因此靠 UID 认证的门禁卡
  即使复制成功也刷不开门。
- 复制他人卡片可能涉及法律与授权问题，请只对**你自己拥有或已获授权**的卡片操作。
