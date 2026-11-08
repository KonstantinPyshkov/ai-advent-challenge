package ru.pyshkov.llm.day05;

import ru.pyshkov.llm.core.check.CheckResult;
import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;
import ru.pyshkov.llm.core.client.Message;
import ru.pyshkov.llm.core.experiment.StepResult;

import java.util.List;

/** Проверки Day05Checker без JUnit. */
public final class Day05CheckerSelfTest {

    public static void main(String[] args) {
        Day05Task numeric = new Day05Task("a", "A", "?", "136", "numeric", List.of());
        Day05Task normalized = new Day05Task("b", "B", "?", "нет", "normalized",
                List.of("нет", "не следует"));
        check(new Day05Checker(numeric), "ОТВЕТ: 136", true, true);
        check(new Day05Checker(numeric), "ОТВЕТ: 135", true, false);
        check(new Day05Checker(numeric), "136", false, false);
        check(new Day05Checker(normalized), "ОТВЕТ: не следует", true, true);
        check(new Day05Checker(normalized), "ОТВЕТ: да", true, false);
        System.out.println("Day05CheckerSelfTest OK");
    }

    private static void check(Day05Checker checker, String content,
                              boolean answer, boolean correct) {
        ChatRequest request = ChatRequest.builder()
                .model("test").message(Message.user("q")).build();
        ChatResponse response = new ChatResponse(
                "test", content, null, "stop", 1, 1, 1, 0, 0, false);
        CheckResult result = checker.check(List.of(new StepResult("answer", request, response, null, 1)));
        if (result.flag("answer") != answer || result.flag("correct") != correct) {
            throw new AssertionError(content + " -> " + result.flags());
        }
    }
}
