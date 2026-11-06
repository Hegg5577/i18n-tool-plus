import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.hegg5577.i18n"
version = "0.1.4"

tasks.named<Zip>("buildPlugin") {
    archiveBaseName.set("i18n-tool-plus")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 默认下载平台，其他开发者无需安装到相同目录；本地开发可指定已有 IDE。
        val localPlatformPath = providers.gradleProperty("localPlatformPath").orNull
        if (localPlatformPath.isNullOrBlank()) {
            create("WS", "2026.3")
        } else {
            local(localPlatformPath)
        }
        testFramework(TestFrameworkType.Platform)
    }
    // 平台自带 gson（lib/intellij.libraries.gson.jar），编译期引用即可，
    // 不重复打进插件包，避免与 IDE 自带版本冲突。
    compileOnly("com.google.code.gson:gson:2.13.1")

    // YAML 配置文件解析
    compileOnly("org.yaml:snakeyaml:2.2")
    testImplementation("org.yaml:snakeyaml:2.2")

    // 单元测试跑在 JVM 里而非 IDE 内，需要一份可执行的 gson 与 JUnit。
    // 上一条是 compileOnly（不打进插件包），测试运行期没有，所以单测要用 test 作用域。
    testImplementation("com.google.code.gson:gson:2.13.1")
    testImplementation("junit:junit:4.13.2")
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
            untilBuild = "263.*"
        }
        changeNotes = """
            <h3>0.1.4</h3>
            <ul>
              <li>New plugin icon and tool-window icon.</li>
              <li>Polished marketplace listing page.</li>
              <li>Fix the hover translation table styling in dark themes.</li>
            </ul>
            <h3>0.1.3</h3>
            <ul>
              <li>Support WebStorm 2026.3 and newer.</li>
              <li>Fix UI freeze when auto-translating inside the key editor.</li>
              <li>Enable JetBrains Marketplace error reporting for exceptions and UI freezes.</li>
            </ul>
            <h3>0.1.2</h3>
            <ul>
              <li>Initial public version with multilingual table, inline preview, and key editing.</li>
            </ul>
        """.trimIndent()
    }
    // 跳过"启动 IDE 索引配置项"这一步，显著加快本地迭代
    buildSearchableOptions = false
}
