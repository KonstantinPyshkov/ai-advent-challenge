# День 3. Порядок запуска

## 1. Ключ

Ключ не хранится в репозитории. Для `--provider ollama` он не требуется.

Для DeepSeek ключ ищется в `DEEPSEEK_API_KEY`, затем в macOS Keychain
(`deepseek-api-key`). Если он уже сохранён в Keychain, экспорт не нужен:

```bash
export DEEPSEEK_API_KEY="..."
```

## 2. Выбор модели

В `config/experiment.properties`:

```properties
llm.model=deepseek-flash
llm.model-label=DeepSeek-V4.1-Flash
```

`deepseek-flash` — API-идентификатор; `DeepSeek-V4.1-Flash` — отображаемое
имя. Разовая замена провайдера и API-модели:

```bash
./scripts/run.sh day3 --provider deepseek --model deepseek-flash
./scripts/run.sh day3 --provider ollama --model qwen3.5:35b-a3b
```

Если `--provider` указан без `--model`, используется модель по умолчанию
для этого провайдера.

## 3. Интерактивный запуск

```bash
./scripts/run.sh
```

Выбери `3` или `03`, затем модель из списка. Для Дня 3 доступны 2B, 9B,
35B (Ollama) и `DeepSeek-V4.1-Flash` (DeepSeek API).

## 4. Проверка плана

```bash
./scripts/run.sh day3 --dry-run
```

Ожидаемый вывод:

```text
Эксперимент: День 3. Разные способы рассуждения
Провайдер: deepseek
Модель: deepseek-flash
Повторов на вариант: 1
Всего прогонов: 40
Варианты:
  - direct/off
  - direct/on
  - step_by_step/off
  - step_by_step/on
  - prompt_then_solve/off
  - prompt_then_solve/on
  - panel/off
  - panel/on
```

Всего API-вызовов: 50, потому что `prompt_then_solve` делает два шага.

## 5. Полный запуск

```bash
./scripts/run.sh day3
```

Скрипт собирает `core`, `days/day_03` и `cli`, затем запускает `lab.jar`.
Перед прогонами core очищает `logs/`, `reports/`, `conclusions/`, ставит
lock, делает прогрев и выполняет 40 прогонов.

## 6. Консольный итог

После прогонов выводятся:

- таблица `способ × рассуждение → верных / avg токены / avg время`;
- матрица по задачам;
- краткий вывод об эффектах способов.

## 7. Выходные файлы

```text
days/day_03/logs/runs.json
days/day_03/reports/report.md
days/day_03/reports/report.csv
days/day_03/conclusions/facts.md
days/day_03/conclusions/conclusion.md
```

## 8. Пересборка отчёта без запросов

```bash
./scripts/run.sh day3 --report-only
```

Читает `logs/runs.json` и заново собирает отчёт, вывод и консольный итог.

## 9. Диагностика

| Симптом | Причина | Действие |
|---|---|---|
| `output_limit` | модель израсходовала `maxPredict` на reasoning | зафиксировать; при необходимости отдельный прогон с большим лимитом |
| `context_overflow` | prompt + completion достигли `numCtx` | зафиксировать; увеличить `numCtx` только для отдельного прогона |
| `empty_content` | модель завершила ответ без текста | зафиксировать, не ретраить |
| `Эксперимент уже выполняется` | остался `.run.lock` | удалить после проверки процесса |
| Нет маркера `ОТВЕТ:` | модель не последовала формату | смотреть `checks.errors` в журнале |
| Таймаут | DeepSeek не отвечает | проверить сеть и `llm.timeout-seconds` |
