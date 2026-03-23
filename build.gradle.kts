import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    // JetBrains IntelliJ 仓库（必须放在前面）
    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
    maven { url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies") }

    // 阿里云镜像加速
    maven { url = uri("https://maven.aliyun.com/repository/central") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
intellij {
    pluginName.set(providers.gradleProperty("pluginName"))
    version.set(providers.gradleProperty("platformVersion"))
    type.set(providers.gradleProperty("platformType"))

    // 不自动更新
    updateSinceUntilBuild.set(false)

    // Plugin Dependencies
    plugins.set(providers.gradleProperty("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) })
}

// 禁用 Java agent（解决 coroutines-javaagent.jar 问题）
tasks.withType<org.jetbrains.intellij.tasks.RunIdeTask> {
    jvmArgs("-Djava.awt.headless=false")
    // 禁用 instrument agent
    systemProperty("idea.log.debug.categories", "#org.jetbrains.kotlin.idea")
}

dependencies {
    // kotlinx-coroutines 已由 IntelliJ Platform 提供，不需要显式添加
    implementation("com.google.code.gson:gson:2.10.1")

    // SQLite for data persistence (排除 SLF4J 避免与 IDEA 冲突)
    implementation("org.xerial:sqlite-jdbc:3.45.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 禁用 instrument 任务，避免生成 instrumented jar 导致低版本 IDEA 不兼容
tasks.named("instrumentCode") {
    enabled = false
}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }
    
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
        kotlinOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
    }

    patchPluginXml {
        sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
        untilBuild.set(providers.gradleProperty("pluginUntilBuild"))
    }

    test {
        useJUnitPlatform()
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN") ?: providers.gradleProperty("PUBLISH_TOKEN").orNull)
    }
}
