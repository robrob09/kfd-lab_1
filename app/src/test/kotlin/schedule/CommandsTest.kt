package schedule

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandsTest {
    private fun run(vararg args: String): Pair<Int, String> {
        val out = ByteArrayOutputStream()
        val old = System.out
        try {
            PrintStream(out, true, UTF_8).use {
                System.setOut(it)
                val code = runApplication(arrayOf(*args))
                return code to out.toString(UTF_8).replace("\r\n", "\n")
            }
        } finally {
            System.setOut(old)
        }
    }

    @Test
    fun includesBothDatesPreservesOrderAndChecksRejectedRowsOutsideThePeriod(@TempDir dir: Path) {
        val file = dir.resolve("period.txt")
        Files.writeString(file, listOf(
            "2026-10-31|LECTURE|Last|Room 1|Ivanov",
            "2026-10-01|PRACTICE|First|Room 2|Petrov",
            "2026-09-30|LECTURE|Outside|Room 1|Ivanov",
            "2026-10-15|CANCELLED|Middle|-|-",
            "2027-01-01|LECTURE||Room 1|Ivanov",
        ).joinToString("\n"))
        val (code, text) = run("period", "--file", file.toString(), "--from", "2026-10-01", "--to", "2026-10-31")
        assertEquals(1, code)
        assertEquals("2026-10-31 | LECTURE | Last | Room 1\n" +
            "2026-10-01 | PRACTICE | First | Room 2\n" +
            "2026-10-15 | CANCELLED | Middle | -\nTotal: 3\n", text)
        assertEquals(1 to "2026-10-01 | PRACTICE | First | Room 2\nTotal: 1\n",
            run("period", "--file", file.toString(), "--from", "2026-10-01", "--to", "2026-10-01"))
        assertEquals(1 to "Total: 0\n",
            run("period", "--file", file.toString(), "--from", "2025-01-01", "--to", "2025-12-31"))
    }

    @Test
    fun printsEveryTypeAndRejectsRowsOutsideTheTypeFilter(@TempDir dir: Path) {
        val file = dir.resolve("types.txt")
        Files.writeString(file, "2026-10-05|LECTURE|L|Room 1|Ivanov\r\n" +
            "2026-10-05|PRACTICE|P|Room 2|Petrov\r\n" +
            "2026-10-05|EXAM|E|Room 3|Petrov,Ivanov\r\n" +
            "2026-10-05|CANCELLED|C|-|-\r\n")
        val rows = listOf(
            "LECTURE" to "2026-10-05 | LECTURE | L | Room 1 | Ivanov\n",
            "PRACTICE" to "2026-10-05 | PRACTICE | P | Room 2 | Petrov\n",
            "EXAM" to "2026-10-05 | EXAM | E | Room 3 | Petrov, Ivanov\n",
            "CANCELLED" to "2026-10-05 | CANCELLED | C | - | -\n",
        )
        for ((type, row) in rows) {
            assertEquals(0 to (row + "Total: 1\n"), run("type", "--type", type, "--file", file.toString()))
        }
        Files.writeString(file, "2026-10-05|LECTURE||Room 1|Ivanov\n" +
            "2026-10-05|EXAM|E|Room 3|Petrov,Ivanov\n")
        assertEquals(1 to "2026-10-05 | EXAM | E | Room 3 | Petrov, Ivanov\nTotal: 1\n",
            run("type", "--file", file.toString(), "--type", "EXAM"))
        assertEquals(1 to "Total: 0\n", run("type", "--file", file.toString(), "--type", "PRACTICE"))
    }

    @Test
    fun countsBlankLinesAndKeepsErrorOrder(@TempDir dir: Path) {
        val file = dir.resolve("errors.txt")
        Files.writeString(file, "2026-10-05|LECTURE|Topic|Room 1|Ivanov\r\n\r\n" +
            "2026-13-45|SEMINAR|Topic|Room 1|Ivanov\r\n" +
            "2026-10-05|EXAM|Topic|Room 1|Ivanov,Ivanov\r\n")
        assertEquals(1 to "line 2: invalid field separator\n" +
            "line 3: invalid date \"2026-13-45\"\n" +
            "line 4: duplicate teacher \"Ivanov\"\nRejected: 3 of 4\n",
            run("errors", "--file", file.toString()))
    }

    @Test
    fun printsZeroTotalsForAnEmptyFile(@TempDir dir: Path) {
        val file = Files.createFile(dir.resolve("empty.txt")).toString()
        assertEquals(0 to "Rejected: 0 of 0\n", run("errors", "--file", file))
        assertEquals(0 to "Total: 0\n", run("type", "--file", file, "--type", "EXAM"))
        assertEquals(0 to "Total: 0\n", run("period", "--file", file, "--from", "2026-01-01", "--to", "2026-12-31"))
    }

    @Test
    fun keepsPrintedRowsAndOmitsTheTotalOnAReadingFailure(@TempDir dir: Path) {
        val file = dir.resolve("broken.txt")
        val row = "2026-10-05|LECTURE|Topic|Room 1|Ivanov\n"
        Files.write(file, row.repeat(1000).toByteArray(UTF_8) + byteArrayOf(0xC3.toByte(), 0x28))
        val (code, text) = run("period", "--file", file.toString(), "--from", "2026-10-05", "--to", "2026-10-05")
        assertEquals(2, code)
        assertTrue(text.startsWith("2026-10-05 | LECTURE | Topic | Room 1\n"))
        assertTrue(text.endsWith("Cannot read file\n"))
        assertFalse("Total:" in text)
    }

    @Test
    fun matchesTheOwnBoundarySample() {
        val root = Path.of(System.getProperty("repo.root"))
        val file = root.resolve("examples/edges.txt").toString()
        val expected = Files.readString(root.resolve("examples/edges.errors.expected")).replace("\r\n", "\n")
        assertEquals(1 to expected, run("errors", "--file", file))
    }
}
