# День 4. Порядок запуска

## 1. Модель

Нужна локальная модель:

```text
qwen3.5:9b-mlx
```

Модель фиксирована в `config/experiment.properties`; в меню выбор модели
для Дня 4 не показывается.

## 2. Проверка плана

```bash
./scripts/run.sh day4 --dry-run
```

Ожидаемый вывод:

```text
Провайдер: ollama
Модель: qwen3.5:9b-mlx
Повторов на вариант: 5
Всего прогонов: 30
```

## 3. Интерактивный запуск

```bash
./scripts/run.sh
# выбрать 4 или 04
```

## 4. Прямой запуск

```bash
./scripts/run.sh day4
```

Перед прогонами core прогревает модель, очищает `logs/reports/conclusions`
и выполняет 30 прогонов.

## 5. Выходные файлы

```text
days/day_04/logs/runs.json
days/day_04/reports/report.md
days/day_04/reports/report.csv
days/day_04/conclusions/facts.md
days/day_04/conclusions/conclusion.md
```

## 6. Пересборка отчёта

```bash
./scripts/run.sh day4 --report-only
```

Читает `logs/runs.json`, модель и число повторов берёт из журнала.

## 7. Диагностика

| Симптом | Причина | Действие |
|---|---|---|
| Модель не найдена | `qwen3.5:9b-mlx` не загружена | выбрать другую локальную модель или загрузить её |
| Пустой `content` | модель не выдала финальный текст | смотреть `checks.errors` и `Failures` |
| Формат generation `< 5` строк | модель добавила пояснения | смотреть примеры в отчёте |
| Таймаут | модель загружается | увеличить `llm.timeout-seconds` |
