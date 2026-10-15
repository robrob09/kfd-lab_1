package schedule

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParsingTest {
    private fun row(d: String = "2026-10-05", type: String = "LECTURE", t: String = "Topic",
                    l: String = "Room 1", names: String = "Ivanov") = "$d|$type|$t|$l|$names"

    private fun reject(line: String, r: Reason) {
        assertEquals(ParseResult.Rejected(7, r), parseLine(7, line), line)
    }

    @Test
    fun parsesAllTypesAndPreservesValues() {
        val l = assertIs<Lecture>(assertIs<ParseResult.Ok>(parseLine(1, row(t = "Two words", names = "Petrov,Ivanov"))).session)
        assertEquals(LocalDate.of(2026, 10, 5), l.date)
        assertEquals("Two words", l.topic)
        assertEquals("Room 1", l.location)
        assertEquals(listOf("Petrov", "Ivanov"), l.teachers)
        assertIs<Practice>(assertIs<ParseResult.Ok>(parseLine(2, row(type = "PRACTICE"))).session)
        assertIs<Exam>(assertIs<ParseResult.Ok>(parseLine(3, row(type = "EXAM", names = "Ivanov,ivanov"))).session)
        assertIs<Cancelled>(assertIs<ParseResult.Ok>(parseLine(4, row(type = "CANCELLED", l = "-", names = "-"))).session)
        for (d in listOf("0001-01-01", "9999-12-31", "2024-02-29")) {
            assertIs<ParseResult.Ok>(parseLine(1, row(d = d)))
        }
    }

    @Test
    fun rejectsSeparatorsCountsAndEmptyFields() {
        reject("", Reason.Separator)
        reject(" ", Reason.Separator)
        reject("2026-10-05;LECTURE;Topic;Room 1;Ivanov", Reason.Separator)
        reject("2026-10-05|LECTURE|Topic|Room 1", Reason.Fields(4))
        reject(row() + "|", Reason.Fields(6))
        reject("||||", Reason.Empty("date"))
        reject(row(d = " ", type = ""), Reason.Empty("date"))
        reject(row(type = ""), Reason.Empty("type"))
        reject(row(t = "\t"), Reason.Empty("topic"))
        reject(row(l = ""), Reason.Empty("location"))
        reject(row(names = ""), Reason.Empty("teachers"))
    }

    @Test
    fun rejectsDatesAndUnknownTypes() {
        for (d in listOf("0000-01-01", "10000-01-01", "2026-02-29", "2026-13-45", "2026-1-05",
            " 2026-10-05", "+2026-10-05", "２０２６-10-05")) {
            reject(row(d = d), Reason.Date(d))
        }
        for (type in listOf("SEMINAR", "lecture", "LECTURE ")) reject(row(type = type), Reason.Type(type))
    }

    @Test
    fun rejectsEveryDomainViolation() {
        val cases = listOf(
            row(t = "A\tB") to Reason.Control("topic", "A\tB"),
            row(l = "A\u0000B") to Reason.Control("location", "A\u0000B"),
            row(names = "Ivanov,A\u0085B") to Reason.Control("teachers", "A\u0085B"),
            row(t = " Topic") to Reason.Whitespace("topic", " Topic"),
            row(l = "Room ") to Reason.Whitespace("location", "Room "),
            row(names = "Ivanov, Petrov") to Reason.Whitespace("teachers", " Petrov"),
            row(type = "CANCELLED", names = "-") to Reason.Cancellation("location", "Room 1"),
            row(type = "CANCELLED", l = "-", names = "Ivanov,Petrov") to Reason.Cancellation("teachers", "Ivanov,Petrov"),
            row(l = "-") to Reason.Location("-"),
            row(names = "-") to Reason.Teacher("-"),
            row(names = "Ivanov,") to Reason.Teacher(""),
            row(names = ",Ivanov") to Reason.Teacher(""),
            row(names = "Ivanov,,Petrov") to Reason.Teacher(""),
            row(names = "Ivanov,Petrov,Ivanov,Petrov") to Reason.Duplicate("Ivanov"),
        )
        for ((line, r) in cases) reject(line, r)
    }

    @Test
    fun countsExamNamesBeforeCheckingOtherDomainRules() {
        reject(row(type = "EXAM", names = "-"), Reason.Teachers(0))
        reject(row(type = "EXAM", names = " ,-,"), Reason.Teachers(0))
        reject(row(type = "EXAM", names = "Ivanov,,-"), Reason.Teachers(1))
        reject(row(type = "EXAM", t = "A\tB"), Reason.Teachers(1))
        reject(row(type = "EXAM", names = "Ivanov,Ivanov"), Reason.Duplicate("Ivanov"))
        reject(row(type = "EXAM", names = "Ivanov,Petrov,"), Reason.Teacher(""))
        reject(row(type = "EXAM", names = ""), Reason.Empty("teachers"))
    }

    @Test
    fun choosesTheRequiredErrorWhenSeveralDefectsExist() {
        val cases = listOf(
            row(d = "2026-13-45", type = "SEMINAR") to Reason.Date("2026-13-45"),
            row(d = "bad", t = "") to Reason.Empty("topic"),
            (row(d = "", type = "") + "|") to Reason.Fields(6),
            row(type = "SEMINAR", t = "A\tB") to Reason.Type("SEMINAR"),
            row(t = "A\tB", l = "C\tD") to Reason.Control("topic", "A\tB"),
            row(t = " Topic", l = "A\tB") to Reason.Control("location", "A\tB"),
            row(t = " Topic", names = "A\tB") to Reason.Control("teachers", "A\tB"),
            row(t = " Topic", l = " Room") to Reason.Whitespace("topic", " Topic"),
            row(type = "CANCELLED", l = "Room", names = "Ivanov") to Reason.Cancellation("location", "Room"),
            row(l = "-", names = "Ivanov,") to Reason.Location("-"),
            row(names = "Ivanov,Ivanov,-") to Reason.Teacher("-"),
            row(names = "Ivanov,Ivanov,Petrov ") to Reason.Whitespace("teachers", "Petrov "),
        )
        for ((line, r) in cases) reject(line, r)
    }

    @Test
    fun formatsTheSixRequiredMessagesExactly() {
        val cases = listOf(
            Reason.Date("2026-13-45") to "invalid date \"2026-13-45\"",
            Reason.Empty("date") to "empty required field \"date\"",
            Reason.Separator to "invalid field separator",
            Reason.Type("SEMINAR") to "unknown session type \"SEMINAR\"",
            Reason.Teachers(1) to "type \"EXAM\" requires at least 2 teachers, got 1",
            Reason.Fields(6) to "got 6 fields, expected 5",
        )
        for ((r, text) in cases) assertEquals("line 12: $text", formatError(ParseResult.Rejected(12, r)))
    }

    @Test
    fun escapesQuotesBackslashesControlsAndUtf16Characters() {
        val value = "A\\\"\n\r\t\u0000\u007F\u0085\u0416\uD83D\uDE00"
        val expected = "line 9: unknown session type \"A\\\\\\\"\\u000A\\u000D\\u0009\\u0000\\u007F\\u0085\\u0416\\uD83D\\uDE00\""
        assertEquals(expected, formatError(ParseResult.Rejected(9, Reason.Type(value))))
        assertEquals("line 7: control character in field \"topic\": \"A\\u0009B\"",
            formatError(assertIs<ParseResult.Rejected>(parseLine(7, row(t = "A\tB")))))
    }

    @Test
    fun formatsTeachersAndCancellation() {
        val s = Exam(LocalDate.of(1, 1, 1), "Topic", "Room 1", listOf("Petrov", "Ivanov"))
        assertEquals("0001-01-01 | EXAM | Topic | Room 1", formatSession(s))
        assertEquals("0001-01-01 | EXAM | Topic | Room 1 | Petrov, Ivanov", formatSession(s, true))
        val c = Cancelled(LocalDate.of(9999, 12, 31), "Topic")
        assertEquals("9999-12-31 | CANCELLED | Topic | -", formatSession(c))
        assertEquals("9999-12-31 | CANCELLED | Topic | - | -", formatSession(c, true))
    }
}
