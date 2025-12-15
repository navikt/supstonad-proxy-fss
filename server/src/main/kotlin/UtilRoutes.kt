package no.nav.supstonad

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.utilApi() {
     get("/ping") {
         call.respond("pong")
     }
    get("/isAlive") {
        call.respond(HttpStatusCode.OK)
    }

    post("/isReady") {
        call.respond(HttpStatusCode.OK)
    }
}