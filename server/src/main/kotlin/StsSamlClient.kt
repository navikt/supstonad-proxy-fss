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


data object KunneIkkeHenteSamlToken

private val EXPIRATION_MARGIN = Duration.ofSeconds(10)

class SamlToken(
    val token: String,
    private val expirationTime: Instant,
) {
    fun isExpired(clock: Clock) = expirationTime <= Instant.now(clock).truncatedTo(ChronoUnit.MICROS).plus(EXPIRATION_MARGIN)

    override fun toString(): String = token
}

class StsSamlClient(
    baseUrl: String,
    private val serviceUser: Config.Sts.ServiceUser,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val sikkerLogg = LoggerFactory.getLogger(this::class.java) // TODO bjg

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private val uri = URI("$baseUrl/rest/v1/sts/samltoken")

    private val token = atomic<SamlToken?>(null)

    fun samlToken(): Either<KunneIkkeHenteSamlToken, SamlToken> {
        return token.updateAndGet { currentToken ->
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
        }!!.right()
    }

    private fun generateNewToken(): Either<KunneIkkeHenteSamlToken, SamlToken> {
        return Either.catch {
            val encodedCredentials = Base64.getEncoder()
                .encodeToString("${serviceUser.username}:${serviceUser.password}".toByteArray(StandardCharsets.UTF_8))
            val request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Basic $encodedCredentials")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body()?.let { decodeSamlTokenResponse(it) } ?: KunneIkkeHenteSamlToken.left()
        }.mapLeft {
            log.error("STS/Gandalf: Kunne ikke hente SAML token for bruker $serviceUser.username og uri $uri", it)
            KunneIkkeHenteSamlToken
        }.flatten()
    }

    private fun decodeSamlTokenResponse(body: String): Either<KunneIkkeHenteSamlToken, SamlToken> {
        return Either.catch {
            //jsonNode(body)
            privateObjectMapper.readTree(body)
        }.mapLeft {
            log.error(
                "STS/Gandalf: Kunne ikke tolke JSON-respons. Se sikkerlogg for mer info.",
                RuntimeException("Stacktrace"),
            )
            sikkerLogg.error("STS/Gandalf: Kunne ikke tolke JSON-respons. Body: $body", it)
            KunneIkkeHenteSamlToken
        }.flatMap {
            extractSamlTokenFromResponse(it)
        }
    }

    private fun extractSamlTokenFromResponse(node: JsonNode): Either<KunneIkkeHenteSamlToken, SamlToken> {
        return Either.catch {
            val accessToken =
                node.path("access_token").takeIf(JsonNode::isTextual)?.asText() ?: return KunneIkkeHenteSamlToken.left()
                    .also {
                        log.error("STS/Gandalf: Kunne ikke hente access_token fra respons. Se sikkerlogg for context.")
                        sikkerLogg.error("STS/Gandalf: Kunne ikke hente access_token fra respons. Node: $node")
                    }
            val issuedTokenType = node.path("issued_token_type").takeIf(JsonNode::isTextual)?.asText()
                ?: return KunneIkkeHenteSamlToken.left().also {
                    log.error("STS/Gandalf: Kunne ikke hente issued_token_type fra respons. Se sikkerlogg for context.")
                    sikkerLogg.error("STS/Gandalf: Kunne ikke hente issued_token_type fra respons. Node: $node")
                }
            val expiresIn =
                node.path("expires_in").takeIf(JsonNode::isNumber)?.asLong() ?: return KunneIkkeHenteSamlToken.left()
                    .also {
                        log.error("STS/Gandalf: Kunne ikke hente expires_in fra respons. Se sikkerlogg for context.")
                        sikkerLogg.error("STS/Gandalf: Kunne ikke hente expires_in fra respons. Node: $node")
                    }
            if (issuedTokenType != "urn:ietf:params:oauth:token-type:saml2") {
                return KunneIkkeHenteSamlToken.left()
                    .also {
                        log.error("STS/Gandalf: Ukjent token type: $issuedTokenType. Se sikkerlogg for context.")
                        sikkerLogg.error("STS/Gandalf: Ukjent token type: $issuedTokenType. Node: $node")
                    }
            }
            SamlToken(
                token = Base64.getDecoder().decode(accessToken).decodeToString(),
                expirationTime = Instant.now(clock).truncatedTo(ChronoUnit.MICROS).plus(expiresIn, ChronoUnit.SECONDS),
            )
        }.mapLeft {
            log.error("STS/Gandalf: Kunne ikke hente SAML token fra respons. Se sikkerlogg for context.", it)
            sikkerLogg.error("STS/Gandalf: Kunne ikke hente SAML token fra respons. Node: $node", it)
            KunneIkkeHenteSamlToken
        }
    }
}