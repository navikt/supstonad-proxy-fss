package no.nav.supstonad

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallId) { //TODO: fiks
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()?.value ?: "unknown"
            val method = call.request.httpMethod.value
            val path = call.request.path()
            "HTTP $method $path -> $status"
        }
    }
}


val logger: Logger = LoggerFactory.getLogger("team-logs-logger")
val sikkerlogg = MarkerFactory.getMarker("TEAM_LOGS")
