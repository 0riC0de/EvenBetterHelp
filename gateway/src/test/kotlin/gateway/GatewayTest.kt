package gateway

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import it.auties.whatsapp.model.message.standard.TextMessage
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import java.net.URI
import java.util.UUID
import kotlin.test.*

class GatewayTest {
    @Test fun `signed URL rotation does not change the delivery identity`() {
        val input = Outbound(UUID.randomUUID().toString(), "15555550100@s.whatsapp.net", "image", "Photo", "https://bucket.test/a?signature=old", "media-1")
        assertEquals(payloadHash(input), payloadHash(input.copy(mediaUrl = "https://bucket.test/a?signature=new")))
        assertNotEquals(payloadHash(input), payloadHash(input.copy(mediaId = "media-2")))
        assertNotEquals(payloadHash(input), payloadHash(input.copy(body = "Other caption")))
    }
    @Test fun `attachment download rejects SSRF origins`() {
        assertFailsWith<GatewayFailure> { validateMediaOrigin(URI("http://169.254.169.254/latest/meta-data/"), "bucket.test") }
        assertFailsWith<GatewayFailure> { validateMediaOrigin(URI("https://bucket.test@evil.test/a"), "bucket.test") }
        assertFailsWith<GatewayFailure> { validateMediaOrigin(URI("https://bucket.test:8443/a"), "bucket.test") }
        validateMediaOrigin(URI("https://bucket.test/attachments/a?signature=valid"), "bucket.test")
    }
    @Test fun `published Cobalt SDK constructs a text message`() = runBlocking {
        val message = MediaFactory("bucket.test").create(Outbound(UUID.randomUUID().toString(), "15555550100@s.whatsapp.net", "text", "Hello"))
        assertEquals("Hello", (message.content() as TextMessage).text())
    }
    @Test fun `gateway reserves once and persists accepted and uncertain outcomes`() = runBlocking {
        EmbeddedPostgres.builder().start().use { postgres ->
            val source = HikariDataSource(HikariConfig().apply { dataSource = postgres.postgresDatabase; maximumPoolSize = 8 })
            GatewayRepository(source).use { repository ->
                Flyway.configure().dataSource(source).locations("classpath:db/gateway").load().migrate()
                verifyDeliveryLedger(repository)
                verifyCallbacks(repository)
            }
        }
    }
    private suspend fun verifyDeliveryLedger(repository: GatewayRepository) = coroutineScope {
        val input = Outbound(UUID.randomUUID().toString(), "15555550100@s.whatsapp.net", "text", "Hello")
        val attempts = (1..6).map { async { runCatching { repository.reserve(input) } } }.awaitAll()
        assertEquals(1, attempts.count { it.isSuccess })
        assertEquals(5, attempts.count { it.exceptionOrNull() is GatewayFailure })
        repository.accepted(input.id, "provider-message")
        assertEquals("provider-message", repository.reserve(input))
        assertFailsWith<GatewayFailure> { repository.reserve(input.copy(body = "Different")) }
        val uncertain = input.copy(id = UUID.randomUUID().toString())
        assertNull(repository.reserve(uncertain))
        repository.uncertain(uncertain.id)
        assertFailsWith<GatewayFailure> { repository.reserve(uncertain) }
    }
    private suspend fun verifyCallbacks(repository: GatewayRepository) {
        val event = Event("message", "provider-id", "15555550100@s.whatsapp.net", "Customer", "Hello")
        repository.save(event); repository.save(event)
        val next = assertNotNull(repository.next())
        repository.delivered(next.first)
        assertNull(repository.next())
    }
}
