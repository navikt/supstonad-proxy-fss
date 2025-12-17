package no.nav.supstonad.tilbakekreving

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post

fun Route.TilkbakekrevingRoutes(
    tilbakekrevingSoapClient: TilbakekrevingSoapClient
) {
    val logger = application.log
    post("tilbakekreving/vedtak") {
        logger.info("Tilbakekreving vedtak")
        val soapBody = call.receiveText() // TODO
        val soapResponse = tilbakekrevingSoapClient.sendTilbakekrevingsvedtak(soapBody).getOrElse {
            call.respond(HttpStatusCode.InternalServerError, it.toTilbakekrevingErrorDto())
        }
        call.respond(soapResponse)
    }

    post("tilbakekreving/annuller") {
        logger.info("Tilbakekreving annuller")
        val soapBody = call.receiveText() // TODO
        val soapResponse = tilbakekrevingSoapClient.annullerKravgrunnlag(soapBody).getOrElse {
            call.respond(HttpStatusCode.InternalServerError, it.toTilbakekrevingErrorDto())
        }
        call.respond(soapResponse)
    }
}

fun TilbakekrevingFeil.toTilbakekrevingErrorDto() = TilbakekrevingErrorDto(
    when (this) {
        TilbakekrevingFeil.FeilStatusFraOppdrag -> TilbakekrevingErrorCode.FeilStatusFraOppdrag
        TilbakekrevingFeil.KlarteIkkeHenteSamlToken -> TilbakekrevingErrorCode.KlarteIkkeHenteSamlToken
        TilbakekrevingFeil.UkjentFeil -> TilbakekrevingErrorCode.UkjentFeil
        TilbakekrevingFeil.NullRespons -> TilbakekrevingErrorCode.NullRespons
    }
)

data class TilbakekrevingErrorDto(
    val code: TilbakekrevingErrorCode,
)

enum class TilbakekrevingErrorCode {
    FeilStatusFraOppdrag,
    KlarteIkkeHenteSamlToken,
    NullRespons,
    UkjentFeil
}
