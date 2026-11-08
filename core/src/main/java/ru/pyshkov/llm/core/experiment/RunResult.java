package ru.pyshkov.llm.core.experiment;

import ru.pyshkov.llm.core.check.CheckResult;

import java.util.List;
import java.util.Map;

/** Полный результат одного прогона. */
public record RunResult(
        String caseId,
        String variantId,
        int repeat,
        Map<String, String> labels,
        List<StepResult> steps,
        CheckResult check,
        boolean success,
        String error,
        long wallClockMs
) {
    public RunResult {
        labels = Map.copyOf(labels);
        steps = List.copyOf(steps);
    }
}
