package schedule

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RepositoryTest {
    private val a = Cancelled(LocalDate.of(2026, 10, 5), "A")
    private val b = Cancelled(LocalDate.of(2026, 10, 6), "B")

    @Test
    fun keepsOrderDuplicatesAndIndependentSnapshots() {
        val repo = MemorySessionRepository()
        val empty = repo.all()
        repo.add(a)
        val first = repo.all()
        val batch = mutableListOf(b, a, Cancelled(a.date, a.topic))
        val expected = listOf(a) + batch
        repo.addAll(batch)
        batch.clear()
        assertEquals(emptyList(), empty)
        assertEquals(listOf(a), first)
        assertEquals(expected, repo.all())
        val saved = repo.all()
        assertFailsWith<UnsupportedOperationException> { (saved as MutableList<Session>).clear() }
        assertEquals(expected, repo.all())
    }

    @Test
    fun auditsSingleBatchAndEmptyBatchWithoutDoubleCounting() {
        val inner = MemorySessionRepository()
        val repo = AuditedSessionRepository(inner)
        repo.add(a)
        val first = repo.events
        repo.addAll(listOf(b, a, b))
        repo.addAll(emptyList())
        assertEquals(listOf(a, b, a, b), repo.all())
        assertEquals(inner.all(), repo.all())
        assertEquals(4, repo.added)
        assertEquals(listOf(AuditEvent("add", 1)), first)
        assertEquals(listOf(AuditEvent("add", 1), AuditEvent("addAll", 3), AuditEvent("addAll", 0)), repo.events)
        assertFailsWith<UnsupportedOperationException> { (repo.events as MutableList<AuditEvent>).clear() }
        assertEquals(3, repo.events.size)
    }

    @Test
    fun doesNotAuditFailedOperations() {
        val inner = object : SessionRepository {
            override fun add(session: Session) { error("failed") }
            override fun addAll(sessions: List<Session>) { error("failed") }
            override fun all(): List<Session> = emptyList()
        }
        val repo = AuditedSessionRepository(inner)
        assertFailsWith<IllegalStateException> { repo.add(a) }
        assertFailsWith<IllegalStateException> { repo.addAll(listOf(a, b)) }
        assertEquals(0, repo.added)
        assertEquals(emptyList(), repo.events)
    }
}
