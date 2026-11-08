package ru.pyshkov.llm.core.experiment;

import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;

import java.util.Locale;

/** Классификация технических сбоев прогона. */
public final class Failures {

    private Failures() {
    }

    public static String classify(StepResult step) {
        if (step == null) {
            return "unknown";
        }
        ChatRequest request = step.request();
        if (request == null) {
            return "build";
        }
        ChatResponse response = step.response();
        if (response == null) {
            String error = step.error() == null ? "" : step.error().toLowerCase(Locale.ROOT);
            if (error.contains("timeout") || error.contains("timed out")) {
                return "timeout";
            }
            if (error.contains("http ")) {
                return "http_error";
            }
            return "transport";
        }

        if (response.content() == null || response.content().isBlank()) {
            if ("length".equals(response.doneReason())) {
                int used = response.promptTokens() + response.completionTokens();
                if (used >= request.numCtx()) {
                    return "context_overflow";
                }
                if (response.completionTokens() >= request.maxPredict()) {
                    return "output_limit";
                }
                return "length";
            }
            return "empty_content";
        }

        if ("length".equals(response.doneReason())) {
            return "truncated";
        }
        return "none";
    }

    public static String describe(ChatResponse response, ChatRequest request) {
        if (response == null) {
            return "нет ответа";
        }
        return "done=" + value(response.doneReason())
                + ", prompt=" + response.promptTokens()
                + ", completion=" + response.completionTokens()
                + ", num_ctx=" + (request == null ? "—" : request.numCtx())
                + ", max_predict=" + (request == null ? "—" : request.maxPredict());
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
