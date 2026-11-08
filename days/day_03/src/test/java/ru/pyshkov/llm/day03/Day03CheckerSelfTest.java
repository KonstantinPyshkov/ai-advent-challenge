package ru.pyshkov.llm.day03;

import ru.pyshkov.llm.core.check.CheckResult;
import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;
import ru.pyshkov.llm.core.client.Message;
import ru.pyshkov.llm.core.experiment.StepResult;

import java.util.List;

/** Проверки Day03Checker без JUnit. */
public final class Day03CheckerSelfTest {

    public static void main(String[] args) {
        Day03Task numeric = new Day03Task("a", "Арифметика", "17*23", "391", "numeric", List.of());
        Day03Task normalized = new Day03Task("b", "Логика", "?", "нет", "normalized",
                List.of("нет", "не следует"));

        check(new Day03Checker(numeric), "391", false, false);
        check(new Day03Checker(numeric), "ОТВЕТ: 391", true, true);
        check(new Day03Checker(numeric), "ОТВЕТ: 392", true, false);
        check(new Day03Checker(numeric), "391", false, false);
        check(new Day03Checker(normalized), "ОТВЕТ: не следует", true, true);
        check(new Day03Checker(normalized), "ОТВЕТ: да", true, false);
        System.out.println("Day03CheckerSelfTest OK");
    }

    private static void check(Day03Checker checker, String content,
                              boolean answer, boolean correct) {
        ChatRequest request = ChatRequest.builder()
                .model("test").message(Message.user("q")).build();
        ChatResponse response = new ChatResponse(
                "test", content, null, "stop", 1, 1, 1, 0, 0, false);
        CheckResult result = checker.check(List.of(new StepResult("answer", request, response, null, 1)));
        if (result.flag("answer") != answer || result.flag("correct") != correct) {
            throw new AssertionError("content=" + content + " flags=" + result.flags());
        }
    }
}
