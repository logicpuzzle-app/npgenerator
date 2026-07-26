#!/bin/zsh
# 4言語の bench --count N --seed S を実行し、各実装内部計測の ELAPSED_MS を収集する
set -eu
cd "$(dirname "$0")/.."
COUNT=${1:-5}
SEED=${2:-1}
OUT=bench/results-count${COUNT}-seed${SEED}.txt

{
  echo "# NPGenerator 2007 rewrite benchmark"
  echo "# bench --count $COUNT --seed $SEED / $(uname -m) / $(sysctl -n machdep.cpu.brand_string 2>/dev/null || echo unknown CPU)"
  echo

  echo "== Rust (release) =="
  rust/target/release/npgen bench --count "$COUNT" --seed "$SEED"
  echo

  echo "== Java (openjdk 17) =="
  java/run.sh bench --count "$COUNT" --seed "$SEED"
  echo

  echo "== TypeScript (node22 + tsx) =="
  (cd typescript && node --import tsx src/cli.ts bench --count "$COUNT" --seed "$SEED")
  echo

  echo "== Python (CPython $(python3 -V | cut -d' ' -f2)) =="
  python3 python/npgen.py bench --count "$COUNT" --seed "$SEED"
} | tee "$OUT"
