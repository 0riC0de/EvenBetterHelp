package helpdesk

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface WhatsAppBridge {
    suspend fun send(message: BridgeSend): BridgeAck
    suspend fun signal(signal: Signal)
    suspend fun capabilities(): Capabilities
}

// This is OUR private gateway contract, not an HTTP API shipped by Cobalt.
class CobaltBridgeAdapter : WhatsAppBridge, AutoCloseable {
    private val baseUrl = System.getenv("COBALT_BRIDGE_URL")?.trimEnd('/')?.takeIf { it.isNotBlank() }
    private val token = System.getenv("COBALT_BRIDGE_TOKEN")?.takeIf { it.isNotBlank() }
    private val client = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(ContentNegotiation) { json() }
        install(HttpTimeout) { requestTimeoutMillis = 20000 }
        expectSuccess = true
    }
    private val healthLock = Mutex()
    private var healthExpires = 0L
    private var cachedHealth = Capabilities(false, false, false)
    override suspend fun capabilities(): Capabilities = healthLock.withLock {
        if (System.nanoTime() < healthExpires) return@withLock cachedHealth
        cachedHealth = probe()
        healthExpires = System.nanoTime() + 5_000_000_000L
        cachedHealth
    }
    private suspend fun probe(): Capabilities {
        val uploads = !System.getenv("S3_BUCKET").isNullOrBlank()
        if (baseUrl == null || token == null) return Capabilities(false, false, uploads)
        return try {
            val remote = client.get("$baseUrl/capabilities") { bearerAuth(token); timeout { requestTimeoutMillis = 2000 } }.body<Capabilities>()
            Capabilities(remote.messaging, remote.calling && callingConfigured(), uploads)
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: Exception) { Capabilities(false, false, uploads) }
    }
    private fun callingConfigured(): Boolean = System.getenv("CALLS_ENABLED") == "true" && !System.getenv("TURN_SHARED_SECRET").isNullOrBlank()
    override suspend fun send(message: BridgeSend): BridgeAck {
        val endpoint = baseUrl ?: throw DomainError.Unavailable("Cobalt bridge is not configured")
        return client.post("$endpoint/messages") {
            bearerAuth(token ?: throw DomainError.Unavailable("Bridge credential is missing"))
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", message.id)
            setBody(message)
        }.body()
    }
    override suspend fun signal(signal: Signal) {
        if (!capabilities().calling) throw DomainError.Unavailable("Call media gateway is not configured")
        client.post("$baseUrl/signals") {
            bearerAuth(token ?: throw DomainError.Unavailable("Bridge credential is missing"))
            contentType(ContentType.Application.Json)
            setBody(signal)
        }
    }
    override fun close() = client.close()
}
