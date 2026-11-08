# День 1. Порядок запуска

## 1. Проверка окружения

```bash
java -version
ollama --version
ollama list
```

Требуется модель `qwen3.5:2b-mlx`, предварительно загруженная в Ollama.

## 2. Сборка

Из корня репозитория:

```bash
mvn package
```

На macOS при отсутствии Maven в `PATH`:

```bash
/opt/homebrew/bin/mvn package
```

## 3. Запуск

```bash
./scripts/run.sh "Перечисли три преимущества локального запуска LLM."
```

Скрипт собирает модуль и запускает `days/day_01/target/day01.jar`.
Прямой запуск:

```bash
java -jar days/day_01/target/day01.jar "Перечисли три преимущества локального запуска LLM."
```

Допускается запуск из корня репозитория и из каталога `days/day_01`.

## 4. Результат

Ответ модели выводится в стандартный поток вывода. Путь к журналу
выводится последней строкой:

```text
Журнал: /.../days/day_01/logs/day01.json
```

## 5. Структура журнала

```json
{
  "runId": "day01-20261108T144449",
  "experiment": "day01",
  "status": "success",
  "startedAt": "...",
  "finishedAt": "...",
  "model": "qwen3.5:2b-mlx",
  "baseUrl": "http://127.0.0.1:11434",
  "request": {
    "messages": [],
    "stream": false,
    "think": false,
    "options": {}
  },
  "response": {
    "content": "...",
    "doneReason": "stop"
  },
  "timing": {
    "requestMs": 661,
    "serverTotalMs": 632,
    "loadMs": 1
  },
  "tokens": {
    "prompt": 60,
    "completion": 87
  }
}
```

При ошибке поля `response`, `timing` и `tokens` отсутствуют; добавляются
`status: "error"` и `error` с текстом ошибки.

## 6. Очистка и повторный запуск

Перед запросом удаляются только файлы `*.json` и `*.tmp` в
`days/day_01/logs/`. Каталоги `docs/`, `prompts/`, `config/` и `src/`
не изменяются.

## 7. Диагностика

| Симптом | Причина | Действие |
|---|---|---|
| `Connection refused` | Сервис Ollama не запущен | Запустить `ollama serve` |
| `HTTP 404: model ... not found` | Модель не загружена | Загрузить модель `qwen3.5:2b-mlx` в Ollama |
| Таймаут | Модель загружается или не отвечает | Проверить `ollama ps`, увеличить `llm.timeout-seconds` |
| Пустой ответ | Модель вернула только блок рассуждения | Проверить значение `think` и выбранную модель |
