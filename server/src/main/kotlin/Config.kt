package no.nav.supstonad

import io.ktor.server.config.ApplicationConfig
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


fun load(config: ApplicationConfig? = null): Config {
    val envOrConfig: (String) -> String = { name ->
        config?.propertyOrNull(name)?.getString()
            ?: System.getenv(name)
            ?: error("Configuration $name not set")
    }
    return Config(
        sts = Config.Sts(
            gandalfUrlSts = envOrConfig("GANDALF_URL"),
            serviceuser = Config.Sts.ServiceUser(
                ///var/run/secrets/nais.io/srvuser/ injectes fra vault i yaml speccen
                username = readSecretFromFile("/var/run/secrets/nais.io/srvuser/", "username", envOrConfig),
                password = readSecretFromFile("/var/run/secrets/nais.io/srvuser/", "password", envOrConfig)
            )
        ),
        simuleringUrl = envOrConfig("SIMULERING_OPPDRAG_URL"),
        soapEndpointTilbakekreving = envOrConfig("TILBAKEKREVING_URL"),
    )
}

private fun readSecretFromFile(dirPath: String, envName: String, envOrConfig: (String) -> String): String {
    //Bare pga lokal test
    val log = LoggerFactory.getLogger("SecretReader")
    try {
        envOrConfig(envName).let {
            return it
        }
    } catch (e: Exception) {
        log.info("Fant ikke secret i env", e)
    }
    try {
        val file: Path = Paths.get(dirPath).resolve(envName)
        val lines = Files.readAllLines(file)
        return lines.firstOrNull() ?: throw IOException("File $file is empty")
    } catch (exception: IOException) {
        throw RuntimeException("Failed to read property value from $dirPath og envname $envName", exception)
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