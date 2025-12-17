package no.nav.supstonad.tilbakekreving

import arrow.core.Either
import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.supstonad.StsSamlClient
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

/**
 * Dersom vi ikke kunne sende kravgrunnlaget (for å avgjøre om feilutbetalingen skulle føre til tilbakekreving eller ikke) til økonomisystemet
 */
sealed interface TilbakekrevingFeil {
    //data object AlvorlighetsgradFeil : KunneIkkeSendeTilbakekrevingsvedtak
    data object FeilStatusFraOppdrag : TilbakekrevingFeil
    data object UkjentFeil : TilbakekrevingFeil
    data object KlarteIkkeHenteSamlToken : TilbakekrevingFeil
    //data object KlarteIkkeSerialisereRequest : KunneIkkeSendeTilbakekrevingsvedtak
}

class TilbakekrevingSoapClient(
    private val baseUrl: String,
    private val samlTokenProvider: StsSamlClient,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    /**
     * Sender informasjon til oppdrag hvordan vi vil avgjøre om vi vil kreve tilbake eller ikke.
     *
     * @param attestertAv Saksbehandleren som har attestert vedtaket og trykker iverksett. Ideélt sett skulle vi sendt både saksbehandler og attestant, siden økonomiloven krever attstant.
     */
    fun sendTilbakekrevingsvedtak(
        soapBody: String
    ): Either<TilbakekrevingFeil, String> {

        return Either.catch {
            val assertion = getSamlToken(soapBody).getOrElse { return it.left() }

            val soapRequest = buildSoapEnvelope(
                action = TILBAKEKREV_ACTION,
                messageId = UUID.randomUUID().toString(),
                serviceUrl = baseUrl,
                assertion = assertion,
                body = soapBody,
            )
            val httpRequest = HttpRequest.newBuilder(URI(baseUrl))
                .header("SOAPAction", TILBAKEKREV_ACTION)
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
                .build()
            val (soapResponse: String?, status: Int) = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
                .let {
                    it.body() to it.statusCode()
                }
            if (status != 200) {
                log.error(
                    "Feil ved sending av tilbakekrevingsvedtak: Forventet statusCode 200, statusCode: $status. Se sikkerlogg for request.",
                    RuntimeException("Trigger stacktrace"),
                )
                logger.error(sikkerlogg, "Feil ved sending av tilbakekrevingsvedtak: Forventet statusCode 200, statusCode: $status, Response: $soapResponse Request: $soapRequest")
                return TilbakekrevingFeil.FeilStatusFraOppdrag.left()
            }
            soapResponse.right()
            //kontrollerResponse(soapRequest, soapResponse).map { mapKontrollertResponse(soapResponse, soapRequest) } TODO bjg kontroller her eller bakover?
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
        }.flatten()
    }

    /*
    https://confluence.adeo.no/display/OKSY/Detaljer+om+de+enkelte+ID-koder?preview=/178067795/178067800/worddav1549728a4f1bb4ae0651e7017a7cae86.png
     */
    fun annullerKravgrunnlag(
        soapBody: String
    ): Either<TilbakekrevingFeil, String> {
        val assertion = getSamlToken(soapBody).getOrElse {
            return TilbakekrevingFeil.KlarteIkkeHenteSamlToken.left()
        }

        return Either.catch {
            val soapRequest = buildSoapEnvelope(
                action = ANNULLER_ACTION,
                messageId = UUID.randomUUID().toString(),
                serviceUrl = baseUrl,
                assertion = assertion,
                body = soapBody,
            )
            val httpRequest = HttpRequest.newBuilder(URI(baseUrl))
                .header("SOAPAction", ANNULLER_ACTION)
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
                .build()
            val (soapResponse: String?, status: Int) = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
                .let {
                    it.body() to it.statusCode()
                }

            if (status != 200) {
                log.error(
                    "Feil ved annullering av kravgrunnlag: Forventet statusCode 200 for, statusCode: $status. Se sikkerlogg for request.",
                    RuntimeException("Trigger stacktrace"),
                )
                logger.error(sikkerlogg, "Feil ved annullering av kravgrunnlag: Forventet statusCode 200, statusCode: $status, Response: $soapResponse Request: $soapRequest")
                return TilbakekrevingFeil.FeilStatusFraOppdrag.left()
            }
            soapResponse.right()
            /*
            TODO bjg kontroller?
            kontrollerResponse(soapRequest, soapResponse, saksnummer)
                .map {
                    mapKontrollertResponse(saksnummer, soapResponse, soapRequest)
                }.mapLeft {
                    tilbakekreving.domain.vedtak.TilbakekrevingFeil.FeilStatusFraOppdrag
                }
             */
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
        }.flatten()
    }

    private fun getSamlToken(soapBody: String): Either<TilbakekrevingFeil, String> {
        return samlTokenProvider.samlToken().getOrElse {
            // SamlTokenProvider logger, men mangler kontekst.
            log.error(
                "Feil ved sending av tilbakekrevingsvedtak: Kunne ikke hente SAML-token. Se sikkerlogg for soap body.",
                RuntimeException("Trigger stacktrace"),
            )
            logger.error(sikkerlogg, "Feil ved sending av tilbakekrevingsvedtak: Kunne ikke hente SAML-token. soapBody: $soapBody")
            return TilbakekrevingFeil.KlarteIkkeHenteSamlToken.left()
        }.toString().right()
    }

    /**
     * Dersom vi får en alvorlighetsgrad som ikke er OK, så skal vi logge dette og returnere en feil.
     * I andre tilfeller antar vi at alt er OK, men logger error der noe må følges opp manuelt.
     */
    /*
    private fun kontrollerResponse(
        request: String,
        response: String?,
    ): Either<KunneIkkeSendeTilbakekrevingsvedtak, Unit> {
        val deserialisert = response?.deserializeTilbakekrevingsvedtakResponse(request) ?: run {
            log.error("Fikk null-response ved sending av tilbakekrevingsvedtak. Antar det var OK. Må følges opp manuelt. Saksnummer $saksnummer, se sikkerlogg for detaljer.")
            logger.error(sikkerlogg, "Fikk null-response ved sending av tilbakekrevingsvedtak. Antar det var OK. Må følges opp manuelt. Saksnummer $saksnummer. Request: $request.")
            return Unit.right()
        }
        return deserialisert.fold(
            {
                // Vi logger i funksjonen. Dersom vi ikke klarer deserialisere antar vi at det har gått OK. Men det må følges opp manuelt.
                Unit.right()
            },
            { result ->
                when (val a = result.mmel.alvorlighetsgrad) {
                    null -> {
                        log.error(
                            "Mottok ikke mmel.alvorlighetsgrad. Antar det var OK. Må følges opp manuelt. Saksnummer $saksnummer. Se sikkerlogg for detaljer.",
                            RuntimeException("Legger på stacktrace for enklere debug"),
                        )
                        logger.error(sikkerlogg, 
                            "Mottok ikke mmel.alvorlighetsgrad. Antar det var OK. Må følges opp manuelt. Saksnummer $saksnummer. Response $response. Request: $request.",
                        )
                        Unit.right()
                    }

                    else -> tilbakekreving.infrastructure.client.dto.Alvorlighetsgrad.Companion.fromString(a).fold(
                        {
                            log.error(
                                "Feil ved sending av tilbakekrevingsvedtak: Ukjent alvorlighetsgrad: $a. Antar det var OK. Må følges opp manuelt. Se sikkerlogg for detaljer.",
                                RuntimeException("Trigger stacktrace"),
                            )
                            logger.error(sikkerlogg, "Feil ved sending av tilbakekrevingsvedtak: Ukjent alvorlighetsgrad: $a. Antar det var OK. Må følges opp manuelt. Response: $response, Request: $request.")
                            Unit.right()
                        },
                        { alvorlighetsgrad ->
                            kontrollerAlvorlighetsgrad(alvorlighetsgrad, saksnummer, response, request)
                        },
                    )
                }
            },
        )
    }

    private fun mapKontrollertResponse(soapResponse: String?, soapRequest: String): RåTilbakekrevingsvedtakForsendelse {
        log.info("SOAP kall mot tilbakekrevingskomponenten OK. Se sikkerlogg for detaljer.")
        no.nav.su.se.bakover.common.sikkerLogg.info("SOAP kall mot tilbakekrevingskomponenten OK. Response: $soapResponse, Request: $soapRequest.")

        return RåTilbakekrevingsvedtakForsendelse(
            requestXml = soapRequest,
            tidspunkt = no.nav.su.se.bakover.common.tid.Tidspunkt.Companion.now(clock),
            responseXml = soapResponse
                ?: "soapResponse var null - dette er sannsynligvis en teksnisk feil, f.eks. ved at http-body er lest mer enn 1 gang.",
        )
    }

    private fun kontrollerAlvorlighetsgrad(
        alvorlighetsgrad: tilbakekreving.infrastructure.client.dto.Alvorlighetsgrad,
        saksnummer: no.nav.su.se.bakover.common.domain.Saksnummer,
        response: String,
        request: String,
    ): Either<tilbakekreving.domain.vedtak.KunneIkkeSendeTilbakekrevingsvedtak, Unit> {
        return when (alvorlighetsgrad) {
            tilbakekreving.infrastructure.client.dto.Alvorlighetsgrad.OK -> Unit.right()

            tilbakekreving.infrastructure.client.dto.Alvorlighetsgrad.OK_MED_VARSEL,
            -> {
                log.error(
                    "Fikk et varsel fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. Saksnummer $saksnummer. Se sikkerlogg for detaljer, og request.",
                    RuntimeException("Legger på stacktrace for enklere debug"),
                )
                logger.error(sikkerlogg, 
                    "Fikk et varsel fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. Den er fremdeles sendt OK. Saksnummer $saksnummer. Response $response. Request: $request. ",
                )
                Unit.right()
            }

            tilbakekreving.infrastructure.client.dto.Alvorlighetsgrad.ALVORLIG_FEIL,
            tilbakekreving.infrastructure.client.dto.Alvorlighetsgrad.SQL_FEIL,
            -> {
                log.error(
                    "Fikk $alvorlighetsgrad fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. Saksnummer $saksnummer. Se sikkerlogg for detaljer.",
                    RuntimeException("Legger på stacktrace for enklere debug"),
                )
                logger.error(sikkerlogg, 
                    "Fikk et varsel fra tilbakekrevingskomponenten når vi vedtok en tilbakekreving. Saksnummer $saksnummer. Response $response. Request: $request.",
                )
                tilbakekreving.domain.vedtak.KunneIkkeSendeTilbakekrevingsvedtak.AlvorlighetsgradFeil.left()
            }
        }
    }
    */
}