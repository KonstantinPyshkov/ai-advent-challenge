package ru.pyshkov.llm.core.experiment;

import ru.pyshkov.llm.core.check.Checker;

import java.util.List;
import java.util.Map;

/** Описание одного прогона до его выполнения. */
public record RunPlan(
        String caseId,
        String variantId,
        int repeat,
        List<Step> steps,
        Map<String, String> labels,
        Checker checker
) {
    public RunPlan {
        steps = List.copyOf(steps);
        labels = Map.copyOf(labels);
    }
}
