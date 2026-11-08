package ru.pyshkov.llm.day04;

import ru.pyshkov.llm.core.check.CheckResult;
import ru.pyshkov.llm.core.check.Checker;
import ru.pyshkov.llm.core.experiment.StepResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Проверка задачи с одним правильным ответом. */
public final class Day04AnswerChecker implements Checker {

    private static final Pattern ANSWER = Pattern.compile("(?i)ОТВЕТ\\s*:\\s*([^\\r\\n]+)");
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:[.,]\\d+)?");

    private final String expected;

    public Day04AnswerChecker(String expected) {
        this.expected = expected;
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
        boolean correct = hasAnswer && numericEquals(answer);
        boolean truncated = "length".equals(last.response().doneReason());

        flags.put("answer", hasAnswer);
        flags.put("correct", correct);
        flags.put("truncated", truncated);

        if (!hasAnswer) {
            errors.add("не найден маркер ОТВЕТ");
        } else if (!correct) {
            errors.add("ответ не совпал: «" + answer + "», ожидалось «" + expected + "»");
        }
        return new CheckResult(flags, errors);
    }

    private boolean numericEquals(String answer) {
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
                    .compareTo(new BigDecimal(expected.replace(',', '.'))) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String extractAnswer(String content) {
        Matcher matcher = ANSWER.matcher(content);
        String answer = null;
        while (matcher.find()) {
            answer = matcher.group(1).strip();
        }
        return answer;
    }
}
