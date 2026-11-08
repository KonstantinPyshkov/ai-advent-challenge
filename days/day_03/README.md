# День 3. Разные способы рассуждения

## Назначение

Пять задач с известными ответами решаются четырьмя способами при двух
режимах рассуждения. Сравниваются правильность, токены и время.
Модель — DeepSeek `DeepSeek-V4.1-Flash` (API id: `deepseek-flash`) через
OpenAI-совместимый API.

Способы:

- `direct` — прямой ответ;
- `step_by_step` — пошаговое решение;
- `prompt_then_solve` — сначала составляется промпт, затем по нему решается задача;
- `panel` — один вызов с ролями аналитика, инженера и критика.

Режимы:

- `off` — `thinking.type=disabled`;
- `on` — `thinking.type=enabled`, `reasoning_effort=high`.

## Задачи

| № | Тип | Задача | Ответ |
|---|---|---|---|
| 1 | Арифметика | 17 × 23 | 391 |
| 2 | Логика | Все коты — животные; некоторые животные пушистые | нет |
| 3 | Алгоритм | Сумма 1 + 2 + 3 + 4 | 10 |
| 4 | Календарь | День недели 1 января 2030 года | вторник |
| 5 | Данные | Среднее 120, 80, 100 | 100 |

## Запуск

Для DeepSeek нужен ключ (для `--provider ollama` не нужен). Он берётся
из `DEEPSEEK_API_KEY` или из macOS Keychain `deepseek-api-key`; экспорт
не обязателен:

```bash
export DEEPSEEK_API_KEY="..."
```

Запуск из корня репозитория — интерактивно:

```bash
./scripts/run.sh
```

Выбери `3` или `03`, затем модель из списка.

Прямой запуск:

```bash
./scripts/run.sh day3
```

Проверка плана без запросов:

```bash
./scripts/run.sh day3 --dry-run
```

Пересборка отчёта и консольного итога из сохранённого журнала:

```bash
./scripts/run.sh day3 --report-only
```

## Артефакты

```text
logs/runs.json              все 40 прогонов
reports/report.md           сводка, матрица задач, примеры
reports/report.csv          сводка для таблиц
conclusions/facts.md        метрики по способам и режимам
conclusions/conclusion.md   вывод по правилам
```

## Выбор модели

Модель задаётся в `config/experiment.properties`:

```properties
llm.model=deepseek-flash
llm.model-label=DeepSeek-V4.1-Flash
```

`llm.model` — идентификатор модели в API, `llm.model-label` — отображаемое
имя в отчёте. Для разового запуска можно выбрать провайдера и API-модель:

```bash
./scripts/run.sh day3 --provider deepseek --model deepseek-flash
./scripts/run.sh day3 --provider ollama --model qwen3.5:35b-a3b
```

Если `--provider` меняется без `--model`, берётся модель по умолчанию:
`deepseek-flash` для DeepSeek или `qwen3.5:35b-a3b` для Ollama.

## Ограничения

- `panel` — один вызов с ролями, а не три независимых агента.
- В `prompt_then_solve` оба шага выполняются в одном режиме рассуждения.
- В `direct` добавлен только маркер ответа; пошаговых и ролевых инструкций
  нет.
- Модель не имеет доступа в интернет.
- Повторы не используются: ТЗ Дня 3 их не требует.

## Документация

- [Техническое задание](docs/01-task.md)
- [Дизайн эксперимента](docs/02-design.md)
- [Порядок запуска](docs/03-run.md)
- [Результат](docs/04-result.md)
