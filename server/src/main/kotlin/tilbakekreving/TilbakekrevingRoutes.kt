package no.nav.supstonad.tilbakekreving

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import no.nav.supstonad.receiveTextUTF8

fun Route.TilkbakekrevingRoutes(
    tilbakekrevingSoapClient: TilbakekrevingSoapClient
) {
    val logger = application.log
    post("tilbakekreving/vedtak") {
        val soapBody = call.receiveTextUTF8()
        val soapResponse = tilbakekrevingSoapClient.sendTilbakekrevingsvedtak(soapBody).getOrElse {
            logger.error("Kunne ikke sende tilbakekrevingsvedtak, feil: $it")
             return@post call.respond(HttpStatusCode.InternalServerError, it.toTilbakekrevingErrorDto())
        }
        logger.info("Tilbakekreving sendt OK")
        call.respond(soapResponse)
    }

    post("tilbakekreving/annuller") {
        val soapBody = call.receiveTextUTF8()
        val soapResponse = tilbakekrevingSoapClient.annullerKravgrunnlag(soapBody).getOrElse {
            logger.error("Kunne ikke annullere kravgrunnlag, feil: $it")
            return@post call.respond(HttpStatusCode.InternalServerError, it.toTilbakekrevingErrorDto())
        }
        logger.info("Tilbakekreving annullert OK")
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
