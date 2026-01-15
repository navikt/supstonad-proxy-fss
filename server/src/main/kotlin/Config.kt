package no.nav.supstonad

import io.ktor.server.config.ApplicationConfig
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path


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
                username = readSecret("/var/run/secrets/nais.io/srvuser/username"),
                password = readSecret("/var/run/secrets/nais.io/srvuser/password")
            )
        ),
        simuleringUrl = envOrConfig("SIMULERING_OPPDRAG_URL"),
        soapEndpointTilbakekreving = envOrConfig("TILBAKEKREVING_URL"),
    )
}

private fun readSecret(filename: String): String {
    try {
        val file: Path = Paths.get(filename)
        val lines = Files.readAllLines(file)
        return lines.first()
    } catch (exception: IOException) {
        throw RuntimeException("Failed to read property value from " + filename, exception)
    }
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