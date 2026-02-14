import org.apache.tools.ant.filters.ReplaceTokens
import java.nio.charset.StandardCharsets
import java.util.*

/*
 * Copyright (c) 2025, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.runelite.net") }
}

group = "net.runelite"
version = "2.7.6-SNAPSHOT"
description = "RuneLite Launcher"

dependencies {
    implementation(libs.org.slf4j.slf4j.api)
    implementation(libs.ch.qos.logback.logback.classic)
    implementation(libs.net.sf.jopt.simple.jopt.simple)
    implementation(libs.com.google.code.gson.gson)
    implementation(libs.com.google.guava.guava) {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
        exclude(group = "com.google.j2objc", module = "j2objc-annotations")
        exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
    }
    implementation(libs.net.runelite.archive.patcher.archive.patcher.applier)
    compileOnly(libs.com.google.code.findbugs.jsr305)
    compileOnly(libs.org.projectlombok.lombok)
    annotationProcessor(libs.org.projectlombok.lombok)
    testImplementation(libs.junit.junit)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val exportFile = file("build/exported-app-name.properties")

tasks.register("exportAppNameManually") {
    doLast {
        val appName = project.findProperty("finalName") as? String ?: "RuneLite"
        val lowerName = project.findProperty("lowerName") as? String ?: appName.lowercase(Locale.getDefault())

        println("Exporting app names:")
        println("  FINAL_NAME = $appName")
        println("  LOWER_NAME = $lowerName")

        // Ensure build folder exists
        exportFile.parentFile.mkdirs()

        // Write to GitHub Actions env if available
        System.getenv("GITHUB_ENV")?.let { envPath ->
            file(envPath).appendText("GRADLE_APP_NAME=$appName\n")
            file(envPath).appendText("GRADLE_APP_NAME_LOWER=$lowerName\n")
        }

        // Write to manual properties file
        exportFile.writeText("finalName=$appName\nlowerName=$lowerName\n")
        println("Exported properties to: ${exportFile.absolutePath}")
    }
}


val exportedProps = Properties().apply {
    if (exportFile.exists()) {
        load(exportFile.reader())
    }
}
val finalNameGlobal = exportedProps.getProperty("finalName") ?: "RuneLite"
val lowerNameGlobal = exportedProps.getProperty("lowerName") ?: finalNameGlobal.lowercase(Locale.getDefault())

tasks {
    processResources {
        filesMatching(listOf("**/*.properties", "**/*.xml")) {
            val props = arrayOf(
                "user" to System.getProperty("user.home"),
                "lowerName" to lowerNameGlobal,
                "finalName" to finalNameGlobal,
                "runelite_net" to project.extra["website"] as String,
                "runelite_128" to "runelite_128.png",
                "runelite_splash" to "runelite_splash.png"
            )
            expand("project" to project, *props)
        }
    }
}

tasks.register<Copy>("filterAppimage") {
    from("appimage/runelite.desktop")
    into("build/filtered-resources")
    expand("project" to project)
}

tasks.register<Copy>("filterInnosetup") {
    from("innosetup") {
        include("*.iss")
        expand(
            "finalName" to finalNameGlobal,
            "lowerName" to lowerNameGlobal
        )
    }
    into("build/filtered-resources")
}

tasks.register<Copy>("filterPackr") {
    from("packr") {
        include("*.json")
        filter { line ->
            line.replace("RuneLite.jar", "$finalNameGlobal.jar")
        }
    }
    into("build/packr")
}

tasks.register<Copy>("filterInnosetupPas") {
    from("innosetup") {
        include("*.pas")
        filter { line ->
            line.replace("\${project.finalName_upper}", finalNameGlobal.uppercase(Locale.getDefault()))
                .replace("{project.finalName}", finalNameGlobal)
        }
    }
    into("build/filtered-resources")
}

tasks.register<Copy>("copyInstallerScripts") {
    from("innosetup") { include("*.pas") }
    into("build/filtered-resources")
}

tasks.register<Copy>("filterOsx") {
    from("osx/Info.plist")
    into("build/filtered-resources")
    expand("project" to project)
}

tasks.shadowJar {
    from(sourceSets.main.get().output)
    minimize { exclude(dependency("ch.qos.logback:.*:.*")) }
    archiveFileName.set("$finalNameGlobal.jar")
    manifest { attributes("Main-Class" to "net.runelite.launcher.Launcher") }
}

tasks.named("build") {
    dependsOn(
        "exportAppNameManually",
        "filterAppimage",
        "filterInnosetup",
        "filterInnosetupPas",
        "filterPackr",
        "copyInstallerScripts",
        "filterOsx",
        tasks.shadowJar
    )
}
