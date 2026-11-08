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

case "${1:-}" in
  day1)
    shift
    "$MVN" -q -Dmaven.repo.local="$ROOT/.m2repo" -pl days/day_01 -am package
    exec java -jar days/day_01/target/day01.jar "$@"
    ;;
  day2)
    shift
    "$MVN" -q -Dmaven.repo.local="$ROOT/.m2repo" -pl cli -am package
    exec java -jar cli/target/lab.jar day2 "$@"
    ;;
  *)
    echo "Использование: ./scripts/run.sh day1 \"вопрос\" | ./scripts/run.sh day2 [--dry-run]" >&2
    exit 2
    ;;
esac
