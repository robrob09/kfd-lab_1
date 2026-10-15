package schedule

import java.time.LocalDate

sealed interface Session {
    val date: LocalDate
    val topic: String
}

class Lecture(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = java.util.List.copyOf(teachers)

    init { checkSession(date, topic, location, this.teachers, SessionType.LECTURE) }
}

class Practice(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = java.util.List.copyOf(teachers)

    init { checkSession(date, topic, location, this.teachers, SessionType.PRACTICE) }
}

class Exam(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = java.util.List.copyOf(teachers)

    init { checkSession(date, topic, location, this.teachers, SessionType.EXAM) }
}

class Cancelled(override val date: LocalDate, override val topic: String) : Session {
    init { checkSession(date, topic, "-", listOf("-"), SessionType.CANCELLED) }
}

private fun checkSession(d: LocalDate, t: String, l: String, names: List<String>, type: SessionType) {
    require(d.year in 1..9999) { "Year must be in 1..9999" }
    require(type == SessionType.CANCELLED || names.isNotEmpty()) { "At least 1 teacher is required" }
    val r = domainReason(t, l, names, type)
    require(r == null) { reasonText(checkNotNull(r)) }
}

val Session.type: SessionType
    get() = when (this) {
        is Lecture -> SessionType.LECTURE
        is Practice -> SessionType.PRACTICE
        is Exam -> SessionType.EXAM
        is Cancelled -> SessionType.CANCELLED
    }
