package ru.pyshkov.llm.day01;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Один вызов Ollama: POST /api/chat, без стриминга и истории.
 *
 * <p>Время запроса измеряется только вокруг HTTP-вызова. Разбор JSON,
 * проверки и запись журнала сюда не входят.</p>
 */
final class OllamaClient {

    record Reply(
            String content,
            String thinking,
            String doneReason,
            int promptTokens,
            int completionTokens,
            long requestMs,
            long serverTotalMs,
            long loadMs
    ) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String baseUrl;
    private final Duration timeout;

    OllamaClient(String baseUrl, Duration timeout) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    Reply chat(String model, String systemPrompt, String userPrompt, boolean think)
            throws IOException, InterruptedException {

        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject()
                    .put("role", "system")
                    .put("content", systemPrompt);
        }
        messages.addObject()
                .put("role", "user")
                .put("content", userPrompt);

        body.put("stream", false);
        body.put("think", think);

        ObjectNode options = body.putObject("options");
        options.put("temperature", 0.0);
        options.put("num_predict", 1024);
        options.put("num_ctx", 8192);

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        long started = System.nanoTime();
        HttpResponse<String> response = http.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long requestMs = (System.nanoTime() - started) / 1_000_000;

        if (response.statusCode() != 200) {
            throw apiError(response.statusCode(), response.body(), model);
        }

        JsonNode root = JSON.readTree(response.body());
        JsonNode content = root.path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new IOException("Ollama вернула пустой ответ: " + abbreviate(response.body()));
        }

        JsonNode thinking = root.path("message").path("thinking");
        long totalNs = root.path("total_duration").asLong(0);
        long loadNs = root.path("load_duration").asLong(0);

        return new Reply(
                content.asText(),
                thinking.isTextual() && !thinking.asText().isBlank() ? thinking.asText() : null,
                root.path("done_reason").asText(""),
                root.path("prompt_eval_count").asInt(0),
                root.path("eval_count").asInt(0),
                requestMs,
                totalNs / 1_000_000,
                loadNs / 1_000_000
        );
    }

    private static IOException apiError(int status, String body, String model) {
        String detail = abbreviate(body);
        try {
            JsonNode error = JSON.readTree(body).path("error");
            if (error.isTextual()) {
                detail = error.asText();
            } else {
                JsonNode message = error.path("message");
                if (message.isTextual()) {
                    detail = message.asText();
                }
            }
        } catch (IOException ignored) {
            // тело не JSON — оставляем как есть
        }

        String hint = switch (status) {
            case 404 -> " Проверь модель: ollama pull " + model;
            case 400 -> " Проверь параметры запроса.";
            default -> "";
        };
        return new IOException("HTTP " + status + ": " + detail + hint);
    }

    private static String abbreviate(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "…";
    }
}
