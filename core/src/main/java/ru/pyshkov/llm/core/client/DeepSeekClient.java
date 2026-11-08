package ru.pyshkov.llm.core.client;

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

/** Клиент DeepSeek через OpenAI-совместимый /chat/completions. */
public final class DeepSeekClient implements ChatModel {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient http;

    public DeepSeekClient(String baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws IOException, InterruptedException {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", request.model());
        body.put("stream", false);
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxPredict());

        ArrayNode messages = body.putArray("messages");
        for (Message message : request.messages()) {
            messages.addObject()
                    .put("role", message.role())
                    .put("content", message.content());
        }

        ObjectNode thinking = body.putObject("thinking");
        if (request.think()) {
            thinking.put("type", "enabled");
            body.put("reasoning_effort", "high");
        } else {
            thinking.put("type", "disabled");
        }

        if (!request.stop().isEmpty()) {
            ArrayNode stop = body.putArray("stop");
            request.stop().forEach(stop::add);
        }

        if (request.schema() != null) {
            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_schema");
            ObjectNode jsonSchema = responseFormat.putObject("json_schema");
            jsonSchema.put("name", "answer");
            jsonSchema.put("strict", true);
            jsonSchema.set("schema", request.schema());
        } else if (request.jsonMode()) {
            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_object");
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        long started = System.nanoTime();
        HttpResponse<String> response = http.send(
                httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long requestMs = (System.nanoTime() - started) / 1_000_000;

        if (response.statusCode() != 200) {
            throw apiError(response.statusCode(), response.body(), request.model());
        }

        JsonNode root = JSON.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IOException("DeepSeek вернул ответ без choices");
        }

        JsonNode message = choices.get(0).path("message");
        JsonNode content = message.path("content");
        JsonNode reasoning = message.path("reasoning_content");
        JsonNode usage = root.path("usage");

        String contentText = content.isTextual() ? content.asText() : "";
        String thinkingText = reasoning.isTextual() ? reasoning.asText() : null;
        String doneReason = choices.get(0).path("finish_reason").asText("");

        return new ChatResponse(
                request.model(),
                contentText,
                thinkingText == null || thinkingText.isBlank() ? null : thinkingText,
                doneReason,
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                requestMs,
                0,
                0,
                false
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
            case 401 -> " Проверь переменную окружения с ключом DeepSeek.";
            case 404 -> " Проверь модель: " + model;
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
