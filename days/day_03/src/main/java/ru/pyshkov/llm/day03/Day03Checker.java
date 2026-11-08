package ru.pyshkov.llm.day03;

import ru.pyshkov.llm.core.check.CheckResult;
import ru.pyshkov.llm.core.check.Checker;
import ru.pyshkov.llm.core.experiment.StepResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Проверка финального ответа после маркера ОТВЕТ. */
public final class Day03Checker implements Checker {

    private static final Pattern ANSWER = Pattern.compile("(?i)ОТВЕТ\\s*:\\s*([^\\r\\n]+)");
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:[.,]\\d+)?");

    private final Day03Task task;

    public Day03Checker(Day03Task task) {
        this.task = task;
    }

    @Override
    public CheckResult check(List<StepResult> steps) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        StepResult last = steps.isEmpty() ? null : steps.get(steps.size() - 1);
        if (last == null || last.response() == null) {
            flags.put("answer", false);
            flags.put("correct", false);
            flags.put("truncated", false);
            errors.add("нет ответа");
            return new CheckResult(flags, errors);
        }

        String content = last.response().content();
        String answer = extractAnswer(content);
        boolean hasAnswer = answer != null && !answer.isBlank();
        boolean correct = hasAnswer && matches(answer);
        boolean truncated = "length".equals(last.response().doneReason());

        flags.put("answer", hasAnswer);
        flags.put("correct", correct);
        flags.put("truncated", truncated);

        if (!hasAnswer) {
            errors.add("не найден маркер ОТВЕТ");
        } else if (!correct) {
            errors.add("ответ не совпал: «" + answer + "», ожидалось «"
                    + task.expected() + "»");
        }
        return new CheckResult(flags, errors);
    }

    private boolean matches(String answer) {
        return switch (task.check()) {
            case "numeric" -> numeric(answer);
            case "normalized" -> normalized(answer);
            default -> normalize(answer).equals(normalize(task.expected()));
        };
    }

    private boolean numeric(String answer) {
        Matcher matcher = NUMBER.matcher(answer);
        String last = null;
        while (matcher.find()) {
            last = matcher.group();
        }
        if (last == null) {
            return false;
        }
        try {
            return new BigDecimal(last.replace(',', '.'))
                    .compareTo(new BigDecimal(task.expected().replace(',', '.'))) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean normalized(String answer) {
        String actual = normalize(answer);
        if (!task.accepted().isEmpty()) {
            return task.accepted().stream().map(Day03Checker::normalize).anyMatch(actual::equals);
        }
        return actual.equals(normalize(task.expected()));
    }

    private static String extractAnswer(String content) {
        Matcher matcher = ANSWER.matcher(content);
        String answer = null;
        while (matcher.find()) {
            answer = matcher.group(1).strip();
        }
        return answer;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^a-zа-я0-9]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
