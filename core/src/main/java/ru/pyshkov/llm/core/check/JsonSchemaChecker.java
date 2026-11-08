package ru.pyshkov.llm.core.check;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Небольшой валидатор JSON Schema для используемого в проекте подмножества:
 * type, properties, required, additionalProperties, items.
 *
 * <p>Без внешних зависимостей: для контрактов экспериментов этого достаточно,
 * а поведение остаётся предсказуемым и тестируемым.</p>
 */
public final class JsonSchemaChecker {

    public record Result(boolean valid, List<String> errors) {
        public Result {
            errors = List.copyOf(errors);
        }
    }

    private final JsonNode schema;

    public JsonSchemaChecker(JsonNode schema) {
        this.schema = schema;
    }

    public Result validate(JsonNode value) {
        List<String> errors = new ArrayList<>();
        validateNode(value, schema, "ответ", errors);
        return new Result(errors.isEmpty(), errors);
    }

    private static void validateNode(
            JsonNode value, JsonNode node, String path, List<String> errors) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }

        String type = node.path("type").asText("");
        switch (type) {
            case "object" -> validateObject(value, node, path, errors);
            case "array" -> validateArray(value, node, path, errors);
            case "string" -> require(value.isTextual(), path, "строку", value, errors);
            case "integer" -> require(value.isIntegralNumber(), path, "целое число", value, errors);
            case "number" -> require(value.isNumber(), path, "число", value, errors);
            case "boolean" -> require(value.isBoolean(), path, "boolean", value, errors);
            case "" -> {
                // Схема без type: проверять нечего.
            }
            default -> errors.add(path + ": неизвестный тип «" + type + "»");
        }
    }

    private static void validateObject(
            JsonNode value, JsonNode schema, String path, List<String> errors) {
        if (!value.isObject()) {
            errors.add(path + ": ожидался объект, пришло " + typeName(value));
            return;
        }

        JsonNode properties = schema.path("properties");
        JsonNode required = schema.path("required");

        if (required.isArray()) {
            for (JsonNode field : required) {
                String name = field.asText("");
                JsonNode child = value.get(name);
                if (child == null || child.isNull()) {
                    errors.add(path + "." + name + ": обязательное поле отсутствует");
                }
            }
        }

        if (properties.isObject()) {
            var fields = properties.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                JsonNode child = value.get(field.getKey());
                if (child != null && !child.isNull()) {
                    validateNode(child, field.getValue(),
                            path + "." + field.getKey(), errors);
                }
            }
        }

        if (schema.path("additionalProperties").isBoolean()
                && !schema.path("additionalProperties").asBoolean()) {
            var actual = value.fieldNames();
            while (actual.hasNext()) {
                String name = actual.next();
                if (!properties.has(name)) {
                    errors.add(path + "." + name + ": лишнее поле");
                }
            }
        }
    }

    private static void validateArray(
            JsonNode value, JsonNode schema, String path, List<String> errors) {
        if (!value.isArray()) {
            errors.add(path + ": ожидался массив, пришло " + typeName(value));
            return;
        }

        JsonNode items = schema.path("items");
        for (int i = 0; i < value.size(); i++) {
            validateNode(value.get(i), items, path + "[" + i + "]", errors);
        }
    }

    private static void require(
            boolean condition,
            String path,
            String expected,
            JsonNode value,
            List<String> errors
    ) {
        if (!condition) {
            errors.add(path + ": ожидалось " + expected + ", пришло " + typeName(value));
        }
    }

    private static String typeName(JsonNode value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        if (value.isTextual()) {
            return "строка";
        }
        if (value.isIntegralNumber()) {
            return "целое число";
        }
        if (value.isNumber()) {
            return "число";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        if (value.isArray()) {
            return "массив";
        }
        if (value.isObject()) {
            return "объект";
        }
        return "неизвестно";
    }
}
