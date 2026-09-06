package helpdesk

import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TurnCredentials {
    fun issue(agent: String): IceConfiguration {
        val secret = System.getenv("TURN_SHARED_SECRET") ?: throw DomainError.Unavailable("TURN is not configured")
        val urls = env("TURN_URLS").split(',')
        val username = "${Instant.now().epochSecond + 600}:$agent"
        val hmac = Mac.getInstance("HmacSHA1")
        hmac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA1"))
        val credential = Base64.getEncoder().encodeToString(hmac.doFinal(username.toByteArray()))
        return IceConfiguration(listOf(IceServer(urls, username, credential)))
    }
}
