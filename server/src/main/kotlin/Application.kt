package no.nav.supstonad

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.config.ApplicationConfig
import no.nav.security.token.support.v3.tokenValidationSupport

fun main(args: Array<String>) {
    io.ktor.server.cio.EngineMain.main(args)
}

fun Application.proxyappRoutes() {
    val config = environment.config
    val stsConfig = load(config)
    configureMonitoring()
    configureSerialization()
    installTokenValidation(config)
    configureRouting(stsConfig)
}


fun Application.installTokenValidation(config: ApplicationConfig) {
    val tokenSupportConfig = config
        install(Authentication) {
            tokenValidationSupport(
                config = tokenSupportConfig,
            )
    }
}
