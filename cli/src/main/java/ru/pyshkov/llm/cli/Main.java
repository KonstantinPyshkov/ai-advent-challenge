package ru.pyshkov.llm.cli;

import ru.pyshkov.llm.core.client.ChatModel;
import ru.pyshkov.llm.core.client.DeepSeekClient;
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
import ru.pyshkov.llm.day03.Day03Experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
        if (args.length == 0) {
            usage();
            System.exit(2);
        }

        String day = args[0];
        Path dayDir = switch (day) {
            case "day2" -> Path.of("days/day_02");
            case "day3" -> Path.of("days/day_03");
            default -> null;
        };
        if (dayDir == null) {
            usage();
            System.exit(2);
        }

        LabConfig config = LabConfig.load(dayDir.resolve("config/experiment.properties"));
        Options options = Options.parse(args, config);
        Experiment experiment = switch (day) {
            case "day2" -> new Day02Experiment(
                    dayDir, config, options.repeats(), options.model());
            case "day3" -> new Day03Experiment(
                    dayDir, config, options.repeats(), options.model());
            default -> throw new IllegalStateException("Неизвестный день: " + day);
        };
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
        ChatModel client = createModel(config, options.provider());
        workspace.lock();
        try {
            workspace.clean();
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
        System.out.println("Провайдер: " + options.provider());
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
                  lab day2|day3 [--provider ollama|deepseek] [--model M] [--repeats N] [--dry-run] [--report-only]

                Флаги:
                  --provider P    ollama или deepseek (по умолчанию из конфига)
                  --model M       переопределить API-модель
                  --repeats N     количество повторов на вариант
                  --dry-run       показать план без запуска
                  --report-only   пересобрать отчёт из logs/runs.json без запросов
                """);
    }

    private static ChatModel createModel(LabConfig config, String provider) {
        String baseUrl = provider.equals(config.provider())
                ? config.baseUrl()
                : defaultBaseUrl(provider);
        return switch (provider) {
            case "ollama" -> new OllamaClient(
                    baseUrl, Duration.ofSeconds(config.timeoutSeconds()));
            case "deepseek" -> new DeepSeekClient(
                    baseUrl, apiKey(config), Duration.ofSeconds(config.timeoutSeconds()));
            default -> throw new IllegalStateException(
                    "Неизвестный провайдер: " + provider);
        };
    }

    private static String defaultBaseUrl(String provider) {
        return switch (provider) {
            case "ollama" -> "http://127.0.0.1:11434";
            case "deepseek" -> "https://api.deepseek.com";
            default -> throw new IllegalStateException("Неизвестный провайдер: " + provider);
        };
    }

    private static String defaultModel(String provider) {
        return switch (provider) {
            case "ollama" -> "qwen3.5:35b-a3b";
            case "deepseek" -> "deepseek-flash";
            default -> throw new IllegalStateException("Неизвестный провайдер: " + provider);
        };
    }

    private static String apiKey(LabConfig config) {
        String value = System.getenv(config.apiKeyEnv());
        if (value != null && !value.isBlank()) {
            return value;
        }
        String keychain = keychainKey("deepseek-api-key");
        if (keychain != null && !keychain.isBlank()) {
            return keychain;
        }
        throw new IllegalStateException(
                "Не задан ключ DeepSeek: переменная " + config.apiKeyEnv()
                        + " или Keychain deepseek-api-key");
    }

    private static String keychainKey(String service) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac")) {
            return null;
        }
        String user = System.getenv("USER");
        if (user == null || user.isBlank()) {
            user = System.getProperty("user.name", "");
        }
        try {
            Process process = new ProcessBuilder(
                    "security", "find-generic-password",
                    "-a", user, "-s", service, "-w")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(
                    process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? output : null;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private record Options(String provider, String model, int repeats,
                           boolean dryRun, boolean reportOnly) {

        static Options parse(String[] args, LabConfig config) {
            String provider = config.provider();
            String model = config.model();
            boolean modelSpecified = false;
            int repeats = config.repeats();
            boolean dryRun = false;
            boolean reportOnly = false;

            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "--provider" -> provider = next(args, ++i, "--provider");
                    case "--model" -> {
                        model = next(args, ++i, "--model");
                        modelSpecified = true;
                    }
                    case "--repeats" -> repeats = Integer.parseInt(next(args, ++i, "--repeats"));
                    case "--dry-run" -> dryRun = true;
                    case "--report-only" -> reportOnly = true;
                    default -> throw new IllegalArgumentException("Неизвестный флаг: " + args[i]);
                }
            }

            if (!provider.equals("ollama") && !provider.equals("deepseek")) {
                throw new IllegalArgumentException("Неизвестный провайдер: " + provider);
            }
            if (!provider.equals(config.provider()) && !modelSpecified) {
                model = defaultModel(provider);
            }
            if (repeats < 1) {
                throw new IllegalArgumentException("Количество повторов должно быть больше нуля");
            }
            return new Options(provider, model, repeats, dryRun, reportOnly);
        }

        private static String next(String[] args, int index, String flag) {
            if (index >= args.length || args[index].isBlank()) {
                throw new IllegalArgumentException("После " + flag + " нужен аргумент");
            }
            return args[index];
        }
    }
}
