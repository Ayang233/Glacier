pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Glacier"

// MIUIX OS4 —— 以官方 main 源码 composite build 引入。
// 版本不写死在本文件中，实际基线记录在 docs/architecture/miuix-baseline.md。
val miuixDir = providers.environmentVariable("MIUIX_DIR").orNull
    ?.let { file(it) }
    ?: file("../miuix")

if (!miuixDir.resolve("settings.gradle.kts").exists()) {
    error(
        """
        |找不到 MIUIX 源码目录：${miuixDir.absolutePath}
        |
        |Glacier 以 composite build 引用 compose-miuix-ui/miuix 的 main 源码。
        |请先：
        |  git clone https://github.com/compose-miuix-ui/miuix.git <workspace>/miuix
        |或设置环境变量 MIUIX_DIR 指向已有源码目录。
        """.trimMargin(),
    )
}
includeBuild(miuixDir)

include(":app")
