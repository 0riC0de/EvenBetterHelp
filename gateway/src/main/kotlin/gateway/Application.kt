package gateway

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import java.security.MessageDigest

fun main() { embeddedServer(Netty, port = 8090, module = Application::gateway).start(wait = true) }
fun Application.gateway() {
    val repository = GatewayRepository.configured()
    Flyway.configure().dataSource(repository.source).table("gateway_schema_history").baselineOnMigrate(true).baselineVersion("0").locations("classpath:db/gateway").load().migrate()
    val session = CobaltSession(repository, MediaFactory(System.getenv("MEDIA_ALLOWED_HOST").orEmpty()))
    val incomingStorage = IncomingStorage()
    InboundListener(repository, incomingStorage).attach(session.client)
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<GatewayFailure> { call, error -> call.respond(HttpStatusCode.fromValue(error.status), mapOf("message" to error.message)) }
        exception<Exception> { call, error ->
            if (error is CancellationException) throw error
            call.application.log.error("Gateway request failed: {}", error.javaClass.simpleName)
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("message" to "Gateway operation failed"))
        }
    }
    routing {
        get("/capabilities") { call.authorize(); call.respond(Capabilities(session.ready())) }
        post("/messages") { call.authorize(); call.respond(session.send(call.receive())) }
        post("/signals") { call.authorize(); throw GatewayFailure(501, "Published Cobalt SDK has no browser media gateway") }
    }
    val callbacks = launch { CallbackWorker(repository).run() }
    val connection = launch { session.connect() }
    monitor.subscribe(ApplicationStopping) { callbacks.cancel(); connection.cancel(); session.close(); incomingStorage.close(); repository.close() }
}
fun ApplicationCall.authorize() {
    val expected = setting("COBALT_BRIDGE_TOKEN").toByteArray()
    val actual = request.header("Authorization")?.removePrefix("Bearer ")?.toByteArray() ?: byteArrayOf()
    if (!MessageDigest.isEqual(expected, actual)) throw GatewayFailure(401, "Unauthorized")
}
