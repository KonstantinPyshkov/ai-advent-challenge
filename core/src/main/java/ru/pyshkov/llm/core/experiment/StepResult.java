package ru.pyshkov.llm.core.experiment;

import ru.pyshkov.llm.core.client.ChatRequest;
import ru.pyshkov.llm.core.client.ChatResponse;

/** Результат одного шага. */
public record StepResult(
        String label,
        ChatRequest request,
        ChatResponse response,
        String error,
        long wallClockMs
) {
}
