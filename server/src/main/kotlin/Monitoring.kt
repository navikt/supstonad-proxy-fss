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

private const val IS_ALIVE_PATH = "/isalive"
private const val IS_READY_PATH = "/isready"
private const val METRICS_PATH = "/metrics"
private const val PING_PATH = "/ping"

internal val naisPaths = listOf(IS_ALIVE_PATH, IS_READY_PATH, METRICS_PATH, PING_PATH)

fun Application.configureMonitoring() {
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }
    install(CallLogging) {
        level = Level.INFO

        filter { call ->
            !naisPaths.contains(call.request.path())
        }
        format { call ->
            val status = call.response.status()?.value ?: "unknown"
            val method = call.request.httpMethod.value
            val path = call.request.path()
            "HTTP $method $path -> $status"
        }
    }
}