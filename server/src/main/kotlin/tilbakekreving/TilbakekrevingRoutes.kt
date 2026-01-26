package no.nav.supstonad.tilbakekreving

import arrow.core.getOrElse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import no.nav.supstonad.receiveTextUTF8
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("no.nav.supstonad.tilbakekreving")
fun Route.TilkbakekrevingRoutes(
    tilbakekrevingSoapClient: TilbakekrevingSoapClient
) {
    post("tilbakekreving/vedtak") {
        val soapBody = call.receiveTextUTF8()
        val soapResponse = tilbakekrevingSoapClient.sendTilbakekrevingsvedtak(soapBody).getOrElse {
            logger.error("Kunne ikke sende tilbakekrevingsvedtak, feil: $it")
             return@post call.respond(HttpStatusCode.InternalServerError, it.toTilbakekrevingErrorDto())
        }
        logger.info("Tilbakekreving sendt OK")
        call.respondText(soapResponse, contentType = ContentType.Application.Xml, status = HttpStatusCode.OK)
    }

    post("tilbakekreving/annuller") {
        val soapBody = call.receiveTextUTF8()
        val soapResponse = tilbakekrevingSoapClient.annullerKravgrunnlag(soapBody).getOrElse {
            logger.error("Kunne ikke annullere kravgrunnlag, feil: $it")
            return@post call.respond(HttpStatusCode.InternalServerError, it.toTilbakekrevingErrorDto())
        }
        logger.info("Tilbakekreving annullert OK")
        call.respondText(soapResponse, contentType = ContentType.Application.Xml, status = HttpStatusCode.OK)
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
