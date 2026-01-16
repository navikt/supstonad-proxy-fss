package no.nav.supstonad.simulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.supstonad.SamlTokenProvider
import no.nav.supstonad.buildSoapEnvelope
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

private const val ACTION =
    "http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt/simulerFpService/simulerBeregningRequest"


sealed interface SimuleringFeilet {
    data object UtenforÅpningstid : SimuleringFeilet
    data object TekniskFeil : SimuleringFeilet
}

class SimuleringSoapClient(
    private val baseUrl: String,
    private val samlTokenProvider: SamlTokenProvider,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun simulerUtbetaling(soapBody: String): Either<SimuleringFeilet, String> {
        val assertion = samlTokenProvider.samlToken().getOrElse {
            // SamlTokenProvider logger, men mangler kontekst.
            log.error(
                "Feil ved simulering: Kunne ikke hente SAML-token.",
                RuntimeException("Trigger stacktrace"),
            )
            return SimuleringFeilet.TekniskFeil.left()
        }.toString()
        val soapRequest = buildSoapEnvelope(
            action = ACTION,
            messageId = UUID.randomUUID().toString(),
            serviceUrl = baseUrl,
            assertion = assertion,
            body = soapBody,
        )
        // TODO jah: Kan fjerne debug etter vi har fått verifisert.
        log.debug("Simulerer utbetaling baseUrl: $baseUrl.")

        return Either.catch {
            val httpRequest = HttpRequest.newBuilder(URI(baseUrl))
                .header("SOAPAction", ACTION)
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
                .build()
            val (response: String?, status: Int) = client.send(httpRequest, HttpResponse.BodyHandlers.ofString()).let {
                it.body() to it.statusCode()
            }
            // TODO jah: Kan fjerne debug etter vi har fått verifisert.
            log.debug("Simuleringsrespons for statusCode: {}.", status)
            if (status != 200) {
                log.error(
                    "Feil ved simulering: Forventet statusCode 200, statusCode: $status.",
                    RuntimeException("Trigger stacktrace"),
                )
                return SimuleringFeilet.TekniskFeil.left()
            }

            response ?: return SimuleringFeilet.TekniskFeil.left().also {
                log.error(
                    "Feil ved simulering: Simuleringsresponsen fra Oppdrag var tom (forventet soap). statusCode: $status.",
                    RuntimeException("Trigger stacktrace"),
                )
            }
        }.mapLeft { error: Throwable ->
            when (error) {
                is IOException -> {
                    log.warn(
                        "Feil ved simulering: Antar Oppdrag/UR stengt.",
                        RuntimeException("Trigger stacktrace"),
                    )
                    SimuleringFeilet.UtenforÅpningstid
                }

                else -> {
                    log.warn(
                        "Feil ved simulering: Ukjent feil.",
                        RuntimeException("Trigger stacktrace"),
                    )
                    SimuleringFeilet.TekniskFeil
                }
            }
        }.flatMap { soapResponse -> soapResponse.right() }
    }
}