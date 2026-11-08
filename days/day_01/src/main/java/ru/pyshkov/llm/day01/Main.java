package ru.pyshkov.llm.day01;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * День 1: минимальный код, который отправляет один запрос в LLM через API,
 * получает ответ и печатает его в консоль.
 *
 * <p>Здесь нет истории, стриминга, сессий, веба и core. Это эталон
 * «одного запроса».</p>
 */
public final class Main {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Main() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (ConnectException e) {
            System.err.println("Не удалось подключиться к Ollama. Запусти `ollama serve` и проверь llm.base-url.");
            System.exit(1);
        } catch (HttpTimeoutException e) {
            System.err.println("Ollama не ответила за отведённое время. Попробуй ещё раз или увеличь llm.timeout-seconds.");
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Запрос прерван.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Path dayDir = resolveDayDir();
        Config config = Config.load(dayDir.resolve("config/day.properties"));
        Cli cli = Cli.parse(args, config);

        if (cli.help()) {
            printUsage();
            return;
        }
        if (cli.prompt().isBlank()) {
            System.err.println("Не задан вопрос.");
            printUsage();
            System.exit(2);
        }

        String systemPrompt = Files.readString(
                dayDir.resolve("prompts/system.md"), StandardCharsets.UTF_8).strip();
        Path logsDir = dayDir.resolve("logs");
        cleanLogs(logsDir);
        Files.createDirectories(logsDir);

        OffsetDateTime startedAt = OffsetDateTime.now();
        String runId = "day01-" + startedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
        Path logFile = logsDir.resolve("day01.json");

        OllamaClient client = new OllamaClient(
                cli.baseUrl(), Duration.ofSeconds(config.timeoutSeconds()));

        OllamaClient.Reply reply;
        try {
            reply = client.chat(cli.model(), systemPrompt, cli.prompt(), config.think());
        } catch (Exception e) {
            try {
                writeErrorLog(logFile, runId, startedAt, cli.model(), cli.baseUrl(),
                        systemPrompt, cli.prompt(), config.think(), e);
            } catch (IOException logError) {
                e.addSuppressed(logError);
            }
            throw e;
        }

        System.out.println(reply.content());
        System.out.println();

        writeLog(logFile, runId, startedAt, cli.model(), cli.baseUrl(),
                systemPrompt, cli.prompt(), config.think(), reply);

        System.out.println("Журнал: " + logFile.toAbsolutePath());
    }

    /**
     * Позволяет запускать и из корня репозитория, и из каталога дня.
     */
    private static Path resolveDayDir() throws IOException {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.isRegularFile(cwd.resolve("config/day.properties"))) {
            return cwd;
        }

        Path fromRoot = cwd.resolve("days/day_01");
        if (Files.isRegularFile(fromRoot.resolve("config/day.properties"))) {
            return fromRoot;
        }

        throw new IOException("Не найден days/day_01/config/day.properties. "
                + "Запустите команду из корня репозитория.");
    }

    /**
     * Второй запуск дня не должен оставлять журнал предыдущего запуска.
     * Чистим только сгенерированные файлы, .gitkeep не трогаем.
     */
    private static void cleanLogs(Path logsDir) throws IOException {
        if (!Files.isDirectory(logsDir)) {
            return;
        }
        try (var files = Files.list(logsDir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".json") || name.endsWith(".tmp")) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static ObjectNode baseLog(
            String runId,
            OffsetDateTime startedAt,
            String model,
            String baseUrl,
            String systemPrompt,
            String prompt,
            boolean think
    ) {
        ObjectNode root = JSON.createObjectNode();
        root.put("runId", runId);
        root.put("experiment", "day01");
        root.put("startedAt", startedAt.toString());
        root.put("model", model);
        root.put("baseUrl", baseUrl);

        ObjectNode request = root.putObject("request");
        ArrayNode messages = request.putArray("messages");
        if (!systemPrompt.isBlank()) {
            messages.addObject()
                    .put("role", "system")
                    .put("content", systemPrompt);
        }
        messages.addObject()
                .put("role", "user")
                .put("content", prompt);
        request.put("stream", false);
        request.put("think", think);

        ObjectNode options = request.putObject("options");
        options.put("temperature", 0.0);
        options.put("num_predict", 1024);
        options.put("num_ctx", 8192);
        return root;
    }

    private static void writeLog(
            Path target,
            String runId,
            OffsetDateTime startedAt,
            String model,
            String baseUrl,
            String systemPrompt,
            String prompt,
            boolean think,
            OllamaClient.Reply reply
    ) throws IOException {

        ObjectNode root = baseLog(runId, startedAt, model, baseUrl, systemPrompt, prompt, think);
        root.put("status", "success");
        root.put("finishedAt", OffsetDateTime.now().toString());

        ObjectNode response = root.putObject("response");
        response.put("content", reply.content());
        if (reply.thinking() != null) {
            response.put("thinking", reply.thinking());
        }
        response.put("doneReason", reply.doneReason());

        ObjectNode timing = root.putObject("timing");
        timing.put("requestMs", reply.requestMs());
        timing.put("serverTotalMs", reply.serverTotalMs());
        timing.put("loadMs", reply.loadMs());

        ObjectNode tokens = root.putObject("tokens");
        tokens.put("prompt", reply.promptTokens());
        tokens.put("completion", reply.completionTokens());

        writeJson(target, root);
    }

    private static void writeErrorLog(
            Path target,
            String runId,
            OffsetDateTime startedAt,
            String model,
            String baseUrl,
            String systemPrompt,
            String prompt,
            boolean think,
            Exception error
    ) throws IOException {

        ObjectNode root = baseLog(runId, startedAt, model, baseUrl, systemPrompt, prompt, think);
        root.put("status", "error");
        root.put("finishedAt", OffsetDateTime.now().toString());
        root.put("error", error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage());
        writeJson(target, root);
    }

    private static void writeJson(Path target, ObjectNode root) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void printUsage() {
        System.out.println("""
                День 1 — один запрос в локальный Ollama.

                Использование:
                  day01 [--model M] [--base-url URL] "вопрос"

                Примеры:
                  ./scripts/run.sh "Привет! Кто ты?"
                  ./scripts/run.sh --model qwen3.5:2b-mlx "Сколько будет 17 × 23?"

                Флаги:
                  --model M        переопределить модель из config/day.properties
                  --base-url URL   переопределить адрес Ollama
                  -h, --help       эта справка
                """);
    }

    private record Config(String baseUrl, String model, boolean think, int timeoutSeconds) {

        static Config load(Path file) throws IOException {
            if (!Files.isRegularFile(file)) {
                throw new IOException("Не найден " + file.toAbsolutePath());
            }

            Properties props = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }

            String baseUrl = get(props, "llm.base-url", "http://127.0.0.1:11434");
            String model = get(props, "llm.model", "qwen3.5:2b-mlx");
            boolean think = Boolean.parseBoolean(get(props, "llm.think", "false"));
            int timeout = Integer.parseInt(get(props, "llm.timeout-seconds", "300"));
            return new Config(baseUrl, model, think, timeout);
        }

        private static String get(Properties props, String key, String fallback) {
            String value = props.getProperty(key);
            return value == null || value.isBlank() ? fallback : value.strip();
        }
    }

    private record Cli(String model, String baseUrl, String prompt, boolean help) {

        static Cli parse(String[] args, Config config) {
            String model = config.model();
            String baseUrl = config.baseUrl();
            boolean help = false;
            List<String> promptParts = new ArrayList<>();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h", "--help" -> help = true;
                    case "--model" -> model = next(args, ++i, "--model");
                    case "--base-url" -> baseUrl = next(args, ++i, "--base-url");
                    default -> {
                        if (arg.startsWith("-")) {
                            throw new IllegalArgumentException("Неизвестный флаг: " + arg);
                        }
                        promptParts.add(arg);
                    }
                }
            }

            return new Cli(model, baseUrl, String.join(" ", promptParts).strip(), help);
        }

        private static String next(String[] args, int index, String flag) {
            if (index >= args.length || args[index].isBlank()) {
                throw new IllegalArgumentException("После " + flag + " нужен аргумент");
            }
            return args[index];
        }
    }
}
