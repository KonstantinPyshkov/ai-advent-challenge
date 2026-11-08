package ru.pyshkov.llm.day04;

import ru.pyshkov.llm.core.check.CheckResult;
import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;
import ru.pyshkov.llm.core.client.Message;
import ru.pyshkov.llm.core.experiment.StepResult;

import java.util.List;

/** Проверки Day04 без JUnit. */
public final class Day04CheckerSelfTest {

    public static void main(String[] args) {
        answer("ОТВЕТ: 352", true, true);
        answer("ОТВЕТ: 353", true, false);
        answer("352", false, false);
        generation("Название 1\nНазвание 2\nНазвание 3\nНазвание 4\nНазвание 5", true);
        generation("1. Название 1\n2. Название 2\n3. Название 3\n4. Название 4\n5. Название 5", false);
        generation("Одно\nДва\nТри\nЧетыре", false);
        System.out.println("Day04CheckerSelfTest OK");
    }

    private static void answer(String content, boolean hasAnswer, boolean correct) {
        CheckResult result = check(new Day04AnswerChecker("352"), content);
        require(result.flag("answer") == hasAnswer, "answer: " + content);
        require(result.flag("correct") == correct, "correct: " + content);
    }

    private static void generation(String content, boolean format) {
        CheckResult result = check(new Day04GenerationChecker(), content);
        require(result.flag("format") == format, "format: " + content);
    }

    private static CheckResult check(ru.pyshkov.llm.core.check.Checker checker, String content) {
        ChatRequest request = ChatRequest.builder()
                .model("test").message(Message.user("q")).build();
        ChatResponse response = new ChatResponse(
                "test", content, null, "stop", 1, 1, 1, 0, 0, false);
        return checker.check(List.of(new StepResult("answer", request, response, null, 1)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
