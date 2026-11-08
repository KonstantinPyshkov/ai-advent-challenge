package ru.pyshkov.llm.day05;

import com.fasterxml.jackson.databind.JsonNode;
import ru.pyshkov.llm.core.Json;
import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;
import ru.pyshkov.llm.core.client.Message;
import ru.pyshkov.llm.core.config.LabConfig;
import ru.pyshkov.llm.core.experiment.Experiment;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** День 5: сравнение моделей разных размеров и провайдеров. */
public final class Day05Experiment implements Experiment {

    private static final String ID = "day05";
    private static final String TITLE = "День 5. Версии моделей";

    private final int repeats;
    private final double temperature;
    private final int maxPredict;
    private final int numCtx;
    private final double usdRub;
    private final String system;
    private final String answer;
    private final List<Day05Task> tasks;
    private final List<Day05Model> models;

    public Day05Experiment(Path dayDir, LabConfig lab, int repeats, String model) throws IOException {
        if (repeats < 1) {
            throw new IllegalArgumentException("Количество повторов должно быть больше нуля");
        }
        this.repeats = repeats;
        this.system = read(dayDir.resolve("prompts/system.md"));
        this.answer = read(dayDir.resolve("prompts/answer.md"));
        this.tasks = loadTasks(dayDir.resolve("data/tasks.json"));
        this.models = loadModels(dayDir.resolve("data/models.json"));
        ExperimentConfig settings = ExperimentConfig.load(
                dayDir.resolve("config/experiment.properties"));
        this.temperature = settings.temperature();
        this.maxPredict = settings.maxPredict();
        this.numCtx = settings.numCtx();
        this.usdRub = settings.usdRub();
    }

    public List<Day05Model> models() {
        return models;
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
        for (Day05Model model : models) {
            for (Day05Task task : tasks) {
                for (int repeat = 1; repeat <= repeats; repeat++) {
                    ChatRequest request = ChatRequest.builder()
                            .model(model.id())
                            .message(Message.system(system))
                            .message(Message.user(userPrompt(task)))
                            .temperature(temperature)
                            .maxPredict(maxPredict)
                            .think(false)
                            .numCtx(numCtx)
                            .build();
                    plans.add(new RunPlan(
                            task.id(),
                            model.id(),
                            repeat,
                            List.of(Step.fixed("answer", request)),
                            labels(model, task),
                            new Day05Checker(task)
                    ));
                }
            }
        }
        return plans;
    }

    @Override
    public Report report(List<RunResult> results) {
        List<String> headers = List.of(
                "Модель", "Верных", "Формат", "Время", "Токены", "Стоимость", "Ресурс");
        List<List<String>> rows = new ArrayList<>();
        for (Day05Model model : models) {
            rows.add(row(results, model));
        }

        StringBuilder md = new StringBuilder();
        md.append("# ").append(TITLE).append("\n\n");
        md.append("**Задач:** ").append(tasks.size()).append(" · **моделей:** ")
                .append(models.size()).append(" · **повторов:** ").append(repeats)
                .append(" · **прогонов:** ").append(results.size()).append("\n\n");
        md.append("## Результаты\n\n");
        md.append(Markdown.table(headers, rows));
        md.append("\nСтоимость — за все прогоны модели; для локальных моделей `—`. ");
        md.append("DeepSeek — cache miss, off-peak / peak. ");
        md.append("Курс: 1 USD = ").append(String.format(Locale.ROOT, "%.2f", usdRub))
                .append(" ₽.\n");
        md.append("Токены p/c/t — prompt / completion / total.\n");

        List<String> matrixHeaders = new ArrayList<>();
        matrixHeaders.add("Модель");
        tasks.forEach(task -> matrixHeaders.add(task.id()));
        List<List<String>> matrix = new ArrayList<>();
        for (Day05Model model : models) {
            List<String> row = new ArrayList<>();
            row.add(model.label());
            for (Day05Task task : tasks) {
                row.add(cell(results, model, task));
            }
            matrix.add(row);
        }
        md.append("\n## По задачам\n\n");
        md.append("✅ — верно; ❌ — неверно; ⚠️ — нет маркера; 💥 — ошибка.\n\n");
        md.append(Markdown.table(matrixHeaders, matrix));

        md.append("\n## Примеры\n\n");
        for (Day05Model model : models) {
            RunResult result = first(results, model.id());
            md.append("### ").append(model.label()).append(" (`")
                    .append(model.id()).append("`)\n\n");
            ChatResponse response = result == null ? null : lastResponse(result);
            if (response == null) {
                md.append("Ошибка: ").append(result == null ? "нет результата" : result.error())
                        .append("\n\n");
                continue;
            }
            md.append(Markdown.code("text", shortContent(response.content())));
            if (!result.check().errors().isEmpty()) {
                md.append("\nПроверки: ").append(shortErrors(result.check().errors()))
                        .append("\n");
            }
            md.append("\n");
        }

        md.append("## Ограничения\n\n");
        md.append("- Один прогон на модель × задачу, без повторов.\n");
        md.append("- `temperature=0`, `think=false`, одинаковые промпты и лимиты.\n");
        md.append("- Локальные модели бесплатны; стоимость DeepSeek — по прайсу cache miss.\n");
        md.append("- Время включает только HTTP-вызов; загрузка моделей прогрета заранее.\n");
        return new Report(md.toString(), csv(results), console(results));
    }

    @Override
    public Conclusion conclusion(List<RunResult> results) {
        return new Conclusion(facts(results), conclusionText(results));
    }

    private List<String> row(List<RunResult> results, Day05Model model) {
        List<RunResult> list = forModel(results, model);
        return List.of(
                model.label() + " (`" + model.id() + "`)",
                rate(list, "correct"),
                rate(list, "answer"),
                time(list),
                tokens(list),
                cost(list, model),
                model.resource()
        );
    }

    private String facts(List<RunResult> results) {
        List<String> headers = List.of(
                "Модель", "Верных", "Формат", "Время", "Токены", "Стоимость", "Ресурс");
        List<List<String>> rows = new ArrayList<>();
        for (Day05Model model : models) {
            rows.add(row(results, model));
        }
        return "# Факты\n\n" + Markdown.table(headers, rows);
    }

    private String conclusionText(List<RunResult> results) {
        StringBuilder md = new StringBuilder();
        md.append("# Вывод\n\n## Модели\n\n");
        for (Day05Model model : models) {
            List<RunResult> list = forModel(results, model);
            md.append("- ").append(model.label()).append(": верных ")
                    .append(rate(list, "correct")).append(", время ").append(time(list))
                    .append(", токены ").append(tokens(list)).append(", стоимость ")
                    .append(cost(list, model)).append(".\n");
        }

        Day05Model small = models.getFirst();
        List<RunResult> smallResults = forModel(results, small);
        List<String> smallCorrect = new ArrayList<>();
        for (Day05Task task : tasks) {
            if (smallResults.stream().anyMatch(r -> r.caseId().equals(task.id())
                    && r.success() && r.check().flag("correct"))) {
                smallCorrect.add(task.id());
            }
        }
        md.append("\n## Маленькая модель\n\n");
        List<String> smallWrong = tasks.stream()
                .map(Day05Task::id)
                .filter(id -> !smallCorrect.contains(id))
                .toList();
        md.append(small.label()).append(" решила ").append(smallCorrect.size())
                .append(" из ").append(tasks.size()).append(" задач: ")
                .append(smallCorrect.isEmpty() ? "ни одной" : String.join(", ", smallCorrect))
                .append(".\n");
        md.append("- Недостаточно для: ")
                .append(smallWrong.isEmpty() ? "всех задач" : String.join(", ", smallWrong))
                .append(".\n");

        Day05Model flash = modelByName("DeepSeek V4.1 Flash");
        Day05Model pro = modelByName("DeepSeek V4 Pro");
        if (flash != null && pro != null) {
            double flashCost = totalCost(forModel(results, flash), flash);
            double proCost = totalCost(forModel(results, pro), pro);
            md.append("\n## Платные модели\n\n");
            md.append("- Flash: ").append(rate(forModel(results, flash), "correct"))
                    .append(", ").append(formatRub(flashCost)).append(".\n");
            md.append("- Pro: ").append(rate(forModel(results, pro), "correct"))
                    .append(", ").append(formatRub(proCost)).append(".\n");
            if (flashCost > 0) {
                md.append("- Фактическое отношение Pro / Flash по стоимости: ")
                        .append(String.format(Locale.ROOT, "%.2f", proCost / flashCost))
                        .append("×.\n");
            }
            md.append("- Прайс Pro / Flash: input 4.4×, output 3.3×.\n");
        }

        md.append("\n## Вывод\n\n");
        md.append("- Маленькая локальная модель бесплатна; для простых задач её ");
        md.append("достаточно, если она решает их без ошибок.\n");
        md.append("- Платный флагман сравнивается с локальной по абсолютной стоимости; ");
        md.append("отношение к бесплатной модели не определено.\n");
        md.append("- Для сложных задач смотрите матрицу: там видно, где 9B ошибается, ");
        md.append("а Flash/Pro/35B справляются.\n");
        md.append("- Для задач, где все модели верны, маленькой модели достаточно; ");
        md.append("флагман не даёт выигрыша в точности.\n");
        return md.toString();
    }

    private String console(List<RunResult> results) {
        StringBuilder out = new StringBuilder();
        out.append("=== Итог ===\n");
        out.append(String.format("%-32s %8s %8s %14s %18s %22s%n",
                "Модель", "Верных", "Формат", "Время (сумма/ср.)", "Токены p/c/t", "Стоимость ₽ off/peak"));
        for (Day05Model model : models) {
            List<RunResult> list = forModel(results, model);
            out.append(String.format("%-32s %8s %8s %14s %18s %22s%n",
                    model.label(),
                    rate(list, "correct"),
                    rate(list, "answer"),
                    time(list),
                    tokens(list),
                    cost(list, model)));
        }
        out.append("\np/c/t = prompt / completion / total.\n");
        out.append("Стоимость DeepSeek: off-peak / peak, курс "
                + String.format(Locale.ROOT, "%.2f", usdRub) + " ₽/$.\n");
        out.append("\nВывод:\n");
        Day05Model small = models.getFirst();
        List<RunResult> smallResults = forModel(results, small);
        out.append("- ").append(small.label()).append(": ")
                .append(rate(smallResults, "correct")).append(".\n");
        out.append("- Локальные модели бесплатны; DeepSeek — по прайсу cache miss.\n");
        out.append("- Смотрите матрицу задач: где 9B верна, флагман не нужен.\n");
        return out.toString();
    }

    private String userPrompt(Day05Task task) {
        return task.prompt() + "\n\n" + answer.strip();
    }

    private Map<String, String> labels(Day05Model model, Day05Task task) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("model", model.id());
        labels.put("provider", model.provider());
        labels.put("model_label", model.label());
        labels.put("task", task.id());
        labels.put("temperature", String.valueOf(temperature));
        return labels;
    }

    private List<RunResult> forModel(List<RunResult> results, Day05Model model) {
        return results.stream().filter(r -> r.variantId().equals(model.id())).toList();
    }

    private static RunResult first(List<RunResult> results, String model) {
        return results.stream().filter(r -> r.variantId().equals(model)).findFirst().orElse(null);
    }

    private static ChatResponse lastResponse(RunResult result) {
        return result.steps().isEmpty() ? null : result.steps().getLast().response();
    }

    private static String cell(List<RunResult> results, Day05Model model, Day05Task task) {
        List<RunResult> list = results.stream()
                .filter(r -> r.variantId().equals(model.id()) && r.caseId().equals(task.id()))
                .toList();
        if (list.isEmpty()) {
            return "—";
        }
        if (list.stream().anyMatch(r -> !r.success())) {
            return "💥";
        }
        if (list.stream().allMatch(r -> r.check().flag("correct"))) {
            return "✅";
        }
        if (list.stream().allMatch(r -> r.check().flag("answer"))) {
            return "❌";
        }
        return "⚠️";
    }

    private static String rate(List<RunResult> list, String flag) {
        if (list.isEmpty()) {
            return "—";
        }
        long count = list.stream().filter(r -> r.success() && r.check().flag(flag)).count();
        return count + "/" + list.size();
    }

    private static String time(List<RunResult> list) {
        List<ChatResponse> responses = responses(list);
        if (responses.isEmpty()) {
            return "—";
        }
        long total = responses.stream().mapToLong(ChatResponse::requestMs).sum();
        double avg = responses.stream().mapToLong(ChatResponse::requestMs).average().orElse(0);
        return String.format(Locale.ROOT, "%.1f / %.1f с", total / 1000.0, avg / 1000.0);
    }

    private static String tokens(List<RunResult> list) {
        List<ChatResponse> responses = responses(list);
        if (responses.isEmpty()) {
            return "—";
        }
        int prompt = responses.stream().mapToInt(ChatResponse::promptTokens).sum();
        int completion = responses.stream().mapToInt(ChatResponse::completionTokens).sum();
        return prompt + "/" + completion + "/" + (prompt + completion);
    }

    private static List<ChatResponse> responses(List<RunResult> list) {
        return list.stream()
                .map(Day05Experiment::lastResponse)
                .filter(r -> r != null)
                .toList();
    }

    private String cost(List<RunResult> list, Day05Model model) {
        if (model.pricing() == null) {
            return "—";
        }
        Day05Model.Pricing pricing = model.pricing();
        double off = 0;
        double peak = 0;
        for (ChatResponse response : responses(list)) {
            off += response.promptTokens() / 1_000_000.0 * pricing.inputOffPeak()
                    + response.completionTokens() / 1_000_000.0 * pricing.outputOffPeak();
            peak += response.promptTokens() / 1_000_000.0 * pricing.inputPeak()
                    + response.completionTokens() / 1_000_000.0 * pricing.outputPeak();
        }
        return formatRub(off) + " / " + formatRub(peak);
    }

    private static double totalCost(List<RunResult> list, Day05Model model) {
        if (model.pricing() == null) {
            return 0;
        }
        Day05Model.Pricing pricing = model.pricing();
        double total = 0;
        for (ChatResponse response : responses(list)) {
            total += response.promptTokens() / 1_000_000.0 * pricing.inputOffPeak()
                    + response.completionTokens() / 1_000_000.0 * pricing.outputOffPeak();
        }
        return total;
    }

    private static String formatRub(double usd, double rate) {
        return String.format(Locale.ROOT, "₽%.5f", usd * rate);
    }

    private String formatRub(double usd) {
        return formatRub(usd, usdRub);
    }

    private Day05Model modelByName(String label) {
        return models.stream().filter(m -> m.label().equals(label)).findFirst().orElse(null);
    }

    private static String shortContent(String content) {
        String value = content.strip().replaceAll("(?m)[ \\t]+$", "");
        if (value.length() > 300) {
            value = value.substring(0, 300) + "\n… (сокращено)";
        }
        return value.replace("```", "'''");
    }

    private static String shortErrors(List<String> errors) {
        return errors.stream().limit(2)
                .map(e -> e.length() > 140 ? e.substring(0, 140) + "…" : e)
                .reduce((a, b) -> a + "; " + b).orElse("");
    }

    private String csv(List<RunResult> results) {
        StringBuilder csv = new StringBuilder();
        csv.append("model,provider,label,correct,total,format,seconds,tokens_prompt,tokens_completion,cost_off_peak_usd,cost_peak_usd,cost_off_peak_rub,cost_peak_rub,resource\n");
        for (Day05Model model : models) {
            List<RunResult> list = forModel(results, model);
            List<ChatResponse> responses = responses(list);
            long ms = responses.stream().mapToLong(ChatResponse::requestMs).sum();
            int prompt = responses.stream().mapToInt(ChatResponse::promptTokens).sum();
            int completion = responses.stream().mapToInt(ChatResponse::completionTokens).sum();
            Day05Model.Pricing pricing = model.pricing();
            double off = 0;
            double peak = 0;
            if (pricing != null) {
                for (ChatResponse response : responses) {
                    off += response.promptTokens() / 1_000_000.0 * pricing.inputOffPeak()
                            + response.completionTokens() / 1_000_000.0 * pricing.outputOffPeak();
                    peak += response.promptTokens() / 1_000_000.0 * pricing.inputPeak()
                            + response.completionTokens() / 1_000_000.0 * pricing.outputPeak();
                }
            }
            csv.append(model.id()).append(',')
                    .append(model.provider()).append(',')
                    .append(model.label()).append(',')
                    .append(count(list, "correct")).append(',')
                    .append(list.size()).append(',')
                    .append(count(list, "answer")).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", ms / 1000.0)).append(',')
                    .append(prompt).append(',').append(completion).append(',')
                    .append(pricing == null ? "" : String.format(Locale.ROOT, "%.8f", off)).append(',')
                    .append(pricing == null ? "" : String.format(Locale.ROOT, "%.8f", peak)).append(',')
                    .append(pricing == null ? "" : String.format(Locale.ROOT, "%.8f", off * usdRub)).append(',')
                    .append(pricing == null ? "" : String.format(Locale.ROOT, "%.8f", peak * usdRub)).append(',')
                    .append(model.resource()).append('\n');
        }
        return csv.toString();
    }

    private static int count(List<RunResult> list, String flag) {
        return (int) list.stream().filter(r -> r.success() && r.check().flag(flag)).count();
    }

    private static String read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Не найден файл: " + file.toAbsolutePath());
        }
        return Files.readString(file, StandardCharsets.UTF_8).strip();
    }

    private static List<Day05Task> loadTasks(Path file) throws IOException {
        JsonNode root = Json.read(file);
        if (!root.isArray()) {
            throw new IOException("tasks.json должен содержать массив");
        }
        List<Day05Task> tasks = new ArrayList<>();
        for (JsonNode node : root) {
            List<String> accepted = new ArrayList<>();
            for (JsonNode item : node.path("accepted")) {
                accepted.add(item.asText());
            }
            tasks.add(new Day05Task(
                    node.path("id").asText(),
                    node.path("title").asText(),
                    node.path("prompt").asText(),
                    node.path("expected").asText(),
                    node.path("check").asText("normalized"),
                    accepted
            ));
        }
        return List.copyOf(tasks);
    }

    private static List<Day05Model> loadModels(Path file) throws IOException {
        JsonNode root = Json.read(file);
        if (!root.isArray()) {
            throw new IOException("models.json должен содержать массив");
        }
        List<Day05Model> models = new ArrayList<>();
        for (JsonNode node : root) {
            JsonNode pricing = node.get("pricing");
            Day05Model.Pricing price = null;
            if (pricing != null && !pricing.isNull()) {
                price = new Day05Model.Pricing(
                        pricing.path("inputOffPeak").asDouble(),
                        pricing.path("inputPeak").asDouble(),
                        pricing.path("outputOffPeak").asDouble(),
                        pricing.path("outputPeak").asDouble()
                );
            }
            models.add(new Day05Model(
                    node.path("id").asText(),
                    node.path("provider").asText(),
                    node.path("label").asText(),
                    node.path("resource").asText(),
                    price
            ));
        }
        return List.copyOf(models);
    }

    private record ExperimentConfig(
            double temperature,
            int maxPredict,
            int numCtx,
            double usdRub
    ) {
        static ExperimentConfig load(Path file) throws IOException {
            Properties props = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            return new ExperimentConfig(
                    Double.parseDouble(props.getProperty("experiment.temperature", "0.0").strip()),
                    Integer.parseInt(props.getProperty("experiment.max-predict", "2048").strip()),
                    Integer.parseInt(props.getProperty("experiment.num-ctx", "8192").strip()),
                    Double.parseDouble(props.getProperty("experiment.usd-rub", "82.14").strip())
            );
        }
    }
}
