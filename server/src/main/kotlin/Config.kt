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
            soapUrl = envOrConfig("STS_SOAP_URL"),
            serviceuser = Config.Sts.ServiceUser(
                username = envOrConfig("username"),
                password = envOrConfig("password")
            )
        ),
        simulering = Config.Simulering(
            soapUrl = envOrConfig("SIMULERING_OPPDRAG_URL"),
        )
    )
}


data class Config(
    val sts: Sts,
    val simulering: Simulering,
) {
    data class Sts(
        val soapUrl: String,
        val serviceuser: ServiceUser
    ) {
        data class ServiceUser(
            val username: String,
            val password: String
        ) {
            override fun toString(): String = "name=$username, password=<REDACTED>"
        }
    }

    data class Simulering(
        val soapUrl: String,
    )
}