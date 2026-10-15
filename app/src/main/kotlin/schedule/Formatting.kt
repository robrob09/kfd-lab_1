package schedule

private fun quote(s: String) = buildString {
    append('"')
    for (c in s) when (c) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        else -> if (c.code in 32..126) append(c) else append("\\u%04X".format(c.code))
    }
    append('"')
}

fun reasonText(r: Reason): String = when (r) {
    Reason.Separator -> "invalid field separator"
    is Reason.Fields -> "got ${r.count} fields, expected 5"
    is Reason.Empty -> "empty required field ${quote(r.field)}"
    is Reason.Date -> "invalid date ${quote(r.value)}"
    is Reason.Type -> "unknown session type ${quote(r.value)}"
    is Reason.Teachers -> "type \"EXAM\" requires at least 2 teachers, got ${r.count}"
    is Reason.Control -> "control character in field ${quote(r.field)}: ${quote(r.value)}"
    is Reason.Whitespace -> "surrounding whitespace in field ${quote(r.field)}: ${quote(r.value)}"
    is Reason.Cancellation -> "type \"CANCELLED\" requires \"-\" in field ${quote(r.field)}, got ${quote(r.value)}"
    is Reason.Location -> "invalid location ${quote(r.value)}"
    is Reason.Teacher -> "invalid teacher ${quote(r.value)}"
    is Reason.Duplicate -> "duplicate teacher ${quote(r.value)}"
}

fun formatError(r: ParseResult.Rejected) = "line ${r.line}: ${reasonText(r.reason)}"

fun formatSession(s: Session, teachers: Boolean = false): String {
    val (l, names) = when (s) {
        is Lecture -> s.location to s.teachers
        is Practice -> s.location to s.teachers
        is Exam -> s.location to s.teachers
        is Cancelled -> "-" to listOf("-")
    }
    val row = "${s.date} | ${s.type} | ${s.topic} | $l"
    return if (teachers) "$row | ${names.joinToString(", ")}" else row
}
