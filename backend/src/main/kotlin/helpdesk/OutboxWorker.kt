package helpdesk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

class OutboxWorker(private val db: Database, private val bridge: WhatsAppBridge, private val hub: RealtimeHub, private val media: MediaService) {
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            if (!bridge.capabilities().messaging) { delay(5000); continue }
            processNext()
        }
    }
    private suspend fun processNext() {
        try {
            val message = lease()
            if (message == null) { delay(500); return }
            deliver(message)
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: Exception) { delay(5000) }
    }
    private suspend fun lease(): BridgeSend? = db.transaction { connection ->
        connection.query("""
            WITH next AS (
                SELECT message_id FROM message_outbox WHERE completed_at IS NULL AND attempts<8
                AND available_at<=now() AND (lease_until IS NULL OR lease_until<now())
                ORDER BY available_at FOR UPDATE SKIP LOCKED LIMIT 1
            ), leased AS (
                UPDATE message_outbox o SET lease_until=now()+interval '60 seconds', attempts=attempts+1
                FROM next WHERE o.message_id=next.message_id RETURNING o.message_id
            ) SELECT m.*,c.customer_jid FROM leased JOIN messages m ON m.id=leased.message_id
              JOIN conversations c ON c.id=m.conversation_id
        """.trimIndent()) { BridgeSend(it.getString("id"), it.getString("customer_jid"), it.getString("kind"), it.getString("body"), mediaId = it.getString("media_id")) }.firstOrNull()
    }
    private suspend fun deliver(message: BridgeSend) {
        try {
            val prepared = message.copy(mediaUrl = message.mediaId?.let { media.internalDownload(it) })
            val ack = bridge.send(prepared)
            finish(message.id, ack.providerId)
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: Exception) {
            println("OUTBOX_DELIVER_ERROR: ${error.javaClass.name}: ${error.message}")
            retry(message.id)
        }
    }
    private suspend fun finish(id: String, providerId: String) = db.transaction { connection ->
        connection.query("SELECT pg_advisory_xact_lock(hashtextextended(?,1))", providerId) { true }
        val chat = connection.query("UPDATE messages SET provider_id=?, status=coalesce((SELECT status FROM provider_receipts WHERE provider_id=?),'sent') WHERE id=? RETURNING conversation_id", providerId, providerId, uuid(id)) { it.getString(1) }.single()
        connection.execute("UPDATE message_outbox SET completed_at=now(),lease_until=NULL WHERE message_id=?", uuid(id))
        hub.changed(chat)
    }
    private suspend fun retry(id: String) = db.transaction { connection ->
        connection.execute("UPDATE message_outbox SET lease_until=NULL, available_at=now()+make_interval(secs => least(300,power(2,attempts)::int)),last_error='bridge_unavailable' WHERE message_id=?", uuid(id))
        connection.execute("UPDATE messages SET status='failed' WHERE id=? AND EXISTS (SELECT 1 FROM message_outbox WHERE message_id=? AND attempts>=8)", uuid(id), uuid(id))
    }
}
