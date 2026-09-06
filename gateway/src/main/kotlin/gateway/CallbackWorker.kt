package gateway

import kotlinx.coroutines.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class CallbackWorker(private val repository: GatewayRepository) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    suspend fun run() = withContext(Dispatchers.IO) {
        while (currentCoroutineContext().isActive) {
            attempt()
        }
    }
    private suspend fun attempt() {
        try { deliverNext() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { delay(5000) }
    }
    private suspend fun deliverNext() {
        val event = repository.next()
        if (event == null) { delay(500); return }
        try {
            val request = HttpRequest.newBuilder(URI.create(setting("BACKEND_CALLBACK_URL"))).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer ${setting("COBALT_WEBHOOK_TOKEN")}").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event.second)).build()
            val response = http.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() !in 200..299) { repository.retry(event.first); return }
            repository.delivered(event.first)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { repository.retry(event.first) }
    }
}
