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
    echo "Maven не найден." >&2
    exit 1
  fi
fi

"$MVN" -q -Dmaven.repo.local="$ROOT/.m2repo" package
./scripts/test.sh
./scripts/run.sh day2 --dry-run >/dev/null
./scripts/run.sh day3 --dry-run >/dev/null
./scripts/run.sh day4 --dry-run >/dev/null
./scripts/run.sh day5 --dry-run >/dev/null
echo "CHECK_OK"
