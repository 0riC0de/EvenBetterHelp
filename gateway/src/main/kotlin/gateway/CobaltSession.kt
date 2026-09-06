package gateway

import it.auties.whatsapp.api.ErrorHandler
import it.auties.whatsapp.api.QrHandler
import it.auties.whatsapp.api.Whatsapp
import it.auties.whatsapp.controller.ControllerSerializer
import it.auties.whatsapp.exception.RequestException
import it.auties.whatsapp.model.info.ChatMessageInfoBuilder
import it.auties.whatsapp.model.jid.Jid
import it.auties.whatsapp.model.message.model.ChatMessageKeyBuilder
import it.auties.whatsapp.model.message.model.MessageStatus
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class CobaltSession(private val repository: GatewayRepository, private val media: MediaFactory) : AutoCloseable {
    private val ready = AtomicBoolean(false)
    private val sends = Semaphore(2)
    private val directory = Path.of(setting("COBALT_SESSION_DIR"))
    val client: Whatsapp = createClient()
    fun ready(): Boolean = ready.get()
    private fun createClient(): Whatsapp {
        Files.createDirectories(directory)
        return Whatsapp.webBuilder().serializer(ControllerSerializer.toProtobuf(directory)).newConnection("helpdesk")
            .name("EvenBetterHelp").automaticMessageReceipts(false)
            .errorHandler { _, location, throwable -> handleSocketError(location, throwable) }
            .unregistered(QrHandler.toFile(directory.resolve("pairing.jpg")) { println("Pairing required: open the protected pairing.jpg in COBALT_SESSION_DIR") })
            .addLoggedInListener { _: Whatsapp -> ready.set(true); Files.deleteIfExists(directory.resolve("pairing.jpg")) }
            .addDisconnectedListener { _: it.auties.whatsapp.api.DisconnectReason -> ready.set(false) }
    }
    private fun handleSocketError(location: ErrorHandler.Location, throwable: Throwable): ErrorHandler.Result {
        if (isIgnorableTimeout(throwable) || location == ErrorHandler.Location.MESSAGE) {
            return ErrorHandler.Result.DISCARD
        }
        return ErrorHandler.Result.DISCONNECT
    }
    private fun isIgnorableTimeout(throwable: Throwable?): Boolean {
        var curr = throwable
        while (curr != null) {
            if (curr is RequestException || curr.message?.contains("timed out") == true) {
                return true
            }
            curr = curr.cause
        }
        return false
    }
    suspend fun connect() { client.connect().await() }
    suspend fun send(input: Outbound): Ack = sends.withPermit { deliver(input) }
    private suspend fun deliver(input: Outbound): Ack {
        if (!ready()) throw GatewayFailure(503, "WhatsApp is not paired or connected")
        validate(input)
        val message = media.create(input)
        repository.reserve(input)?.let { return Ack(it) }
        try {
            val result = withTimeout(15000) { client.sendMessage(buildMessage(input, message), false).await() }
            if (result.status() == MessageStatus.ERROR) throw GatewayFailure(502, "WhatsApp rejected the recipient")
            repository.accepted(input.id, result.id())
            return Ack(result.id())
        } catch (error: Exception) {
            println("GATEWAY_SEND_ERROR: ${error.javaClass.name}: ${error.message}")
            error.printStackTrace()
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { repository.uncertain(input.id) }
            throw error
        }
    }
    private fun buildMessage(input: Outbound, message: it.auties.whatsapp.model.message.model.MessageContainer): it.auties.whatsapp.model.info.ChatMessageInfo {
        val self = client.store().jid().orElseThrow { GatewayFailure(503, "WhatsApp session is not ready") }
        val key = ChatMessageKeyBuilder().id(providerId(input.id)).chatJid(Jid.of(input.jid)).fromMe(true).senderJid(self).build()
        return ChatMessageInfoBuilder().key(key).senderJid(self).message(message).status(MessageStatus.PENDING).timestampSeconds(Instant.now().epochSecond).build()
    }
    private fun validate(input: Outbound) {
        clientId(input.id)
        if (!input.jid.matches(Regex("[0-9]+@s\\.whatsapp\\.net"))) throw GatewayFailure(400, "Only individual WhatsApp customers are supported")
        if (input.body.length > 10000) throw GatewayFailure(400, "Message exceeds the size limit")
    }
    override fun close() { client.store().serialize(false); client.disconnect().join() }
}
