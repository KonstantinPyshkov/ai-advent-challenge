package ru.pyshkov.llm.core.experiment;

import java.util.List;

/** Содержимое logs/runs.json. */
public record RunLog(
        String experiment,
        String title,
        String startedAt,
        String model,
        int plannedRuns,
        List<RunResult> runs
) {
    public RunLog {
        runs = List.copyOf(runs);
    }
}
