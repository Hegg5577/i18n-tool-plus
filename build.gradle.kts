import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.up3d.i18n"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 复用本机已安装的 WebStorm，免去 ~1.5GB 平台包下载。
        // 若要改为下载独立平台包，替换为：create("WS", "2026.1.4")
        local("D:/webstrom/WebStorm 2026.1.4")
        testFramework(TestFrameworkType.Platform)
    }
    // 平台自带 gson（lib/intellij.libraries.gson.jar），编译期引用即可，
    // 不重复打进插件包，避免与 IDE 自带版本冲突。
    compileOnly("com.google.code.gson:gson:2.13.1")
}

// 统一 Java / Kotlin 两者的 JVM 目标，避免 target 不一致。
// 平台 262（2026.x）要求 Java 21 字节码；本机无 JDK 21，由 foojay 自动下载。
kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "262.*"
        }
    }
    // 跳过"启动 IDE 索引配置项"这一步，显著加快本地迭代
    buildSearchableOptions = false
}
