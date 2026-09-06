package helpdesk

import java.sql.Connection

class ConversationRepository(private val db: Database) {
    suspend fun agent(subject: String): Agent? = db.transaction { connection ->
        connection.query("SELECT id, name FROM agents WHERE subject = ? AND active", subject) {
            Agent(it.getString("id"), it.getString("name"))
        }.firstOrNull()
    }
    suspend fun queues(agent: String): List<Queue> = db.transaction { connection ->
        connection.query("SELECT q.* FROM queues q JOIN agent_queues a ON a.queue_id=q.id WHERE a.agent_id=? ORDER BY q.name", uuid(agent)) {
            Queue(it.getString("id"), it.getString("name"), it.getInt("sla_seconds"))
        }
    }
    suspend fun list(agent: String, search: String): List<Conversation> = db.transaction { connection ->
        connection.query("""
            SELECT c.* FROM conversations c JOIN agent_queues a ON a.queue_id=c.queue_id
            WHERE a.agent_id=? AND (?='' OR c.customer_name ILIKE '%' || ? || '%' OR EXISTS (
                SELECT 1 FROM messages m WHERE m.conversation_id=c.id
                AND m.search_vector @@ websearch_to_tsquery('simple', ?)))
            ORDER BY c.updated_at DESC, c.id LIMIT 100
        """.trimIndent(), uuid(agent), search, search, search) { it.conversation() }
    }
    fun accessible(connection: Connection, agent: String, id: String): Conversation = connection.query("""
        SELECT c.* FROM conversations c JOIN agent_queues a ON a.queue_id=c.queue_id
        WHERE c.id=? AND a.agent_id=? FOR UPDATE OF c
    """.trimIndent(), uuid(id), uuid(agent)) { it.conversation() }.firstOrNull() ?: throw DomainError.Forbidden()

    suspend fun claim(agent: String, queue: String, id: String?): Conversation = db.transaction { connection ->
        val result = connection.query("""
            WITH candidate AS (
                SELECT c.id FROM conversations c JOIN agent_queues a ON a.queue_id=c.queue_id
                WHERE a.agent_id=? AND c.queue_id=? AND c.status='unassigned'
                AND c.assigned_agent_id IS NULL AND (?::uuid IS NULL OR c.id=?::uuid)
                ORDER BY c.created_at, c.id FOR UPDATE OF c SKIP LOCKED LIMIT 1
            ) UPDATE conversations c SET assigned_agent_id=?, status='assigned',
              updated_at=now(), version=version+1 FROM candidate WHERE c.id=candidate.id RETURNING c.*
        """.trimIndent(), uuid(agent), uuid(queue), id?.let(::uuid), id?.let(::uuid), uuid(agent)) { it.conversation() }
        val claimed = result.firstOrNull() ?: throw DomainError.Conflict()
        audit(connection, agent, claimed.id, "claimed")
        claimed
    }
    suspend fun move(agent: String, id: String, input: MoveConversation): Conversation = db.transaction { connection ->
        val current = accessible(connection, agent, id)
        requireOwner(current, agent)
        val member = connection.query("SELECT 1 FROM agent_queues WHERE agent_id=? AND queue_id=?", uuid(agent), uuid(input.queueId)) { true }
        if (member.isEmpty()) throw DomainError.Forbidden()
        val next = connection.query("""
            UPDATE conversations SET status=?, queue_id=?, tags=?, updated_at=now(), version=version+1,
            assigned_agent_id=CASE WHEN ?='unassigned' THEN NULL ELSE assigned_agent_id END
            WHERE id=? RETURNING *
        """.trimIndent(), input.status, uuid(input.queueId), connection.createArrayOf("text", input.tags.toTypedArray()), input.status, uuid(id)) { it.conversation() }.single()
        audit(connection, agent, id, "moved:${input.status}")
        next
    }
    private fun audit(connection: Connection, agent: String, id: String, action: String) {
        connection.execute("INSERT INTO audit_events(agent_id, conversation_id, action) VALUES (?,?,?)", uuid(agent), uuid(id), action)
    }
}
fun requireOwner(conversation: Conversation, agent: String) {
    if (conversation.assignedAgentId != agent) throw DomainError.Forbidden()
    if (conversation.status == "resolved") throw DomainError.Conflict()
}
