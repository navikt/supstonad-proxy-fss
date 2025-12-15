package no.nav.supstonad

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get

fun Route.utilApi() {
     get("/ping") {
         call.respond("pong")
     }
    get("/isalive") {
        application.log.info("isalive")
        println("printisalive")
        call.respond(HttpStatusCode.OK)
    }

    get("/isready") {
        call.respond(HttpStatusCode.OK)
    }
}