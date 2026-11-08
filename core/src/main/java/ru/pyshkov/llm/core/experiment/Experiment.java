package ru.pyshkov.llm.core.experiment;

import ru.pyshkov.llm.core.report.Conclusion;
import ru.pyshkov.llm.core.report.Report;

import java.util.List;

/** Плагин одного дня: план, отчёт и вывод. */
public interface Experiment {

    String id();

    String title();

    List<RunPlan> plans();

    Report report(List<RunResult> results);

    Conclusion conclusion(List<RunResult> results);
}
