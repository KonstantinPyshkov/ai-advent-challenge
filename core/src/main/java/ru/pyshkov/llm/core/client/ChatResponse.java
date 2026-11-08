package ru.pyshkov.llm.core.client;

/** Ответ модели и метрики одного HTTP-вызова. */
public record ChatResponse(
        String model,
        String content,
        String thinking,
        String doneReason,
        int promptTokens,
        int completionTokens,
        long requestMs,
        long serverTotalMs,
        long loadMs,
        boolean stopEmulated
) {
    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
