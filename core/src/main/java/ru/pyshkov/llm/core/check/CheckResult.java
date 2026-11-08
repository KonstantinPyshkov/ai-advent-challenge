package ru.pyshkov.llm.core.check;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Результат проверки одного прогона. */
public record CheckResult(Map<String, Boolean> flags, List<String> errors) {

    public CheckResult {
        flags = Map.copyOf(new LinkedHashMap<>(flags));
        errors = List.copyOf(errors);
    }

    public static CheckResult empty() {
        return new CheckResult(Map.of(), List.of());
    }

    public boolean flag(String name) {
        return Boolean.TRUE.equals(flags.get(name));
    }
}
