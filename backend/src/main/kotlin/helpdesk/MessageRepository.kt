package helpdesk

class MessageRepository(private val db: Database, private val conversations: ConversationRepository) {
    suspend fun list(agent: String, id: String, before: String?): List<Message> = db.transaction { connection ->
        conversations.accessible(connection, agent, id)
        connection.query("""
            SELECT * FROM messages WHERE conversation_id=? AND (?::uuid IS NULL OR (created_at,id) <
            (SELECT created_at,id FROM messages WHERE id=?::uuid AND conversation_id=?))
            ORDER BY created_at DESC,id DESC LIMIT 100
        """.trimIndent(), uuid(id), before?.let(::uuid), before?.let(::uuid), uuid(id)) { it.message() }.reversed()
    }
    suspend fun enqueue(agent: String, id: String, input: SendMessage): Message = db.transaction { connection ->
        requireOwner(conversations.accessible(connection, agent, id), agent)
        val existing = connection.query("SELECT * FROM messages WHERE id=?", uuid(input.id)) { it.message() }.firstOrNull()
        if (existing != null) return@transaction matchingRetry(existing, id, input)
        verifyReferences(connection, agent, id, input)
        val message = connection.query("""
            INSERT INTO messages(id,conversation_id,sender_agent_id,direction,kind,body,media_id,reply_to,status)
            VALUES (?,?,?,'out',?,?,?,?,'queued') RETURNING *
        """.trimIndent(), uuid(input.id), uuid(id), uuid(agent), input.kind, input.body, input.mediaId?.let(::uuid), input.replyTo?.let(::uuid)) { it.message() }.single()
        connection.execute("INSERT INTO message_outbox(message_id) VALUES (?)", uuid(input.id))
        connection.execute("UPDATE conversations SET preview=?, waiting_since=NULL, unread=0, updated_at=now(), version=version+1 WHERE id=?", input.body.ifBlank { input.kind }, uuid(id))
        message
    }
    private fun verifyReferences(connection: java.sql.Connection, agent: String, id: String, input: SendMessage) {
        input.mediaId?.let { media ->
            val found = connection.query("SELECT 1 FROM media_assets WHERE id=? AND conversation_id=? AND agent_id=?", uuid(media), uuid(id), uuid(agent)) { true }
            if (found.isEmpty()) throw DomainError.Invalid("Invalid attachment")
        }
        input.replyTo?.let { reply ->
            val found = connection.query("SELECT 1 FROM messages WHERE id=? AND conversation_id=?", uuid(reply), uuid(id)) { true }
            if (found.isEmpty()) throw DomainError.Invalid("Invalid reply")
        }
    }
    private fun matchingRetry(existing: Message, id: String, input: SendMessage): Message {
        if (existing.conversationId != id) throw DomainError.Conflict()
        if (existing.body != input.body || existing.kind != input.kind) throw DomainError.Conflict()
        if (existing.mediaId != input.mediaId || existing.replyTo != input.replyTo) throw DomainError.Conflict()
        return existing
    }
}
