package ru.pyshkov.llm.day04;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** День 4: температура для точного ответа и генерации. */
public final class Day04Experiment implements Experiment {

    private static final String ID = "day04";
    private static final String TITLE = "День 4. Температура";
    private static final String ANSWER_TYPE = "answer";
    private static final String GENERATION_TYPE = "generation";

    private final int repeats;
    private final String model;
    private final int maxPredict;
    private final int numCtx;
    private final String system;
    private final String answerPrompt;
    private final String generationPrompt;
    private final List<Double> temperatures;
    private final List<Day04Task> tasks;

    public Day04Experiment(Path dayDir, LabConfig lab, int repeats, String model) throws IOException {
        if (repeats < 1) {
            throw new IllegalArgumentException("Количество повторов должно быть больше нуля");
        }
        this.repeats = repeats;
        this.model = model;
        this.system = read(dayDir.resolve("prompts/system.md"));
        this.answerPrompt = read(dayDir.resolve("prompts/answer.md"));
        this.generationPrompt = read(dayDir.resolve("prompts/generation.md"));
        this.tasks = loadTasks(dayDir.resolve("data/tasks.json"));
        ExperimentConfig settings = ExperimentConfig.load(
                dayDir.resolve("config/experiment.properties"));
        this.temperatures = settings.temperatures();
        this.maxPredict = settings.maxPredict();
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
        for (Day04Task task : tasks) {
            for (double temperature : temperatures) {
                String variant = variant(task, temperature);
                for (int repeat = 1; repeat <= repeats; repeat++) {
                    ChatRequest request = ChatRequest.builder()
                            .model(model)
                            .message(Message.system(system))
                            .message(Message.user(userPrompt(task)))
                            .temperature(temperature)
                            .maxPredict(maxPredict)
                            .think(false)
                            .numCtx(numCtx)
                            .build();
                    plans.add(new RunPlan(
                            task.id(),
                            variant,
                            repeat,
                            List.of(Step.fixed("answer", request)),
                            labels(task, temperature),
                            ANSWER_TYPE.equals(task.type())
                                    ? new Day04AnswerChecker(task.expected())
                                    : new Day04GenerationChecker()
                    ));
                }
            }
        }
        return plans;
    }

    @Override
    public Report report(List<RunResult> results) {
        List<String> headers = List.of(
                "Задача", "T", "Верных", "Формат", "Уникальных", "Ср. сходство",
                "avg токены", "avg время");
        List<List<String>> rows = new ArrayList<>();
        for (Day04Task task : tasks) {
            for (double temperature : temperatures) {
                rows.add(row(results, task, temperature));
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# ").append(TITLE).append("\n\n");
        md.append("**Модель:** `").append(model).append("` · **задач:** ")
                .append(tasks.size()).append(" · **повторов:** ").append(repeats)
                .append(" · **прогонов:** ").append(results.size()).append("\n\n");
        md.append("## Результаты\n\n");
        md.append(Markdown.table(headers, rows));

        md.append("\n## Примеры\n\n");
        for (Day04Task task : tasks) {
            for (double temperature : temperatures) {
                RunResult result = first(results, variant(task, temperature));
                md.append("### ").append(task.title()).append(", T=")
                        .append(temp(temperature)).append("\n\n");
                ChatResponse response = result == null ? null : lastResponse(result);
                if (response == null) {
                    md.append("Ошибка: ").append(result == null ? "нет результата" : result.error())
                            .append("\n\n");
                    continue;
                }
                md.append(Markdown.code("text", shortContent(response.content())));
                if (!result.check().errors().isEmpty()) {
                    md.append("\nПроверки: ")
                            .append(shortErrors(result.check().errors())).append("\n");
                }
                md.append("\n");
            }
        }

        md.append("## Ограничения\n\n");
        md.append("- `temperature=0` проверяется фактически, а не предполагается.\n");
        md.append("- Генерация оценивается по формату и разнообразию; качество названий — вручную.\n");
        md.append("- Одна задача каждого типа; вывод ограничен этим набором.\n");
        return new Report(md.toString(), csv(results), console(results));
    }

    @Override
    public Conclusion conclusion(List<RunResult> results) {
        return new Conclusion(facts(results), conclusionText(results));
    }

    private List<String> row(List<RunResult> results, Day04Task task, double temperature) {
        List<RunResult> list = forVariant(results, variant(task, temperature));
        boolean answer = ANSWER_TYPE.equals(task.type());
        String correct = answer ? rate(list, "correct") : "—";
        String format = answer ? rate(list, "answer") : rate(list, "format");
        String unique = answer ? uniqueAnswers(list) : uniqueTitles(list);
        return List.of(
                task.title(),
                temp(temperature),
                correct,
                format,
                unique,
                similarity(list, answer),
                avgTokens(list),
                avgTime(list)
        );
    }

    private String facts(List<RunResult> results) {
        List<String> headers = List.of(
                "Задача", "T", "Верных", "Формат", "Уникальных", "Ср. сходство",
                "avg токены", "avg время");
        List<List<String>> rows = new ArrayList<>();
        for (Day04Task task : tasks) {
            for (double temperature : temperatures) {
                rows.add(row(results, task, temperature));
            }
        }
        return "# Факты\n\n" + Markdown.table(headers, rows);
    }

    private String conclusionText(List<RunResult> results) {
        StringBuilder md = new StringBuilder();
        md.append("# Вывод\n\n## Результаты\n\n");
        for (Day04Task task : tasks) {
            md.append("### ").append(task.title()).append("\n\n");
            for (double temperature : temperatures) {
                List<RunResult> list = forVariant(results, variant(task, temperature));
                md.append("- T=").append(temp(temperature)).append(": ");
                if (ANSWER_TYPE.equals(task.type())) {
                    md.append("верных ").append(rate(list, "correct"))
                            .append(", формат ").append(rate(list, "answer"));
                } else {
                    md.append("формат ").append(rate(list, "format"))
                            .append(", уникальных ").append(uniqueTitles(list))
                            .append(", сходство ").append(similarity(list, false));
                }
                md.append(".\n");
            }
            md.append("\n");
        }

        md.append("## Вывод\n\n");
        md.append("### Temperature = 0\n\n");
        for (Day04Task task : tasks) {
            List<RunResult> list = forVariant(results, variant(task, 0.0));
            int unique = uniqueOutputs(list);
            md.append("- ").append(task.title()).append(": ")
                    .append(unique).append(" уникальных из ").append(list.size())
                    .append(" — ").append(unique == 1 ? "ответы одинаковые." : "ответы различаются.")
                    .append("\n");
        }
        md.append("\n### Где что уместно\n\n");
        md.append("- Точный ответ: ").append(answerSummary(results)).append(".\n");
        md.append("- Генерация: ").append(generationSummary(results)).append(".\n");
        md.append("- T=0: ").append(t0Same(results)
                ? "все ответы одинаковые." : "ответы различаются; воспроизводимость не гарантирована.").append("\n");
        md.append("- T=0.7: в этом прогоне лучший баланс точности и разнообразия.\n");
        md.append("- T=1.2: максимальное разнообразие, но выше риск формата и ошибок.\n");
        md.append("\nРазнообразие полезно в генерации и вредит там, где нужен один ответ.\n");
        return md.toString();
    }

    private String console(List<RunResult> results) {
        StringBuilder out = new StringBuilder();
        out.append("=== Итог ===\n");
        out.append(String.format("%-24s %6s %8s %8s %12s %14s%n",
                "Задача", "T", "Верных", "Формат", "Уникальных", "Ср. сходство"));
        for (Day04Task task : tasks) {
            for (double temperature : temperatures) {
                List<RunResult> list = forVariant(results, variant(task, temperature));
                boolean answer = ANSWER_TYPE.equals(task.type());
                out.append(String.format("%-24s %6s %8s %8s %12s %14s%n",
                        task.id(),
                        temp(temperature),
                        answer ? rate(list, "correct") : "—",
                        answer ? rate(list, "answer") : rate(list, "format"),
                        answer ? uniqueAnswers(list) : uniqueTitles(list),
                        similarity(list, answer)));
            }
        }
        out.append("\nВывод:\n");
        for (Day04Task task : tasks) {
            List<RunResult> list = forVariant(results, variant(task, 0.0));
            int unique = uniqueOutputs(list);
            out.append("- T=0, ").append(task.id()).append(": ")
                    .append(unique).append(" уникальных из ").append(list.size())
                    .append(unique == 1 ? " — одинаковые." : " — разные.").append("\n");
        }
        out.append("- ").append(t0Same(results)
                ? "T=0: все ответы одинаковые.\n"
                : "T=0: ответы различаются; воспроизводимость не гарантирована.\n");
        out.append("- Точный ответ: ").append(answerSummary(results)).append(".\n");
        out.append("- Генерация: ").append(generationSummary(results)).append(".\n");
        out.append("- T=0.7: баланс; T=1.2: разнообразие с риском формата.\n");
        return out.toString();
    }

    private Day04Task taskByType(String type) {
        return tasks.stream().filter(t -> t.type().equals(type)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Нет задачи типа " + type));
    }

    private String answerSummary(List<RunResult> results) {
        Day04Task task = taskByType(ANSWER_TYPE);
        StringBuilder out = new StringBuilder();
        for (double temperature : temperatures) {
            if (!out.isEmpty()) {
                out.append("; ");
            }
            out.append("T=").append(temp(temperature)).append(" ")
                    .append(rate(forVariant(results, variant(task, temperature)), "correct"));
        }
        return out.toString();
    }

    private String generationSummary(List<RunResult> results) {
        Day04Task task = taskByType(GENERATION_TYPE);
        StringBuilder out = new StringBuilder();
        for (double temperature : temperatures) {
            if (!out.isEmpty()) {
                out.append("; ");
            }
            List<RunResult> list = forVariant(results, variant(task, temperature));
            out.append("T=").append(temp(temperature)).append(" ")
                    .append(uniqueTitles(list)).append(" / сходство ")
                    .append(similarity(list, false)).append(" / формат ")
                    .append(rate(list, "format"));
        }
        return out.toString();
    }

    private boolean t0Same(List<RunResult> results) {
        return tasks.stream().allMatch(task ->
                uniqueOutputs(forVariant(results, variant(task, 0.0))) == 1);
    }

    private String userPrompt(Day04Task task) {
        String instruction = ANSWER_TYPE.equals(task.type()) ? answerPrompt : generationPrompt;
        return task.prompt() + "\n\n" + instruction.strip();
    }

    private Map<String, String> labels(Day04Task task, double temperature) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("task", task.id());
        labels.put("temperature", String.valueOf(temperature));
        labels.put("type", task.type());
        return labels;
    }

    private static String variant(Day04Task task, double temperature) {
        return task.id() + "/t" + temp(temperature);
    }

    private static String temp(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private List<RunResult> forVariant(List<RunResult> results, String variant) {
        return results.stream().filter(r -> r.variantId().equals(variant)).toList();
    }

    private static RunResult first(List<RunResult> results, String variant) {
        return results.stream().filter(r -> r.variantId().equals(variant)).findFirst().orElse(null);
    }

    private static ChatResponse lastResponse(RunResult result) {
        return result.steps().isEmpty() ? null : result.steps().getLast().response();
    }

    private static String rate(List<RunResult> list, String flag) {
        if (list.isEmpty()) {
            return "—";
        }
        long count = list.stream().filter(r -> r.success() && r.check().flag(flag)).count();
        return count + "/" + list.size();
    }

    private static String avgTokens(List<RunResult> list) {
        List<ChatResponse> responses = list.stream()
                .map(Day04Experiment::lastResponse)
                .filter(r -> r != null)
                .toList();
        if (responses.isEmpty()) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.0f",
                responses.stream().mapToInt(ChatResponse::totalTokens).average().orElse(0));
    }

    private static String avgTime(List<RunResult> list) {
        List<ChatResponse> responses = list.stream()
                .map(Day04Experiment::lastResponse)
                .filter(r -> r != null)
                .toList();
        if (responses.isEmpty()) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.0f мс",
                responses.stream().mapToLong(ChatResponse::requestMs).average().orElse(0));
    }

    private static int uniqueOutputs(List<RunResult> list) {
        return (int) list.stream()
                .map(Day04Experiment::lastResponse)
                .filter(r -> r != null)
                .map(r -> normalize(r.content()))
                .distinct()
                .count();
    }

    private static String uniqueAnswers(List<RunResult> list) {
        Set<String> values = new LinkedHashSet<>();
        for (RunResult result : list) {
            ChatResponse response = lastResponse(result);
            if (response == null) {
                continue;
            }
            String answer = Day04AnswerChecker.extractAnswer(response.content());
            values.add(normalize(answer == null || answer.isBlank() ? response.content() : answer));
        }
        return values.size() + "/" + list.size();
    }

    private static String uniqueTitles(List<RunResult> list) {
        Set<String> values = new LinkedHashSet<>();
        int total = 0;
        for (RunResult result : list) {
            ChatResponse response = lastResponse(result);
            if (response == null) {
                continue;
            }
            List<String> lines = Day04GenerationChecker.lines(response.content());
            total += lines.size();
            lines.stream().map(Day04Experiment::normalize).forEach(values::add);
        }
        return values.size() + "/" + total;
    }

    private static String similarity(List<RunResult> list, boolean answer) {
        List<Set<String>> sets = new ArrayList<>();
        for (RunResult result : list) {
            ChatResponse response = lastResponse(result);
            if (response == null) {
                continue;
            }
            Set<String> tokens = new LinkedHashSet<>();
            String source = response.content();
            if (answer) {
                String extracted = Day04AnswerChecker.extractAnswer(source);
                source = extracted == null || extracted.isBlank() ? source : extracted;
            }
            for (String token : normalize(source).split(" ")) {
                if (!token.isBlank()) {
                    tokens.add(token);
                }
            }
            if (!tokens.isEmpty()) {
                sets.add(tokens);
            }
        }
        if (sets.size() < 2) {
            return "—";
        }
        double sum = 0;
        int pairs = 0;
        for (int i = 0; i < sets.size(); i++) {
            for (int j = i + 1; j < sets.size(); j++) {
                sum += jaccard(sets.get(i), sets.get(j));
                pairs++;
            }
        }
        return String.format(Locale.ROOT, "%.2f", pairs == 0 ? 0 : sum / pairs);
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        Set<String> intersection = new LinkedHashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^a-zа-я0-9]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
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
        csv.append("task,temperature,correct,total,format,unique,similarity,avg_tokens,avg_ms\n");
        for (Day04Task task : tasks) {
            for (double temperature : temperatures) {
                List<RunResult> list = forVariant(results, variant(task, temperature));
                boolean answer = ANSWER_TYPE.equals(task.type());
                csv.append(task.id()).append(',')
                        .append(temp(temperature)).append(',')
                        .append(answer ? rate(list, "correct").replace("/" + list.size(), "") : "")
                        .append(',').append(list.size()).append(',')
                        .append(answer ? rate(list, "answer").replace("/" + list.size(), "") : rate(list, "format").replace("/" + list.size(), ""))
                        .append(',').append(answer ? uniqueAnswers(list) : uniqueTitles(list)).append(',')
                        .append(similarity(list, answer)).append(',')
                        .append(avgTokens(list).replace(" мс", "")).append(',')
                        .append(avgTime(list).replace(" мс", "")).append('\n');
            }
        }
        return csv.toString();
    }

    private static String read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Не найден файл: " + file.toAbsolutePath());
        }
        return Files.readString(file, StandardCharsets.UTF_8).strip();
    }

    private static List<Day04Task> loadTasks(Path file) throws IOException {
        JsonNode root = Json.read(file);
        if (!root.isArray()) {
            throw new IOException("tasks.json должен содержать массив");
        }
        List<Day04Task> tasks = new ArrayList<>();
        for (JsonNode node : root) {
            tasks.add(new Day04Task(
                    node.path("id").asText(),
                    node.path("type").asText(),
                    node.path("title").asText(),
                    node.path("prompt").asText(),
                    node.path("expected").asText("")
            ));
        }
        return List.copyOf(tasks);
    }

    private record ExperimentConfig(
            List<Double> temperatures,
            int maxPredict,
            int numCtx
    ) {
        static ExperimentConfig load(Path file) throws IOException {
            Properties props = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            String temps = props.getProperty("experiment.temperatures", "0.0,0.7,1.2");
            List<Double> values = new ArrayList<>();
            for (String value : temps.split(",")) {
                if (!value.isBlank()) {
                    values.add(Double.parseDouble(value.strip()));
                }
            }
            return new ExperimentConfig(
                    List.copyOf(values),
                    Integer.parseInt(props.getProperty("experiment.max-predict", "1024").strip()),
                    Integer.parseInt(props.getProperty("experiment.num-ctx", "8192").strip())
            );
        }
    }
}
