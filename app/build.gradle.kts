// Gradle Kotlin DSL 只默认导入 java.lang.* 等少数包，java.util.Properties 必须显式引入，
// 否则整个脚本会以「Unresolved reference 'Properties'」连锁报错。
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * release 签名。
 *
 * keystore 与密码**不进仓库**（.gitignore 已挡住）：丢了就永久无法更新已上架的应用，
 * 所以要离线备份 keystore 文件 + 这里的四行属性。
 *
 * 只有 release 需要它，debug 仍走 Android 自动生成的调试密钥，因此缺文件不在这里报错，
 * 否则会让日常 debug 编译也跑不起来——真正的拦截放在 release 构建任务执行时。
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.reader().use { load(it) }
    }
}

/** 用 keystore.properties 里的四项建 signingConfig；缺文件时由 release 构建任务拦截。 */
fun com.android.build.api.dsl.ApplicationExtension.configureReleaseSigning() {
    if (!keystorePropertiesFile.exists()) return

    val storeFileName = requireNotNull(keystoreProperties.getProperty("storeFile")) {
        "keystore.properties 里缺少 storeFile"
    }
    signingConfigs.create("release") {
        storeFile = rootProject.file(storeFileName)
        storePassword = keystoreProperties.getProperty("storePassword")
        keyAlias = keystoreProperties.getProperty("keyAlias")
        keyPassword = keystoreProperties.getProperty("keyPassword")
    }
}

android {
    // 必须先于下面的 = 赋值执行：Kotlin DSL 里成员函数调用不能排在属性赋值之后。
    configureReleaseSigning()

    namespace = "com.example.campuslife"
    compileSdk = 36

    defaultConfig {
        // 发布身份。上架后永久不可改（改了等于换一个全新应用，老用户收不到更新），
        // 所以这里是发布前必须定死的值。它同时决定 Android 的"升级链"身份：
        // 只有用同一把签名密钥、同一个 applicationId 打的包，才能覆盖安装并保留用户数据。
        // 段名规则：每段以小写字母开头，只允许 a-z 0-9 _，不能用连字符，不能用中文。
        //   所以 io.github.203.* 这类数字开头的段虽然 AGP 能接受，但不符合规范（详见下方备注）。
        // 前置条件：chengyishuanger 必须在 github.com 上被你注册（当前该用户名未占用）。
        // io.github.* 是"我就是这个人"的声明，账号没注册就失去意义，且存在被别人抢注的风险。
        applicationId = "io.github.chengyishuanger.campuslife"
        // 代码命名空间与发布身份解耦：namespace 只决定 R / BuildConfig 生成到哪个包，
        // 保留 com.example.campuslife 让这次改名零源码改动。
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // 原型阶段平时只跑 debug；release 提前按官方推荐打开 R8，
            // 避免等到真正打包时才发现混淆问题。
            // 注意：AGP 9 下必须用 proguard-android-optimize.txt，
            // 使用 proguard-android.txt 会因为自带 -dontoptimize 而失败。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // 有本机签名配置时产出已签名 release；公开源码中的 debug 构建不依赖该文件。
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 拦截只发生在执行 release 构建时，避免下载公开源码后连 Gradle Sync 和 debug 都失败。
// 仍然禁止在缺少密钥时静默产出无法安装的未签名 release。
if (!keystorePropertiesFile.exists()) {
    val requireReleaseSigning = tasks.register("requireReleaseSigning") {
        doLast {
            throw GradleException(
                "缺少 keystore.properties，release 无法签名。" +
                    "参考 README.md 配置自己的签名密钥，或用 ./gradlew :app:assembleDebug 构建调试版。",
            )
        }
    }
    tasks.matching { it.name == "preReleaseBuild" }.configureEach {
        dependsOn(requireReleaseSigning)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    // 通知兼容层（NotificationCompat / NotificationManagerCompat）从这里来，
    // 渠道、权限判断与低版本行为统一走它。
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // 生命周期观察者（MainActivity 里「每次回到前台重排一次」用的就是它）。
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.webkit:webkit:1.16.0")
    // 提醒自愈：周期任务定期重排一遍闹钟。系统静默清掉闹钟时不发任何通知，
    // 只能靠自己回头查、自己补（见 data/reminder/ReminderHealWorker.kt）。
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.commonmark:commonmark:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.30.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.30.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
