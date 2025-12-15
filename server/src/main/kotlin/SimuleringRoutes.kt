package no.nav.supstonad

import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post

//TODO: få inn dtos
fun Route.simuleringRoutes() {
    val logger = application.log
    post("simulerberegning") {
        logger.info("Simulering")
        call.respond("Simulering")
    }
}