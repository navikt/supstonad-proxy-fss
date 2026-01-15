package no.nav.supstonad.simulering

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import no.nav.supstonad.receiveTextUTF8

fun Route.SimuleringRoutes(
    simuleringSoapClient: SimuleringSoapClient
) {
    val logger = application.log
    post("simulerberegning") {
        logger.info("Simulering")
        val soapBody = call.receiveTextUTF8()
        val soapResponse = simuleringSoapClient.simulerUtbetaling(soapBody).getOrElse {
            val feilmelding = SimuleringErrorDto(
                when (it) {
                    SimuleringFeilet.TekniskFeil -> SimuleringErrorCode.TEKNISK_FEIL
                    SimuleringFeilet.UtenforÅpningstid -> SimuleringErrorCode.UTENFOR_APNINGSTID
                }
            )
            call.respond(HttpStatusCode.InternalServerError, feilmelding)
        }
        call.respond(soapResponse)
    }
}

data class SimuleringErrorDto(
    val code: SimuleringErrorCode,
)

enum class SimuleringErrorCode {
    UTENFOR_APNINGSTID,
    TEKNISK_FEIL,
}