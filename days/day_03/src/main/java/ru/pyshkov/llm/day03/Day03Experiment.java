package ru.pyshkov.llm.day03;

import com.fasterxml.jackson.databind.JsonNode;
import ru.pyshkov.llm.core.Json;
import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;
import ru.pyshkov.llm.core.client.Message;
import ru.pyshkov.llm.core.config.LabConfig;
import ru.pyshkov.llm.core.experiment.Experiment;
import ru.pyshkov.llm.core.experiment.Failures;
import ru.pyshkov.llm.core.experiment.RunPlan;
import ru.pyshkov.llm.core.experiment.RunResult;
import ru.pyshkov.llm.core.experiment.Step;
import ru.pyshkov.llm.core.experiment.StepResult;
import ru.pyshkov.llm.core.report.Conclusion;
import ru.pyshkov.llm.core.report.Markdown;
import ru.pyshkov.llm.core.report.Report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** День 3: четыре способа рассуждения при двух режимах think. */
public final class Day03Experiment implements Experiment {

    private static final String ID = "day03";
    private static final String TITLE = "День 3. Разные способы рассуждения";
    private static final List<String> STRATEGIES = List.of(
            "direct", "step_by_step", "prompt_then_solve", "panel");
    private static final List<String> REASONING = List.of("off", "on");

    private final int repeats;
    private final String model;
    private final String modelLabel;
    private final double temperature;
    private final int maxPredict;
    private final int promptBuilderMaxPredict;
    private final int numCtx;
    private final String system;
    private final String answer;
    private final String direct;
    private final String stepByStep;
    private final String promptBuilder;
    private final String solver;
    private final String panel;
    private final List<Day03Task> tasks;

    public Day03Experiment(Path dayDir, LabConfig lab, int repeats, String model) throws IOException {
        if (repeats < 1) {
            throw new IllegalArgumentException("Количество повторов должно быть больше нуля");
        }
        this.repeats = repeats;
        this.model = model;
        this.system = read(dayDir.resolve("prompts/system.md"));
        this.answer = read(dayDir.resolve("prompts/answer.md"));
        this.direct = read(dayDir.resolve("prompts/direct.md"));
        this.stepByStep = read(dayDir.resolve("prompts/step_by_step.md"));
        this.promptBuilder = read(dayDir.resolve("prompts/prompt_builder.md"));
        this.solver = read(dayDir.resolve("prompts/solver.md"));
        this.panel = read(dayDir.resolve("prompts/panel.md"));
        this.tasks = loadTasks(dayDir.resolve("data/tasks.json"));
        ExperimentConfig settings = ExperimentConfig.load(
                dayDir.resolve("config/experiment.properties"));
        this.modelLabel = settings.modelLabel();
        this.temperature = settings.temperature();
        this.maxPredict = settings.maxPredict();
        this.promptBuilderMaxPredict = settings.promptBuilderMaxPredict();
        this.numCtx = settings.numCtx();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<RunPlan> plans() {
        List<RunPlan> plans = new ArrayList<>();
        for (Day03Task task : tasks) {
            for (String strategy : STRATEGIES) {
                for (String reasoning : REASONING) {
                    boolean think = "on".equals(reasoning);
                    String variant = variant(strategy, reasoning);
                    for (int repeat = 1; repeat <= repeats; repeat++) {
                        plans.add(new RunPlan(
                                task.id(),
                                variant,
                                repeat,
                                steps(task, strategy, think),
                                labels(strategy, reasoning, think),
                                new Day03Checker(task)
                        ));
                    }
                }
            }
        }
        return plans;
    }

    @Override
    public Report report(List<RunResult> results) {
        List<String> headers = List.of(
                "Способ", "Рассуждение", "Верных", "avg токены", "avg время",
                "формат", "сбои");
        List<List<String>> summary = new ArrayList<>();
        for (String strategy : STRATEGIES) {
            for (String reasoning : REASONING) {
                String variant = variant(strategy, reasoning);
                summary.add(List.of(
                        strategy,
                        reasoning,
                        rate(results, variant, "correct"),
                        avgTokens(results, variant),
                        avgRequestMs(results, variant),
                        rate(results, variant, "answer"),
                        String.valueOf(failures(results, variant))
                ));
            }
        }

        List<String> matrixHeaders = new ArrayList<>();
        matrixHeaders.add("Способ");
        matrixHeaders.add("Рассуждение");
        tasks.forEach(task -> matrixHeaders.add(task.id()));
        List<List<String>> matrix = new ArrayList<>();
        for (String strategy : STRATEGIES) {
            for (String reasoning : REASONING) {
                List<String> row = new ArrayList<>();
                row.add(strategy);
                row.add(reasoning);
                for (Day03Task task : tasks) {
                    RunResult result = find(results, variant(strategy, reasoning), task.id(), 1);
                    row.add(result == null ? "—" : symbol(result));
                }
                matrix.add(row);
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# ").append(TITLE).append("\n\n");
        md.append("**Модель:** `").append(modelLabel).append("` (`")
                .append(model).append("`) · **задач:** ")
                .append(tasks.size()).append(" · **повторов:** ").append(repeats)
                .append(" · **прогонов:** ").append(results.size()).append("\n\n");

        md.append("## Главное\n\n");
        for (String strategy : STRATEGIES) {
            md.append("- `").append(strategy).append("`: off ")
                    .append(rate(results, variant(strategy, "off"), "correct"))
                    .append(", on ")
                    .append(rate(results, variant(strategy, "on"), "correct"))
                    .append(".\n");
        }

        md.append("\n## Результаты\n\n");
        md.append(Markdown.table(headers, summary));

        md.append("\n## По задачам\n\n");
        md.append("✅ — верно; ❌ — ответ неверный; ⚠️ — нет маркера ОТВЕТ; ")
                .append("💥 — ошибка выполнения.\n\n");
        md.append(Markdown.table(matrixHeaders, matrix));

        md.append("\n## Сбои\n\n");
        List<RunResult> failed = results.stream().filter(r -> !r.success()).toList();
        if (failed.isEmpty()) {
            md.append("Технических сбоев нет.\n");
        } else {
            md.append("Технические сбои не считаются ошибкой способа, но входят ")
                    .append("в знаменатель таблицы.\n\n");
            List<List<String>> failureRows = new ArrayList<>();
            for (RunResult result : failed) {
                StepResult last = lastStep(result);
                ChatResponse response = last == null ? null : last.response();
                ChatRequest request = last == null ? null : last.request();
                failureRows.add(List.of(
                        result.caseId(),
                        result.variantId(),
                        last == null ? "—" : last.label(),
                        Failures.classify(last),
                        response == null ? "—" : value(response.doneReason()),
                        response == null ? "—" : String.valueOf(response.promptTokens()),
                        response == null ? "—" : String.valueOf(response.completionTokens()),
                        request == null ? "—" : String.valueOf(request.numCtx()),
                        request == null ? "—" : String.valueOf(request.maxPredict())
                ));
            }
            md.append(Markdown.table(
                    List.of("Задача", "Способ", "Шаг", "Тип", "done",
                            "prompt", "completion", "num_ctx", "max_predict"),
                    failureRows));
            md.append("\n");
            for (RunResult result : failed) {
                StepResult last = lastStep(result);
                md.append("- `").append(result.variantId()).append("` / `")
                        .append(result.caseId()).append("`: ")
                        .append(Failures.classify(last)).append(".\n");
            }
        }

        md.append("\n## Примеры\n\n");
        for (String strategy : STRATEGIES) {
            RunResult result = first(results, variant(strategy, "off"));
            if (result == null) {
                continue;
            }
            md.append("### ").append(strategy).append(", think=off\n\n");
            ChatResponse response = lastResponse(result);
            if (response == null) {
                md.append("Ошибка: ").append(result.error()).append("\n\n");
                continue;
            }
            md.append(Markdown.code("text", shortContent(response.content())));
            if (!result.check().errors().isEmpty()) {
                md.append("\nПроверки: ")
                        .append(shortErrors(result.check().errors()))
                        .append("\n");
            }
            md.append("\n");
        }

        md.append("## Ограничения\n\n");
        md.append("- `panel` — один вызов с ролями, а не три независимых агента.\n");
        md.append("- `prompt_then_solve` — два шага; токены и время суммируются.\n");
        md.append("- Модель не имеет доступа в интернет.\n");
        md.append("- Полный журнал: [logs/runs.json](../logs/runs.json).\n");

        return new Report(md.toString(), csv(results), console(results));
    }

    @Override
    public Conclusion conclusion(List<RunResult> results) {
        return new Conclusion(facts(results), conclusionText(results));
    }

    private String facts(List<RunResult> results) {
        List<String> headers = List.of(
                "Способ", "Рассуждение", "Верных", "avg токены", "avg время",
                "формат", "сбои");
        List<List<String>> rows = new ArrayList<>();
        for (String strategy : STRATEGIES) {
            for (String reasoning : REASONING) {
                String variant = variant(strategy, reasoning);
                rows.add(List.of(
                        strategy,
                        reasoning,
                        rate(results, variant, "correct"),
                        avgTokens(results, variant),
                        avgRequestMs(results, variant),
                        rate(results, variant, "answer"),
                        String.valueOf(failures(results, variant))
                ));
            }
        }
        return "# Факты\n\n" + Markdown.table(headers, rows);
    }

    private String conclusionText(List<RunResult> results) {
        int total = actualTotal(results);
        int directOff = count(results, variant("direct", "off"), "correct");
        int directOn = count(results, variant("direct", "on"), "correct");
        int stepOff = count(results, variant("step_by_step", "off"), "correct");
        int stepOn = count(results, variant("step_by_step", "on"), "correct");
        int promptOff = count(results, variant("prompt_then_solve", "off"), "correct");
        int promptOn = count(results, variant("prompt_then_solve", "on"), "correct");
        int panelOff = count(results, variant("panel", "off"), "correct");
        int panelOn = count(results, variant("panel", "on"), "correct");

        StringBuilder md = new StringBuilder();
        md.append("# Вывод\n\n");
        md.append("## Результаты\n\n");
        md.append("- `direct`: off ").append(directOff).append("/").append(total)
                .append(", on ").append(directOn).append("/").append(total).append(".\n");
        md.append("- `step_by_step`: off ").append(stepOff).append("/").append(total)
                .append(", on ").append(stepOn).append("/").append(total).append(".\n");
        md.append("- `prompt_then_solve`: off ").append(promptOff).append("/").append(total)
                .append(", on ").append(promptOn).append("/").append(total).append(".\n");
        md.append("- `panel`: off ").append(panelOff).append("/").append(total)
                .append(", on ").append(panelOn).append("/").append(total).append(".\n\n");

        md.append("## Сбои\n\n");
        long failures = results.stream().filter(r -> !r.success()).count();
        if (failures == 0) {
            md.append("Технических сбоев нет.\n");
        } else {
            md.append("Технических сбоев: ").append(failures)
                    .append(". Они входят в знаменатель, но не считаются ошибкой способа.\n");
        }
        for (RunResult result : results.stream().filter(r -> !r.success()).toList()) {
            StepResult last = lastStep(result);
            md.append("- `").append(result.variantId()).append("` / `")
                    .append(result.caseId()).append("`: ")
                    .append(Failures.classify(last)).append(".\n");
        }
        md.append("\n");

        md.append("## Вывод\n\n");
        md.append("Прямой ответ — базовая линия.\n\n");
        md.append("- ").append(effectLine("step_by_step",
                stepOff - directOff, stepOn - directOn)).append("\n");
        md.append("- ").append(effectLine("prompt_then_solve",
                promptOff - directOff, promptOn - directOn)).append("\n");
        md.append("- ").append(effectLine("panel",
                panelOff - directOff, panelOn - directOn)).append("\n\n");

        List<String> lost = lostEffects(
                stepOff - directOff, stepOn - directOn,
                promptOff - directOff, promptOn - directOn,
                panelOff - directOff, panelOn - directOn);
        List<String> noEffect = new ArrayList<>();
        List<String> onlyOn = new ArrayList<>();
        if (stepOff - directOff <= 0 && stepOn - directOn <= 0) noEffect.add("step_by_step");
        if (promptOff - directOff <= 0 && promptOn - directOn <= 0) noEffect.add("prompt_then_solve");
        if (panelOff - directOff <= 0 && panelOn - directOn <= 0) noEffect.add("panel");
        if (stepOff - directOff <= 0 && stepOn - directOn > 0) onlyOn.add("step_by_step");
        if (promptOff - directOff <= 0 && promptOn - directOn > 0) onlyOn.add("prompt_then_solve");
        if (panelOff - directOff <= 0 && panelOn - directOn > 0) onlyOn.add("panel");

        if (lost.isEmpty()) {
            md.append("Приёмов, которые давали эффект при выключенном рассуждении ")
                    .append("и перестали давать его при включённом, в этом прогоне нет.");
        } else {
            md.append("При включённом рассуждении перестали давать эффект: ")
                    .append(String.join(", ", lost)).append(".");
        }
        md.append("\n");
        if (!noEffect.isEmpty()) {
            md.append("Не дают выигрыша ни при одном режиме: ")
                    .append(String.join(", ", noEffect)).append(".\n");
        }
        if (!onlyOn.isEmpty()) {
            md.append("Эффект только при включённом рассуждении: ")
                    .append(String.join(", ", onlyOn)).append(".\n");
        }

        md.append("\n| Способ | off: токены / время | on: токены / время |\n");
        md.append("|---|---|---|\n");
        for (String strategy : STRATEGIES) {
            md.append("| ").append(strategy).append(" | ")
                    .append(avgTokens(results, variant(strategy, "off"))).append(" / ")
                    .append(avgRequestMs(results, variant(strategy, "off"))).append(" | ")
                    .append(avgTokens(results, variant(strategy, "on"))).append(" / ")
                    .append(avgRequestMs(results, variant(strategy, "on"))).append(" |\n");
        }
        md.append("\nВывод делается по фактическим числам; при малом числе задач ")
                .append("разница в один ответ может быть шумом.\n");
        return md.toString();
    }

    private String console(List<RunResult> results) {
        StringBuilder out = new StringBuilder();
        out.append("=== Итог ===\n");
        out.append(String.format("%-18s %10s %8s %12s %14s %7s%n",
                "Способ", "Рассужд.", "Верных", "avg токены", "avg время", "сбои"));
        for (String strategy : STRATEGIES) {
            for (String reasoning : REASONING) {
                String variant = variant(strategy, reasoning);
                out.append(String.format("%-18s %10s %8s %12s %14s %7s%n",
                        strategy,
                        reasoning,
                        rate(results, variant, "correct"),
                        avgTokens(results, variant),
                        avgRequestMs(results, variant),
                        failures(results, variant)));
            }
        }
        out.append("\n=== По задачам ===\n");
        out.append(String.format("%-18s %10s %s%n", "Способ", "Рассужд.",
                String.join(" ", tasks.stream().map(Day03Task::id).toList())));
        for (String strategy : STRATEGIES) {
            for (String reasoning : REASONING) {
                StringBuilder row = new StringBuilder();
                for (Day03Task task : tasks) {
                    RunResult result = find(results, variant(strategy, reasoning), task.id(), 1);
                    row.append(' ').append(result == null ? "—" : symbol(result));
                }
                out.append(String.format("%-18s %10s %s%n", strategy, reasoning, row));
            }
        }
        out.append("\nВывод:\n");
        out.append("- прямой ответ: off ").append(count(results, variant("direct", "off"), "correct"))
                .append("/").append(actualTotal(results)).append(", on ")
                .append(count(results, variant("direct", "on"), "correct"))
                .append("/").append(actualTotal(results)).append(".\n");
        out.append("- step_by_step: off ")
                .append(count(results, variant("step_by_step", "off"), "correct"))
                .append("/").append(actualTotal(results)).append(", on ")
                .append(count(results, variant("step_by_step", "on"), "correct"))
                .append("/").append(actualTotal(results)).append(".\n");
        out.append("- prompt_then_solve: off ")
                .append(count(results, variant("prompt_then_solve", "off"), "correct"))
                .append("/").append(actualTotal(results)).append(", on ")
                .append(count(results, variant("prompt_then_solve", "on"), "correct"))
                .append("/").append(actualTotal(results)).append(".\n");
        out.append("- panel: off ")
                .append(count(results, variant("panel", "off"), "correct"))
                .append("/").append(actualTotal(results)).append(", on ")
                .append(count(results, variant("panel", "on"), "correct"))
                .append("/").append(actualTotal(results)).append(".\n");
        int dOff = count(results, variant("direct", "off"), "correct");
        int dOn = count(results, variant("direct", "on"), "correct");
        int sOff = count(results, variant("step_by_step", "off"), "correct");
        int sOn = count(results, variant("step_by_step", "on"), "correct");
        int pOff = count(results, variant("prompt_then_solve", "off"), "correct");
        int pOn = count(results, variant("prompt_then_solve", "on"), "correct");
        int aOff = count(results, variant("panel", "off"), "correct");
        int aOn = count(results, variant("panel", "on"), "correct");
        List<RunResult> failed = results.stream().filter(r -> !r.success()).toList();
        out.append("- сбои: ").append(failed.size()).append("\n");
        for (RunResult result : failed) {
            StepResult last = lastStep(result);
            out.append("  · ").append(result.variantId()).append(" / ")
                    .append(result.caseId()).append(": ")
                    .append(Failures.classify(last)).append("\n");
        }
        out.append("- эффект: step_by_step ").append(gain(sOff - dOff))
                .append(" off / ").append(gain(sOn - dOn)).append(" on; ")
                .append("prompt_then_solve ").append(gain(pOff - dOff))
                .append(" off / ").append(gain(pOn - dOn)).append(" on; ")
                .append("panel ").append(gain(aOff - dOff))
                .append(" off / ").append(gain(aOn - dOn)).append(" on.\n");
        List<String> lost = lostEffects(sOff - dOff, sOn - dOn,
                pOff - dOff, pOn - dOn, aOff - dOff, aOn - dOn);
        out.append("- перестали давать эффект при on: ")
                .append(lost.isEmpty() ? "нет" : String.join(", ", lost))
                .append(".\n");
        List<String> noEffect = new ArrayList<>();
        if (sOff - dOff <= 0 && sOn - dOn <= 0) noEffect.add("step_by_step");
        if (pOff - dOff <= 0 && pOn - dOn <= 0) noEffect.add("prompt_then_solve");
        if (aOff - dOff <= 0 && aOn - dOn <= 0) noEffect.add("panel");
        out.append("- без выигрыша: ")
                .append(noEffect.isEmpty() ? "нет" : String.join(", ", noEffect))
                .append(".\n");
        return out.toString();
    }

    private List<Step> steps(Day03Task task, String strategy, boolean think) {
        return switch (strategy) {
            case "direct" -> List.of(Step.fixed("answer",
                    request(user(task, direct), maxPredict, think)));
            case "step_by_step" -> List.of(Step.fixed("answer",
                    request(user(task, stepByStep), maxPredict, think)));
            case "panel" -> List.of(Step.fixed("answer",
                    request(user(task, panel), maxPredict, think)));
            case "prompt_then_solve" -> List.of(
                    Step.fixed("build-prompt", request(builderMessages(task),
                            promptBuilderMaxPredict, think)),
                    Step.of("solve", previous -> request(solverMessages(task, previous),
                            maxPredict, think))
            );
            default -> throw new IllegalArgumentException("Неизвестный способ: " + strategy);
        };
    }

    private List<Message> user(Day03Task task, String instruction) {
        StringBuilder body = new StringBuilder("Задача:\n").append(task.prompt());
        if (instruction != null && !instruction.isBlank()) {
            body.append("\n\n").append(instruction.strip());
        }
        body.append("\n\n").append(answer);
        return List.of(Message.system(system), Message.user(body.toString()));
    }

    private List<Message> builderMessages(Day03Task task) {
        return List.of(
                Message.system(system),
                Message.user(promptBuilder + "\n\nЗадача:\n" + task.prompt())
        );
    }

    private List<Message> solverMessages(Day03Task task, List<StepResult> previous) {
        String generated = "";
        if (!previous.isEmpty() && previous.get(0).response() != null) {
            generated = previous.get(0).response().content();
        }
        if (generated == null || generated.isBlank()) {
            throw new IllegalStateException("модель не вернула промпт");
        }
        return List.of(
                Message.system(system),
                Message.user("Задача:\n" + task.prompt()
                        + "\n\nПромпт для решения:\n" + generated
                        + "\n\n" + solver + "\n\n" + answer)
        );
    }

    private ChatRequest request(List<Message> messages, int limit, boolean think) {
        return ChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(temperature)
                .maxPredict(limit)
                .think(think)
                .numCtx(numCtx)
                .build();
    }

    private Map<String, String> labels(String strategy, String reasoning, boolean think) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("strategy", strategy);
        labels.put("reasoning", reasoning);
        labels.put("think", String.valueOf(think));
        labels.put("temperature", String.valueOf(temperature));
        labels.put("maxPredict", String.valueOf(maxPredict));
        return labels;
    }

    private static String variant(String strategy, String reasoning) {
        return strategy + "/" + reasoning;
    }

    private int actualTotal(List<RunResult> results) {
        int total = total(results, variant("direct", "off"));
        return total == 0 ? tasks.size() * repeats : total;
    }

    private static int total(List<RunResult> results, String variant) {
        return (int) results.stream().filter(r -> r.variantId().equals(variant)).count();
    }

    private static int count(List<RunResult> results, String variant, String flag) {
        return (int) results.stream()
                .filter(r -> r.variantId().equals(variant))
                .filter(r -> r.check().flag(flag))
                .count();
    }

    private static String rate(List<RunResult> results, String variant, String flag) {
        int total = total(results, variant);
        return total == 0 ? "—" : count(results, variant, flag) + "/" + total;
    }

    private static String avgTokens(List<RunResult> results, String variant) {
        List<RunResult> list = results.stream()
                .filter(r -> r.variantId().equals(variant))
                .filter(r -> sumTokens(r) > 0)
                .toList();
        if (list.isEmpty()) {
            return "—";
        }
        double avg = list.stream().mapToInt(Day03Experiment::sumTokens).average().orElse(0);
        return String.format("%.0f", avg);
    }

    private static String avgRequestMs(List<RunResult> results, String variant) {
        List<RunResult> list = results.stream()
                .filter(r -> r.variantId().equals(variant))
                .filter(r -> sumRequestMs(r) > 0)
                .toList();
        if (list.isEmpty()) {
            return "—";
        }
        double avg = list.stream().mapToLong(Day03Experiment::sumRequestMs).average().orElse(0);
        return String.format("%.0f мс", avg);
    }

    private static int sumTokens(RunResult result) {
        return result.steps().stream()
                .map(StepResult::response)
                .filter(r -> r != null)
                .mapToInt(ChatResponse::totalTokens)
                .sum();
    }

    private static long sumRequestMs(RunResult result) {
        return result.steps().stream()
                .map(StepResult::response)
                .filter(r -> r != null)
                .mapToLong(ChatResponse::requestMs)
                .sum();
    }

    private static String symbol(RunResult result) {
        if (!result.success()) {
            return "💥";
        }
        if (result.check().flag("correct")) {
            return "✅";
        }
        if (result.check().flag("answer")) {
            return "❌";
        }
        return "⚠️";
    }

    private static RunResult find(List<RunResult> results, String variant, String task, int repeat) {
        return results.stream()
                .filter(r -> r.variantId().equals(variant)
                        && r.caseId().equals(task)
                        && r.repeat() == repeat)
                .findFirst()
                .orElse(null);
    }

    private static RunResult first(List<RunResult> results, String variant) {
        return results.stream().filter(r -> r.variantId().equals(variant)).findFirst().orElse(null);
    }

    private static int failures(List<RunResult> results, String variant) {
        return (int) results.stream()
                .filter(r -> r.variantId().equals(variant))
                .filter(r -> !r.success())
                .count();
    }

    private static StepResult lastStep(RunResult result) {
        return result.steps().isEmpty() ? null : result.steps().get(result.steps().size() - 1);
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static ChatResponse lastResponse(RunResult result) {
        if (result.steps().isEmpty()) {
            return null;
        }
        return result.steps().get(result.steps().size() - 1).response();
    }

    private static String shortContent(String content) {
        String value = content.strip().replaceAll("(?m)[ \\t]+$", "");
        if (value.length() > 300) {
            value = value.substring(0, 300) + "\n… (сокращено)";
        }
        return value.replace("```", "'''");
    }

    private static String shortErrors(List<String> errors) {
        List<String> shown = errors.stream()
                .limit(2)
                .map(error -> error.length() > 140 ? error.substring(0, 140) + "…" : error)
                .toList();
        String text = String.join("; ", shown);
        return errors.size() > 2 ? text + " …" : text;
    }

    private String csv(List<RunResult> results) {
        StringBuilder csv = new StringBuilder();
        csv.append("strategy,reasoning,correct,total,format_ok,failures,avg_tokens,avg_request_ms\n");
        for (String strategy : STRATEGIES) {
            for (String reasoning : REASONING) {
                String variant = variant(strategy, reasoning);
                csv.append(strategy).append(',')
                        .append(reasoning).append(',')
                        .append(count(results, variant, "correct")).append(',')
                        .append(total(results, variant)).append(',')
                        .append(count(results, variant, "answer")).append(',')
                        .append(failures(results, variant)).append(',')
                        .append(avgTokens(results, variant)).append(',')
                        .append(avgRequestMs(results, variant).replace(" мс", ""))
                        .append('\n');
            }
        }
        return csv.toString();
    }

    private static String gain(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static String effectLine(String name, int gainOff, int gainOn) {
        if (gainOff > 0 && gainOn <= 0) {
            return name + ": " + gain(gainOff) + " при off, " + gain(gainOn)
                    + " при on — эффект пропадает при рассуждении.";
        }
        if (gainOff <= 0 && gainOn > 0) {
            return name + ": " + gain(gainOff) + " при off, " + gain(gainOn)
                    + " при on — эффект появляется только при рассуждении.";
        }
        if (gainOff <= 0) {
            return name + ": " + gain(gainOff) + " при off, " + gain(gainOn)
                    + " при on — выигрыша нет.";
        }
        return name + ": " + gain(gainOff) + " при off, " + gain(gainOn)
                + " при on — эффект сохраняется.";
    }

    private static List<String> lostEffects(
            int stepOff, int stepOn, int promptOff, int promptOn, int panelOff, int panelOn) {
        List<String> lost = new ArrayList<>();
        if (stepOff > 0 && stepOn <= 0) {
            lost.add("step_by_step");
        }
        if (promptOff > 0 && promptOn <= 0) {
            lost.add("prompt_then_solve");
        }
        if (panelOff > 0 && panelOn <= 0) {
            lost.add("panel");
        }
        return lost;
    }

    private static String read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Не найден файл: " + file.toAbsolutePath());
        }
        return Files.readString(file, StandardCharsets.UTF_8).strip();
    }

    private static List<Day03Task> loadTasks(Path file) throws IOException {
        JsonNode root = Json.read(file);
        if (!root.isArray()) {
            throw new IOException("tasks.json должен содержать массив");
        }
        List<Day03Task> tasks = new ArrayList<>();
        for (JsonNode node : root) {
            List<String> accepted = new ArrayList<>();
            for (JsonNode item : node.path("accepted")) {
                accepted.add(item.asText());
            }
            tasks.add(new Day03Task(
                    node.path("id").asText(),
                    node.path("category").asText(),
                    node.path("prompt").asText(),
                    node.path("expected").asText(),
                    node.path("check").asText("normalized"),
                    accepted
            ));
        }
        return List.copyOf(tasks);
    }

    private record ExperimentConfig(
            String modelLabel,
            double temperature,
            int maxPredict,
            int promptBuilderMaxPredict,
            int numCtx
    ) {
        static ExperimentConfig load(Path file) throws IOException {
            Properties props = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            return new ExperimentConfig(
                    props.getProperty("llm.model-label",
                            props.getProperty("llm.model", "deepseek-flash")).strip(),
                    Double.parseDouble(props.getProperty("experiment.temperature", "0.0").strip()),
                    Integer.parseInt(props.getProperty("experiment.max-predict", "4096").strip()),
                    Integer.parseInt(props.getProperty("experiment.prompt-builder-max-predict", "512").strip()),
                    Integer.parseInt(props.getProperty("experiment.num-ctx", "16384").strip())
            );
        }
    }
}
