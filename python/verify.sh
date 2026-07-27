#!/bin/sh
set -eu

cd "$(dirname "$0")"

PYTHON=${PYTHON:-python3}
JAVA=../java/run.sh
TESTDATA=../java/testdata
RESULTS=$(mktemp -d "${TMPDIR:-/tmp}/npgen-python-verify.XXXXXX")
trap 'rm -rf "$RESULTS"' EXIT HUP INT TERM

"$PYTHON" -m compileall -q .

solve_count=0
for problem in "$TESTDATA"/problem-*.txt; do
    name=${problem##*/}
    "$PYTHON" npgen.py solve "$problem" > "$RESULTS/python-$name.out"
    "$JAVA" solve "$problem" > "$RESULTS/java-$name.out"
    diff -u "$RESULTS/java-$name.out" "$RESULTS/python-$name.out"
    expected="$TESTDATA/expected/solve-${name%.txt}.out"
    diff -u "$expected" "$RESULTS/python-$name.out"
    solve_count=$((solve_count + 1))
done

generate_count=0
for pattern in "$TESTDATA"/pattern-*.txt; do
    name=${pattern##*/}
    "$PYTHON" npgen.py generate "$pattern" --seed 42 \
        > "$RESULTS/python-$name.out"
    "$JAVA" generate "$pattern" --seed 42 > "$RESULTS/java-$name.out"
    diff -u "$RESULTS/java-$name.out" "$RESULTS/python-$name.out"
    generate_count=$((generate_count + 1))
done
diff -u "$TESTDATA/expected/generate-heart-seed42.out" \
    "$RESULTS/python-pattern-heart.txt.out"

"$PYTHON" npgen.py random --hints 20 --seed 1 \
    > "$RESULTS/python-random.out"
"$JAVA" random --hints 20 --seed 1 > "$RESULTS/java-random.out"
diff -u "$RESULTS/java-random.out" "$RESULTS/python-random.out"
"$PYTHON" npgen.py random --hints 20 --seed 1 --symmetry rot4 \
    > "$RESULTS/python-random-explicit-rot4.out"
cmp "$RESULTS/java-random.out" \
    "$RESULTS/python-random-explicit-rot4.out"

for symmetry in rot2 mirror-h mirror-v none; do
    "$JAVA" random --size 5 --blocks 5x1 --hints 10 --seed 42 \
        --symmetry "$symmetry" > "$RESULTS/java-symmetry-$symmetry.out"
    "$PYTHON" npgen.py random --size 5 --blocks 5x1 --hints 10 --seed 42 \
        --symmetry "$symmetry" \
        > "$RESULTS/python-symmetry-$symmetry.out"
    cmp "$RESULTS/java-symmetry-$symmetry.out" \
        "$RESULTS/python-symmetry-$symmetry.out"
done

"$JAVA" generate "$TESTDATA/variant-size6-pattern.txt" \
    --size 6 --blocks 3x2 --seed 42 \
    > "$RESULTS/java-variant-size6.out"
"$PYTHON" npgen.py generate "$TESTDATA/variant-size6-pattern.txt" \
    --size 6 --blocks 3x2 --seed 42 \
    > "$RESULTS/python-variant-size6.out"
cmp "$RESULTS/java-variant-size6.out" \
    "$RESULTS/python-variant-size6.out"

"$JAVA" generate "$TESTDATA/pattern-heart.txt" --diagonal --seed 42 \
    > "$RESULTS/java-variant-diagonal.out"
"$PYTHON" npgen.py generate "$TESTDATA/pattern-heart.txt" \
    --diagonal --seed 42 > "$RESULTS/python-variant-diagonal.out"
cmp "$RESULTS/java-variant-diagonal.out" \
    "$RESULTS/python-variant-diagonal.out"

"$JAVA" generate "$TESTDATA/variant-size6-pattern.txt" \
    --size 6 --blocks random --seed 42 \
    > "$RESULTS/java-variant-random-block.out"
"$PYTHON" npgen.py generate "$TESTDATA/variant-size6-pattern.txt" \
    --size 6 --blocks random --seed 42 \
    > "$RESULTS/python-variant-random-block.out"
cmp "$RESULTS/java-variant-random-block.out" \
    "$RESULTS/python-variant-random-block.out"

"$JAVA" generate "$TESTDATA/variant-size6-pattern.txt" \
    --size 6 --blocks "@$TESTDATA/variant-size6-blocks.txt" --seed 42 \
    > "$RESULTS/java-variant-free-block.out"
"$PYTHON" npgen.py generate "$TESTDATA/variant-size6-pattern.txt" \
    --size 6 --blocks "@$TESTDATA/variant-size6-blocks.txt" --seed 42 \
    > "$RESULTS/python-variant-free-block.out"
cmp "$RESULTS/java-variant-free-block.out" \
    "$RESULTS/python-variant-free-block.out"

"$JAVA" random --size 6 --blocks 3x2 --hints 20 --seed 42 \
    > "$RESULTS/java-variant-random-size6.out"
"$PYTHON" npgen.py random --size 6 --blocks 3x2 --hints 20 --seed 42 \
    > "$RESULTS/python-variant-random-size6.out"
cmp "$RESULTS/java-variant-random-size6.out" \
    "$RESULTS/python-variant-random-size6.out"

"$JAVA" generate "$TESTDATA/variant-size6-pattern.txt" \
    --size 6 --blocks 3x2 --seed 42 \
    --out "$RESULTS/java-cli-generated.xml"
"$PYTHON" npgen.py generate "$TESTDATA/variant-size6-pattern.txt" \
    --size 6 --blocks 3x2 --seed 42 \
    --out "$RESULTS/python-cli-generated.xml"
cmp "$RESULTS/java-cli-generated.xml" \
    "$RESULTS/python-cli-generated.xml"

"$JAVA" solve "$RESULTS/python-cli-generated.xml" \
    --format xml > "$RESULTS/java-read-python.xml"
"$PYTHON" npgen.py solve "$RESULTS/java-cli-generated.xml" \
    --format xml > "$RESULTS/python-read-java.xml"
cmp "$RESULTS/java-read-python.xml" \
    "$RESULTS/python-read-java.xml"

"$JAVA" generate "$RESULTS/python-cli-generated.xml" --seed 42 \
    --out "$RESULTS/java-roundtrip.xml"
"$PYTHON" npgen.py generate "$RESULTS/java-cli-generated.xml" --seed 42 \
    --out "$RESULTS/python-roundtrip.xml"
cmp "$RESULTS/java-roundtrip.xml" "$RESULTS/python-roundtrip.xml"

"$JAVA" generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --out "$RESULTS/java-cli-default-block.xml"
"$PYTHON" npgen.py generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --out "$RESULTS/python-cli-default-block.xml"
cmp "$RESULTS/java-cli-default-block.xml" \
    "$RESULTS/python-cli-default-block.xml"

"$JAVA" solve "$TESTDATA/problem-heart.txt" \
    --use localization,naked-pair,hidden-pair --unique vh,cell,block \
    > "$RESULTS/java-option-solve.out"
"$PYTHON" npgen.py solve "$TESTDATA/problem-heart.txt" \
    --use localization,naked-pair,hidden-pair --unique vh,cell,block \
    > "$RESULTS/python-option-solve.out"
diff -u "$RESULTS/java-option-solve.out" \
    "$RESULTS/python-option-solve.out"

set +e
"$JAVA" solve "$TESTDATA/problem-heart.txt" --use localization \
    > "$RESULTS/java-option-solve-limited.out" \
    2> "$RESULTS/java-option-solve-limited.err"
java_limited_exit=$?
"$PYTHON" npgen.py solve "$TESTDATA/problem-heart.txt" --use localization \
    > "$RESULTS/python-option-solve-limited.out" \
    2> "$RESULTS/python-option-solve-limited.err"
python_limited_exit=$?
set -e
[ "$java_limited_exit" -eq 1 ]
[ "$python_limited_exit" -eq "$java_limited_exit" ]
diff -u "$RESULTS/java-option-solve-limited.out" \
    "$RESULTS/python-option-solve-limited.out"
diff -u "$RESULTS/java-option-solve-limited.err" \
    "$RESULTS/python-option-solve-limited.err"

"$JAVA" generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --use localization > "$RESULTS/java-option-use.out"
"$PYTHON" npgen.py generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --use localization > "$RESULTS/python-option-use.out"
diff -u "$RESULTS/java-option-use.out" \
    "$RESULTS/python-option-use.out"

"$JAVA" generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --use localization,naked-pair,hidden-pair \
    > "$RESULTS/java-option-use-pairs.out"
"$PYTHON" npgen.py generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --use localization,naked-pair,hidden-pair \
    > "$RESULTS/python-option-use-pairs.out"
diff -u "$RESULTS/java-option-use-pairs.out" \
    "$RESULTS/python-option-use-pairs.out"

"$JAVA" generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --unique cell > "$RESULTS/java-option-unique.out"
"$PYTHON" npgen.py generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --unique cell > "$RESULTS/python-option-unique.out"
diff -u "$RESULTS/java-option-unique.out" \
    "$RESULTS/python-option-unique.out"

"$JAVA" generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --dp-min 13000 --dp-max -1 > "$RESULTS/java-option-dp.out"
"$PYTHON" npgen.py generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --dp-min 13000 --dp-max -1 > "$RESULTS/python-option-dp.out"
diff -u "$RESULTS/java-option-dp.out" \
    "$RESULTS/python-option-dp.out"

"$JAVA" generate "$TESTDATA/pattern-heart.txt" --seed 42 --forbidden 9 \
    > "$RESULTS/java-option-forbidden.out"
"$PYTHON" npgen.py generate "$TESTDATA/pattern-heart.txt" --seed 42 \
    --forbidden 9 > "$RESULTS/python-option-forbidden.out"
diff -u "$RESULTS/java-option-forbidden.out" \
    "$RESULTS/python-option-forbidden.out"

"$JAVA" random --hints 20 --seed 1 --forbidden 9 \
    > "$RESULTS/java-option-random.out"
"$PYTHON" npgen.py random --hints 20 --seed 1 --forbidden 9 \
    > "$RESULTS/python-option-random.out"
diff -u "$RESULTS/java-option-random.out" \
    "$RESULTS/python-option-random.out"

"$JAVA" generate "$TESTDATA/use-none-pattern.txt" --seed 42 \
    --use none > "$RESULTS/java-use-none.out"
"$PYTHON" npgen.py generate "$TESTDATA/use-none-pattern.txt" --seed 42 \
    --use none > "$RESULTS/python-use-none.out"
cmp "$RESULTS/java-use-none.out" "$RESULTS/python-use-none.out"

"$JAVA" solve "$TESTDATA/xml-vertical-off.xml" \
    > "$RESULTS/java-xml-vertical-off-solve.out"
"$PYTHON" npgen.py solve "$TESTDATA/xml-vertical-off.xml" \
    > "$RESULTS/python-xml-vertical-off-solve.out"
cmp "$RESULTS/java-xml-vertical-off-solve.out" \
    "$RESULTS/python-xml-vertical-off-solve.out"

"$JAVA" generate "$TESTDATA/xml-vertical-off.xml" --seed 42 \
    > "$RESULTS/java-xml-vertical-off-generate.out"
"$PYTHON" npgen.py generate "$TESTDATA/xml-vertical-off.xml" --seed 42 \
    > "$RESULTS/python-xml-vertical-off-generate.out"
cmp "$RESULTS/java-xml-vertical-off-generate.out" \
    "$RESULTS/python-xml-vertical-off-generate.out"

"$JAVA" generate "$TESTDATA/xml-seed.xml" --seed 42 \
    > "$RESULTS/java-xml-seed-generate.out"
"$PYTHON" npgen.py generate "$TESTDATA/xml-seed.xml" --seed 42 \
    > "$RESULTS/python-xml-seed-generate.out"
cmp "$RESULTS/java-xml-seed-generate.out" \
    "$RESULTS/python-xml-seed-generate.out"

"$JAVA" solve "$TESTDATA/xml-multiple-groups.xml" \
    > "$RESULTS/java-xml-multiple-groups.out"
"$PYTHON" npgen.py solve "$TESTDATA/xml-multiple-groups.xml" \
    > "$RESULTS/python-xml-multiple-groups.out"
cmp "$RESULTS/java-xml-multiple-groups.out" \
    "$RESULTS/python-xml-multiple-groups.out"

if [ -x /opt/homebrew/opt/openjdk@17/bin/javac ]; then
    JAVAC=/opt/homebrew/opt/openjdk@17/bin/javac
else
    JAVAC=$(command -v javac)
fi
if [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then
    JAVA_BIN=/opt/homebrew/opt/openjdk@17/bin/java
else
    JAVA_BIN=$(command -v java)
fi
DRIVER_CLASSES="$RESULTS/java-driver"
mkdir -p "$DRIVER_CLASSES"
"$JAVAC" -encoding UTF-8 -cp ../java/build/classes \
    -d "$DRIVER_CLASSES" ../java/verify/RewriteXmlConstraintDriver.java
"$JAVA_BIN" -cp "../java/build/classes:$DRIVER_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteXmlConstraintDriver \
    blocks "$TESTDATA/xml-diagonal-order.xml" \
    > "$RESULTS/java-xml-diagonal-order.out"
"$PYTHON" - "$TESTDATA/xml-diagonal-order.xml" \
    > "$RESULTS/python-xml-diagonal-order.out" <<'PY'
import sys

from npgen2007.variant import build_xml_variant
from npgen2007.xmlio import read_number_place_file

variant = build_xml_variant(read_number_place_file(sys.argv[1]), False)
print(f"BLOCKS {len(variant.block.blocks)}")
for block in variant.block.blocks:
    print(" ".join(str(cell) for cell in block))
PY
cmp "$RESULTS/java-xml-diagonal-order.out" \
    "$RESULTS/python-xml-diagonal-order.out"

"$JAVA" solve "$TESTDATA/xml-hint-comment.xml" \
    --out "$RESULTS/java-xml-hint-comment-out.xml"
"$PYTHON" npgen.py solve "$TESTDATA/xml-hint-comment.xml" \
    --out "$RESULTS/python-xml-hint-comment-out.xml"
cmp "$RESULTS/java-xml-hint-comment-out.xml" \
    "$RESULTS/python-xml-hint-comment-out.xml"

"$JAVA" solve "$TESTDATA/xml-no-hint.xml" \
    --out "$RESULTS/java-xml-no-hint-out.xml"
"$PYTHON" npgen.py solve "$TESTDATA/xml-no-hint.xml" \
    --out "$RESULTS/python-xml-no-hint-out.xml"
cmp "$RESULTS/java-xml-no-hint-out.xml" \
    "$RESULTS/python-xml-no-hint-out.xml"

"$JAVA" solve "$TESTDATA/xml-vertical-off.xml" --no-horizontal \
    --out "$RESULTS/java-xml-no-lines-out.xml"
"$PYTHON" npgen.py solve "$TESTDATA/xml-vertical-off.xml" --no-horizontal \
    --out "$RESULTS/python-xml-no-lines-out.xml"
cmp "$RESULTS/java-xml-no-lines-out.xml" \
    "$RESULTS/python-xml-no-lines-out.xml"

"$JAVA" solve "$TESTDATA/xml-vertical-off.xml" --unique none \
    > "$RESULTS/java-unique-none.out"
"$PYTHON" npgen.py solve "$TESTDATA/xml-vertical-off.xml" --unique none \
    > "$RESULTS/python-unique-none.out"
cmp "$RESULTS/java-unique-none.out" "$RESULTS/python-unique-none.out"

set +e
"$JAVA" solve "$TESTDATA/no-answer.txt" \
    > "$RESULTS/java-no-answer.stdout" \
    2> "$RESULTS/java-no-answer.stderr"
java_no_answer_exit=$?
"$PYTHON" npgen.py solve "$TESTDATA/no-answer.txt" \
    > "$RESULTS/no-answer.stdout" 2> "$RESULTS/no-answer.stderr"
no_answer_exit=$?
"$JAVA" random --hints 20 --seed 0 --attempts 1 \
    > "$RESULTS/java-attempts-limited.stdout" \
    2> "$RESULTS/java-attempts-limited.stderr"
java_attempts_limited_exit=$?
"$PYTHON" npgen.py random --hints 20 --seed 0 --attempts 1 \
    > "$RESULTS/attempts-limited.stdout" \
    2> "$RESULTS/attempts-limited.stderr"
attempts_limited_exit=$?
"$JAVA" generate "$TESTDATA/pattern-heart.txt" \
    --seed 0 --attempts 1 \
    > "$RESULTS/java-attempts-limited-generate.stdout" \
    2> "$RESULTS/java-attempts-limited-generate.stderr"
java_attempts_limited_generate_exit=$?
"$PYTHON" npgen.py generate "$TESTDATA/pattern-heart.txt" \
    --seed 0 --attempts 1 \
    > "$RESULTS/attempts-limited-generate.stdout" \
    2> "$RESULTS/attempts-limited-generate.stderr"
attempts_limited_generate_exit=$?
"$PYTHON" npgen.py generate "$TESTDATA/pattern-heart.txt" \
    --seed 42 --attempts -1 > "$RESULTS/attempts-invalid.stdout" \
    2> "$RESULTS/attempts-invalid.stderr"
attempts_invalid_exit=$?
"$PYTHON" npgen.py solve "$RESULTS/missing.txt" \
    > "$RESULTS/input-error.stdout" 2> "$RESULTS/input-error.stderr"
input_error_exit=$?
"$PYTHON" npgen.py solve "$TESTDATA/problem-heart.txt" --dp-min 1 \
    > "$RESULTS/solve-dp.stdout" 2> "$RESULTS/solve-dp.stderr"
solve_dp_exit=$?
set -e
[ "$java_no_answer_exit" -eq 1 ]
[ "$no_answer_exit" -eq 1 ]
[ "$java_attempts_limited_exit" -eq 1 ]
[ "$attempts_limited_exit" -eq 1 ]
[ "$java_attempts_limited_generate_exit" -eq 1 ]
[ "$attempts_limited_generate_exit" -eq 1 ]
[ "$attempts_invalid_exit" -eq 2 ]
[ "$input_error_exit" -eq 2 ]
[ "$solve_dp_exit" -eq 2 ]
test ! -s "$RESULTS/no-answer.stdout"
cmp "$RESULTS/java-no-answer.stderr" "$RESULTS/no-answer.stderr"
test ! -s "$RESULTS/attempts-limited.stdout"
cmp "$RESULTS/java-attempts-limited.stderr" \
    "$RESULTS/attempts-limited.stderr"
test ! -s "$RESULTS/attempts-limited-generate.stdout"
cmp "$RESULTS/java-attempts-limited-generate.stderr" \
    "$RESULTS/attempts-limited-generate.stderr"

"$JAVA" generate "$TESTDATA/pattern-heart.txt" \
    --seed 42 --attempts 0 > "$RESULTS/java-attempts-unlimited-generate"
"$PYTHON" npgen.py generate "$TESTDATA/pattern-heart.txt" \
    --seed 42 --attempts 0 > "$RESULTS/python-attempts-unlimited-generate"
cmp "$RESULTS/java-attempts-unlimited-generate" \
    "$RESULTS/python-attempts-unlimited-generate"
"$JAVA" random --hints 20 --seed 40 --attempts 0 \
    > "$RESULTS/java-attempts-unlimited-random"
"$PYTHON" npgen.py random --hints 20 --seed 40 --attempts 0 \
    > "$RESULTS/python-attempts-unlimited-random"
cmp "$RESULTS/java-attempts-unlimited-random" \
    "$RESULTS/python-attempts-unlimited-random"

"$PYTHON" npgen.py bench --count 1 --seed 1 > "$RESULTS/bench.out"
grep -q '^COUNT 1$' "$RESULTS/bench.out"
grep -q '^SUCCEEDED 1$' "$RESULTS/bench.out"

echo "COMPILEALL: OK"
echo "SOLVE_MATCHES $solve_count"
echo "GENERATE_MATCHES $generate_count"
echo "RANDOM_MATCHES 1"
echo "SYMMETRY_MATCHES rot2=1 mirror-h=1 mirror-v=1 none=1"
echo "VARIANT_MATCHES size6=1 diagonal=1 random-block=1 free-block=1 random-size6=1"
echo "XML_ROUND_TRIP java-to-python=1 python-to-java=1 cli=2"
echo "OPTION_MATCHES solve=3 generate=6 random=1"
echo "XML_FEATURE_MATCHES vertical=2 seed=1 groups=1 diagonal-order=1 metadata=3"
echo "EXPECTED_OUTPUT_MATCHES 6"
echo "EXIT_CODES no-answer=1 input-error=2 solve-dp=2"
echo "ATTEMPTS_CHECKS limited-generate=1 limited-random=1 unlimited-generate=1 unlimited-random=1 invalid=1"
echo "BENCH count=1: OK"
