package no.nav.supstonad

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.utilApi() {
     get("/ping") {
         call.respond("pong")
     }
    get("/isalive") {
        call.respond(HttpStatusCode.OK)
    }

    get("/isready") {
        call.respond(HttpStatusCode.OK)
    }
}