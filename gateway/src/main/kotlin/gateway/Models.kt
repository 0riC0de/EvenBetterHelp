package gateway

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.UUID

@Serializable data class Outbound(val id: String, val jid: String, val kind: String, val body: String, val mediaUrl: String? = null, val mediaId: String? = null)
@Serializable data class Ack(val providerId: String)
@Serializable data class Capabilities(val messaging: Boolean, val calling: Boolean = false, val uploads: Boolean = false)
@Serializable data class IncomingMedia(val id: String, val objectKey: String, val fileName: String, val contentType: String, val bytes: Long)
@Serializable data class Event(
    val type: String, val providerId: String, val jid: String = "", val name: String = "", val body: String = "",
    val status: String = "sent", val kind: String = "text", val media: IncomingMedia? = null,
)
class GatewayFailure(val status: Int, override val message: String) : RuntimeException(message)
fun setting(name: String): String = System.getenv(name)?.takeIf { it.isNotBlank() } ?: error("Missing configuration: $name")
fun clientId(id: String): UUID = try { UUID.fromString(id) } catch (_: IllegalArgumentException) { throw GatewayFailure(400, "Invalid client ID") }
fun providerId(id: String): String = clientId(id).toString().replace("-", "").uppercase()
fun payloadHash(input: Outbound): String {
    val fields = listOf(input.jid, input.kind, input.body, input.mediaId.orEmpty())
    val canonical = fields.joinToString("") { "${it.length}:$it" }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
}
