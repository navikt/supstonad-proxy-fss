plugins {
    id("supstonad-proxy-app")
}

dependencies {
    implementation(libs.ktor.serverAuth)
    implementation(libs.ktor.callLogging)
    implementation(libs.ktor.clientApache)
    implementation(libs.ktor.clientLogging)
    implementation(libs.ktor.jackson)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverAuthJwt)
    implementation(libs.cxf.logging)
    implementation(libs.cxf.jax.ws)
    implementation(libs.cxf.transports.http)
    implementation(libs.cxf.ws.security)
    implementation(libs.micrometer.prometheus)

    testImplementation(libs.mockOauth2Server)
    testImplementation(libs.ktor.serverTests)
    testRuntimeOnly(libs.junit.platform.launcher)
}