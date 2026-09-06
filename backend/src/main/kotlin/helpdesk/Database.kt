package helpdesk

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

class Database(val source: HikariDataSource) : AutoCloseable {
    suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        source.connection.use { connection ->
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                result
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    }
    override fun close() = source.close()
    companion object {
        fun fromEnvironment(): Database = Database(HikariDataSource(HikariConfig().apply {
            jdbcUrl = env("DATABASE_URL")
            username = env("DATABASE_USER")
            password = env("DATABASE_PASSWORD")
            maximumPoolSize = 16
            connectionTimeout = 5000
            validationTimeout = 2000
        }))
    }
}
fun env(name: String): String = System.getenv(name) ?: error("Missing configuration: $name")
fun uuid(value: String): UUID = try { UUID.fromString(value) } catch (_: IllegalArgumentException) {
    throw DomainError.Invalid("Expected a UUID")
}
fun PreparedStatement.bind(values: Array<out Any?>) = values.forEachIndexed { index, value -> setObject(index + 1, value) }
fun <T> Connection.query(sql: String, vararg values: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { statement ->
        statement.bind(values)
        statement.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
    }
fun Connection.execute(sql: String, vararg values: Any?): Int = prepareStatement(sql).use {
    it.bind(values)
    it.executeUpdate()
}
fun ResultSet.conversation() = Conversation(
    getString("id"), getString("customer_name"), getString("customer_jid"), getString("queue_id"),
    getString("assigned_agent_id"), getString("status"), (getArray("tags").array as Array<*>).map { it.toString() },
    getString("preview"), getInt("unread"), getTimestamp("waiting_since")?.toInstant()?.toString(),
    getTimestamp("updated_at").toInstant().toString(),
)
fun ResultSet.message() = Message(
    getString("id"), getString("conversation_id"), getString("direction"), getString("kind"),
    getString("body"), getString("status"), getTimestamp("created_at").toInstant().toString(),
    getString("media_id"), getString("reply_to"),
)
