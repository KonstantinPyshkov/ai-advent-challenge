package ru.pyshkov.llm.day02;

import com.fasterxml.jackson.databind.JsonNode;
import ru.pyshkov.llm.core.Json;
import ru.pyshkov.llm.core.check.CheckResult;
import ru.pyshkov.llm.core.check.Checker;
import ru.pyshkov.llm.core.check.JsonSchemaChecker;
import ru.pyshkov.llm.core.experiment.StepResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Проверка JSON, схемы и фактического содержания ответа. */
public final class Day02Checker implements Checker {

    private final JsonSchemaChecker schemaChecker;
    private final JsonNode expected;

    public Day02Checker(JsonNode schema, JsonNode expected) {
        this.schemaChecker = new JsonSchemaChecker(schema);
        this.expected = expected;
    }

    @Override
    public CheckResult check(List<StepResult> steps) {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        StepResult last = steps.isEmpty() ? null : steps.get(steps.size() - 1);
        if (last == null || last.response() == null) {
            flags.put("json", false);
            flags.put("schema", false);
            flags.put("line", false);
            flags.put("count", false);
            flags.put("transfers", false);
            flags.put("fields", false);
            flags.put("truncated", false);
            flags.put("fenced", false);
            errors.add(last == null ? "нет шага" : "нет ответа");
            return new CheckResult(flags, errors);
        }

        String content = last.response().content();
        String stripped = content.strip();
        boolean fenced = stripped.startsWith("```") || stripped.contains("```json");
        boolean truncated = "length".equals(last.response().doneReason());

        JsonNode parsed = null;
        try {
            parsed = Json.MAPPER.readTree(stripped);
        } catch (Exception e) {
            errors.add("JSON не разобран: " + oneLine(e.getMessage()));
        }

        flags.put("json", parsed != null);
        flags.put("truncated", truncated);
        flags.put("fenced", fenced);

        boolean schemaValid = false;
        if (parsed != null) {
            JsonSchemaChecker.Result schemaResult = schemaChecker.validate(parsed);
            schemaValid = schemaResult.valid();
            errors.addAll(limit(schemaResult.errors(), 10));
        }
        flags.put("schema", schemaValid);

        Fields fields = parsed == null ? Fields.invalid() : checkFields(parsed);
        flags.put("line", fields.line);
        flags.put("count", fields.count);
        flags.put("transfers", fields.transfers);
        flags.put("fields", fields.line && fields.count && fields.transfers);
        errors.addAll(fields.errors);

        return new CheckResult(flags, limit(errors, 20));
    }

    private Fields checkFields(JsonNode actual) {
        List<String> errors = new ArrayList<>();

        String expectedLine = normalize(expected.path("line").asText(""));
        String actualLine = normalize(actual.path("line").asText(""));
        boolean lineOk = !actualLine.isEmpty()
                && (actualLine.contains("замоскворец") || actualLine.contains("зелен"));

        int expectedCount = expected.path("station_count").asInt(-1);
        boolean countOk = actual.path("station_count").isInt()
                && actual.path("station_count").asInt() == expectedCount;
        if (!countOk) {
            errors.add("station_count: ожидалось " + expectedCount
                    + ", получено " + actual.path("station_count").asText("—"));
        }

        Set<String> expectedTransfers = transfers(expected);
        Set<String> actualTransfers = new LinkedHashSet<>();
        boolean structureOk = true;
        int actualConnectionCount = 0;

        JsonNode transfers = actual.path("transfers");
        if (!transfers.isArray()) {
            structureOk = false;
            errors.add("transfers: ожидался массив");
        } else {
            for (JsonNode transfer : transfers) {
                String station = normalize(transfer.path("station").asText(""));
                JsonNode connections = transfer.path("connections");
                if (station.isEmpty() || !connections.isArray()) {
                    structureOk = false;
                    errors.add("transfers: некорректная запись пересадки");
                    continue;
                }
                for (JsonNode connection : connections) {
                    if (!connection.path("line").isInt()) {
                        structureOk = false;
                        errors.add("connections: line должен быть числом");
                        continue;
                    }
                    String target = normalize(connection.path("station").asText(""));
                    if (target.isEmpty()) {
                        structureOk = false;
                        errors.add("connections: пустое название станции");
                        continue;
                    }
                    actualTransfers.add(station + "|" + connection.path("line").asInt() + "|" + target);
                    actualConnectionCount++;
                }
            }
        }

        List<String> missing = expectedTransfers.stream()
                .filter(value -> !actualTransfers.contains(value))
                .sorted()
                .toList();
        List<String> extra = actualTransfers.stream()
                .filter(value -> !expectedTransfers.contains(value))
                .sorted()
                .toList();

        missing.forEach(value -> errors.add("нет пересадки: " + readable(value)));
        extra.forEach(value -> errors.add("лишняя пересадка: " + readable(value)));

        boolean duplicates = actualConnectionCount != actualTransfers.size();
        if (duplicates) {
            errors.add("в transfers есть дубликаты");
        }

        boolean transfersOk = structureOk
                && missing.isEmpty()
                && extra.isEmpty()
                && !duplicates;

        boolean lineMatches = !expectedLine.isEmpty() && lineOk;
        return new Fields(lineMatches, countOk, transfersOk, errors);
    }

    private static Set<String> transfers(JsonNode root) {
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode transfer : root.path("transfers")) {
            String station = normalize(transfer.path("station").asText(""));
            for (JsonNode connection : transfer.path("connections")) {
                int line = connection.path("line").asInt(-1);
                String target = normalize(connection.path("station").asText(""));
                if (!station.isEmpty() && line > 0 && !target.isEmpty()) {
                    result.add(station + "|" + line + "|" + target);
                }
            }
        }
        return result;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^a-zа-я0-9]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String readable(String key) {
        String[] parts = key.split("\\|", 3);
        return parts.length == 3 ? parts[0] + " → линия " + parts[1] + " → " + parts[2] : key;
    }

    private static String oneLine(String value) {
        if (value == null) {
            return "неизвестная ошибка";
        }
        String line = value.replaceAll("\\s+", " ").strip();
        return line.length() <= 200 ? line : line.substring(0, 200) + "…";
    }

    private static List<String> limit(List<String> values, int max) {
        return values.size() <= max ? List.copyOf(values) : List.copyOf(values.subList(0, max));
    }

    private record Fields(boolean line, boolean count, boolean transfers, List<String> errors) {
        static Fields invalid() {
            return new Fields(false, false, false, List.of());
        }
    }
}
