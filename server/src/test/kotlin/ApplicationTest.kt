package no.nav.supstonad

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.auth.authenticate
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.supstonad.simulering.SimuleringSoapClient
import no.nav.supstonad.simulering.SimuleringRoutes
import no.nav.supstonad.tilbakekreving.TilbakekrevingSoapClient
import no.nav.supstonad.tilbakekreving.TilkbakekrevingRoutes
import no.nav.supstonad.tilbakekreving.tilbakekrevingSoapResponseOk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ApplicationTest {

    val mockOAuth2Server = MockOAuth2Server()

    @BeforeAll
    fun beforeAll() {
        mockOAuth2Server.start()
    }

    @AfterAll
    fun afterAll() {
        mockOAuth2Server.shutdown()
    }

    val CLIENT_ID = "CLIENT_ID"

    @Test
    fun testBaseRouteAuth() {
        val issuerName = "azure"
        val wellKnown = mockOAuth2Server.wellKnownUrl(issuerName)

        testApplication {
            val appconfig = appConfig(issuerName, wellKnown.toString())
            environment {
                config = HoconApplicationConfig(appconfig)
            }

            application {
                proxyappRoutes()
            }

            val response = client.get("/pingAuth") {
                header(HttpHeaders.Authorization, "Bearer ${mockOAuth2Server.issueToken(issuerName, audience = CLIENT_ID).serialize()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun skalFåUnauthUtentoken() {
        val issuerName = "azure"
        val wellKnown = mockOAuth2Server.wellKnownUrl(issuerName)

        testApplication {
            val appconfig = appConfig(issuerName, wellKnown.toString())
            environment {
                config = HoconApplicationConfig(appconfig)
            }

            application {
                proxyappRoutes()
            }

            val response = client.get("/pingAuth")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `simulering feilrespons serialiseres som JSON med Jackson`() {
        val issuerName = "azure"
        val wellKnown = mockOAuth2Server.wellKnownUrl(issuerName)

        testApplication {
            val appconfig = appConfig(issuerName, wellKnown.toString())
            environment {
                config = HoconApplicationConfig(appconfig)
            }

            application {
                configureSerialization()
                installTokenValidation(environment.config)
                routing {
                    authenticate {
                        get("/test-jackson") {
                            call.respond(no.nav.supstonad.simulering.SimuleringErrorDto(
                                no.nav.supstonad.simulering.SimuleringErrorCode.TEKNISK_FEIL
                            ))
                        }
                    }
                }
            }

            val response = client.get("/test-jackson") {
                header(HttpHeaders.Authorization, "Bearer ${mockOAuth2Server.issueToken(issuerName, audience = CLIENT_ID).serialize()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"code\""), "Responsen skal inneholde JSON med 'code'-felt, var: $body")
            assertTrue(body.contains("TEKNISK_FEIL"), "Responsen skal inneholde feilkoden, var: $body")
        }
    }

    @Test
    fun `tilbakekreving feilrespons serialiseres som JSON med Jackson`() {
        val issuerName = "azure"
        val wellKnown = mockOAuth2Server.wellKnownUrl(issuerName)

        testApplication {
            val appconfig = appConfig(issuerName, wellKnown.toString())
            environment {
                config = HoconApplicationConfig(appconfig)
            }

            application {
                configureSerialization()
                installTokenValidation(environment.config)
                routing {
                    authenticate {
                        get("/test-jackson-tilbakekreving") {
                            call.respond(no.nav.supstonad.tilbakekreving.TilbakekrevingErrorDto(
                                no.nav.supstonad.tilbakekreving.TilbakekrevingErrorCode.FeilStatusFraOppdrag
                            ))
                        }
                    }
                }
            }

            val response = client.get("/test-jackson-tilbakekreving") {
                header(HttpHeaders.Authorization, "Bearer ${mockOAuth2Server.issueToken(issuerName, audience = CLIENT_ID).serialize()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"code\""), "Responsen skal inneholde JSON med 'code'-felt, var: $body")
            assertTrue(body.contains("FeilStatusFraOppdrag"), "Responsen skal inneholde feilkoden, var: $body")
        }
    }

    @Test
    fun `simulerberegning returnerer XML ved OK respons`() {
        val issuerName = "azure"
        val wellKnown = mockOAuth2Server.wellKnownUrl(issuerName)
        val soapResponse = "<soap:Envelope><soap:Body><result>OK</result></soap:Body></soap:Envelope>"

        val wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        try {
            wireMock.stubFor(
                WireMock.post(WireMock.urlPathEqualTo("/simulering"))
                    .willReturn(WireMock.okXml(soapResponse))
            )

            testApplication {
                val appconfig = appConfig(issuerName, wellKnown.toString(), simulerUrl = "${wireMock.baseUrl()}/simulering")
                environment {
                    config = HoconApplicationConfig(appconfig)
                }

                application {
                    configureSerialization()
                    installTokenValidation(environment.config)
                    routing {
                        authenticate {
                            SimuleringRoutes(
                                SimuleringSoapClient(
                                    baseUrl = "${wireMock.baseUrl()}/simulering",
                                    samlTokenProvider = FakeSamlTokenProvider(),
                                )
                            )
                        }
                    }
                }

                val response = client.post("/simulerberegning") {
                    header(HttpHeaders.Authorization, "Bearer ${mockOAuth2Server.issueToken(issuerName, audience = CLIENT_ID).serialize()}")
                    contentType(ContentType.Application.Xml)
                    setBody("<request/>")
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("<result>OK</result>"), "Skal returnere SOAP XML, var: $body")
            }
        } finally {
            wireMock.stop()
        }
    }

    @Test
    fun `tilbakekreving vedtak returnerer XML ved OK respons`() {
        val issuerName = "azure"
        val wellKnown = mockOAuth2Server.wellKnownUrl(issuerName)
        val soapResponse = tilbakekrevingSoapResponseOk()

        val wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        try {
            wireMock.stubFor(
                WireMock.post(WireMock.urlPathEqualTo("/tilbakekreving"))
                    .willReturn(WireMock.okXml(soapResponse))
            )

            testApplication {
                val appconfig = appConfig(issuerName, wellKnown.toString())
                environment {
                    config = HoconApplicationConfig(appconfig)
                }

                application {
                    configureSerialization()
                    installTokenValidation(environment.config)
                    routing {
                        authenticate {
                            TilkbakekrevingRoutes(
                                TilbakekrevingSoapClient(
                                    soapEndpointTK = "${wireMock.baseUrl()}/tilbakekreving",
                                    samlTokenProvider = FakeSamlTokenProvider(),
                                )
                            )
                        }
                    }
                }

                val response = client.post("/tilbakekreving/vedtak") {
                    header(HttpHeaders.Authorization, "Bearer ${mockOAuth2Server.issueToken(issuerName, audience = CLIENT_ID).serialize()}")
                    contentType(ContentType.Application.Xml)
                    setBody("<vedtak/>")
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("tilbakekrevingsvedtakResponse"), "Skal returnere tilbakekreving SOAP XML, var: $body")
            }
        } finally {
            wireMock.stop()
        }
    }

    private fun appConfig(
        issuer: String,
        wellKnown: String,
        simulerUrl: String = "http://localhost:1234/simulering",
    ): Config {

        return ConfigFactory.parseMap(
            mapOf(
                "no.nav.security.jwt.issuers" to
                        listOf(
                            mapOf(
                                "discoveryurl" to wellKnown,
                                "issuer_name" to issuer,
                                "accepted_audience" to CLIENT_ID,
                            ),
                        ),
                "username" to "testuser",
                "password" to "testpass",
                "SIMULERING_OPPDRAG_URL" to simulerUrl,
                "TILBAKEKREVING_URL" to "http://localhost:1234/tilbakekreving",
                "GANDALF_URL" to "http://localhost:1234/gandalf",
            ),
        )
    }

}
