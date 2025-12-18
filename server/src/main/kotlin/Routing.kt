package no.nav.supstonad

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.supstonad.simulering.SimuleringRoutes
import no.nav.supstonad.simulering.SimuleringSoapClient
import no.nav.supstonad.tilbakekreving.TilbakekrevingSoapClient
import no.nav.supstonad.tilbakekreving.TilkbakekrevingRoutes
import java.time.Clock

fun Application.configureRouting(config: Config) {

    install(Resources)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            this@configureRouting.log.warn("Fikk exception", cause)
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        val clock = Clock.systemUTC()
        utilApi()
        authenticate {
            //TODO: for å sjekke auth fra su-se-bakover
            get("/pingAuth") {
                call.respond("pong")
            }
            SimuleringRoutes(
                SimuleringSoapClient(
                    baseUrl = config.simuleringUrl,
                    samlTokenProvider = StsSamlClient(
                        baseUrl = config.tilbakekrevingSoapUrl,
                        serviceUser = config.sts.serviceuser,
                        clock = clock
                    ),
                )
            )
            TilkbakekrevingRoutes(
                TilbakekrevingSoapClient(
                    baseUrl = config.tilbakekrevingUrl,
                    samlTokenProvider = StsSamlClient(
                        baseUrl = config.tilbakekrevingSoapUrl,
                        serviceUser = config.sts.serviceuser,
                        clock = clock
                    ),
                )
            )
        }
    }
}
