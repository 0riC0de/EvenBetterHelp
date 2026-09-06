package helpdesk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.UUID

class MediaService(private val db: Database, private val conversations: ConversationRepository) : AutoCloseable {
    private val bucket = System.getenv("S3_BUCKET")?.takeIf { it.isNotBlank() }
    private val region = Region.of(System.getenv("AWS_REGION") ?: "us-east-1")
    private val signer by lazy { S3Presigner.builder().region(region).credentialsProvider(DefaultCredentialsProvider.builder().build()).build() }
    private val storage by lazy { S3Client.builder().region(region).build() }
    private val allowed = setOf("image/jpeg", "image/png", "image/webp", "application/pdf", "audio/webm", "audio/ogg", "audio/mp4")

    suspend fun upload(agent: String, conversation: String, input: UploadRequest): UploadGrant {
        if (input.contentType !in allowed) throw DomainError.Invalid("Unsupported file type")
        if (input.bytes !in 1..16777216) throw DomainError.Invalid("Files must be under 16 MB")
        if (input.fileName.length !in 1..200) throw DomainError.Invalid("Invalid file name")
        val id = UUID.randomUUID().toString()
        val key = "attachments/$conversation/$id"
        val grant = presignUpload(id, key, input)
        db.transaction { connection ->
            requireOwner(conversations.accessible(connection, agent, conversation), agent)
            connection.execute("INSERT INTO media_assets(id,conversation_id,agent_id,object_key,file_name,content_type,bytes) VALUES (?,?,?,?,?,?,?)",
                uuid(id), uuid(conversation), uuid(agent), key, input.fileName, input.contentType, input.bytes)
        }
        return grant
    }
    private suspend fun presignUpload(id: String, key: String, input: UploadRequest): UploadGrant = withContext(Dispatchers.IO) {
        val request = PutObjectRequest.builder().bucket(requireBucket()).key(key).contentType(input.contentType).contentLength(input.bytes).build()
        val signed = signer.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(5)).putObjectRequest(request).build())
        UploadGrant(id, signed.url().toString(), mapOf("Content-Type" to input.contentType))
    }
    suspend fun verify(agent: String, conversation: String, id: String) {
        val asset = db.transaction { connection ->
            requireOwner(conversations.accessible(connection, agent, conversation), agent)
            connection.query("SELECT object_key,bytes,content_type FROM media_assets WHERE id=? AND conversation_id=? AND agent_id=?", uuid(id), uuid(conversation), uuid(agent)) {
                Triple(it.getString(1), it.getLong(2), it.getString(3))
            }.firstOrNull() ?: throw DomainError.Forbidden()
        }
        withContext(Dispatchers.IO) {
            val head = storage.headObject(HeadObjectRequest.builder().bucket(requireBucket()).key(asset.first).build())
            if (head.contentLength() != asset.second || head.contentType() != asset.third) throw DomainError.Invalid("Uploaded file does not match the declared attachment")
        }
    }
    suspend fun download(agent: String, id: String): DownloadGrant {
        db.transaction { connection ->
            val chat = connection.query("SELECT conversation_id FROM media_assets WHERE id=?", uuid(id)) { it.getString(1) }.firstOrNull() ?: throw DomainError.Forbidden()
            conversations.accessible(connection, agent, chat)
        }
        return DownloadGrant(internalDownload(id))
    }
    suspend fun internalDownload(id: String): String {
        val key = db.transaction { connection -> connection.query("SELECT object_key FROM media_assets WHERE id=?", uuid(id)) { it.getString(1) }.firstOrNull() } ?: throw DomainError.Invalid("Attachment not found")
        return withContext(Dispatchers.IO) {
            val get = GetObjectRequest.builder().bucket(requireBucket()).key(key).responseContentDisposition("attachment").build()
            signer.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(5)).getObjectRequest(get).build()).url().toString()
        }
    }
    private fun requireBucket(): String = bucket ?: throw DomainError.Unavailable("Attachment storage is not configured")
    override fun close() { if (bucket != null) { signer.close(); storage.close() } }
}
