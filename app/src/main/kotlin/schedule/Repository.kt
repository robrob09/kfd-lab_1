package schedule

interface SessionRepository {
    fun add(session: Session)
    fun addAll(sessions: List<Session>)
    fun all(): List<Session>
}

class MemorySessionRepository : SessionRepository {
    private val data = mutableListOf<Session>()

    override fun add(session: Session) { data.add(session) }
    override fun addAll(sessions: List<Session>) { sessions.forEach(::add) }
    override fun all(): List<Session> = java.util.List.copyOf(data)
}

data class AuditEvent(val op: String, val count: Int)

class AuditedSessionRepository(private val delegate: SessionRepository) : SessionRepository by delegate {
    private val log = mutableListOf<AuditEvent>()
    val events: List<AuditEvent> get() = java.util.List.copyOf(log)
    var added = 0
        private set

    override fun add(session: Session) {
        delegate.add(session)
        record("add", 1)
    }

    override fun addAll(sessions: List<Session>) {
        val n = sessions.size
        delegate.addAll(sessions)
        record("addAll", n)
    }

    private fun record(op: String, n: Int) {
        log.add(AuditEvent(op, n))
        added += n
    }
}
