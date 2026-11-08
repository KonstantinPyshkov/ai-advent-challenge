package ru.pyshkov.llm.core.report;

/** Готовый отчёт: файлы Markdown/CSV и краткий итог для консоли. */
public record Report(String markdown, String csv, String console) {
}
