package helpdesk

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CallService(private val db: Database, private val conversations: ConversationRepository, private val bridge: WhatsAppBridge, private val hub: RealtimeHub) {
    suspend fun start(agent: String, conversation: String, input: CallRequest): CallSession {
        if (!bridge.capabilities().calling) throw DomainError.Unavailable("WhatsApp call media gateway is not connected")
        if (input.kind !in setOf("voice", "video")) throw DomainError.Invalid("Invalid call type")
        val session = db.transaction { connection ->
            requireOwner(conversations.accessible(connection, agent, conversation), agent)
            connection.execute("INSERT INTO call_sessions(id,conversation_id,agent_id,direction,kind,status) VALUES (?,?,?,'out',?,'connecting')", uuid(input.id), uuid(conversation), uuid(agent), input.kind)
            CallSession(input.id, conversation, input.kind, "connecting")
        }
        try { bridge.signal(Signal(input.id, "start", callDestination(agent, conversation, input.kind))) }
        catch (error: Exception) {
            db.transaction { updateStatus(it, input.id, "failed") }
            throw error
        }
        return session
    }
    private suspend fun callDestination(agent: String, conversation: String, kind: String): String = db.transaction { connection ->
        val chat = conversations.accessible(connection, agent, conversation)
        buildJsonObject { put("jid", chat.jid); put("kind", kind) }.toString()
    }
    suspend fun signal(agent: String, input: Signal) {
        if (input.type !in setOf("offer", "answer", "ice", "accept", "decline", "end")) throw DomainError.Invalid("Unknown signal")
        if (input.payload.length > 32000) throw DomainError.Invalid("Signal is too large")
        db.transaction { connection ->
            val call = connection.query("SELECT conversation_id FROM call_sessions WHERE id=? AND agent_id=? AND status IN ('ringing','connecting','active')", uuid(input.callId), uuid(agent)) { it.getString(1) }.firstOrNull() ?: throw DomainError.Forbidden()
            requireOwner(conversations.accessible(connection, agent, call), agent)
        }
        try { bridge.signal(input) } finally {
            if (input.type in setOf("end", "decline")) finish(agent, input)
        }
    }
    private suspend fun finish(agent: String, input: Signal) = db.transaction { connection ->
        val status = if (input.type == "decline") "declined" else "ended"
        connection.execute("UPDATE call_sessions SET status=?, ended_at=now() WHERE id=? AND agent_id=?", status, uuid(input.callId), uuid(agent))
    }
    suspend fun gateway(event: GatewayCallEvent) {
        if (!bridge.capabilities().calling) throw DomainError.Unavailable("Calling is disabled")
        if (event.payload.length > 32000) throw DomainError.Invalid("Signal is too large")
        if (event.type == "incoming") { incoming(event); return }
        val status = mapOf("active" to "active", "end" to "ended", "decline" to "declined", "missed" to "missed", "failed" to "failed")
        if (event.type !in status.keys + setOf("offer", "answer", "ice")) throw DomainError.Invalid("Unknown gateway signal")
        val agent = db.transaction { connection ->
            val owner = connection.query("SELECT agent_id FROM call_sessions WHERE id=? AND status IN ('ringing','connecting','active') FOR UPDATE", uuid(event.callId)) { it.getString(1) }.firstOrNull() ?: throw DomainError.Conflict()
            status[event.type]?.let { next -> updateStatus(connection, event.callId, next) }
            owner
        }
        hub.signal(agent, Signal(event.callId, event.type, event.payload))
    }
    private suspend fun incoming(event: GatewayCallEvent) {
        if (event.kind !in setOf("voice", "video")) throw DomainError.Invalid("Invalid call kind")
        val chatId = event.conversationId ?: throw DomainError.Invalid("Conversation is required")
        val owner = db.transaction { connection ->
            val agent = connection.query("SELECT assigned_agent_id FROM conversations WHERE id=? AND status IN ('assigned','escalated') FOR UPDATE", uuid(chatId)) { it.getString(1) }.firstOrNull() ?: throw DomainError.Conflict()
            connection.execute("INSERT INTO call_sessions(id,conversation_id,agent_id,direction,kind,status) VALUES (?,?,?,'in',?,'ringing') ON CONFLICT (id) DO NOTHING", uuid(event.callId), uuid(chatId), uuid(agent), event.kind)
            agent
        }
        val payload = kotlinx.serialization.json.Json.encodeToString(CallSession(event.callId, chatId, event.kind, "ringing"))
        hub.signal(owner, Signal(event.callId, "incoming", payload))
    }
    private fun updateStatus(connection: java.sql.Connection, id: String, status: String) {
        connection.execute("UPDATE call_sessions SET status=?,answered_at=CASE WHEN ?='active' THEN now() ELSE answered_at END,ended_at=CASE WHEN ?<>'active' THEN now() ELSE ended_at END WHERE id=?", status, status, status, uuid(id))
    }
    suspend fun expire() {
        val expired = db.transaction { connection -> connection.query("""
            UPDATE call_sessions SET status='missed',ended_at=now()
            WHERE status IN ('ringing','connecting') AND created_at<now()-interval '60 seconds'
            RETURNING id,agent_id
        """.trimIndent()) { it.getString(1) to it.getString(2) } }
        expired.forEach { (id, agent) -> hub.signal(agent, Signal(id, "missed", "{}")) }
    }
}
