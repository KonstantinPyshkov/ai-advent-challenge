# День 5. Порядок запуска

## 1. Модели

Локально нужны:

```text
qwen3.5:9b-mlx
qwen3.5:35b-a3b
```

Для DeepSeek нужен ключ в `DEEPSEEK_API_KEY` или Keychain
`deepseek-api-key`.

## 2. Проверка плана

```bash
./scripts/run.sh day5 --dry-run
```

Ожидаемый вывод:

```text
Эксперимент: День 5. Версии моделей
Модели:
  - 9B local (qwen3.5:9b-mlx)
  - DeepSeek V4.1 Flash (deepseek-flash)
  - 35B local (qwen3.5:35b-a3b)
  - DeepSeek V4 Pro (deepseek-v4-pro)
Всего прогонов: 28
```

## 3. Интерактивный запуск

```bash
./scripts/run.sh
# выбрать 5 или 05
```

## 4. Прямой запуск

```bash
./scripts/run.sh day5
```

## 5. Выходные файлы

```text
days/day_05/logs/runs.json
days/day_05/reports/report.md
days/day_05/reports/report.csv
days/day_05/conclusions/facts.md
days/day_05/conclusions/conclusion.md
```

## 6. Пересборка отчёта

```bash
./scripts/run.sh day5 --report-only
```

## 7. Диагностика

| Симптом | Причина | Действие |
|---|---|---|
| `model not found` | локальная модель не загружена | проверить `ollama list` |
| HTTP 401 | нет ключа DeepSeek | проверить env/Keychain |
| `output_limit` | модель израсходовала `maxPredict` | смотреть `checks.errors` |
| Модель недоступна | API/сеть | проверить статус провайдера |
