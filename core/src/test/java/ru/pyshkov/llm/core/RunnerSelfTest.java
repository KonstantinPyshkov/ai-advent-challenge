package ru.pyshkov.llm.core;

import ru.pyshkov.llm.core.check.CheckResult;
import ru.pyshkov.llm.core.client.ChatModel;
import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;
import ru.pyshkov.llm.core.client.Message;
import ru.pyshkov.llm.core.client.NonRetryableException;
import ru.pyshkov.llm.core.config.LabConfig;
import ru.pyshkov.llm.core.experiment.RunPlan;
import ru.pyshkov.llm.core.experiment.RunResult;
import ru.pyshkov.llm.core.experiment.Runner;
import ru.pyshkov.llm.core.experiment.Step;
import ru.pyshkov.llm.core.workspace.DayWorkspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Проверки Runner на фейковой модели, без сети. */
public final class RunnerSelfTest {

    public static void main(String[] args) throws Exception {
        happyPath();
        emptyContentDoesNotRetry();
        nonRetryableDoesNotRetry();
        retryableIsRetried();
        System.out.println("RunnerSelfTest OK");
    }

    private static void happyPath() throws Exception {
        FakeModel model = new FakeModel(
                response("pong"), response("ok"));
        RunResult result = run(model, 0);
        check(result.success(), "happy path success");
        check("ok".equals(result.steps().getLast().response().content()), "happy path content");
        check(model.calls == 2, "happy path calls=" + model.calls);
        check(result.steps().size() == 1, "happy path steps");
    }

    private static void emptyContentDoesNotRetry() throws Exception {
        FakeModel model = new FakeModel(
                response("pong"), response(""));
        RunResult result = run(model, 2);
        check(!result.success(), "empty content must fail");
        check(result.error().contains("пустой content"), "empty error: " + result.error());
        check(model.calls == 2, "empty content calls=" + model.calls);
    }

    private static void nonRetryableDoesNotRetry() throws Exception {
        FakeModel model = new FakeModel(
                response("pong"), new NonRetryableException("HTTP 401"));
        RunResult result = run(model, 2);
        check(!result.success(), "non-retryable must fail");
        check(model.calls == 2, "non-retryable calls=" + model.calls);
    }

    private static void retryableIsRetried() throws Exception {
        FakeModel model = new FakeModel(
                response("pong"), new IOException("temporary"), response("ok"));
        RunResult result = run(model, 1);
        check(result.success(), "retryable must succeed");
        check(model.calls == 3, "retryable calls=" + model.calls);
    }

    private static RunResult run(FakeModel model, int retries) throws Exception {
        Path dir = Files.createTempDirectory("runner-self-test");
        DayWorkspace workspace = new DayWorkspace(dir);
        workspace.clean();

        LabConfig config = new LabConfig(
                "ollama", "http://localhost", "test", "TEST_KEY",
                30, 1, retries, 0);
        ChatRequest request = ChatRequest.builder()
                .model("test")
                .message(Message.user("q"))
                .build();
        RunPlan plan = new RunPlan(
                "case", "variant", 1,
                List.of(Step.fixed("answer", request)),
                Map.of(),
                steps -> new CheckResult(
                        Map.of("correct", "ok".equals(steps.getLast().response().content())),
                        List.of()));
        Runner runner = new Runner(model, config, workspace);
        List<RunPlan> plans = List.of(plan);
        runner.warmup(plans);
        return runner.run("test", "Test", plans).getFirst();
    }

    private static ChatResponse response(String content) {
        return new ChatResponse("test", content, null, "stop", 1, 1, 1, 0, 0, false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeModel implements ChatModel {
        private final List<Object> answers = new ArrayList<>();
        private int calls;

        private FakeModel(Object... answers) {
            this.answers.addAll(List.of(answers));
        }

        @Override
        public ChatResponse chat(ChatRequest request) throws IOException, InterruptedException {
            calls++;
            if (answers.isEmpty()) {
                throw new AssertionError("нет ответа для вызова " + calls);
            }
            Object answer = answers.removeFirst();
            if (answer instanceof IOException error) {
                throw error;
            }
            return (ChatResponse) answer;
        }
    }
}
