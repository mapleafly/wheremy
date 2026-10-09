import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.spotless)
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

    buildTypes {
        debug {
            // 与参照包（com.wheremy.app）并存，便于同机对比（PLAN.md §6.2）
            applicationIdSuffix = ".dsh"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
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
