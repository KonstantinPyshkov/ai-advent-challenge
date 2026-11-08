# День 2. Формат ответа

**Модель:** `qwen3.5:35b-a3b` · **повторов:** 3 · **прогонов:** 18

## Главное

- `baseline`: JSON 0/3, обрезано 1/3.
- `format`: JSON 3/3, схема 3/3.
- `max_tokens`: обрезано 3/3, JSON 0/3.
- `stop`: JSON 0/3.
- `json_mode`: JSON 3/3, схема 0/3.
- `schema`: схема 3/3, поля 0/3.

## Результаты

| Вариант | JSON | Схема | Поля | Обрезка | avg токены | avg время |
|---|---|---|---|---|---|---|
| baseline | 0/3 | 0/3 | 0/3 | 1/3 | 3352 | 34295 мс |
| format | 3/3 | 3/3 | 0/3 | 0/3 | 797 | 5649 мс |
| max_tokens | 0/3 | 0/3 | 0/3 | 3/3 | 389 | 1301 мс |
| stop | 0/3 | 0/3 | 0/3 | 0/3 | 887 | 6467 мс |
| json_mode | 3/3 | 0/3 | 0/3 | 0/3 | 654 | 5155 мс |
| schema | 3/3 | 3/3 | 0/3 | 0/3 | 816 | 6829 мс |

## Прогоны

Обозначения: ✅ — JSON, схема и поля; 🟡 — JSON и схема, поля неверны; 🟠 — JSON без схемы; ❌ — JSON не разобран; 💥 — ошибка выполнения.

| Вариант | 1 | 2 | 3 |
|---|---|---|---|
| baseline | ❌ | ❌ | ❌ |
| format | 🟡 | 🟡 | 🟡 |
| max_tokens | ❌ | ❌ | ❌ |
| stop | ❌ | ❌ | ❌ |
| json_mode | 🟠 | 🟠 | 🟠 |
| schema | 🟡 | 🟡 | 🟡 |

## Примеры

### max_tokens, повтор 1

```json
{
  "line": "Замоскворецкая",
  "station_count": 15,
  "transfers": [
    {
      "station": "Парк Победы",
      "connections": []
    },
    {
      "station": "Кунцевская",
      "connections": []
    },
    {
      "station": "Филёвский парк",
      "connections": []
    },
    {
      "station": "Киевская",
      "
```

Проверки: JSON не разобран: Unexpected end-of-input in field name at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 19, column: 8]

### json_mode, повтор 1

```json
{
  "line_name": "Замоскворецкая линия",
  "total_stations": 20,
  "transfers": [
    {
      "station_zamoskvoretskaya": "Тушинская",
      "transfer_line_number": 6,
      "transfer_station_name": "Тушинская"
    },
    {
      "station_zamoskvoretskaya": "Алтуфьево",
      "transfer_line_number": 8,
      "transfer_station_name": "Алтуфьево"
    },
    {
      "station_zamoskvoretskaya": "Владыкино",
      "transfer_line_number": 10,
      "tr
… (сокращено)
```

Проверки: ответ.line: обязательное поле отсутствует; ответ.station_count: обязательное поле отсутствует; ответ.transfers[0].station: обязательное поле отсутствует …

### schema, повтор 1

```json
{
  "line": "Замоскворецкая",
  "station_count": 17,
  "transfers": [
    {
      "station": "Белорусская",
      "connections": [
        {"line": 5, "station": "Белорусская"},
        {"line": 6, "station": "Пушкинская"}
      ]
    },
    {
      "station": "Цветной бульвар",
      "connections": [
        {"line": 9, "station": "Тургеневская"}
      ]
    },
    {
      "station": "Тверская",
      "connections": [
        {"line": 10, "stati
… (сокращено)
```

Проверки: station_count: ожидалось 24, получено 17; нет пересадки: динамо → линия 11 → петровский парк; нет пересадки: каширская → линия 11 → каширская …

## Ограничения

- Ollama 0.40.15 не применяет `stop` в `/api/chat`: параметр отправляется, но обрезка для варианта `stop` выполнена клиентом.
- Содержание не совпало с эталоном: локальная модель ошиблась в фактах. Это ограничение знаний модели, а не формата.
- Полные ответы и проверки: [logs/runs.json](../logs/runs.json).
