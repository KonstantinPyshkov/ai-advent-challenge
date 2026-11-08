#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MVN="${MVN:-}"
if [ -z "$MVN" ]; then
  if command -v mvn >/dev/null 2>&1; then
    MVN="mvn"
  elif [ -x /opt/homebrew/bin/mvn ]; then
    MVN="/opt/homebrew/bin/mvn"
  else
    echo "Maven не найден. Установи Maven или задай MVN=/путь/к/mvn." >&2
    exit 1
  fi
fi

interactive() {
  local day choice question
  while true; do
    cat <<'EOF'
Доступные эксперименты:

  1) День 1 — минимальный запрос к Ollama (модель 2B, без выбора модели)
  2) День 2 — контроль формата ответа (модель 35B, без выбора модели)
  3) День 3 — способы рассуждения (4 модели на выбор)
  4) День 4 — температура (модель 9B, без выбора модели)
  5) День 5 — версии моделей (4 модели, без выбора модели)

EOF
    printf "Выберите день: "
    if ! read -r choice; then
      echo
      exit 0
    fi

    case "$choice" in
      1|01|day1|day_01) day="day1"; break ;;
      2|02|day2|day_02) day="day2"; break ;;
      3|03|day3|day_03) day="day3"; break ;;
      4|04|day4|day_04) day="day4"; break ;;
      5|05|day5|day_05) day="day5"; break ;;
      *) echo "Выберите день из списка."; echo ;;
    esac
  done

  if [ "$day" = "day1" ]; then
    printf "Вопрос для Дня 1 [Enter — стандартный]: "
    if ! read -r question; then
      question=""
    fi
    if [ -z "$question" ]; then
      question="Перечисли три преимущества локального запуска LLM."
    fi
    exec "$ROOT/scripts/run.sh" day1 "$question"
  fi

  if [ "$day" = "day2" ]; then
    echo "Модель Дня 2 фиксирована: qwen3.5:35b-a3b (выбор не требуется)."
    exec "$ROOT/scripts/run.sh" day2
  fi

  if [ "$day" = "day4" ]; then
    echo "Модель Дня 4 фиксирована: qwen3.5:9b-mlx (выбор не требуется)."
    exec "$ROOT/scripts/run.sh" day4
  fi

  if [ "$day" = "day5" ]; then
    echo "День 5 сам прогоняет 4 модели: 9B, DeepSeek Flash, 35B, DeepSeek Pro."
    exec "$ROOT/scripts/run.sh" day5
  fi

  while true; do
    cat <<'EOF'
Доступные модели для Дня 3:

  1) qwen3.5:2b-mlx      — самая быстрая, ~3 ГБ, простые задачи
  2) qwen3.5:9b-mlx      — баланс скорости и качества, ~8 ГБ
  3) qwen3.5:35b-a3b     — лучшее локальное качество, ~20 ГБ
  4) DeepSeek-V4.1-Flash — API DeepSeek, ключ из Keychain или env

EOF
    printf "Выберите модель: "
    if ! read -r choice; then
      echo
      exit 0
    fi

    case "$choice" in
      1|01) exec "$ROOT/scripts/run.sh" day3 --provider ollama --model qwen3.5:2b-mlx ;;
      2|02) exec "$ROOT/scripts/run.sh" day3 --provider ollama --model qwen3.5:9b-mlx ;;
      3|03) exec "$ROOT/scripts/run.sh" day3 --provider ollama --model qwen3.5:35b-a3b ;;
      4|04) exec "$ROOT/scripts/run.sh" day3 --provider deepseek --model deepseek-flash ;;
      *) echo "Выберите модель из списка."; echo ;;
    esac
  done
}

case "${1:-}" in
  "")
    interactive
    ;;
  day1)
    shift
    "$MVN" -q -Dmaven.repo.local="$ROOT/.m2repo" -pl days/day_01 -am package
    exec java -jar days/day_01/target/day01.jar "$@"
    ;;
  day2|day3|day4|day5)
    day="$1"
    shift
    "$MVN" -q -Dmaven.repo.local="$ROOT/.m2repo" -pl cli -am package
    exec java -jar cli/target/lab.jar "$day" "$@"
    ;;
  *)
    echo "Использование: ./scripts/run.sh (интерактивно) | ./scripts/run.sh day1 \"вопрос\" | ./scripts/run.sh day2|day3|day4|day5 [--provider P] [--model M] [--dry-run]" >&2
    exit 2
    ;;
esac
