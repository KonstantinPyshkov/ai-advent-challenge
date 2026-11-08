package ru.pyshkov.llm.core.experiment;

import ru.pyshkov.llm.core.client.ChatRequest;

/** Один шаг прогона: обычно один запрос. */
public record Step(String label, ChatRequest request) {
}
