package helpdesk

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.*

class PostgresClaimTest {
    @Test fun `simultaneous agents produce exactly one owner and no duplicate sends`() = runBlocking {
        EmbeddedPostgres.builder().start().use { postgres ->
            val source = HikariDataSource(HikariConfig().apply { dataSource = postgres.postgresDatabase; maximumPoolSize = 12 })
            Database(source).use { db ->
                Flyway.configure().dataSource(source).load().migrate()
                val fixture = seed(db)
                val repository = ConversationRepository(db)
                val results = fixture.agents.map { agent -> async(Dispatchers.IO) { runCatching { repository.claim(agent, fixture.queue, fixture.chat) } } }.awaitAll()
                val claimed = results.mapNotNull { it.getOrNull() }
                assertEquals(1, claimed.size)
                assertEquals(7, results.count { it.exceptionOrNull() is DomainError.Conflict })
                verifyIdempotency(db, repository, claimed.single(), fixture)
                verifyInbound(db, repository, fixture)
                verifyEarlyReceipt(db, repository, fixture)
            }
        }
    }
    private suspend fun verifyInbound(db: Database, repository: ConversationRepository, fixture: Fixture) {
        val inbound = InboundService(db, DepartmentRouter(), RealtimeHub())
        val event = BridgeEvent("message", "incoming-id", "new-customer", "New Customer", "Refund requested")
        inbound.receive(event)
        inbound.receive(event)
        val found = repository.list(fixture.agents.first(), "refund")
        assertEquals(1, found.size)
        assertEquals(1, found.single().unread)
        assertNotNull(found.single().waitingSince)
        val mediaId = UUID.randomUUID().toString()
        val media = IncomingMedia(mediaId, "incoming/$mediaId", "Photo", "image/jpeg", 100)
        inbound.receive(event.copy(providerId = "media-incoming", body = "Photo", kind = "image", media = media))
        val messages = MessageRepository(db, repository).list(fixture.agents.first(), found.single().id, null)
        assertTrue(messages.any { it.kind == "image" && it.mediaId == mediaId })
    }
    private suspend fun verifyEarlyReceipt(db: Database, repository: ConversationRepository, fixture: Fixture) = coroutineScope {
        val hub = RealtimeHub()
        val inbound = InboundService(db, DepartmentRouter(), hub)
        val bridge = object : WhatsAppBridge {
            override suspend fun capabilities() = Capabilities(true, false, false)
            override suspend fun signal(signal: Signal) = Unit
            override suspend fun send(message: BridgeSend): BridgeAck {
                inbound.receive(BridgeEvent("receipt", "outgoing-provider-id", status = "read"))
                return BridgeAck("outgoing-provider-id")
            }
        }
        val media = MediaService(db, repository)
        val worker = launch { OutboxWorker(db, bridge, hub, media).run() }
        try {
            withTimeout(10000) { while (outgoingStatus(db) != "read") delay(25) }
            inbound.receive(BridgeEvent("receipt", "outgoing-provider-id", status = "delivered"))
            assertEquals("read", outgoingStatus(db))
            assertTrue(repository.list(fixture.agents.first(), "Happy").isNotEmpty())
        } finally { worker.cancelAndJoin(); media.close() }
    }
    private suspend fun outgoingStatus(db: Database): String? = db.transaction { connection ->
        connection.query("SELECT status FROM messages WHERE direction='out'") { it.getString(1) }.firstOrNull()
    }
    private suspend fun verifyIdempotency(db: Database, repository: ConversationRepository, chat: Conversation, fixture: Fixture) {
        val messages = MessageRepository(db, repository)
        val input = SendMessage(UUID.randomUUID().toString(), body = "Happy to help")
        val owner = chat.assignedAgentId!!
        val sent = coroutineScope { (1..6).map { async { messages.enqueue(owner, chat.id, input) } }.awaitAll() }
        assertEquals(1, sent.map { it.id }.distinct().size)
        assertFailsWith<DomainError.Forbidden> { messages.enqueue(fixture.agents.first { it != owner }, chat.id, input.copy(id = UUID.randomUUID().toString())) }
        assertFailsWith<DomainError.Conflict> { messages.enqueue(owner, chat.id, input.copy(body = "Different payload")) }
        val count = db.transaction { connection -> connection.query("SELECT count(*) FROM message_outbox") { it.getInt(1) }.single() }
        assertEquals(1, count)
    }
    private suspend fun seed(db: Database): Fixture = db.transaction { connection ->
        val agents = (1..8).map { UUID.randomUUID().toString() }
        val queue = UUID.randomUUID().toString()
        val chat = UUID.randomUUID().toString()
        connection.execute("INSERT INTO queues(id,name) VALUES (?,'Support')", uuid(queue))
        agents.forEach { agent ->
            connection.execute("INSERT INTO agents(id,subject,name,email) VALUES (?,?,?,?)", uuid(agent), agent, "Agent", "$agent@example.test")
            connection.execute("INSERT INTO agent_queues(agent_id,queue_id) VALUES (?,?)", uuid(agent), uuid(queue))
        }
        connection.execute("INSERT INTO conversations(id,customer_jid,customer_name,queue_id) VALUES (?,'customer','Customer',?)", uuid(chat), uuid(queue))
        Fixture(agents, queue, chat)
    }
    private data class Fixture(val agents: List<String>, val queue: String, val chat: String)
}
