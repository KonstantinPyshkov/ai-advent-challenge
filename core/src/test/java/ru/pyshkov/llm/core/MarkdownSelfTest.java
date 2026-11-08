package ru.pyshkov.llm.core;

import ru.pyshkov.llm.core.report.Markdown;

import java.util.List;

/** Проверка таблицы Markdown без JUnit. */
public final class MarkdownSelfTest {

    public static void main(String[] args) {
        String table = Markdown.table(List.of("A", "B"), List.of(List.of("1", "2")));
        if (!table.contains("| A | B |") || !table.contains("| 1 | 2 |")) {
            throw new AssertionError("unexpected table: " + table);
        }
        System.out.println("MarkdownSelfTest OK");
    }
}
