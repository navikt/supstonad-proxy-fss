package no.nav.supstonad

import arrow.core.Either
import arrow.core.right
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class FakeSamlTokenProvider(
    private val clock: Clock = Clock.fixed(LocalDate.of(2021, Month.JANUARY, 1).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
    private val token: String = "fake-saml-token",
) : SamlTokenProvider {
    override fun samlToken(): Either<SamlFeil.KunneIkkeHenteSamlToken, SamlToken> {
        return SamlToken(
            token = token,
            expirationTime = Instant.now(clock).plus(1, ChronoUnit.HOURS),
        ).right()
    }
}