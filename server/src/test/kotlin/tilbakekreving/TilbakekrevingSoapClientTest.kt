package tilbakekreving

import arrow.core.getOrElse
import arrow.core.left
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.supstonad.FakeSamlTokenProvider
import no.nav.supstonad.tilbakekreving.TilbakekrevingFeil
import no.nav.supstonad.tilbakekreving.TilbakekrevingSoapClient
import no.nav.supstonad.tilbakekreving.tilbakekrevingSoapResponseConversionError
import no.nav.supstonad.tilbakekreving.tilbakekrevingSoapResponseOk
import org.junit.jupiter.api.Test

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
                soapEndpointTK = "${this.baseUrl()}/a",
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
                soapEndpointTK = "${this.baseUrl()}/c",
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
                soapEndpointTK = "${this.baseUrl()}/c",
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