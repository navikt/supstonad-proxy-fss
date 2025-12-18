package no.nav.supstonad.simulering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.supstonad.SamlTokenProvider
import no.nav.supstonad.buildSoapEnvelope
import no.nav.supstonad.logger
import no.nav.supstonad.sikkerlogg
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
                "Feil ved simulering: Kunne ikke hente SAML-token. Se sikkerlogg for soap body.",
                RuntimeException("Trigger stacktrace"),
            )
            logger.error(sikkerlogg, "Feil ved simulering: Kunne ikke hente SAML-token. soapBody: $soapBody")
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
        log.debug("Simulerer utbetaling baseUrl: $baseUrl. Se sikkerlogg for mer kontekst.")
        logger.debug(sikkerlogg, "Simulerer utbetaling soapRequest: {}", soapRequest)
        return Either.catch {
            val httpRequest = HttpRequest.newBuilder(URI(baseUrl))
                .header("SOAPAction", ACTION)
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
                .build()
            val (response: String?, status: Int) = client.send(httpRequest, HttpResponse.BodyHandlers.ofString()).let {
                it.body() to it.statusCode()
            }
            // TODO jah: Kan fjerne debug etter vi har fått verifisert.
            log.debug("Simuleringsrespons for statusCode: {}. Se sikkerlogg for mer kontekst.", status)
            logger.debug(sikkerlogg, "Simuleringsrespons statusCode: {}, response: {}", status, response)
            if (status != 200) {
                log.error(
                    "Feil ved simulering: Forventet statusCode 200, statusCode: $status. Se sikkerlogg for request.",
                    RuntimeException("Trigger stacktrace"),
                )
                logger.error(sikkerlogg, "Feil ved simulering: Forventet statusCode 200, statusCode: $status, soap-response: $response, soap-request: $soapRequest")
                return SimuleringFeilet.TekniskFeil.left()
            }

            response ?: return SimuleringFeilet.TekniskFeil.left().also {
                log.error(
                    "Feil ved simulering: Simuleringsresponsen fra Oppdrag var tom (forventet soap). statusCode: $status. Se sikkerlogg for request.",
                    RuntimeException("Trigger stacktrace"),
                )
                logger.error(sikkerlogg, "Simuleringsresponsen fra Oppdrag var tom (forventet soap). statusCode: $status, soap-request: $soapRequest")
            }
        }.mapLeft { error: Throwable ->
            when (error) {
                is IOException -> {
                    log.warn(
                        "Feil ved simulering: Antar Oppdrag/UR stengt. Se sikkerlogg for kontekst.",
                        RuntimeException("Trigger stacktrace"),
                    )
                    logger.warn(sikkerlogg, "Feil ved simulering: Antar Oppdrag/UR stengt. Soap-request: $soapRequest", error)
                    SimuleringFeilet.UtenforÅpningstid
                }

                else -> {
                    log.warn(
                        "Feil ved simulering: Ukjent feil. Se sikkerlogg for kontekst.",
                        RuntimeException("Trigger stacktrace"),
                    )
                    logger.warn(sikkerlogg, "Feil ved simulering: Ukjent feil. Soap-request: $soapRequest", error)
                    SimuleringFeilet.TekniskFeil
                }
            }
        }.flatMap { soapResponse -> soapResponse.right() }
    }
}