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

/** Клиент нативного API Ollama. */
public final class OllamaClient implements ChatModel {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String baseUrl;
    private final Duration timeout;

    public OllamaClient(String baseUrl, Duration timeout) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws IOException, InterruptedException {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", request.model());

        ArrayNode messages = body.putArray("messages");
        for (Message message : request.messages()) {
            messages.addObject()
                    .put("role", message.role())
                    .put("content", message.content());
        }

        body.put("stream", false);
        body.put("think", request.think());

        ObjectNode options = body.putObject("options");
        options.put("temperature", request.temperature());
        options.put("num_predict", request.maxPredict());
        options.put("num_ctx", request.numCtx());
        if (!request.stop().isEmpty()) {
            ArrayNode stop = options.putArray("stop");
            request.stop().forEach(stop::add);
        }

        if (request.schema() != null) {
            body.set("format", request.schema());
        } else if (request.jsonMode()) {
            body.put("format", "json");
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
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
        JsonNode content = root.path("message").path("content");
        JsonNode thinking = root.path("message").path("thinking");
        long totalNs = root.path("total_duration").asLong(0);
        long loadNs = root.path("load_duration").asLong(0);

        String contentText = content.isTextual() ? content.asText() : "";
        boolean stopEmulated = false;
        if (request.emulateStop() && !request.stop().isEmpty()) {
            int cut = -1;
            for (String stop : request.stop()) {
                int index = contentText.indexOf(stop);
                if (index >= 0 && (cut < 0 || index < cut)) {
                    cut = index;
                }
            }
            if (cut >= 0) {
                contentText = contentText.substring(0, cut);
                stopEmulated = true;
            }
        }

        return new ChatResponse(
                request.model(),
                contentText,
                thinking.isTextual() && !thinking.asText().isBlank() ? thinking.asText() : null,
                root.path("done_reason").asText(""),
                root.path("prompt_eval_count").asInt(0),
                root.path("eval_count").asInt(0),
                requestMs,
                totalNs / 1_000_000,
                loadNs / 1_000_000,
                stopEmulated
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
