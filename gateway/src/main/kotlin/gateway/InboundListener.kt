package gateway

import it.auties.whatsapp.api.Whatsapp
import it.auties.whatsapp.model.info.ChatMessageInfo
import it.auties.whatsapp.model.info.MessageInfo
import it.auties.whatsapp.model.message.model.MessageStatus
import it.auties.whatsapp.model.message.model.MediaMessage
import it.auties.whatsapp.model.message.standard.TextMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class InboundListener(private val repository: GatewayRepository, private val storage: IncomingStorage) {
    private val downloads = Semaphore(2)
    private val statuses = mapOf(MessageStatus.SERVER_ACK to "sent", MessageStatus.DELIVERED to "delivered", MessageStatus.READ to "read", MessageStatus.PLAYED to "read")
    fun attach(client: Whatsapp) {
        client.addNewMessageListener { api: Whatsapp, info: MessageInfo<*> -> incoming(api, info) }
        client.addMessageStatusListener { _: Whatsapp, info: MessageInfo<*> -> receipt(info) }
    }
    private fun incoming(client: Whatsapp, info: MessageInfo<*>) {
        if (info !is ChatMessageInfo) return
        if (info.fromMe()) return
        if (!info.chatJid().toString().endsWith("@s.whatsapp.net")) return
        val content = info.message().content()
        runBlocking(Dispatchers.IO) { downloads.withPermit { saveContent(client, info, content) } }
    }
    private suspend fun saveContent(client: Whatsapp, info: ChatMessageInfo, content: it.auties.whatsapp.model.message.model.Message) {
        if (content is TextMessage) {
            repository.save(Event("message", info.id(), info.chatJid().toString(), info.pushName().orElse(info.chatJid().toString()), content.text()))
            return
        }
        if (content is MediaMessage<*>) repository.save(storage.ingest(client, info, content))
    }
    private fun receipt(info: MessageInfo<*>) {
        if (info !is ChatMessageInfo) return
        if (!info.fromMe()) return
        val status = statuses[info.status()] ?: return
        runBlocking(Dispatchers.IO) { repository.save(Event("receipt", info.id(), status = status)) }
    }
}
