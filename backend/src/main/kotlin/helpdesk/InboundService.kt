package helpdesk

import java.sql.Connection
import java.util.UUID

data class RoutingContext(val body: String, val tags: List<String>, val region: String)
class DepartmentRouter {
    private val matchers: Map<String, (RoutingContext, String) -> Boolean> = mapOf(
        "keyword" to { context, pattern -> context.body.contains(pattern, ignoreCase = true) },
        "tag" to { context, pattern -> context.tags.any { it.equals(pattern, true) } },
        "region" to { context, pattern -> context.region.equals(pattern, true) },
    )
    fun matches(field: String, pattern: String, context: RoutingContext): Boolean = matchers[field]?.invoke(context, pattern) ?: false
    fun resolve(connection: Connection, event: BridgeEvent): String {
        val context = RoutingContext(event.body, event.tags, event.region)
        val rules = connection.query("SELECT queue_id,field,pattern FROM routing_rules WHERE enabled ORDER BY priority,id") {
            Triple(it.getString(1), it.getString(2), it.getString(3))
        }
        return rules.firstOrNull { matches(it.second, it.third, context) }?.first
            ?: connection.query("SELECT id FROM queues ORDER BY name LIMIT 1") { it.getString(1) }.firstOrNull()
            ?: throw DomainError.Unavailable("No department is configured")
    }
}
class InboundService(private val db: Database, private val router: DepartmentRouter, private val hub: RealtimeHub) {
    private val handlers: Map<String, suspend (BridgeEvent) -> String?> = mapOf("message" to ::receiveText, "receipt" to ::receipt)
    suspend fun receive(event: BridgeEvent) {
        if (event.providerId.length !in 1..200) throw DomainError.Invalid("Invalid provider ID")
        val handler = handlers[event.type] ?: throw DomainError.Invalid("Unsupported bridge event")
        handler(event)?.let(hub::changed)
    }
    private suspend fun receiveText(event: BridgeEvent): String? = db.transaction { connection ->
        validateInbound(event)
        connection.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", event.jid) { true }
        val duplicate = connection.query("SELECT 1 FROM messages WHERE provider_id=?", event.providerId) { true }
        if (duplicate.isNotEmpty()) return@transaction null
        val conversation = findOrCreate(connection, event)
        event.media?.let { saveMedia(connection, conversation, it) }
        connection.execute("INSERT INTO messages(id,conversation_id,provider_id,direction,kind,body,media_id,status) VALUES (?,?,?,'in',?,?,?,'delivered')", UUID.randomUUID(), uuid(conversation), event.providerId, event.kind, event.body, event.media?.id?.let(::uuid))
        connection.execute("UPDATE conversations SET preview=?,unread=unread+1,waiting_since=coalesce(waiting_since,now()),updated_at=now(),version=version+1 WHERE id=?", event.body, uuid(conversation))
        conversation
    }
    private fun validateInbound(event: BridgeEvent) {
        if (event.jid.length !in 1..200) throw DomainError.Invalid("Invalid inbound customer")
        if (event.body.length > 10000) throw DomainError.Invalid("Incoming message is too long")
        if (event.kind !in setOf("text", "image", "document", "sticker", "voice")) throw DomainError.Invalid("Unsupported incoming message")
        validateContent(event)
    }
    private fun validateContent(event: BridgeEvent) {
        if (event.kind == "text" && event.body.isBlank()) throw DomainError.Invalid("Empty incoming text")
        if (event.kind != "text" && event.media == null) throw DomainError.Invalid("Missing incoming attachment")
    }
    private fun saveMedia(connection: Connection, conversation: String, media: IncomingMedia) {
        if (media.objectKey != "incoming/${media.id}") throw DomainError.Invalid("Invalid incoming object key")
        if (media.bytes !in 1..16777216 || media.fileName.length !in 1..200) throw DomainError.Invalid("Invalid incoming attachment")
        connection.execute("INSERT INTO media_assets(id,conversation_id,object_key,file_name,content_type,bytes) VALUES (?,?,?,?,?,?)", uuid(media.id), uuid(conversation), media.objectKey, media.fileName, media.contentType, media.bytes)
    }
    private fun findOrCreate(connection: Connection, event: BridgeEvent): String {
        val existing = connection.query("SELECT id FROM conversations WHERE customer_jid=? AND status<>'resolved'", event.jid) { it.getString(1) }.firstOrNull()
        if (existing != null) return existing
        return connection.query("INSERT INTO conversations(customer_jid,customer_name,queue_id,tags) VALUES (?,?,?,?) RETURNING id", event.jid, event.name.ifBlank { event.jid }, uuid(router.resolve(connection, event)), connection.createArrayOf("text", event.tags.toTypedArray())) { it.getString(1) }.single()
    }
    private suspend fun receipt(event: BridgeEvent): String? = db.transaction { connection ->
        if (event.status !in setOf("sent", "delivered", "read")) throw DomainError.Invalid("Invalid receipt")
        connection.query("SELECT pg_advisory_xact_lock(hashtextextended(?,1))", event.providerId) { true }
        connection.execute("""
            INSERT INTO provider_receipts(provider_id,status) VALUES (?,?)
            ON CONFLICT(provider_id) DO UPDATE SET status=EXCLUDED.status, received_at=now()
            WHERE array_position(ARRAY['sent','delivered','read'],provider_receipts.status) <
                  array_position(ARRAY['sent','delivered','read'],EXCLUDED.status)
        """.trimIndent(), event.providerId, event.status)
        connection.query("""
            UPDATE messages SET status=? WHERE provider_id=? AND
            array_position(ARRAY['queued','sent','delivered','read'],status) <
            array_position(ARRAY['queued','sent','delivered','read'],?::text) RETURNING conversation_id
        """.trimIndent(), event.status, event.providerId, event.status) { it.getString(1) }.firstOrNull()
    }
}
