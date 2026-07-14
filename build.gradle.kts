import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij") version "1.17.4"
    kotlin("jvm") version "1.9.24"
}

group = "smarthex"
version = "1.0.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2022.1")
    type.set("PC") // PyCharm Community; plugin runs on any JetBrains IDE
    plugins.set(listOf())
    // Don't auto-inject until-build — we set <idea-version since-build="221"/>
    // manually in plugin.xml with no upper bound, so the plugin installs on
    // 2022.1 through the latest builds (e.g. 2026.1 / build 261).
    updateSinceUntilBuild.set(false)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    patchPluginXml {
        // version range controlled by <idea-version> in plugin.xml
    }

    buildSearchableOptions {
        enabled = false
    }

    // Disable bytecode instrumentation — not needed since we use no GUI Designer forms.
    // Also works around a Windows + Microsoft JDK "Packages does not exist" issue.
    instrumentCode {
        enabled = false
    }

    runIde {
        // Disable for automated builds
    }
}
