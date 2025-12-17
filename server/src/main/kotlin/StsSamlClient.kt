package no.nav.supstonad

import arrow.core.Either
import java.time.temporal.Temporal

interface SamlTokenProvider {
    fun samlToken(): Either<KunneIkkeHenteSamlToken, SamlToken>
}

data object KunneIkkeHenteSamlToken

class SamlToken(
    val token: String,
    private val expirationTime: Temporal,
)

class StsSamlClient: SamlTokenProvider  {
    override fun samlToken(): Either<KunneIkkeHenteSamlToken, SamlToken> {
        TODO("Not yet implemented")
    }
}