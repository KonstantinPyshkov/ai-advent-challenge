package ru.pyshkov.llm.core;

import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;
import ru.pyshkov.llm.core.client.Message;
import ru.pyshkov.llm.core.experiment.Failures;
import ru.pyshkov.llm.core.experiment.StepResult;

/** Минимальные проверки без внешних тестовых зависимостей. */
public final class FailuresSelfTest {

    public static void main(String[] args) {
        ChatRequest request = ChatRequest.builder()
                .model("test")
                .message(Message.user("ping"))
                .maxPredict(100)
                .numCtx(200)
                .build();

        check("output_limit", Failures.classify(step(request,
                new ChatResponse("test", "", null, "length", 20, 100, 1, 0, 0, false))));
        check("empty_content", Failures.classify(step(request,
                new ChatResponse("test", "", null, "stop", 20, 0, 1, 0, 0, false))));
        check("truncated", Failures.classify(step(request,
                new ChatResponse("test", "часть", null, "length", 20, 50, 1, 0, 0, false))));
        check("none", Failures.classify(step(request,
                new ChatResponse("test", "ответ", null, "stop", 20, 5, 1, 0, 0, false))));
        System.out.println("FailuresSelfTest OK");
    }

    private static StepResult step(ChatRequest request, ChatResponse response) {
        return new StepResult("answer", request, response, null, 1);
    }

    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }
}
