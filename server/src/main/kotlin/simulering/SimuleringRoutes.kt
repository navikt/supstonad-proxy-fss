package no.nav.supstonad.simulering

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

fun Route.SimuleringRoutes(
    simuleringSoapClient: SimuleringSoapClient
) {
    val logger = application.log
    /**
     * POST /simulerberegning
     * - 200: application/xml (SOAP)
     * - 500: application/json (SimuleringErrorDto)
     */
    post("simulerberegning") {
        val soapBody = call.receiveTextUTF8()
        val soapResponse = simuleringSoapClient.simulerUtbetaling(soapBody).getOrElse {
            val feilmelding = SimuleringErrorDto(
                when (it) {
                    SimuleringFeilet.TekniskFeil -> SimuleringErrorCode.TEKNISK_FEIL
                    SimuleringFeilet.UtenforÅpningstid -> SimuleringErrorCode.UTENFOR_APNINGSTID
                }
            )
            logger.error("Feil ved simulering: $feilmelding")
            return@post call.respond(HttpStatusCode.InternalServerError, feilmelding)
        }
        logger.info("Simulering OK response")
        call.respondText(soapResponse, contentType = ContentType.Application.Xml, status = HttpStatusCode.OK)
    }
}

data class SimuleringErrorDto(
    val code: SimuleringErrorCode,
)

enum class SimuleringErrorCode {
    UTENFOR_APNINGSTID,
    TEKNISK_FEIL,
}