package no.nav.supstonad.tilbakekreving

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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

private const val TILBAKEKREV_ACTION =
    "http://okonomi.nav.no/tilbakekrevingService/TilbakekrevingPortType/tilbakekrevingsvedtakRequest"

private const val ANNULLER_ACTION =
    "http://okonomi.nav.no/tilbakekrevingService/TilbakekrevingPortType/kravgrunnlagAnnulerRequest"


sealed interface TilbakekrevingFeil {
    data object FeilStatusFraOppdrag : TilbakekrevingFeil
    data object NullRespons: TilbakekrevingFeil
    data object UkjentFeil : TilbakekrevingFeil
    data object KlarteIkkeHenteSamlToken : TilbakekrevingFeil
}

class TilbakekrevingSoapClient(
    private val baseUrl: String,
    private val samlTokenProvider: SamlTokenProvider,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun sendTilbakekrevingsvedtak(soapBody: String) = tilbakekrevingSoapRequest(soapBody, TILBAKEKREV_ACTION)
    fun annullerKravgrunnlag(soapBody: String) = tilbakekrevingSoapRequest(soapBody, ANNULLER_ACTION)

    private fun tilbakekrevingSoapRequest(
        soapBody: String,
        action: String
    ): Either<TilbakekrevingFeil, String> {
        val assertion = getSamlToken(soapBody).getOrElse {
            return TilbakekrevingFeil.KlarteIkkeHenteSamlToken.left()
        }
        val soapRequest = buildSoapEnvelope(
            action = action,
            messageId = UUID.randomUUID().toString(),
            serviceUrl = baseUrl,
            assertion = assertion,
            body = soapBody,
        )
        return Either.catch {
            val httpRequest = HttpRequest.newBuilder(URI(baseUrl))
                .header("SOAPAction", action)
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
                .build()
            val (soapResponse: String?, status: Int) = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
                .let {
                    it.body() to it.statusCode()
                }

            if (status != 200) {
                log.error(
                    "Feil ved kall mot tilbakekrevingskomponenten: Forventet statusCode 200 for, statusCode: $status. Se sikkerlogg for request.",
                    RuntimeException("Trigger stacktrace"),
                )
                logger.error(sikkerlogg, "Feil ved kall mot tilbakekrevingskomponenten: Forventet statusCode 200, statusCode: $status, Response: $soapResponse Request: $soapRequest")
                return TilbakekrevingFeil.FeilStatusFraOppdrag.left()
            }
            soapResponse ?: run {
                log.error("Fikk null-response ved kall mot tilbakekrevingskomponenten. Antar det var OK. Må følges opp manuelt. Se sikkerlogg for detaljer.")
                logger.error(sikkerlogg, "Fikk null-response ved kall mot tilbakekrevingskomponenten. Antar det var OK. Må følges opp manuelt. Request: $soapRequest.")
                return TilbakekrevingFeil.NullRespons.left()
            }
        }.mapLeft { throwable ->
            log.error(
                "SOAP kall mot tilbakekrevingskomponenten feilet. Se sikkerlogg for detaljer.",
                RuntimeException("Legger på stacktrace for enklere debug"),
            )
            logger.error(
                sikkerlogg,
                "SOAP kall mot tilbakekrevingskomponenten feilet. Se vanlig logg for stacktrace.",
                throwable,
            )
            TilbakekrevingFeil.UkjentFeil
        }.flatMap { soapResponse -> soapResponse.right() }
    }

    private fun getSamlToken(soapBody: String): Either<TilbakekrevingFeil, String> {
        return samlTokenProvider.samlToken().getOrElse {
            log.error(
                "Feil ved kall mot tilbakekrevingskomponenten: Kunne ikke hente SAML-token. Se sikkerlogg for soap body.",
                RuntimeException("Trigger stacktrace"),
            )
            logger.error(sikkerlogg, "Feil ved kall mot tilbakekrevingskomponenten: Kunne ikke hente SAML-token. soapBody: $soapBody")
            return TilbakekrevingFeil.KlarteIkkeHenteSamlToken.left()
        }.toString().right()
    }

}