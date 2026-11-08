package ru.pyshkov.llm.day02;

import com.fasterxml.jackson.databind.JsonNode;
import ru.pyshkov.llm.core.Json;
import ru.pyshkov.llm.core.check.CheckResult;
import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;
import ru.pyshkov.llm.core.client.Message;
import ru.pyshkov.llm.core.experiment.StepResult;

import java.util.List;

/** Проверки Day02Checker без JUnit. */
public final class Day02CheckerSelfTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "line": {"type": "string"},
                "station_count": {"type": "integer"},
                "transfers": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "station": {"type": "string"},
                      "connections": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": {
                            "line": {"type": "integer"},
                            "station": {"type": "string"}
                          },
                          "required": ["line", "station"]
                        }
                      }
                    },
                    "required": ["station", "connections"]
                  }
                }
              },
              "required": ["line", "station_count", "transfers"]
            }
            """;

    private static final String EXPECTED = """
            {
              "line": "Замоскворецкая",
              "station_count": 2,
              "transfers": [
                {"station": "А", "connections": [{"line": 1, "station": "Б"}]}
              ]
            }
            """;

    public static void main(String[] args) throws Exception {
        JsonNode schema = Json.MAPPER.readTree(SCHEMA);
        JsonNode expected = Json.MAPPER.readTree(EXPECTED);

        CheckResult good = check(schema, expected, """
                {"line":"Замоскворецкая","station_count":2,
                 "transfers":[{"station":"А","connections":[{"line":1,"station":"Б"}]}]}
                """);
        require(good.flag("json"), "valid JSON");
        require(good.flag("schema"), "valid schema");
        require(good.flag("fields"), "valid fields");

        CheckResult badCount = check(schema, expected, """
                {"line":"Замоскворецкая","station_count":3,
                 "transfers":[{"station":"А","connections":[{"line":1,"station":"Б"}]}]}
                """);
        require(badCount.flag("json"), "invalid count JSON");
        require(badCount.flag("schema"), "invalid count schema");
        require(!badCount.flag("fields"), "invalid count fields");

        CheckResult invalidJson = check(schema, expected, "не JSON");
        require(!invalidJson.flag("json"), "invalid JSON");
        require(!invalidJson.flag("schema"), "invalid JSON schema");

        System.out.println("Day02CheckerSelfTest OK");
    }

    private static CheckResult check(JsonNode schema, JsonNode expected, String content) {
        ChatRequest request = ChatRequest.builder()
                .model("test").message(Message.user("q")).build();
        ChatResponse response = new ChatResponse(
                "test", content, null, "stop", 1, 1, 1, 0, 0, false);
        return new Day02Checker(schema, expected)
                .check(List.of(new StepResult("answer", request, response, null, 1)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
