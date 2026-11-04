package no.nav.supstonad.simulering

import arrow.core.left
import arrow.core.right
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.supstonad.FakeSamlTokenProvider
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import tilbakekreving.startedWireMockServerWithCorrelationId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SimuleringSoapClientTest {
    @Test
    fun `logger bare feilresponser og bare til sikkerlogg`() {
        val sikkerLogger = LoggerFactory.getLogger("team-logs-logger") as Logger
        val vanligLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val sikreLogger = ListAppender<ILoggingEvent>().apply { start() }
        val vanligeLogger = ListAppender<ILoggingEvent>().apply { start() }
        sikkerLogger.addAppender(sikreLogger)
        vanligLogger.addAppender(vanligeLogger)

        try {
            startedWireMockServerWithCorrelationId {
                for (status in listOf(400, 500, 200)) {
                    sikreLogger.list.clear()
                    vanligeLogger.list.clear()
                    val respons = "<response>simulering-$status</response>"
                    stubFor(
                        WireMock.post(WireMock.urlPathEqualTo("/simulering/$status"))
                            .willReturn(WireMock.aResponse().withStatus(status).withBody(respons)),
                    )

                    val resultat = SimuleringSoapClient(
                        baseUrl = "${baseUrl()}/simulering/$status",
                        samlTokenProvider = FakeSamlTokenProvider(),
                    ).simulerUtbetaling("<request/>")

                    if (status == 200) {
                        assertEquals(respons.right(), resultat)
                        assertTrue(sikreLogger.list.isEmpty())
                    } else {
                        assertEquals(SimuleringFeilet.TekniskFeil.left(), resultat)
                        val loggmelding = sikreLogger.list.single().formattedMessage
                        assertTrue(loggmelding.contains(status.toString()))
                        assertTrue(loggmelding.contains(respons))
                    }
                    assertFalse(vanligeLogger.list.any { it.formattedMessage.contains(respons) })
                }
            }
        } finally {
            sikkerLogger.detachAppender(sikreLogger)
            vanligLogger.detachAppender(vanligeLogger)
            sikreLogger.stop()
            vanligeLogger.stop()
        }
    }
}
