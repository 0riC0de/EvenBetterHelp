package gateway

import it.auties.whatsapp.model.info.ContextInfo
import it.auties.whatsapp.model.message.model.MessageContainer
import it.auties.whatsapp.model.message.standard.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit

class MediaFactory(private val allowedHost: String) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
    private val strategies: Map<String, (Outbound, ByteArray, String) -> MessageContainer> = mapOf(
        "image" to { input, bytes, mime -> MessageContainer.of(ImageMessageBuilder().caption(input.body).mimetype(mime).contextInfo(ContextInfo.empty()).build().setDecodedMedia(bytes)) },
        "sticker" to { _, bytes, _ -> MessageContainer.of(StickerMessageBuilder().mimetype("image/webp").contextInfo(ContextInfo.empty()).build().setDecodedMedia(bytes)) },
        "document" to { input, bytes, mime -> MessageContainer.of(DocumentMessageBuilder().fileName(input.body.ifBlank { "document.pdf" }).mimetype(mime).contextInfo(ContextInfo.empty()).build().setDecodedMedia(bytes)) },
        "voice" to { _, bytes, _ -> MessageContainer.of(AudioMessageBuilder().mimetype("audio/ogg; codecs=opus").voiceMessage(true).contextInfo(ContextInfo.empty()).build().setDecodedMedia(normalizeVoice(bytes))) },
    )
    suspend fun create(input: Outbound): MessageContainer = withContext(Dispatchers.IO) {
        if (input.kind == "text") return@withContext MessageContainer.of(input.body)
        val strategy = strategies[input.kind] ?: throw GatewayFailure(400, "Unsupported message kind")
        val (bytes, mime) = download(input.mediaUrl ?: throw GatewayFailure(400, "Missing media URL"))
        strategy(input, bytes, mime)
    }
    private fun download(url: String): Pair<ByteArray, String> {
        val uri = URI.create(url)
        validateMediaOrigin(uri, allowedHost)
        val response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { input ->
            if (response.statusCode() != 200) throw GatewayFailure(502, "Attachment storage is unavailable")
            val bytes = input.readNBytes(MAX_BYTES + 1)
            if (bytes.size !in 1..MAX_BYTES) throw GatewayFailure(413, "Attachment exceeds the size limit")
            return bytes to response.headers().firstValue("Content-Type").orElse("application/octet-stream").substringBefore(';')
        }
    }
    private fun normalizeVoice(bytes: ByteArray): ByteArray {
        val input = Files.createTempFile("helpdesk-voice-", ".input")
        val output = Files.createTempFile("helpdesk-voice-", ".ogg")
        try {
            Files.write(input, bytes)
            val process = ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-y", "-protocol_whitelist", "file,pipe", "-format_whitelist", "ogg,mov,matroska,webm,wav,mp3", "-i", input.toString(), "-t", "120", "-vn", "-c:a", "libopus", "-b:a", "32k", "-ac", "1", output.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            if (!process.waitFor(25, TimeUnit.SECONDS)) { process.destroyForcibly(); throw GatewayFailure(422, "Voice conversion timed out") }
            if (process.exitValue() != 0) throw GatewayFailure(422, "Voice recording cannot be decoded")
            return Files.readAllBytes(output)
        } finally { Files.deleteIfExists(input); Files.deleteIfExists(output) }
    }
    companion object { const val MAX_BYTES = 16 * 1024 * 1024 }
}
fun validateMediaOrigin(uri: URI, allowedHost: String) {
    if (uri.scheme != "https" || uri.host != allowedHost) throw GatewayFailure(400, "Untrusted attachment origin")
    if (uri.userInfo != null || uri.port !in setOf(-1, 443)) throw GatewayFailure(400, "Invalid attachment URL")
}
