package ru.pyshkov.llm.core.workspace;

import ru.pyshkov.llm.core.Json;
import ru.pyshkov.llm.core.experiment.RunLog;
import ru.pyshkov.llm.core.report.Conclusion;
import ru.pyshkov.llm.core.report.Report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Каталог одного дня и стандартная иерархия его выходных файлов.
 *
 * <p>Очищаются только logs/, reports/ и conclusions/. Код, docs/, data/,
 * prompts/ и config/ не изменяются.</p>
 */
public final class DayWorkspace {

    private final Path root;
    private final Path logs;
    private final Path reports;
    private final Path conclusions;
    private final Path lock;

    public DayWorkspace(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.logs = this.root.resolve("logs");
        this.reports = this.root.resolve("reports");
        this.conclusions = this.root.resolve("conclusions");
        this.lock = this.root.resolve(".run.lock");
    }

    public Path root() {
        return root;
    }

    public Path logs() {
        return logs;
    }

    public Path reports() {
        return reports;
    }

    public Path conclusions() {
        return conclusions;
    }

    public void clean() throws IOException {
        Files.createDirectories(root);
        for (Path dir : List.of(logs, reports, conclusions)) {
            Files.createDirectories(dir);
            deleteChildren(dir);
        }
        verifyEmpty();
    }

    public void verifyEmpty() throws IOException {
        for (Path dir : List.of(logs, reports, conclusions)) {
            try (var entries = Files.list(dir)) {
                if (entries.findAny().isPresent()) {
                    throw new IOException("Каталог не пуст после очистки: " + dir);
                }
            }
        }
    }

    public void lock() throws IOException {
        try {
            Files.writeString(lock, "run", StandardCharsets.UTF_8);
        } catch (FileAlreadyExistsException e) {
            throw new IOException("Эксперимент уже выполняется: " + lock
                    + ". Если процесс упал, удали файл вручную.");
        }
    }

    public void unlock() {
        try {
            Files.deleteIfExists(lock);
        } catch (IOException ignored) {
            // не мешаем завершению
        }
    }

    public void writeRunLog(RunLog log) throws IOException {
        Json.writeAtomic(logs.resolve("runs.json"), log);
    }

    public RunLog readRunLog() throws IOException {
        var file = logs.resolve("runs.json");
        if (!Files.isRegularFile(file)) {
            throw new IOException("Журнал не найден: " + file.toAbsolutePath());
        }
        return Json.MAPPER.readValue(file.toFile(), RunLog.class);
    }

    public void writeReport(Report report) throws IOException {
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("report.md"), report.markdown(), StandardCharsets.UTF_8);
        Files.writeString(reports.resolve("report.csv"), report.csv(), StandardCharsets.UTF_8);
    }

    public void writeConclusion(Conclusion conclusion) throws IOException {
        Files.createDirectories(conclusions);
        Files.writeString(conclusions.resolve("facts.md"), conclusion.facts(), StandardCharsets.UTF_8);
        Files.writeString(conclusions.resolve("conclusion.md"),
                conclusion.conclusion(), StandardCharsets.UTF_8);
    }

    private static void deleteChildren(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(dir))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
