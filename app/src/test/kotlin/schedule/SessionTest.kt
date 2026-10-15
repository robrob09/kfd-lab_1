package schedule

import java.time.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionTest {
    private val d = LocalDate.of(2026, 10, 5)
    private val names = listOf("Ivanov", "Petrov")
    private val make = listOf<(LocalDate, String, String, List<String>) -> Session>(
        ::Lecture, ::Practice, ::Exam, { date, topic, _, _ -> Cancelled(date, topic) },
    )
    private val lists = listOf<(List<String>) -> List<String>>(
        { Lecture(d, "Topic", "Room 1", it).teachers },
        { Practice(d, "Topic", "Room 1", it).teachers },
        { Exam(d, "Topic", "Room 1", it).teachers },
    )

    @Test
    fun createsAllTypesAndBoundaryYears() {
        assertEquals(SessionType.entries, make.map { it(d, "Topic", "Room 1", names).type })
        for (f in make) for (year in listOf(1, 9999)) {
            val date = LocalDate.of(year, 1, 1)
            val s = f(date, "Two words", "Room 1", names)
            assertEquals(date, s.date)
            assertEquals("Two words", s.topic)
        }
        assertEquals(listOf("Ivanov", "ivanov"), Exam(d, "Topic", "Room 1", listOf("Ivanov", "ivanov")).teachers)
        assertEquals(listOf("Ivan Ivanov"), Lecture(d, "Two words", "Room 1", listOf("Ivan Ivanov")).teachers)
        assertEquals("-", Cancelled(d, "-").topic)
        assertEquals("Тема", Cancelled(d, "Тема").topic)
    }

    @Test
    fun rejectsBadYearsAndTopicsInEveryConstructor() {
        for (f in make) {
            for (year in listOf(0, 10000)) {
                val e = assertFailsWith<IllegalArgumentException> { f(LocalDate.of(year, 1, 1), "Topic", "Room", names) }
                assertTrue(e.message.orEmpty().isNotBlank())
            }
            for (t in listOf("", " ", "\t", " Topic", "Topic ", "A\u0000B", "A\u007FB", "A\u0085B", "\u00A0Topic")) {
                assertFailsWith<IllegalArgumentException>(t) { f(d, t, "Room", names) }
            }
        }
    }

    @Test
    fun rejectsBadLocationsAndNames() {
        for (f in make.take(3)) {
            for (l in listOf("", " ", "-", " Room", "Room ", "A\tB")) {
                assertFailsWith<IllegalArgumentException>(l) { f(d, "Topic", l, names) }
            }
            for (n in listOf(emptyList(), listOf(""), listOf(" "), listOf("-"), listOf(" Ivanov"),
                listOf("Ivanov "), listOf("A\tB"), listOf("Ivanov", "Ivanov"))) {
                assertFailsWith<IllegalArgumentException>(n.toString()) { f(d, "Topic", "Room", n) }
            }
        }
        assertFailsWith<IllegalArgumentException> { Exam(d, "Topic", "Room", listOf("Ivanov")) }
        assertFailsWith<IllegalArgumentException> { Exam(d, "Topic", "Room", listOf("Ivanov", "-")) }
    }

    @Test
    fun copiesTheOriginalList() {
        for (f in lists) {
            val src = names.toMutableList()
            val saved = f(src)
            src.clear()
            assertEquals(names, saved)
        }
    }

    @Test
    fun protectsTheReturnedList() {
        for (f in lists) {
            val saved = f(names)
            assertFailsWith<UnsupportedOperationException> { (saved as MutableList<String>).clear() }
            assertEquals(names, saved)
        }
    }
}
