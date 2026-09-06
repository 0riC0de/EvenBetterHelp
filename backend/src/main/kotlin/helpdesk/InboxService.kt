package helpdesk

class InboxService(private val conversations: ConversationRepository, private val messages: MessageRepository, private val media: MediaService, private val hub: RealtimeHub, private val bridge: WhatsAppBridge) {
    suspend fun claim(agent: String, queue: String, id: String?): Conversation {
        val result = conversations.claim(agent, queue, id)
        hub.changed(result.id)
        return result
    }
    suspend fun send(agent: String, id: String, input: SendMessage): Message {
        validateMessage(input)
        if (!bridge.capabilities().messaging) throw DomainError.Unavailable("Connect a Cobalt bridge before sending messages")
        input.mediaId?.let { media.verify(agent, id, it) }
        val result = messages.enqueue(agent, id, input)
        hub.changed(id)
        return result
    }
    suspend fun move(agent: String, id: String, input: MoveConversation): Conversation {
        if (input.status !in setOf("assigned", "unassigned", "escalated", "resolved")) throw DomainError.Invalid("Invalid inbox status")
        if (input.tags.size > 12 || input.tags.any { it.length !in 1..40 }) throw DomainError.Invalid("Use up to 12 short tags")
        val result = conversations.move(agent, id, input)
        hub.changed(id)
        return result
    }
}
fun validateMessage(input: SendMessage) {
    uuid(input.id)
    if (input.kind !in setOf("text", "voice", "image", "document", "sticker")) throw DomainError.Invalid("Unsupported message type")
    if (input.body.length > 10000) throw DomainError.Invalid("Message is too long")
    if (input.kind == "text" && input.body.isBlank()) throw DomainError.Invalid("Message cannot be empty")
    if (input.kind != "text" && input.mediaId == null) throw DomainError.Invalid("Attachment is required")
}
