package ru.pyshkov.llm.core.report;

/** Факты и вывод по правилам, без участия LLM. */
public record Conclusion(String facts, String conclusion) {
}
