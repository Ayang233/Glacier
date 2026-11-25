import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ayang.glacier"

    // MIUIX main 要求 compile against 37.2（仅编译期 API，不影响运行时行为）。
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.ayang.glacier"
        // miuix-blur 的 RuntimeShader 门槛，决定 OS4 Glass 能否产出真实模糊。
        minSdk = 33
        targetSdk = 36
        versionCode = 9
        versionName = "1.6.2"
    }

    // Release 签名：优先用仓库根的 signing.properties，其次用上一级工作区的
    // signing.properties（本机宿主的密钥放在那里），都没有才退回 debug 签名。
    // storeFile 相对 signing.properties 所在目录解析，这样密钥目录跟着配置文件走。
    val signingPropsFile = listOfNotNull(
        rootProject.file("signing.properties"),
        rootDir.parentFile?.resolve("signing.properties"),
    ).firstOrNull { it.exists() }
    val releaseSigning = if (signingPropsFile != null) {
        val propsDir = signingPropsFile.parentFile
        val props = Properties().apply {
            signingPropsFile.inputStream().use { load(it) }
        }
        signingConfigs.create("release") {
            storeFile = propsDir.resolve(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        signingConfigs.getByName("debug")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = releaseSigning
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    androidResources {
        generateLocaleConfig = true
        // dex 不压缩，便于作为特权应用导入（DeviceOwner / privapp 场景）。
        noCompress += "dex"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val name = if (variant.buildType == "release") "Glacier-release.apk" else "Glacier-debug.apk"
                output.outputFileName = name
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // ---- Compose 基础（不含 material3：Glacier 不允许与 MIUIX 混用第二套设计体系）----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.android)

    // ---- MIUIX OS4 ----
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.material)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.glass)
    implementation(libs.miuix.glass.icons)
    implementation(libs.miuix.nav)
    // 官方 squircle 形状：应用图标与品牌图标的 MIUIX 圆角轮廓
    implementation(libs.miuix.squircle)

    // ---- 系统能力 ----
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.dhizuku.api)
    implementation(libs.hiddenapibypass)
    compileOnly(libs.libxposed.api)

    // ---- 业务基础库 ----
    implementation(libs.appiconloader)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)

    // ---- 单元测试（纯 JVM，不依赖 Android 运行时）----
    testImplementation(libs.junit4)
}
