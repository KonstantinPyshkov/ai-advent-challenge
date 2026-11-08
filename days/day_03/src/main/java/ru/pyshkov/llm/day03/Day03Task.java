package ru.pyshkov.llm.day03;

import java.util.List;

/** Одна задача с эталонным ответом. */
public record Day03Task(
        String id,
        String category,
        String prompt,
        String expected,
        String check,
        List<String> accepted
) {
    public Day03Task {
        accepted = List.copyOf(accepted);
    }
}
