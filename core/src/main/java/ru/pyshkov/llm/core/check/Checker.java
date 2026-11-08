package ru.pyshkov.llm.core.check;

import ru.pyshkov.llm.core.experiment.StepResult;

import java.util.List;

/** Проверка результата прогона. */
@FunctionalInterface
public interface Checker {

    CheckResult check(List<StepResult> steps);
}
