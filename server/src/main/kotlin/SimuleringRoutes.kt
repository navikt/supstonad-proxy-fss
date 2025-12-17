package no.nav.supstonad

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import no.nav.supstonad.simulering.SimuleringFeilet
import no.nav.supstonad.simulering.SimuleringSoapClient

fun Route.SimuleringRoutes(
    simuleringSoapClient: SimuleringSoapClient
) {
    val logger = application.log
    post("simulerberegning") {
        logger.info("Simulering")
        val soapBody = call.receiveText() // TODO
        val soapResponse = simuleringSoapClient.simulerUtbetaling(soapBody).getOrElse {
            val feilmelding = SimuleringErrorDto(
                when (it) {
                    SimuleringFeilet.TekniskFeil -> SimuleringErrorCode.UTENFOR_APNINGSTID
                    SimuleringFeilet.UtenforÅpningstid -> SimuleringErrorCode.TEKNISK_FEIL
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