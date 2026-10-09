import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.spotless)
}

/**
 * release 的签名配置从**仓库外**的一份 `keystore.properties` 读（`docs/KEYSTORE.md`）。
 *
 * 为什么在仓库外：那文件里有口令。`.gitignore` 只保证"不会误提交"，不等于"放这儿安全"。
 * 找两处，按顺序：
 *  1. 仓库旁边：`<仓库父目录>/keystore.properties`（推荐，好找又好备份）
 *  2. 家目录：`~/.wheremy/keystore.properties`
 *
 * **找不到就退回调试签名**（今天的现状），并在真要出 release 包时喊一声——
 * 免得有人以为手上那个包是正式签名的。
 */
val keystorePropertiesFile: File? =
    listOf(
        rootProject.file("../keystore.properties"),
        File(System.getProperty("user.home"), ".wheremy/keystore.properties"),
    ).firstOrNull { it.isFile }

val keystoreProperties: Properties? =
    keystorePropertiesFile?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }

android {
    namespace = "com.wheremy.app"
    // Compose 1.12.1（BOM 2026.09.00）的 AAR 元数据要求 compileSdk ≥ 37；
    // targetSdk 仍保持 36（运行时行为不跟着跳，等 P6 统一评估）。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wheremy.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // 先把两个可空值收进非空局部变量：Kotlin 不会在嵌套 lambda 里智能转换
        val props = keystoreProperties
        val propsFile = keystorePropertiesFile
        if (props != null && propsFile != null) {
            create("release") {
                // 相对路径按 properties 文件所在目录解析（写 `wheremy.jks` 就够了）
                val raw = props.getProperty("storeFile").orEmpty()
                val candidate = File(raw)
                storeFile = if (candidate.isAbsolute) candidate else File(propsFile.parentFile, raw)
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                // PKCS12 里 key 口令与 store 口令是同一个，允许只写一个
                keyPassword = props.getProperty("keyPassword") ?: props.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        debug {
            // 与参照包（com.wheremy.app）并存，便于同机对比（PLAN.md §6.2）
            applicationIdSuffix = ".dsh"
        }
        release {
            // P6 发布准备：R8 打开（含资源收缩）。规则见 `proguard-rules.pro`，
            // 关键三处：Room 3 的实体/DAO、kotlinx.serialization 的生成序列化器、Hilt 的 Worker。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 有 keystore.properties 就用正式签名；没有就退回调试签名（只在真要出包时喊一声，
            // 免得有人以为手上那个包是正式签名的）。做法与理由见 `docs/KEYSTORE.md`。
            signingConfig =
                if (keystoreProperties != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
        /**
         * R8 真机冒烟变体：规则与 release 完全一致，只多一个包名后缀。
         *
         * 为什么不直接装 release：release 的包名是 `com.wheremy.app`，与**参照实现那个应用同名**——
         * 装上去要么签名冲突、要么把用户手机上那个应用顶掉。冒烟必须另起一个包名。
         */
        create("r8smoke") {
            initWith(getByName("release"))
            applicationIdSuffix = ".r8smoke"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        compose = true
        // 备份清单里要写 VERSION_NAME（`RULES.md` §15 的"versionName 进备份清单"）
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            // Robolectric（4.17）在 JDK 17+ 需要开放这几个模块：它用反射改 FileDescriptor 内部字段，
            // 否则报 "Failed to interact with raw FileDescriptor internals; perhaps JRE has changed?"
            all {
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                )
            }
        }
    }
}

// AGP 9 内置 Kotlin：不再 apply org.jetbrains.kotlin.android，编译器选项走这个块
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // 用 KSP 参数导 schema（Phase 1 再评估 androidx.room3 的 Gradle 插件）
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 静态检查：只用 Spotless（内含 ktlint）——不引第二套同类工具（TECH_STACK.md §5 第 4 条）。
// 先只做 check，不自动格式化既有代码；要格式化时跑 ./gradlew spotlessApply。
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlintTool.get()).editorConfigOverride(
            // Compose 的 @Composable 函数按官方约定用 PascalCase；ktlint 的 function-naming 要放开。
            // 写在 editorConfigOverride 里，是因为 Spotless 在临时目录里跑 ktlint，根目录的 .editorconfig 传不进去。
            mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlintTool.get())
    }
}

dependencies {
    val bom = platform(libs.androidx.compose.bom)
    implementation(bom)
    testImplementation(bom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.accessibility.check)
}

// 真要出 release 包而又没配 keystore 时，明确喊一声（别让人以为它是正式签名的包）
gradle.taskGraph.whenReady {
    val assemblingRelease =
        allTasks.any {
            it.project == project && (it.name == "assembleRelease" || it.name == "bundleRelease")
        }
    if (assemblingRelease && keystoreProperties == null) {
        logger.lifecycle(
            "⚠️ release 用的是**调试签名**：没找到 keystore.properties。" +
                "正式交付前请按 docs/KEYSTORE.md 生成自己的 keystore。",
        )
    }
}
