package helpdesk

import kotlin.test.*
import java.util.UUID

class DomainTest {
    @Test fun `empty text is rejected before persistence`() {
        assertFailsWith<DomainError.Invalid> { validateMessage(SendMessage(UUID.randomUUID().toString(), body = "  ")) }
    }
    @Test fun `media messages require an attachment`() {
        assertFailsWith<DomainError.Invalid> { validateMessage(SendMessage(UUID.randomUUID().toString(), kind = "voice")) }
    }
    @Test fun `invalid id cannot reach SQL`() {
        assertFailsWith<DomainError.Invalid> { uuid("not-a-uuid") }
    }
    @Test fun `routing strategies handle metadata and ignore case`() {
        val router = DepartmentRouter()
        val context = RoutingContext("Refund my order", listOf("VIP"), "north")
        assertTrue(router.matches("keyword", "refund", context))
        assertTrue(router.matches("tag", "vip", context))
        assertTrue(router.matches("region", "North", context))
        assertFalse(router.matches("unknown", "anything", context))
        assertFalse(router.matches("keyword", "technical", context))
    }
    @Test fun `another agent cannot send or resolve a chat`() {
        val chat = Conversation("id", "Customer", "jid", "queue", "owner", "assigned", emptyList(), "", 0, null, "")
        assertFailsWith<DomainError.Forbidden> { requireOwner(chat, "other-agent") }
        assertFailsWith<DomainError.Conflict> { requireOwner(chat.copy(status = "resolved"), "owner") }
    }
}
