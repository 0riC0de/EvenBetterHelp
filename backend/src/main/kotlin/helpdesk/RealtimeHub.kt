package helpdesk

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Events contain invalidations only, never customer content. Clients re-fetch through authorization.
class RealtimeHub {
    private val events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    fun subscribe() = events.asSharedFlow()
    fun changed(id: String) { events.tryEmit(RealtimeEvent("invalidate", id)) }
    private val signals = MutableSharedFlow<Pair<String, Signal>>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    fun callSignals() = signals.asSharedFlow()
    suspend fun signal(agent: String, signal: Signal) { signals.emit(agent to signal) }
}
