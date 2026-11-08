package ru.pyshkov.llm.core.experiment;

import ru.pyshkov.llm.core.check.CheckResult;
import ru.pyshkov.llm.core.client.ChatModel;
import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;
import ru.pyshkov.llm.core.client.Message;
import ru.pyshkov.llm.core.client.NonRetryableException;
import ru.pyshkov.llm.core.config.LabConfig;
import ru.pyshkov.llm.core.workspace.DayWorkspace;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Исполнитель плана прогонов: прогрев, ретраи, журнал, прогресс. */
public final class Runner {

    private final ChatModel model;
    private final LabConfig config;
    private final DayWorkspace workspace;

    public Runner(ChatModel model, LabConfig config, DayWorkspace workspace) {
        this.model = model;
        this.config = config;
        this.workspace = workspace;
    }

    public List<RunResult> run(String experimentId, String title, List<RunPlan> plans)
            throws IOException, InterruptedException {

        if (plans.isEmpty()) {
            throw new IOException("План эксперимента пуст");
        }

        ChatRequest firstRequest = plans.get(0).steps().get(0).request(List.of());
        String modelName = firstRequest.model();
        String startedAt = OffsetDateTime.now().toString();
        List<RunResult> results = new ArrayList<>();

        System.out.println("План: " + plans.size() + " прогонов");
        writeLog(experimentId, title, startedAt, modelName, plans.size(), results);

        for (int i = 0; i < plans.size(); i++) {
            RunPlan plan = plans.get(i);
            List<StepResult> stepResults = new ArrayList<>();
            boolean success = true;
            String error = null;
            long wallClockMs = 0;

            for (Step step : plan.steps()) {
                StepResult stepResult = execute(step, stepResults);
                stepResults.add(stepResult);
                wallClockMs += stepResult.wallClockMs();
                if (stepResult.error() != null) {
                    success = false;
                    error = stepResult.error();
                    break;
                }
            }

            CheckResult check = success
                    ? safeCheck(plan, stepResults)
                    : CheckResult.empty();

            RunResult result = new RunResult(
                    plan.caseId(),
                    plan.variantId(),
                    plan.repeat(),
                    plan.labels(),
                    stepResults,
                    check,
                    success,
                    error,
                    wallClockMs
            );

            results.add(result);
            writeLog(experimentId, title, startedAt, modelName, plans.size(), results);
            printProgress(i + 1, plans.size(), result);

            if (config.delayMs() > 0) {
                Thread.sleep(config.delayMs());
            }
        }

        writeLog(experimentId, title, startedAt, modelName, plans.size(), results);
        return results;
    }

    public void warmup(List<RunPlan> plans) throws IOException, InterruptedException {
        if (plans.isEmpty()) {
            throw new IOException("План эксперимента пуст");
        }
        ChatRequest firstRequest = plans.get(0).steps().get(0).request(List.of());
        System.out.println("Модель: " + firstRequest.model());
        System.out.println("Прогрев модели...");
        warmupModel(firstRequest.model(), firstRequest.numCtx());
    }

    private void warmupModel(String modelName, int numCtx) throws IOException, InterruptedException {
        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .message(Message.user("ping"))
                .temperature(0.0)
                .maxPredict(16)
                .think(false)
                .numCtx(numCtx)
                .build();
        model.chat(request);
    }

    private StepResult execute(Step step, List<StepResult> previous) throws InterruptedException {
        ChatRequest request;
        try {
            request = step.request(previous);
        } catch (RuntimeException e) {
            return new StepResult(step.label(), null, null,
                    "ошибка построения шага: " + e.getMessage(), 0);
        }

        IOException last = null;

        for (int attempt = 0; attempt <= config.retries(); attempt++) {
            long started = System.nanoTime();
            try {
                ChatResponse response = model.chat(request);
                long wallClockMs = (System.nanoTime() - started) / 1_000_000;
                if (response.content() == null || response.content().isBlank()) {
                    return new StepResult(step.label(), request, response,
                            "пустой content (" + Failures.describe(response, request) + ")",
                            wallClockMs);
                }
                return new StepResult(step.label(), request, response, null, wallClockMs);
            } catch (NonRetryableException e) {
                long wallClockMs = (System.nanoTime() - started) / 1_000_000;
                return new StepResult(step.label(), request, null, e.getMessage(), wallClockMs);
            } catch (IOException e) {
                last = e;
                if (attempt < config.retries()) {
                    long pause = 1000L * (attempt + 1);
                    System.out.println("  ретрай " + (attempt + 1) + "/" + config.retries()
                            + " через " + pause + " мс: " + e.getMessage());
                    Thread.sleep(pause);
                }
            }
        }

        return new StepResult(step.label(), request, null,
                last == null ? "неизвестная ошибка" : last.getMessage(), 0);
    }

    private CheckResult safeCheck(RunPlan plan, List<StepResult> steps) {
        try {
            CheckResult result = plan.checker().check(steps);
            return result == null ? CheckResult.empty() : result;
        } catch (RuntimeException e) {
            return new CheckResult(Map.of("check_error", false),
                    List.of("ошибка проверки: " + e.getMessage()));
        }
    }

    private void writeLog(
            String experimentId,
            String title,
            String startedAt,
            String modelName,
            int plannedRuns,
            List<RunResult> results
    ) throws IOException {
        workspace.writeRunLog(new RunLog(
                experimentId, title, startedAt, modelName, plannedRuns, List.copyOf(results)));
    }

    private static void printProgress(int done, int total, RunResult result) {
        StringBuilder line = new StringBuilder();
        line.append(String.format("[%02d/%02d] %-12s r%d ", done, total,
                result.variantId(), result.repeat()));
        if (!result.success()) {
            StepResult lastStep = result.steps().isEmpty()
                    ? null : result.steps().get(result.steps().size() - 1);
            line.append("ОШИБКА: ").append(Failures.classify(lastStep))
                    .append(" — ").append(result.error());
        } else {
            line.append("ok");
        }
        System.out.println(line);
    }
}
