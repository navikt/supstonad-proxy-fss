package tilbakekreving

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.assertions.fail
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.supstonad.FakeSamlTokenProvider
import no.nav.supstonad.KunneIkkeHenteSamlToken
import no.nav.supstonad.SamlToken
import no.nav.supstonad.SamlTokenProvider
import no.nav.supstonad.tilbakekreving.TilbakekrevingFeil
import no.nav.supstonad.tilbakekreving.TilbakekrevingSoapClient
import no.nav.supstonad.tilbakekreving.tilbakekrevingSoapResponseConversionError
import no.nav.supstonad.tilbakekreving.tilbakekrevingSoapResponseOk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class TilbakekrevingSoapClientTest {
    @Test
    fun `conversion error`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/a", "http://okonomi.nav.no/tilbakekrevingService/TilbakekrevingPortType/tilbakekrevingsvedtakRequest").willReturn(
                    WireMock.jsonResponse(tilbakekrevingSoapResponseConversionError(), 500),
                ),
            )
            TilbakekrevingSoapClient(
                baseUrl = "${this.baseUrl()}/a",
                samlTokenProvider = FakeSamlTokenProvider(),
            ).sendTilbakekrevingsvedtak(
                soapBody = ""
            ) shouldBe TilbakekrevingFeil.FeilStatusFraOppdrag.left()
        }
    }

    @Test
    fun `happy case`() {
        val forventetRespons = tilbakekrevingSoapResponseOk()
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/c", "http://okonomi.nav.no/tilbakekrevingService/TilbakekrevingPortType/tilbakekrevingsvedtakRequest").willReturn(
                    WireMock.okXml(forventetRespons),
                ),
            )
            TilbakekrevingSoapClient(
                baseUrl = "${this.baseUrl()}/c",
                samlTokenProvider = FakeSamlTokenProvider(),
            ).sendTilbakekrevingsvedtak(
                soapBody = ""
            ).getOrElse { fail("""$it""") } shouldBe forventetRespons
        }
    }

    @Test
    fun `annullerer et kravgrunnlag`() {
        val forventetRespons = tilbakekrevingSoapResponseOk()

        startedWireMockServerWithCorrelationId {
            stubFor(wiremockBuilder("/c", "http://okonomi.nav.no/tilbakekrevingService/TilbakekrevingPortType/kravgrunnlagAnnulerRequest").willReturn(WireMock.okXml(forventetRespons)))
            TilbakekrevingSoapClient(
                baseUrl = "${this.baseUrl()}/c",
                samlTokenProvider = FakeSamlTokenProvider(),
            ).annullerKravgrunnlag(
                soapBody = ""

            ).getOrElse { fail("""$it""") } shouldBe forventetRespons

        }
    }
}

private fun wiremockBuilder(testUrl: String, portType: String): MappingBuilder =
    WireMock.post(WireMock.urlPathEqualTo(testUrl)).withHeader(
        "SOAPAction",
        WireMock.equalTo(portType),
    )

fun startedWireMockServerWithCorrelationId(block: WireMockServer.() -> Unit) {
    runBlocking {
        val server = WireMockServer(WireMockConfiguration.options().dynamicPort())
        server.start()

        try {
            block(server)
        } finally {
            server.stop()
        }
    }
}