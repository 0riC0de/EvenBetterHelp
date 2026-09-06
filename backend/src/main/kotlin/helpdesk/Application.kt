package helpdesk

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.seconds

fun main() { embeddedServer(Netty, port = 8080, module = Application::helpdesk).start(wait = true) }
fun Application.helpdesk() {
    val database = Database.fromEnvironment()
    Flyway.configure().dataSource(database.source).load().migrate()
    install(Koin) { modules(dependencies(database)) }
    install(ContentNegotiation) { json() }
    install(WebSockets) { pingPeriod = 20.seconds; timeout = 30.seconds; maxFrameSize = 65536 }
    configureAuthentication()
    configureErrors()
    configureRoutes()
    val worker by inject<OutboxWorker>()
    val bridge by inject<CobaltBridgeAdapter>()
    val media by inject<MediaService>()
    val calls by inject<CallService>()
    val job = launch(Dispatchers.IO) { worker.run() }
    val expiry = launch(Dispatchers.IO) { while (isActive) { delay(10000); calls.expire() } }
    monitor.subscribe(ApplicationStopping) { job.cancel(); expiry.cancel(); bridge.close(); media.close(); database.close() }
}
fun dependencies(database: Database) = module {
    single { database }; single { RealtimeHub() }; single { DepartmentRouter() }
    single { ConversationRepository(get()) }; single { MessageRepository(get(), get()) }
    single { CobaltBridgeAdapter() }; single<WhatsAppBridge> { get<CobaltBridgeAdapter>() }
    single { MediaService(get(), get()) }; single { InboundService(get(), get(), get()) }
    single { InboxService(get(), get(), get(), get(), get()) }
    single { CallService(get(), get(), get(), get()) }; single { OutboxWorker(get(), get(), get(), get()) }
    single { TurnCredentials() }
}
fun Application.configureAuthentication() {
    val secret = env("JWT_SECRET")
    require(secret.length >= 32) { "JWT_SECRET must contain at least 32 characters" }
    install(Authentication) {
        jwt("agent") {
            verifier(JWT.require(Algorithm.HMAC256(secret)).withIssuer(env("JWT_ISSUER")).withAudience("helpdesk").build())
            validate { credential ->
                if (credential.payload.subject.isNullOrBlank() || credential.payload.expiresAt == null) null else JWTPrincipal(credential.payload)
            }
        }
    }
}
fun Application.configureErrors() {
    install(StatusPages) {
        exception<DomainError> { call, cause ->
            val codes = mapOf("invalid_request" to HttpStatusCode.BadRequest, "forbidden" to HttpStatusCode.Forbidden, "conflict" to HttpStatusCode.Conflict, "unavailable" to HttpStatusCode.ServiceUnavailable)
            call.respond(codes.getValue(cause.code), ApiError(cause.code, cause.message))
        }
        exception<io.ktor.server.plugins.BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("invalid_request", "Invalid request body")) }
        exception<java.sql.SQLException> { call, cause -> respondDatabaseError(call, cause) }
        exception<Exception> { call, cause ->
            if (cause is CancellationException) throw cause
            call.application.log.error("Request failed: {}", cause.javaClass.simpleName)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal", "The request could not be completed"))
        }
    }
}
suspend fun respondDatabaseError(call: ApplicationCall, cause: java.sql.SQLException) {
    if (cause.sqlState == "23505") {
        call.respond(HttpStatusCode.Conflict, ApiError("conflict", "This operation conflicts with an existing record"))
        return
    }
    call.application.log.error("Database operation failed: {}", cause.sqlState)
    call.respond(HttpStatusCode.ServiceUnavailable, ApiError("unavailable", "Please try again shortly"))
}
