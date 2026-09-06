package gateway

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection

class GatewayRepository(val source: HikariDataSource) : AutoCloseable {
    suspend fun reserve(input: Outbound): String? = transaction { connection ->
        val inserted = connection.execute("INSERT INTO gateway_deliveries(client_id,payload_hash,status) VALUES (?,?,'sending') ON CONFLICT DO NOTHING", clientId(input.id), payloadHash(input))
        if (inserted == 1) return@transaction null
        val row = connection.query("SELECT payload_hash,status,provider_id FROM gateway_deliveries WHERE client_id=? FOR UPDATE", clientId(input.id)) { Triple(it.getString(1), it.getString(2), it.getString(3)) }.single()
        acceptedRetry(row, input)
    }
    private fun acceptedRetry(row: Triple<String, String, String?>, input: Outbound): String {
        if (row.first != payloadHash(input)) throw GatewayFailure(409, "The client ID was reused for a different message")
        if (row.second != "accepted") throw GatewayFailure(409, "Delivery is in progress or uncertain; reconciliation is required")
        return row.third ?: throw GatewayFailure(409, "Missing provider acknowledgement")
    }
    suspend fun accepted(id: String, provider: String) = transaction { it.execute("UPDATE gateway_deliveries SET provider_id=?,status='accepted',updated_at=now() WHERE client_id=?", provider, clientId(id)) }
    suspend fun uncertain(id: String) = transaction { it.execute("UPDATE gateway_deliveries SET status='uncertain',updated_at=now() WHERE client_id=? AND status='sending'", clientId(id)) }
    suspend fun save(event: Event) = transaction { connection ->
        connection.execute("INSERT INTO gateway_callbacks(event_key,payload) VALUES (?,?) ON CONFLICT DO NOTHING", "${event.type}:${event.providerId}:${event.status}", Json.encodeToString(event))
    }
    suspend fun next(): Pair<String, String>? = transaction { connection ->
        connection.query("SELECT event_key,payload FROM gateway_callbacks WHERE delivered_at IS NULL AND available_at<=now() ORDER BY available_at LIMIT 1") { it.getString(1) to it.getString(2) }.firstOrNull()
    }
    suspend fun delivered(key: String) = transaction { it.execute("UPDATE gateway_callbacks SET delivered_at=now() WHERE event_key=?", key) }
    suspend fun retry(key: String) = transaction { it.execute("UPDATE gateway_callbacks SET attempts=attempts+1,available_at=now()+make_interval(secs=>least(300,power(2,least(attempts,8))::int)) WHERE event_key=?", key) }
    private suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        source.connection.use { connection ->
            connection.autoCommit = false
            try { val result = block(connection); connection.commit(); result }
            catch (error: Exception) { connection.rollback(); throw error }
        }
    }
    override fun close() = source.close()
    companion object {
        fun configured() = GatewayRepository(HikariDataSource(HikariConfig().apply {
            jdbcUrl = setting("DATABASE_URL"); username = setting("DATABASE_USER"); password = setting("DATABASE_PASSWORD")
            maximumPoolSize = 4; connectionTimeout = 5000
        }))
    }
}
fun Connection.execute(sql: String, vararg params: Any?): Int = prepareStatement(sql).use { statement ->
    params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }; statement.executeUpdate()
}
fun <T> Connection.query(sql: String, vararg params: Any?, map: (java.sql.ResultSet) -> T): List<T> = prepareStatement(sql).use { statement ->
    params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    statement.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
}
