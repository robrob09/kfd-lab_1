package schedule

interface CommandHandlers {
    fun period(command: Command.Period): Int
    fun byType(command: Command.ByType): Int
    fun errors(command: Command.Errors): Int
}

object StudentCommandHandlers : CommandHandlers {
    override fun period(command: Command.Period): Int = runPeriod(command)
    override fun byType(command: Command.ByType): Int = runType(command)
    override fun errors(command: Command.Errors): Int = runErrors(command)
}

fun runPeriod(command: Command.Period): Int = select(command.file, false) {
    it.date in command.from..command.to
}

fun runType(command: Command.ByType): Int = select(command.file, true) {
    it.type == command.type
}

fun runErrors(command: Command.Errors): Int {
    val (total, bad) = read(command.file) {
        if (it is ParseResult.Rejected) println(formatError(it))
    }
    println("Rejected: $bad of $total")
    return if (bad == 0L) 0 else 1
}

private fun select(file: String, teachers: Boolean, test: (Session) -> Boolean): Int {
    var count = 0
    val (_, bad) = read(file) {
        if (it is ParseResult.Ok && test(it.session)) {
            println(formatSession(it.session, teachers))
            count++
        }
    }
    println("Total: $count")
    return if (bad == 0L) 0 else 1
}

private fun read(file: String, action: (ParseResult) -> Unit): Pair<Long, Long> {
    val repo = AuditedSessionRepository(MemorySessionRepository())
    var total = 0L
    var bad = 0L
    forEachScheduleLine(file) { n, line ->
        total = n
        val r = parseLine(n, line)
        when (r) {
            is ParseResult.Ok -> repo.add(r.session)
            is ParseResult.Rejected -> bad++
        }
        action(r)
    }
    return total to bad
}
