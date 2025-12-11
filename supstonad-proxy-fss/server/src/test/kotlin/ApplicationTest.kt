package no.nav.supstonad

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.testApplication
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ApplicationTest {

    val mockOAuth2Server = MockOAuth2Server()

    @BeforeAll
    fun beforeAll() {
        mockOAuth2Server.start()
        println(" mockOAuth2Server.start() Current thread: ${Thread.currentThread().name}")
    }

    @AfterAll
    fun afterAll() {
        mockOAuth2Server.shutdown()
    }

    fun <R> withMockOAuth2Server(test: MockOAuth2Server.() -> R): R {
        val server = MockOAuth2Server()
        server.start()
        try {
            return server.test()
        } finally {
            server.shutdown()
        }
    }

    val CLIENT_ID = "CLIENT_ID"

    @Test
    fun lol() {
        println("Current thread: ${Thread.currentThread().name}")
        println("Server running on: ${mockOAuth2Server.baseUrl()}")
        val url = mockOAuth2Server.wellKnownUrl("azure").toString()
        println(url)
        val issuerName = "azure"
        val token = mockOAuth2Server.issueToken(issuerName, audience = CLIENT_ID).serialize()
        testApplication {
            environment { config = HoconApplicationConfig(appConfig(issuerName, mockOAuth2Server.wellKnownUrl(issuerName).toString())) }
            application { proxyappRoutes() }
            val response = client.get("/") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testbasic() {
        withMockOAuth2Server {
            // Inside this lambda, `this` is the started MockOAuth2Server
            val issuerName = "azure"

            // Print info (optional)
            println("Well-known URL: ${wellKnownUrl(issuerName)}")
            println("JWKS URL: ${jwksUrl(issuerName)}")

            val appConfig = appConfig(issuerName, wellKnownUrl(issuerName).toString())

            testApplication {
                environment {
                    config = HoconApplicationConfig(appConfig)
                }

                application {
                    proxyappRoutes()
                }

                val token = issueToken(issuerName, audience = CLIENT_ID).serialize()

                val response = client.get("/") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                assertEquals(HttpStatusCode.OK, response.status)
            }
        }
    }


    @Test
    fun testBaseRouteAuth() {
        val issuerName = "azure"
        val url = mockOAuth2Server.authorizationEndpointUrl(issuerName)
        val token = mockOAuth2Server.issueToken(issuerName)
        val baseurl = mockOAuth2Server.baseUrl()
        val issuerurl = mockOAuth2Server.issuerUrl(issuerName)
        val tokenendpointurl = mockOAuth2Server.tokenEndpointUrl(issuerName)
        val oatuhmetadata = mockOAuth2Server.oauth2AuthorizationServerMetadataUrl(issuerName)
        val jwks = mockOAuth2Server.jwksUrl(issuerName)
        val wellKnown = mockOAuth2Server.wellKnownUrl(issuerName)
        println(wellKnown.toString())
        println("Authorization endpoint URL: $url")
        println("Issued token: ${token.serialize()}")
        println("Base URL: $baseurl")
        println("Issuer URL: $issuerurl")
        println("Token endpoint URL: $tokenendpointurl")
        println("OAuth2 metadata URL: $oatuhmetadata")
        println("JWKS URL: $jwks")

        testApplication {
            val appconfig = appConfig(issuerName, wellKnown.toString())
            environment {
                config = HoconApplicationConfig(appconfig)
            }

            application {
                proxyappRoutes()
            }

            val response = client.get("/") {
                header(HttpHeaders.Authorization, "Bearer ${mockOAuth2Server.issueToken(issuerName, audience = CLIENT_ID).serialize()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    private fun appConfig(
        issuer: String,
        wellKnown: String,
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
            ),
        )
    }

}
