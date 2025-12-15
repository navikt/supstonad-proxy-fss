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
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals

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
                "STS_SOAP_URL" to "http://localhost:1234/soap",
                "username" to "testuser",
                "password" to "testpass",
            ),
        )
    }

}
