package no.nav.supstonad

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.flatten
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64


sealed interface SamlFeil {
    data object KunneIkkeHenteSamlToken: SamlFeil
    data object KunneIkkeLeseSamlToken: SamlFeil
    data object FeilVedCache: SamlFeil
}

private val EXPIRATION_MARGIN = Duration.ofSeconds(10)

class SamlToken(
    val token: String,
    private val expirationTime: Instant,
) {
    fun isExpired(clock: Clock) = expirationTime <= Instant.now(clock).truncatedTo(ChronoUnit.MICROS).plus(EXPIRATION_MARGIN)

    override fun toString(): String = token
}

interface SamlTokenProvider {
    fun samlToken(): Either<SamlFeil, SamlToken>
}

class StsSamlClient(
    baseUrl: String,
    private val serviceUser: Config.Sts.ServiceUser,
    private val clock: Clock,
): SamlTokenProvider  {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private val uri = URI("$baseUrl/rest/v1/sts/samltoken")

    private val token = atomic<SamlToken?>(null)

    override fun samlToken(): Either<SamlFeil, SamlToken> {
        val oppdatertToken = token.updateAndGet { currentToken ->
            when {
                currentToken != null && !currentToken.isExpired(clock) -> currentToken.also {
                    // TODO jah: Kan fjerne logglinje når vi har fått verifisert at dette virker.
                    log.debug("STS/Gandalf: Bruker eksisterende token som ikke er utløpt for serviceUser ${serviceUser.username}.")
                }
                else -> generateNewToken().onRight {
                    // TODO jah: Kan fjerne logglinje når vi har fått verifisert at dette virker.
                    log.debug("STS/Gandalf: Genererte nytt token for serviceUser ${serviceUser.username}.")
                }.getOrElse {
                    return it.left()
                }
            }
        }
        if (oppdatertToken == null) {
            log.error("Feil ved caching av saml token")
            return SamlFeil.FeilVedCache.left()
        }
        return oppdatertToken.right()
    }

    private fun generateNewToken(): Either<SamlFeil, SamlToken> {
        return Either.catch {
            val encodedCredentials = Base64.getEncoder()
                .encodeToString("${serviceUser.username}:${serviceUser.password}".toByteArray(StandardCharsets.UTF_8))
            val request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Basic $encodedCredentials")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()?.let { decodeSamlTokenResponse(it) } ?: SamlFeil.KunneIkkeHenteSamlToken.left()
        }.mapLeft {
            log.error("STS/Gandalf: Kunne ikke hente SAML token for bruker $serviceUser.username og uri $uri", it)
            SamlFeil.KunneIkkeHenteSamlToken
        }.flatten()
    }

    private fun decodeSamlTokenResponse(body: String): Either<SamlFeil, SamlToken> {
        return Either.catch {
            privateObjectMapper.readTree(body)
        }.mapLeft {
            log.error(
                "STS/Gandalf: Kunne ikke tolke JSON-respons.",
                RuntimeException("Stacktrace"),
            )
            SamlFeil.KunneIkkeLeseSamlToken
        }.flatMap {
            extractSamlTokenFromResponse(it)
        }
    }

    private fun extractSamlTokenFromResponse(node: JsonNode): Either<SamlFeil.KunneIkkeHenteSamlToken, SamlToken> {
        return Either.catch {
            val accessToken =
                node.path("access_token").takeIf(JsonNode::isTextual)?.asText() ?: return SamlFeil.KunneIkkeHenteSamlToken.left()
                    .also {
                        log.error("STS/Gandalf: Kunne ikke hente access_token fra respons.")
                    }
            val issuedTokenType = node.path("issued_token_type").takeIf(JsonNode::isTextual)?.asText()
                ?: return SamlFeil.KunneIkkeHenteSamlToken.left().also {
                    log.error("STS/Gandalf: Kunne ikke hente issued_token_type fra respons.")
                }
            val expiresIn =
                node.path("expires_in").takeIf(JsonNode::isNumber)?.asLong() ?: return SamlFeil.KunneIkkeHenteSamlToken.left()
                    .also {
                        log.error("STS/Gandalf: Kunne ikke hente expires_in fra respons.")
                    }
            if (issuedTokenType != "urn:ietf:params:oauth:token-type:saml2") {
                return SamlFeil.KunneIkkeHenteSamlToken.left()
                    .also {
                        log.error("STS/Gandalf: Ukjent token type: $issuedTokenType.")
                    }
            }
            SamlToken(
                token = Base64.getDecoder().decode(accessToken).decodeToString(),
                expirationTime = Instant.now(clock).truncatedTo(ChronoUnit.MICROS).plus(expiresIn, ChronoUnit.SECONDS),
            )
        }.mapLeft {
            log.error("STS/Gandalf: Kunne ikke hente SAML token fra respons.", it)
            SamlFeil.KunneIkkeHenteSamlToken
        }
    }
}