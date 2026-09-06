package gateway

import it.auties.whatsapp.api.Whatsapp
import it.auties.whatsapp.model.info.ChatMessageInfo
import it.auties.whatsapp.model.message.model.MediaMessage
import it.auties.whatsapp.model.message.standard.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

data class MediaDescription(val kind: String, val mime: String, val bytes: Long, val name: String)
class IncomingStorage : AutoCloseable {
    private val storage by lazy { S3Client.builder().region(Region.of(setting("AWS_REGION"))).build() }
    private val describers: Map<Class<*>, (MediaMessage<*>) -> MediaDescription> = mapOf(
        ImageMessage::class.java to { message -> (message as ImageMessage).let { MediaDescription("image", it.mimetype().orElse("image/jpeg"), it.mediaSize().orElse(0), "Image") } },
        DocumentMessage::class.java to { message -> (message as DocumentMessage).let { MediaDescription("document", it.mimetype().orElse("application/pdf"), it.mediaSize().orElse(0), it.fileName().orElse("Document")) } },
        AudioMessage::class.java to { message -> (message as AudioMessage).let { MediaDescription("voice", it.mimetype().orElse("audio/ogg"), it.mediaSize().orElse(0), "Voice message") } },
        StickerMessage::class.java to { message -> (message as StickerMessage).let { MediaDescription("sticker", "image/webp", it.mediaSize().orElse(0), "Sticker") } },
    )
    suspend fun ingest(client: Whatsapp, info: ChatMessageInfo, message: MediaMessage<*>): Event = withContext(Dispatchers.IO) {
        val description = describers[message.javaClass]?.invoke(message) ?: throw GatewayFailure(422, "Unsupported incoming media")
        if (description.bytes !in 1..MediaFactory.MAX_BYTES) throw GatewayFailure(413, "Incoming attachment exceeds the size limit")
        val bytes = client.downloadMedia(message).await()
        if (bytes.size !in 1..MediaFactory.MAX_BYTES) throw GatewayFailure(413, "Downloaded attachment exceeds the size limit")
        val id = UUID.nameUUIDFromBytes("${info.chatJid()}:${info.id()}".toByteArray()).toString()
        val key = "incoming/$id"
        val mime = description.mime.substringBefore(';')
        storage.putObject(PutObjectRequest.builder().bucket(setting("S3_BUCKET")).key(key).contentType(mime).build(), RequestBody.fromBytes(bytes))
        val media = IncomingMedia(id, key, description.name.take(200), mime, bytes.size.toLong())
        Event("message", info.id(), info.chatJid().toString(), info.pushName().orElse(info.chatJid().toString()), description.name, kind = description.kind, media = media)
    }
    override fun close() { if (!System.getenv("S3_BUCKET").isNullOrBlank()) storage.close() }
}
