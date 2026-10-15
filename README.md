Сборка и запуск из корня проекта в PowerShell. Нужны JDK 25 и Python 3.10+.

```powershell
$env:JAVA_HOME = 'C:\Java\jdk-25'
$env:GRADLE_USER_HOME = 'C:\Java\gradle-cache'
.\gradlew.bat build :app:installDist

.\app\build\install\schedule\bin\schedule.bat period --file samples/clean.txt --from 2026-09-28 --to 2027-05-13
.\app\build\install\schedule\bin\schedule.bat type --file samples/dirty.txt --type EXAM
.\app\build\install\schedule\bin\schedule.bat errors --file samples/dirty.txt

python -B tools/check_samples.py
python tools/verify.py
.\app\build\install\schedule\bin\schedule.bat errors --file examples/edges.txt
```

Коды завершения: 0 — файл корректен, 1 — есть отклонённые строки, 2 — ошибка аргументов или чтения.

Session — sealed-интерфейс. Lecture, Practice, Exam и Cancelled — обычные конечные классы без copy(): создание всегда проходит через проверки конструктора. У Cancelled только дата и тема.

Пустая тема, годы 0 и 10000, действующее занятие без преподавателей, сдача с одним или двумя одинаковыми преподавателями отклоняются конструктором с IllegalArgumentException. Внешние пробелы, управляющие символы и недопустимые место и имена также запрещены. Значения не исправляются, регистр и порядок сохраняются.

List.copyOf защищает от изменения исходного списка преподавателей. Выданный список неизменяемый: попытка изменения через MutableList вызывает UnsupportedOperationException. all() возвращает такой же независимый снимок реестра; повторы сохраняются.

AuditedSessionRepository использует SessionRepository by delegate. Внутри addAll делегат вызывает собственный add, поэтому обёртка переопределяет оба метода. На успешную операцию записывается одно событие с названием и количеством; пустой пакет записывается с нулём. added считает добавленные занятия. Чтение и неуспешные операции событий не создают.

Парсер возвращает занятие либо номер строки и структурированную причину. Сообщение формируется отдельно. Дополнительные сообщения с префиксом line N:

```text
control character in field "F": "V"
surrounding whitespace in field "F": "V"
type "CANCELLED" requires "-" in field "F", got "V"
invalid location "V"
invalid teacher "V"
duplicate teacher "V"
```

F — поле, V — исходное значение или имя. Кавычки и обратные косые черты экранируются; управляющие и не-ASCII символы записываются как \uXXXX по UTF-16.

examples/edges.txt проверяет границы годов, отмену, приоритет ошибок, повторы, пустые элементы и пробелы. Ожидаемый вывод — examples/edges.errors.expected: 7 отказов из 10 строк, код 1.
