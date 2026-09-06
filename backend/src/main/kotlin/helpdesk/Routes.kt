package helpdesk

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.security.MessageDigest

suspend fun ApplicationCall.agent(repository: ConversationRepository): Agent {
    val subject = principal<JWTPrincipal>()?.payload?.subject ?: throw DomainError.Forbidden()
    return repository.agent(subject) ?: throw DomainError.Forbidden()
}
fun ApplicationCall.path(name: String): String = parameters[name] ?: throw DomainError.Invalid("Missing $name")
fun Application.configureRoutes() {
    val conversations by inject<ConversationRepository>()
    val bridge by inject<WhatsAppBridge>()
    val inbound by inject<InboundService>()
    val calls by inject<CallService>()
    val turn by inject<TurnCredentials>()
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        post("/bridge/events") {
            call.verifyBridge()
            inbound.receive(call.receive())
            call.respond(HttpStatusCode.Accepted)
        }
        post("/bridge/calls") { call.verifyBridge(); calls.gateway(call.receive()); call.respond(HttpStatusCode.Accepted) }
        authenticate("agent") {
            get("/me") { call.respond(call.agent(conversations)) }
            get("/capabilities") { call.agent(conversations); call.respond(bridge.capabilities()) }
            get("/queues") { call.respond(conversations.queues(call.agent(conversations).id)) }
            get("/ice-servers") { call.respond(turn.issue(call.agent(conversations).id)) }
            conversationRoutes(); attachmentRoutes(); realtimeRoutes()
        }
    }
}
fun Route.conversationRoutes() {
    val repository by application.inject<ConversationRepository>()
    val messages by application.inject<MessageRepository>()
    val inbox by application.inject<InboxService>()
    val calls by application.inject<CallService>()
    get("/conversations") {
        val search = call.request.queryParameters["search"].orEmpty().take(200)
        call.respond(repository.list(call.agent(repository).id, search))
    }
    post("/queues/{queue}/claim") {
        call.respond(inbox.claim(call.agent(repository).id, call.path("queue"), call.request.queryParameters["conversationId"]))
    }
    route("/conversations/{id}") {
        get("/messages") { call.respond(messages.list(call.agent(repository).id, call.path("id"), call.request.queryParameters["before"])) }
        post("/messages") { call.respond(HttpStatusCode.Accepted, inbox.send(call.agent(repository).id, call.path("id"), call.receive())) }
        patch { call.respond(inbox.move(call.agent(repository).id, call.path("id"), call.receive())) }
        post("/calls") { call.respond(HttpStatusCode.Created, calls.start(call.agent(repository).id, call.path("id"), call.receive())) }
    }
}
fun Route.attachmentRoutes() {
    val repository by application.inject<ConversationRepository>()
    val media by application.inject<MediaService>()
    post("/conversations/{id}/uploads") { call.respond(media.upload(call.agent(repository).id, call.path("id"), call.receive())) }
    get("/media/{id}") { call.respond(media.download(call.agent(repository).id, call.path("id"))) }
}
fun Route.realtimeRoutes() {
    val repository by application.inject<ConversationRepository>()
    val hub by application.inject<RealtimeHub>()
    val calls by application.inject<CallService>()
    webSocket("/ws") {
        if (call.request.header("Origin") != env("APP_ORIGIN")) throw DomainError.Forbidden()
        val agent = call.agent(repository)
        val expiry = call.principal<JWTPrincipal>()!!.payload.expiresAt?.time ?: 0L
        val expireJob = launch { delay((expiry - System.currentTimeMillis()).coerceAtLeast(0)); close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Session expired")) }
        val events = launch { hub.subscribe().collect { send(Json.encodeToString(RealtimeEvent("invalidate"))) } }
        val signals = launch { hub.callSignals().collect { (owner, signal) -> if (owner == agent.id) send(Json.encodeToString(signal)) } }
        try { consumeSignals(agent.id, calls) } finally { events.cancel(); signals.cancel(); expireJob.cancel() }
    }
}
fun ApplicationCall.verifyBridge() {
    val expected = env("COBALT_WEBHOOK_TOKEN").toByteArray()
    val supplied = request.header("Authorization")?.removePrefix("Bearer ")?.toByteArray() ?: byteArrayOf()
    if (!MessageDigest.isEqual(expected, supplied)) throw DomainError.Forbidden()
}
suspend fun DefaultWebSocketServerSession.consumeSignals(agent: String, calls: CallService) {
    for (frame in incoming) {
        if (frame !is Frame.Text) continue
        try { calls.signal(agent, Json.decodeFromString<Signal>(frame.readText())) }
        catch (error: DomainError) { send(Json.encodeToString(ApiError(error.code, error.message))) }
    }
}
