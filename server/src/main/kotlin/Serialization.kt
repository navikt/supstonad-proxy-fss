package no.nav.supstonad

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson()
    }
}

suspend inline fun ApplicationCall.receiveTextUTF8(): String {
    return withContext(Dispatchers.IO) {
        String(receiveStream().readBytes())
    }
}

@PublishedApi
internal val privateObjectMapper: ObjectMapper = JsonMapper.builder()
    .addModule(JavaTimeModule())
    .addModule(KotlinModule.Builder().build())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
    .build()