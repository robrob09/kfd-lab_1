package schedule

import java.time.LocalDate
import java.time.format.DateTimeParseException

sealed interface ParseResult {
    data class Ok(val session: Session) : ParseResult
    data class Rejected(val line: Long, val reason: Reason) : ParseResult
}

sealed interface Reason {
    data object Separator : Reason
    data class Fields(val count: Int) : Reason
    data class Empty(val field: String) : Reason
    data class Date(val value: String) : Reason
    data class Type(val value: String) : Reason
    data class Teachers(val count: Int) : Reason
    data class Control(val field: String, val value: String) : Reason
    data class Whitespace(val field: String, val value: String) : Reason
    data class Cancellation(val field: String, val value: String) : Reason
    data class Location(val value: String) : Reason
    data class Teacher(val value: String) : Reason
    data class Duplicate(val value: String) : Reason
}

fun parseLine(n: Long, line: String): ParseResult {
    fun reject(r: Reason) = ParseResult.Rejected(n, r)
    if ('|' !in line) return reject(Reason.Separator)
    val f = line.split('|')
    if (f.size != 5) return reject(Reason.Fields(f.size))
    val fields = listOf("date", "type", "topic", "location", "teachers")
    for (i in f.indices) if (f[i].isBlank()) return reject(Reason.Empty(fields[i]))
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(f[0])) return reject(Reason.Date(f[0]))
    val d = try {
        LocalDate.parse(f[0])
    } catch (_: DateTimeParseException) {
        return reject(Reason.Date(f[0]))
    }
    if (d.year !in 1..9999) return reject(Reason.Date(f[0]))
    val type = SessionType.entries.firstOrNull { it.name == f[1] }
        ?: return reject(Reason.Type(f[1]))
    val names = f[4].split(',')
    val r = domainReason(f[2], f[3], names, type)
    if (r != null) return reject(r)
    val s = when (type) {
        SessionType.LECTURE -> Lecture(d, f[2], f[3], names)
        SessionType.PRACTICE -> Practice(d, f[2], f[3], names)
        SessionType.EXAM -> Exam(d, f[2], f[3], names)
        SessionType.CANCELLED -> Cancelled(d, f[2])
    }
    return ParseResult.Ok(s)
}

internal fun domainReason(t: String, l: String, names: List<String>, type: SessionType): Reason? {
    if (t.isBlank()) return Reason.Empty("topic")
    if (l.isBlank()) return Reason.Empty("location")
    val n = names.count { it.isNotBlank() && it != "-" }
    if (type == SessionType.EXAM && n < 2) return Reason.Teachers(n)
    val fields = listOf("topic" to t, "location" to l) + names.map { "teachers" to it }
    for ((f, v) in fields) if (v.any { it.isISOControl() }) return Reason.Control(f, v)
    for ((f, v) in fields) if (v != v.trim()) return Reason.Whitespace(f, v)
    if (type == SessionType.CANCELLED) {
        if (l != "-") return Reason.Cancellation("location", l)
        val v = names.joinToString(",")
        return if (v == "-") null else Reason.Cancellation("teachers", v)
    }
    if (l == "-") return Reason.Location(l)
    for (v in names) if (v.isBlank() || v == "-") return Reason.Teacher(v)
    val seen = mutableSetOf<String>()
    for (v in names) if (!seen.add(v)) return Reason.Duplicate(v)
    return null
}
