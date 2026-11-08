package ru.pyshkov.llm.day04;

import ru.pyshkov.llm.core.check.CheckResult;
import ru.pyshkov.llm.core.check.Checker;
import ru.pyshkov.llm.core.experiment.StepResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Проверка формата генерации: ровно 5 строк без нумерации и списков. */
public final class Day04GenerationChecker implements Checker {

    private static final Pattern NUMBERED = Pattern.compile("^\\d+[.)]\\s+.*");
    private static final Pattern BULLET = Pattern.compile("^[-*•]\\s+.*");

    @Override
    public CheckResult check(List<StepResult> steps) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        StepResult last = steps.isEmpty() ? null : steps.get(steps.size() - 1);
        if (last == null || last.response() == null) {
            flags.put("format", false);
            flags.put("truncated", false);
            errors.add("нет ответа");
            return new CheckResult(flags, errors);
        }

        String content = last.response().content();
        List<String> lines = lines(content);
        boolean format = lines.size() == 5
                && lines.stream().noneMatch(line -> NUMBERED.matcher(line).matches()
                        || BULLET.matcher(line).matches());
        boolean truncated = "length".equals(last.response().doneReason());

        flags.put("format", format);
        flags.put("truncated", truncated);
        if (lines.size() != 5) {
            errors.add("ожидалось 5 строк, получено " + lines.size());
        }
        if (lines.stream().anyMatch(line -> NUMBERED.matcher(line).matches())) {
            errors.add("есть нумерация строк");
        }
        if (lines.stream().anyMatch(line -> BULLET.matcher(line).matches())) {
            errors.add("есть маркированный список");
        }
        return new CheckResult(flags, errors);
    }

    public static List<String> lines(String content) {
        return content.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList();
    }
}
