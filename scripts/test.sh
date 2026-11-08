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

"$MVN" -q -Dmaven.repo.local="$ROOT/.m2repo" -pl core,days/day_02,days/day_03 -am test-compile
JACKSON_CP="$(find "$ROOT/.m2repo/com/fasterxml/jackson" -name '*.jar' | paste -sd: -)"
CORE_CP="core/target/classes:core/target/test-classes:$JACKSON_CP"
java -cp "$CORE_CP" \
  ru.pyshkov.llm.core.FailuresSelfTest
java -cp "$CORE_CP" \
  ru.pyshkov.llm.core.MarkdownSelfTest
java -cp "$CORE_CP" \
  ru.pyshkov.llm.core.RunnerSelfTest
java -cp "core/target/classes:days/day_02/target/classes:days/day_02/target/test-classes:$JACKSON_CP" \
  ru.pyshkov.llm.day02.Day02CheckerSelfTest
java -cp "core/target/classes:days/day_03/target/classes:days/day_03/target/test-classes:$JACKSON_CP" \
  ru.pyshkov.llm.day03.Day03CheckerSelfTest
echo "ALL_SELF_TESTS_OK"
