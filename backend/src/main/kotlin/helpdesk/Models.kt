package helpdesk

import kotlinx.serialization.Serializable

@Serializable data class Agent(val id: String, val name: String)
@Serializable data class Queue(val id: String, val name: String, val slaSeconds: Int)
@Serializable data class Conversation(
    val id: String, val name: String, val jid: String, val queueId: String,
    val assignedAgentId: String?, val status: String, val tags: List<String>,
    val preview: String, val unread: Int, val waitingSince: String?, val updatedAt: String,
)
@Serializable data class Message(
    val id: String, val conversationId: String, val direction: String, val kind: String,
    val body: String, val status: String, val createdAt: String,
    val mediaId: String? = null, val replyTo: String? = null,
)
@Serializable data class SendMessage(
    val id: String, val kind: String = "text", val body: String = "",
    val mediaId: String? = null, val replyTo: String? = null,
)
@Serializable data class MoveConversation(val status: String, val queueId: String, val tags: List<String>)
@Serializable data class ApiError(val code: String, val message: String)
@Serializable data class RealtimeEvent(val type: String, val conversationId: String? = null, val payload: String? = null)
@Serializable data class UploadRequest(val fileName: String, val contentType: String, val bytes: Long)
@Serializable data class UploadGrant(val mediaId: String, val url: String, val headers: Map<String, String>)
@Serializable data class DownloadGrant(val url: String)
@Serializable data class BridgeSend(val id: String, val jid: String, val kind: String, val body: String, val mediaUrl: String? = null, val mediaId: String? = null)
@Serializable data class BridgeAck(val providerId: String)
@Serializable data class IncomingMedia(val id: String, val objectKey: String, val fileName: String, val contentType: String, val bytes: Long)
@Serializable data class BridgeEvent(
    val type: String, val providerId: String, val jid: String = "", val name: String = "",
    val body: String = "", val status: String = "sent", val tags: List<String> = emptyList(),
    val region: String = "", val kind: String = "text", val media: IncomingMedia? = null,
)
@Serializable data class CallRequest(val id: String, val kind: String)
@Serializable data class CallSession(val id: String, val conversationId: String, val kind: String, val status: String)
@Serializable data class Signal(val callId: String, val type: String, val payload: String)
@Serializable data class GatewayCallEvent(val callId: String, val type: String, val payload: String = "{}", val conversationId: String? = null, val kind: String = "voice")
@Serializable data class IceServer(val urls: List<String>, val username: String, val credential: String)
@Serializable data class IceConfiguration(val iceServers: List<IceServer>)
@Serializable data class Capabilities(val messaging: Boolean, val calling: Boolean, val uploads: Boolean)

sealed class DomainError(val code: String, override val message: String) : RuntimeException(message) {
    class Invalid(message: String) : DomainError("invalid_request", message)
    class Forbidden : DomainError("forbidden", "This conversation is not available to you")
    class Conflict : DomainError("conflict", "The conversation was already claimed or changed")
    class Unavailable(message: String) : DomainError("unavailable", message)
}
