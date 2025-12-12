val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.2.21"
    id("io.ktor.plugin") version "3.3.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
}

application {
    mainClass = "io.ktor.server.cio.EngineMain"
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-default-headers")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-resources")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-call-id")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-jackson")
    implementation("io.ktor:ktor-server-cio")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    testImplementation("io.ktor:ktor-server-test-host")


    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.21")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")

    implementation("no.nav.security:token-validation-ktor-v3:5.0.30")
    testImplementation("no.nav.security:mock-oauth2-server:3.0.0")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        maxParallelForks = 1
    }
}


tasks.named<Jar>("jar") {
    archiveBaseName.set("app")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "no.nav.su.se.bakover.ApplicationKt"
        attributes["Class-Path"] =
            configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
    }
    doLast {
        configurations.runtimeClasspath.get().forEach {
            val fileProvider: Provider<RegularFile> = layout.buildDirectory.file("libs/${it.name}")
            val targetFile = File(fileProvider.get().toString())
            if (!targetFile.exists()) {
                it.copyTo(targetFile)
            }
        }
    }
}
