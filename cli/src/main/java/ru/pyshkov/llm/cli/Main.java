package ru.pyshkov.llm.cli;

import ru.pyshkov.llm.core.client.OllamaClient;
import ru.pyshkov.llm.core.config.LabConfig;
import ru.pyshkov.llm.core.experiment.Experiment;
import ru.pyshkov.llm.core.experiment.RunPlan;
import ru.pyshkov.llm.core.experiment.RunLog;
import ru.pyshkov.llm.core.experiment.RunResult;
import ru.pyshkov.llm.core.experiment.Runner;
import ru.pyshkov.llm.core.report.Conclusion;
import ru.pyshkov.llm.core.report.Report;
import ru.pyshkov.llm.core.workspace.DayWorkspace;
import ru.pyshkov.llm.day02.Day02Experiment;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Точка входа лаборатории. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 0 || !"day2".equals(args[0])) {
            usage();
            System.exit(2);
        }

        Path dayDir = Path.of("days/day_02");
        LabConfig config = LabConfig.load(dayDir.resolve("config/experiment.properties"));
        Options options = Options.parse(args, config);

        Day02Experiment experiment = new Day02Experiment(
                dayDir, config, options.repeats(), options.model());
        List<RunPlan> plans = experiment.plans();

        if (options.dryRun()) {
            printPlan(experiment, plans, options);
            return;
        }

        DayWorkspace workspace = new DayWorkspace(dayDir);
        if (options.reportOnly()) {
            RunLog log = workspace.readRunLog();
            Report report = experiment.report(log.runs());
            Conclusion conclusion = experiment.conclusion(log.runs());
            workspace.writeReport(report);
            workspace.writeConclusion(conclusion);
            System.out.println(report.console());
            System.out.println("Отчёт: " + workspace.reports().resolve("report.md").toAbsolutePath());
            System.out.println("Вывод: "
                    + workspace.conclusions().resolve("conclusion.md").toAbsolutePath());
            return;
        }
        workspace.lock();
        try {
            workspace.clean();
            OllamaClient client = new OllamaClient(
                    config.baseUrl(), Duration.ofSeconds(config.timeoutSeconds()));
            Runner runner = new Runner(client, config, workspace);
            List<RunResult> results = runner.run(experiment.id(), experiment.title(), plans);

            Report report = experiment.report(results);
            Conclusion conclusion = experiment.conclusion(results);
            workspace.writeReport(report);
            workspace.writeConclusion(conclusion);

            System.out.println();
            System.out.print(report.console());
            System.out.println();
            System.out.println("Готово: " + results.size() + " прогонов");
            System.out.println("Журнал: " + workspace.logs().resolve("runs.json").toAbsolutePath());
            System.out.println("Отчёт: " + workspace.reports().resolve("report.md").toAbsolutePath());
            System.out.println("Вывод: "
                    + workspace.conclusions().resolve("conclusion.md").toAbsolutePath());
        } finally {
            workspace.unlock();
        }
    }

    private static void printPlan(Experiment experiment, List<RunPlan> plans, Options options) {
        System.out.println("Эксперимент: " + experiment.title());
        System.out.println("Модель: " + options.model());
        System.out.println("Повторов на вариант: " + options.repeats());
        System.out.println("Всего прогонов: " + plans.size());
        System.out.println("Варианты:");
        plans.stream()
                .map(RunPlan::variantId)
                .distinct()
                .forEach(variant -> System.out.println("  - " + variant));
    }

    private static void usage() {
        System.out.println("""
                Лаборатория AI Advent Challenge.

                Использование:
                  lab day2 [--model M] [--repeats N] [--dry-run] [--report-only]

                Флаги:
                  --model M       переопределить модель
                  --repeats N     количество повторов на вариант
                  --dry-run       показать план без запуска
                  --report-only   пересобрать отчёт из logs/runs.json без запросов
                """);
    }

    private record Options(String model, int repeats, boolean dryRun, boolean reportOnly) {

        static Options parse(String[] args, LabConfig config) {
            String model = config.model();
            int repeats = config.repeats();
            boolean dryRun = false;
            boolean reportOnly = false;

            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "--model" -> model = next(args, ++i, "--model");
                    case "--repeats" -> repeats = Integer.parseInt(next(args, ++i, "--repeats"));
                    case "--dry-run" -> dryRun = true;
                    case "--report-only" -> reportOnly = true;
                    default -> throw new IllegalArgumentException("Неизвестный флаг: " + args[i]);
                }
            }

            if (repeats < 1) {
                throw new IllegalArgumentException("Количество повторов должно быть больше нуля");
            }
            return new Options(model, repeats, dryRun, reportOnly);
        }

        private static String next(String[] args, int index, String flag) {
            if (index >= args.length || args[index].isBlank()) {
                throw new IllegalArgumentException("После " + flag + " нужен аргумент");
            }
            return args[index];
        }
    }
}
