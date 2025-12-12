plugins {
    kotlin("jvm") version "2.2.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Ktor Server ---
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.call.logging)
    //implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth.jwt)

    // --- Ktor Client ---
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)

    // --- Micrometer ---
    implementation(libs.micrometer.registry.prometheus)

}