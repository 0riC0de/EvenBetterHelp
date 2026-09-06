package helpdesk

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.io.File
import java.sql.Connection

fun main() {
    val port = 5432
    val dataDir = File("c:/VibeCode/EvenBetterHelp/.cache/postgres-data").apply { mkdirs() }
    val postgres = EmbeddedPostgres.builder()
        .setPort(port)
        .setDataDirectory(dataDir)
        .setCleanDataDirectory(false)
        .start()

    postgres.postgresDatabase.connection.use { connection ->
        initRolesAndDatabases(connection)
    }
    println("POSTGRES_READY on port $port")
    Thread.sleep(Long.MAX_VALUE)
}

private fun initRolesAndDatabases(connection: Connection) {
    connection.createStatement().use { statement ->
        statement.execute(
            """
            DO ${'$'}${'$'}
            BEGIN
                IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'helpdesk') THEN
                    CREATE ROLE helpdesk WITH LOGIN SUPERUSER PASSWORD 'helpdesk_secure_dev_pass_2026';
                ELSE
                    ALTER ROLE helpdesk WITH LOGIN SUPERUSER PASSWORD 'helpdesk_secure_dev_pass_2026';
                END IF;
            END
            ${'$'}${'$'};
            """.trimIndent()
        )
        val exists = statement.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'helpdesk'").next()
        if (!exists) {
            statement.execute("CREATE DATABASE helpdesk OWNER helpdesk")
        }
    }
}
