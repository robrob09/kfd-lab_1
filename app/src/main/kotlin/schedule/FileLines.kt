package schedule

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

fun forEachScheduleLine(file: String, action: (Long, String) -> Unit) {
    Files.newBufferedReader(Path.of(file), StandardCharsets.UTF_8).use { reader ->
        var number = 0L
        while (true) {
            val line = reader.readLine() ?: break
            number += 1
            action(number, line)
        }
    }
}
