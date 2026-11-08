package ru.pyshkov.llm.day02;

import com.fasterxml.jackson.databind.JsonNode;
import ru.pyshkov.llm.core.Json;
import ru.pyshkov.llm.core.check.Checker;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** День 2: один вопрос при разном уровне контроля формата. */
public final class Day02Experiment implements Experiment {

    private static final String ID = "day02";
    private static final String TITLE = "День 2. Формат ответа";
    private static final String CASE = "green-line";
    private static final List<String> VARIANT_IDS = List.of(
            "baseline", "format", "max_tokens", "stop", "json_mode", "schema");

    private final int repeats;
    private final String model;
    private final double temperature;
    private final int maxPredict;
    private final int maxTokensVariant;
    private final int numCtx;
    private final String system;
    private final String question;
    private final String formatInstruction;
    private final JsonNode schema;
    private final JsonNode expected;
    private final List<Variant> variants;
    private final Checker checker;

    public Day02Experiment(Path dayDir, LabConfig lab, int repeats, String model) throws IOException {
        if (repeats < 1) {
            throw new IllegalArgumentException("Количество повторов должно быть больше нуля");
        }

        this.repeats = repeats;
        this.model = model;
        this.system = read(dayDir.resolve("prompts/system.md"));
        this.question = read(dayDir.resolve("prompts/question.md"));
        this.formatInstruction = read(dayDir.resolve("prompts/format.md"));
        this.schema = Json.read(dayDir.resolve("data/schema.json"));
        this.expected = Json.read(dayDir.resolve("data/expected.json"));

        ExperimentConfig settings = ExperimentConfig.load(
                dayDir.resolve("config/experiment.properties"));
        this.temperature = settings.temperature();
        this.maxPredict = settings.maxPredict();
        this.maxTokensVariant = settings.maxTokensVariant();
        this.numCtx = settings.numCtx();
        this.checker = new Day02Checker(schema, expected);
        this.variants = buildVariants();
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
        for (Variant variant : variants) {
            for (int repeat = 1; repeat <= repeats; repeat++) {
                plans.add(new RunPlan(
                        CASE,
                        variant.id(),
                        repeat,
                        List.of(new Step("answer", variant.request())),
                        variant.labels(),
                        checker
                ));
            }
        }
        return plans;
    }

    @Override
    public Report report(List<RunResult> results) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(TITLE).append("\n\n");
        md.append("**Модель:** `").append(model).append("` · **повторов:** ")
                .append(repeats).append(" · **прогонов:** ").append(results.size())
                .append("\n\n");

        md.append("## Главное\n\n");
        md.append("- `baseline`: JSON ").append(rate(results, "baseline", "json"));
        if (count(results, "baseline", "truncated") > 0) {
            md.append(", обрезано ").append(rate(results, "baseline", "truncated"));
        }
        md.append(".\n");
        md.append("- `format`: JSON ").append(rate(results, "format", "json"))
                .append(", схема ").append(rate(results, "format", "schema")).append(".\n");
        md.append("- `max_tokens`: обрезано ").append(rate(results, "max_tokens", "truncated"))
                .append(", JSON ").append(rate(results, "max_tokens", "json")).append(".\n");
        md.append("- `stop`: JSON ").append(rate(results, "stop", "json")).append(".\n");
        md.append("- `json_mode`: JSON ").append(rate(results, "json_mode", "json"))
                .append(", схема ").append(rate(results, "json_mode", "schema")).append(".\n");
        md.append("- `schema`: схема ").append(rate(results, "schema", "schema"))
                .append(", поля ").append(rate(results, "schema", "fields")).append(".\n\n");

        md.append("## Результаты\n\n");
        md.append(Markdown.table(
                List.of("Вариант", "JSON", "Схема", "Поля", "Обрезка",
                        "avg токены", "avg время"),
                summaryRows(results)));

        md.append("\n## Прогоны\n\n");
        md.append("Обозначения: ✅ — JSON, схема и поля; 🟡 — JSON и схема, поля неверны; ")
                .append("🟠 — JSON без схемы; ❌ — JSON не разобран; 💥 — ошибка выполнения.\n\n");
        md.append(Markdown.table(runHeaders(), runRows(results)));

        md.append("\n## Примеры\n\n");
        for (String variant : List.of("max_tokens", "json_mode", "schema")) {
            RunResult result = first(results, variant);
            if (result == null) {
                continue;
            }
            md.append("### ").append(variant).append(", повтор ")
                    .append(result.repeat()).append("\n\n");
            ChatResponse response = response(result);
            if (response == null) {
                md.append("Ошибка: ").append(result.error()).append("\n\n");
                continue;
            }
            md.append(Markdown.code("json", shortContent(response.content())));
            if (!result.check().errors().isEmpty()) {
                md.append("\nПроверки: ")
                        .append(shortErrors(result.check().errors()))
                        .append("\n");
            }
            md.append("\n");
        }

        md.append("## Ограничения\n\n");
        md.append("- Ollama 0.40.15 не применяет `stop` в `/api/chat`: параметр ")
                .append("отправляется, но обрезка для варианта `stop` выполнена клиентом.\n");
        md.append("- Содержание не совпало с эталоном: локальная модель ошиблась в фактах. ")
                .append("Это ограничение знаний модели, а не формата.\n");
        md.append("- Полные ответы и проверки: [logs/runs.json](../logs/runs.json).\n");

        return new Report(md.toString(), csvSummary(results), consoleSummary(results));
    }

    @Override
    public Conclusion conclusion(List<RunResult> results) {
        String facts = facts(results);
        String text = conclusionText(results);
        return new Conclusion(facts, text);
    }

    private String consoleSummary(List<RunResult> results) {
        StringBuilder out = new StringBuilder();
        out.append("=== Итог ===\n");
        out.append(String.format("%-12s %8s %8s %8s %10s %10s %12s%n",
                "Вариант", "JSON", "Схема", "Поля", "Обрезка",
                "avg токены", "avg время"));
        for (String variant : VARIANT_IDS) {
            out.append(String.format("%-12s %8s %8s %8s %10s %10s %12s%n",
                    variant,
                    rate(results, variant, "json"),
                    rate(results, variant, "schema"),
                    rate(results, variant, "fields"),
                    rate(results, variant, "truncated"),
                    avgTokens(results, variant),
                    avgRequestMs(results, variant)));
        }
        out.append("\nВывод:\n");
        out.append("- baseline: JSON ").append(rate(results, "baseline", "json"))
                .append("; ответы остаются свободным текстом.\n");
        int formatJson = count(results, "format", "json");
        int formatSchema = count(results, "format", "schema");
        int total = actualTotal(results);
        if (formatJson == total) {
            out.append("- format: JSON ").append(formatJson).append("/").append(total)
                    .append(", схема ").append(formatSchema).append("/").append(total)
                    .append("; инструкция работает, но структуру нужно проверять схемой.\n");
        } else {
            out.append("- format: JSON ").append(formatJson).append("/").append(total)
                    .append(", схема ").append(formatSchema).append("/").append(total)
                    .append("; инструкция не гарантирует структуру.\n");
        }
        out.append("- max_tokens: обрезано ")
                .append(rate(results, "max_tokens", "truncated"))
                .append(", JSON ").append(rate(results, "max_tokens", "json"))
                .append("; лимит ломает JSON.\n");
        out.append("- stop: JSON ").append(rate(results, "stop", "json"))
                .append("; stop обрывает структуру.\n");
        out.append("- json_mode: JSON ").append(rate(results, "json_mode", "json"))
                .append(", схема ").append(rate(results, "json_mode", "schema"))
                .append("; синтаксис без структуры.\n");
        out.append("- schema: схема ").append(rate(results, "schema", "schema"))
                .append(", поля ").append(rate(results, "schema", "fields"))
                .append("; схема гарантирует форму, но не значения.\n");
        if (fieldsTotal(results) == 0) {
            out.append("- содержание: 0/").append(total)
                    .append("; модель ошиблась в фактах, это не формат.\n");
        }
        return out.toString();
    }

    private String facts(List<RunResult> results) {
        return "# Факты\n\n" + Markdown.table(
                List.of("Вариант", "JSON", "Схема", "Поля", "Обрезка",
                        "avg токены", "avg время"),
                summaryRows(results));
    }

    private String conclusionText(List<RunResult> results) {
        int baselineJson = count(results, "baseline", "json");
        int formatJson = count(results, "format", "json");
        int formatSchema = count(results, "format", "schema");
        int maxJson = count(results, "max_tokens", "json");
        int maxTruncated = count(results, "max_tokens", "truncated");
        int stopJson = count(results, "stop", "json");
        int jsonModeJson = count(results, "json_mode", "json");
        int jsonModeSchema = count(results, "json_mode", "schema");
        int schemaJson = count(results, "schema", "json");
        int schemaSchema = count(results, "schema", "schema");
        int schemaFields = count(results, "schema", "fields");
        int total = actualTotal(results);

        StringBuilder md = new StringBuilder();
        md.append("# Вывод\n\n");
        md.append("## Что проверяли\n\n");
        md.append("Один и тот же вопрос о Замоскворецкой линии был задан с разным уровнем ")
                .append("контроля формата: без ограничений, с JSON-инструкцией, с лимитом ")
                .append("`max_tokens`, со `stop`-последовательностью, с JSON mode и с JSON Schema.\n\n");

        md.append("## Результаты\n\n");
        md.append("- `baseline`: JSON разобран в ").append(baselineJson).append("/")
                .append(total);
        int baselineTruncated = count(results, "baseline", "truncated");
        if (baselineTruncated > 0) {
            md.append(", обрезано в ").append(baselineTruncated).append("/").append(total);
        }
        md.append(" прогонов.\n");
        md.append("- `format`: JSON разобран в ").append(formatJson).append("/")
                .append(total).append(", схема соблюдена в ").append(formatSchema)
                .append("/").append(total).append(".\n");
        md.append("- `max_tokens`: ответ обрезан в ").append(maxTruncated).append("/")
                .append(total).append(", JSON разобран в ").append(maxJson).append("/")
                .append(total).append(".\n");
        md.append("- `stop`: JSON разобран в ").append(stopJson).append("/")
                .append(total).append(".\n");
        md.append("- `json_mode`: JSON разобран в ").append(jsonModeJson).append("/")
                .append(total).append(", схема соблюдена в ").append(jsonModeSchema)
                .append("/").append(total).append(".\n");
        md.append("- `schema`: JSON разобран в ").append(schemaJson).append("/")
                .append(total).append(", схема соблюдена в ").append(schemaSchema)
                .append("/").append(total).append(", поля верны в ").append(schemaFields)
                .append("/").append(total).append(".\n\n");

        md.append("## Вывод\n\n");
        if (baselineJson < formatJson) {
            if (formatJson == total) {
                md.append("В этих прогонах JSON-инструкция дала разбираемый JSON во всех ")
                        .append(total).append(" случаях, но это мягкий контроль: ")
                        .append("инструкция не гарантирует структуру и требует проверки схемой.\n");
            } else {
                md.append("Инструкция в промпте повышает разбираемость, но не гарантирует её: ")
                        .append("часть ответов не является чистым JSON.\n");
            }
        } else {
            md.append("Инструкция в промпте не даёт устойчивого выигрыша по разбираемости.\n");
        }
        if (baselineTruncated > 0) {
            md.append("Baseline иногда упирается в технический лимит генерации: ")
                    .append(baselineTruncated).append("/").append(total)
                    .append(" прогонов обрезаны. Это не форматный контроль, ")
                    .append("а ограничение длины ответа.\n");
        }
        if (maxTruncated > 0) {
            md.append("Жёсткий лимит `max_tokens` обрезает ответ и разрушает JSON: ")
                    .append(maxTruncated).append(" из ").append(total)
                    .append(" прогонов обрезаны.\n");
        }
        if (stopJson < formatJson) {
            md.append("`stop`-последовательность обрывает структуру раньше её завершения: ")
                    .append("разбираемость падает до ").append(stopJson).append("/")
                    .append(total).append(".\n");
        }
        if (jsonModeJson > jsonModeSchema) {
            md.append("JSON mode гарантирует синтаксис, но не структуру: JSON валиден в ")
                    .append(jsonModeJson).append("/").append(total)
                    .append(", а схема соблюдена только в ").append(jsonModeSchema)
                    .append("/").append(total).append(".\n");
        }
        if (schemaSchema > jsonModeSchema) {
            md.append("JSON Schema повышает соблюдение структуры по сравнению с JSON mode.\n");
        }
        if (schemaFields < schemaSchema) {
            md.append("Схема гарантирует форму, но не правильность значений: поля верны в ")
                    .append(schemaFields).append("/").append(total)
                    .append(" при валидной схеме в ").append(schemaSchema)
                    .append("/").append(total).append(".\n");
        }
        if (fieldsTotal(results) == 0) {
            md.append("Содержание не совпало с эталоном ни в одном варианте: локальная ")
                    .append("модель ошиблась в фактах. Это ограничение знаний модели, ")
                    .append("а не формата; для проверки содержания нужен внешний источник ")
                    .append("или web-поиск, которого в ТЗ Дня 2 нет.\n");
        }
        md.append("\nДля строгого формата нужен машинно-проверяемый контракт: JSON Schema ")
                .append("плюс клиентская валидация и проверка значений. Инструкция в промпте ")
                .append("остаётся мягким контролем, а `max_tokens` и `stop` несовместимы с ")
                .append("гарантированно целым JSON.\n");

        return md.toString();
    }

    private List<Variant> buildVariants() {
        List<Variant> result = new ArrayList<>();

        result.add(variant("baseline", messages(false, false, false),
                maxPredict, List.of(), false, null, false));
        result.add(variant("format", messages(true, false, false),
                maxPredict, List.of(), false, null, false));
        result.add(variant("max_tokens", messages(true, false, false),
                maxTokensVariant, List.of(), false, null, false));
        result.add(variant("stop", messages(true, false, false),
                maxPredict, List.of("}"), false, null, true));
        result.add(variant("json_mode", messages(false, true, false),
                maxPredict, List.of(), true, null, false));
        result.add(variant("schema", messages(false, false, true),
                maxPredict, List.of(), false, schema, false));

        return List.copyOf(result);
    }

    private Variant variant(
            String id,
            List<Message> messages,
            int maxPredict,
            List<String> stop,
            boolean jsonMode,
            JsonNode schema,
            boolean emulateStop
    ) {
        ChatRequest request = ChatRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(temperature)
                .maxPredict(maxPredict)
                .stop(stop)
                .jsonMode(jsonMode)
                .schema(schema)
                .think(false)
                .numCtx(numCtx)
                .emulateStop(emulateStop)
                .build();

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("temperature", String.valueOf(temperature));
        labels.put("maxPredict", String.valueOf(maxPredict));
        labels.put("stop", stop.isEmpty() ? "" : String.join(",", stop));
        labels.put("jsonMode", String.valueOf(jsonMode));
        labels.put("schema", String.valueOf(schema != null));
        labels.put("think", "false");
        labels.put("stopEmulated", String.valueOf(emulateStop));
        labels.put("prompt", promptMode(messages));
        return new Variant(id, request, labels);
    }

    private List<Message> messages(boolean format, boolean jsonHint, boolean schemaHint) {
        String user = question;
        if (format) {
            user = user + "\n\n" + formatInstruction;
        }
        if (jsonHint) {
            user = user + "\n\nВерни ответ в формате JSON.";
        }
        if (schemaHint) {
            user = user + "\n\nВерни JSON по заданной схеме.";
        }
        return List.of(Message.system(system), Message.user(user.strip()));
    }

    private static String promptMode(List<Message> messages) {
        String text = messages.get(messages.size() - 1).content();
        if (text.contains("строго в формате JSON")) {
            return "format";
        }
        if (text.contains("Верни ответ в формате JSON")) {
            return "json";
        }
        if (text.contains("Верни JSON по заданной схеме")) {
            return "schema";
        }
        return "plain";
    }

    private static String read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Не найден файл: " + file.toAbsolutePath());
        }
        return Files.readString(file, StandardCharsets.UTF_8).strip();
    }

    private static ChatResponse response(RunResult result) {
        if (result.steps().isEmpty()) {
            return null;
        }
        return result.steps().get(result.steps().size() - 1).response();
    }

    private static int total(List<RunResult> results, String variant) {
        return (int) results.stream().filter(r -> r.variantId().equals(variant)).count();
    }

    private List<String> runHeaders() {
        List<String> headers = new ArrayList<>();
        headers.add("Вариант");
        for (int repeat = 1; repeat <= repeats; repeat++) {
            headers.add(String.valueOf(repeat));
        }
        return headers;
    }

    private List<List<String>> runRows(List<RunResult> results) {
        List<List<String>> rows = new ArrayList<>();
        for (String variant : VARIANT_IDS) {
            List<String> row = new ArrayList<>();
            row.add(variant);
            for (int repeat = 1; repeat <= repeats; repeat++) {
                RunResult result = find(results, variant, repeat);
                row.add(result == null ? "—" : runSymbol(result));
            }
            rows.add(row);
        }
        return rows;
    }

    private static RunResult find(List<RunResult> results, String variant, int repeat) {
        return results.stream()
                .filter(r -> r.variantId().equals(variant) && r.repeat() == repeat)
                .findFirst()
                .orElse(null);
    }

    private static String runSymbol(RunResult result) {
        if (!result.success()) {
            return "💥";
        }
        if (result.check().flag("json") && result.check().flag("schema")
                && result.check().flag("fields")) {
            return "✅";
        }
        if (result.check().flag("json") && result.check().flag("schema")) {
            return "🟡";
        }
        if (result.check().flag("json")) {
            return "🟠";
        }
        return "❌";
    }

    private List<List<String>> summaryRows(List<RunResult> results) {
        List<List<String>> rows = new ArrayList<>();
        for (String variant : VARIANT_IDS) {
            rows.add(List.of(
                    variant,
                    rate(results, variant, "json"),
                    rate(results, variant, "schema"),
                    rate(results, variant, "fields"),
                    rate(results, variant, "truncated"),
                    avgTokens(results, variant),
                    avgRequestMs(results, variant)
            ));
        }
        return rows;
    }

    private List<String> observations(List<RunResult> results) {
        List<String> lines = new ArrayList<>();
        lines.add("`baseline`: JSON " + rate(results, "baseline", "json")
                + "; ответы остаются свободным текстом.");
        lines.add("`format`: JSON " + rate(results, "format", "json")
                + ", схема " + rate(results, "format", "schema") + ".");
        lines.add("`max_tokens`: обрезано " + rate(results, "max_tokens", "truncated")
                + ", JSON " + rate(results, "max_tokens", "json") + ".");
        lines.add("`stop`: JSON " + rate(results, "stop", "json")
                + "; структура обрывается stop-последовательностью.");
        lines.add("`json_mode`: JSON " + rate(results, "json_mode", "json")
                + ", схема " + rate(results, "json_mode", "schema") + ".");
        lines.add("`schema`: JSON " + rate(results, "schema", "json")
                + ", схема " + rate(results, "schema", "schema")
                + ", поля " + rate(results, "schema", "fields") + ".");
        if (fieldsTotal(results) == 0) {
            lines.add("Содержание не совпало с эталоном ни в одном варианте: "
                    + "локальная модель ошиблась в фактах, а не в формате.");
        }
        return lines;
    }

    private static int count(List<RunResult> results, String variant, String flag) {
        return (int) results.stream()
                .filter(r -> r.variantId().equals(variant))
                .filter(r -> r.check().flag(flag))
                .count();
    }

    private static int fieldsTotal(List<RunResult> results) {
        return (int) results.stream().filter(r -> r.check().flag("fields")).count();
    }

    private static String rate(List<RunResult> results, String variant, String flag) {
        int total = total(results, variant);
        return total == 0 ? "—" : count(results, variant, flag) + "/" + total;
    }

    private static String flag(RunResult result, String flag) {
        return result.success() ? (result.check().flag(flag) ? "да" : "нет") : "—";
    }

    private static int actualTotal(List<RunResult> results) {
        int total = total(results, "baseline");
        return total == 0 ? 1 : total;
    }

    private static List<ChatResponse> responses(List<RunResult> results, String variant) {
        return results.stream()
                .filter(r -> r.variantId().equals(variant))
                .map(Day02Experiment::response)
                .filter(r -> r != null)
                .toList();
    }

    private static String avgPromptTokens(List<RunResult> results, String variant) {
        List<ChatResponse> list = responses(results, variant);
        if (list.isEmpty()) {
            return "—";
        }
        double avg = list.stream().mapToInt(ChatResponse::promptTokens).average().orElse(0);
        return String.format("%.0f", avg);
    }

    private static String avgCompletionTokens(List<RunResult> results, String variant) {
        List<ChatResponse> list = responses(results, variant);
        if (list.isEmpty()) {
            return "—";
        }
        double avg = list.stream().mapToInt(ChatResponse::completionTokens).average().orElse(0);
        return String.format("%.0f", avg);
    }

    private static String avgTokens(List<RunResult> results, String variant) {
        List<ChatResponse> responses = responses(results, variant);
        if (responses.isEmpty()) {
            return "—";
        }
        double avg = responses.stream().mapToInt(ChatResponse::totalTokens).average().orElse(0);
        return String.format("%.0f", avg);
    }

    private static String avgRequestMs(List<RunResult> results, String variant) {
        List<ChatResponse> responses = responses(results, variant);
        if (responses.isEmpty()) {
            return "—";
        }
        double avg = responses.stream().mapToLong(ChatResponse::requestMs).average().orElse(0);
        return String.format("%.0f мс", avg);
    }

    private static RunResult first(List<RunResult> results, String variant) {
        return results.stream().filter(r -> r.variantId().equals(variant)).findFirst().orElse(null);
    }

    private String csvSummary(List<RunResult> results) {
        StringBuilder csv = new StringBuilder();
        csv.append("variant,json_ok,schema_ok,fields_ok,truncated,")
                .append("avg_prompt_tokens,avg_completion_tokens,avg_total_tokens,avg_request_ms\n");
        for (String variant : VARIANT_IDS) {
            csv.append(variant).append(',')
                    .append(count(results, variant, "json")).append(',')
                    .append(count(results, variant, "schema")).append(',')
                    .append(count(results, variant, "fields")).append(',')
                    .append(count(results, variant, "truncated")).append(',')
                    .append(avgPromptTokens(results, variant)).append(',')
                    .append(avgCompletionTokens(results, variant)).append(',')
                    .append(avgTokens(results, variant)).append(',')
                    .append(avgRequestMs(results, variant).replace(" мс", ""))
                    .append('\n');
        }
        return csv.toString();
    }

    private static String shortContent(String content) {
        String value = content.strip().replaceAll("(?m)[ \t]+$", "");
        if (value.length() > 450) {
            value = value.substring(0, 450) + "\n… (сокращено)";
        }
        return value.replace("```", "'''");
    }

    private static String shortErrors(List<String> errors) {
        List<String> shown = errors.stream()
                .limit(3)
                .map(error -> error.length() > 160 ? error.substring(0, 160) + "…" : error)
                .toList();
        String text = String.join("; ", shown);
        return errors.size() > 3 ? text + " …" : text;
    }

    private record Variant(String id, ChatRequest request, Map<String, String> labels) {
    }

    private record ExperimentConfig(
            double temperature,
            int maxPredict,
            int maxTokensVariant,
            int numCtx
    ) {
        static ExperimentConfig load(Path file) throws IOException {
            Properties props = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            return new ExperimentConfig(
                    Double.parseDouble(props.getProperty("experiment.temperature", "0.7").strip()),
                    Integer.parseInt(props.getProperty("experiment.max-predict", "8192").strip()),
                    Integer.parseInt(props.getProperty("experiment.max-tokens-variant", "120").strip()),
                    Integer.parseInt(props.getProperty("experiment.num-ctx", "8192").strip())
            );
        }
    }
}
