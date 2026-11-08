package ru.pyshkov.llm.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Настройки одного эксперимента. */
public record LabConfig(
        String baseUrl,
        String model,
        int timeoutSeconds,
        int repeats,
        int retries,
        long delayMs
) {

    public static LabConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Не найден файл настроек: " + file.toAbsolutePath());
        }

        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        }

        return new LabConfig(
                text(props, "llm.base-url", "http://127.0.0.1:11434"),
                text(props, "llm.model", "qwen3.5:35b-a3b"),
                number(props, "llm.timeout-seconds", 300),
                number(props, "runner.repeats", 5),
                number(props, "runner.retries", 2),
                number(props, "runner.delay-ms", 0)
        );
    }

    public LabConfig withModel(String value) {
        return new LabConfig(baseUrl, value, timeoutSeconds, repeats, retries, delayMs);
    }

    public LabConfig withRepeats(int value) {
        return new LabConfig(baseUrl, model, timeoutSeconds, value, retries, delayMs);
    }

    private static String text(Properties props, String key, String fallback) {
        String value = props.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static int number(Properties props, String key, int fallback) {
        String value = props.getProperty(key);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
    }
}
