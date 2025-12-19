package no.nav.supstonad

import io.ktor.server.config.ApplicationConfig

fun load(config: ApplicationConfig? = null): Config {
    // Helper to read either from ApplicationConfig or environment variables
    fun envOrConfig(name: String): String =
        config?.propertyOrNull(name)?.getString()
            ?: System.getenv(name)
            ?: error("Configuration $name not set")

    return Config(
        sts = Config.Sts(
            gandalfUrlSts = envOrConfig("GANDALF_URL"),
            serviceuser = Config.Sts.ServiceUser(
                username = envOrConfig("username"),
                password = envOrConfig("password")
            )
        ),
        simuleringUrl = envOrConfig("SIMULERING_OPPDRAG_URL"),
        soapEndpointTilbakekreving = envOrConfig("TILBAKEKREVING_URL"),
    )
}


data class Config(
    val sts: Sts,
    val simuleringUrl: String,
    val soapEndpointTilbakekreving: String,
) {
    //https://github.com/navikt/gandalf
    data class Sts(
        val gandalfUrlSts: String,
        val serviceuser: ServiceUser
    ) {
        data class ServiceUser(
            val username: String,
            val password: String
        ) {
            override fun toString(): String = "name=$username, password=<REDACTED>"
        }
    }
}