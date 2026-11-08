package ru.pyshkov.llm.day05;

import java.util.List;

/** Задача с эталонным ответом. */
public record Day05Task(
        String id,
        String title,
        String prompt,
        String expected,
        String check,
        List<String> accepted
) {
    public Day05Task {
        accepted = List.copyOf(accepted);
    }
}
