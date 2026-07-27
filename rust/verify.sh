#!/bin/sh
set -eu

cd "$(dirname "$0")"

cargo test
cargo build --release

java_dir=$(cd ../java && pwd)
testdata_dir=$(cd ../java/testdata && pwd)
if [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then
    java_bin=/opt/homebrew/opt/openjdk@17/bin/java
    javac_bin=/opt/homebrew/opt/openjdk@17/bin/javac
else
    java_bin=$(command -v java)
    javac_bin=$(command -v javac)
fi
results=$(mktemp -d)
trap 'rm -r "$results"' EXIT HUP INT TERM

solve_count=0
for problem in "$testdata_dir"/problem-*.txt; do
    name=${problem##*/}
    "$java_dir/run.sh" solve "$problem" > "$results/java-$name"
    ./run.sh solve "$problem" > "$results/rust-$name"
    diff -u "$results/java-$name" "$results/rust-$name"
    solve_count=$((solve_count + 1))
done

generate_count=0
for pattern in "$testdata_dir"/pattern-*.txt; do
    name=${pattern##*/}
    "$java_dir/run.sh" generate "$pattern" --seed 42 \
        > "$results/java-$name"
    ./run.sh generate "$pattern" --seed 42 > "$results/rust-$name"
    diff -u "$results/java-$name" "$results/rust-$name"
    generate_count=$((generate_count + 1))
done

"$java_dir/run.sh" generate "$testdata_dir/variant-size6-pattern.txt" \
    --size 6 --blocks 3x2 --seed 42 > "$results/java-variant-size6"
./run.sh generate "$testdata_dir/variant-size6-pattern.txt" \
    --size 6 --blocks 3x2 --seed 42 > "$results/rust-variant-size6"
cmp "$results/java-variant-size6" "$results/rust-variant-size6"

"$java_dir/run.sh" generate "$testdata_dir/pattern-heart.txt" \
    --diagonal --seed 42 > "$results/java-variant-diagonal"
./run.sh generate "$testdata_dir/pattern-heart.txt" \
    --diagonal --seed 42 > "$results/rust-variant-diagonal"
cmp "$results/java-variant-diagonal" "$results/rust-variant-diagonal"

"$java_dir/run.sh" generate "$testdata_dir/variant-size6-pattern.txt" \
    --size 6 --blocks random --seed 42 \
    > "$results/java-variant-random-block"
./run.sh generate "$testdata_dir/variant-size6-pattern.txt" \
    --size 6 --blocks random --seed 42 \
    > "$results/rust-variant-random-block"
cmp "$results/java-variant-random-block" \
    "$results/rust-variant-random-block"

"$java_dir/run.sh" generate "$testdata_dir/variant-size6-pattern.txt" \
    --size 6 --blocks "@$testdata_dir/variant-size6-blocks.txt" --seed 42 \
    > "$results/java-variant-free-block"
./run.sh generate "$testdata_dir/variant-size6-pattern.txt" \
    --size 6 --blocks "@$testdata_dir/variant-size6-blocks.txt" --seed 42 \
    > "$results/rust-variant-free-block"
cmp "$results/java-variant-free-block" "$results/rust-variant-free-block"

"$java_dir/run.sh" random --size 6 --blocks 3x2 --hints 20 --seed 42 \
    > "$results/java-variant-random-size6"
./run.sh random --size 6 --blocks 3x2 --hints 20 --seed 42 \
    > "$results/rust-variant-random-size6"
cmp "$results/java-variant-random-size6" \
    "$results/rust-variant-random-size6"

"$java_dir/run.sh" generate "$testdata_dir/variant-size6-pattern.txt" \
    --size 6 --blocks 3x2 --seed 42 \
    --out "$results/java-variant.xml"
./run.sh generate "$testdata_dir/variant-size6-pattern.txt" \
    --size 6 --blocks 3x2 --seed 42 \
    --out "$results/rust-variant.xml"
cmp "$results/java-variant.xml" "$results/rust-variant.xml"

"$java_dir/run.sh" generate "$testdata_dir/pattern-heart.txt" --seed 42 \
    --out "$results/java-default.xml"
./run.sh generate "$testdata_dir/pattern-heart.txt" --seed 42 \
    --out "$results/rust-default.xml"
cmp "$results/java-default.xml" "$results/rust-default.xml"

"$java_dir/run.sh" solve "$results/java-variant.xml" \
    > "$results/java-read-java-xml"
./run.sh solve "$results/java-variant.xml" \
    > "$results/rust-read-java-xml"
cmp "$results/java-read-java-xml" "$results/rust-read-java-xml"

"$java_dir/run.sh" solve "$results/rust-variant.xml" --format xml \
    > "$results/java-round-trip.xml"
./run.sh solve "$results/rust-variant.xml" --format xml \
    > "$results/rust-round-trip.xml"
cmp "$results/java-round-trip.xml" "$results/rust-round-trip.xml"

"$java_dir/run.sh" random --hints 20 --seed 1 > "$results/java-random"
./run.sh random --hints 20 --seed 1 > "$results/rust-random"
diff -u "$results/java-random" "$results/rust-random"
./run.sh random --hints 20 --seed 1 --symmetry rot4 \
    > "$results/rust-random-explicit-rot4"
cmp "$results/java-random" "$results/rust-random-explicit-rot4"

for symmetry in rot2 mirror-h mirror-v none; do
    "$java_dir/run.sh" random --size 5 --blocks 5x1 --hints 10 --seed 42 \
        --symmetry "$symmetry" > "$results/java-symmetry-$symmetry"
    ./run.sh random --size 5 --blocks 5x1 --hints 10 --seed 42 \
        --symmetry "$symmetry" > "$results/rust-symmetry-$symmetry"
    cmp "$results/java-symmetry-$symmetry" \
        "$results/rust-symmetry-$symmetry"
done

"$java_dir/run.sh" solve "$testdata_dir/problem-heart.txt" \
    --use localization,naked-pair,hidden-pair --unique vh,cell,block \
    > "$results/java-option-solve"
./run.sh solve "$testdata_dir/problem-heart.txt" \
    --use localization,naked-pair,hidden-pair --unique vh,cell,block \
    > "$results/rust-option-solve"
diff -u "$results/java-option-solve" "$results/rust-option-solve"

"$java_dir/run.sh" generate "$testdata_dir/pattern-heart.txt" --seed 42 \
    --use localization > "$results/java-option-use"
./run.sh generate "$testdata_dir/pattern-heart.txt" --seed 42 \
    --use localization > "$results/rust-option-use"
diff -u "$results/java-option-use" "$results/rust-option-use"

"$java_dir/run.sh" generate "$testdata_dir/pattern-heart.txt" --seed 42 \
    --unique cell > "$results/java-option-unique"
./run.sh generate "$testdata_dir/pattern-heart.txt" --seed 42 \
    --unique cell > "$results/rust-option-unique"
diff -u "$results/java-option-unique" "$results/rust-option-unique"

"$java_dir/run.sh" generate "$testdata_dir/pattern-heart.txt" --seed 42 \
    --dp-min 13000 --dp-max -1 > "$results/java-option-dp"
./run.sh generate "$testdata_dir/pattern-heart.txt" --seed 42 \
    --dp-min 13000 --dp-max -1 > "$results/rust-option-dp"
diff -u "$results/java-option-dp" "$results/rust-option-dp"

"$java_dir/run.sh" random --hints 20 --seed 1 --forbidden 9 \
    > "$results/java-option-random"
./run.sh random --hints 20 --seed 1 --forbidden 9 \
    > "$results/rust-option-random"
diff -u "$results/java-option-random" "$results/rust-option-random"

"$java_dir/run.sh" generate "$testdata_dir/use-none-pattern.txt" --seed 42 \
    --use none > "$results/java-use-none"
./run.sh generate "$testdata_dir/use-none-pattern.txt" --seed 42 \
    --use none > "$results/rust-use-none"
cmp "$results/java-use-none" "$results/rust-use-none"

"$java_dir/run.sh" solve "$testdata_dir/xml-vertical-off.xml" \
    > "$results/java-xml-vertical-off-solve"
./run.sh solve "$testdata_dir/xml-vertical-off.xml" \
    > "$results/rust-xml-vertical-off-solve"
cmp "$results/java-xml-vertical-off-solve" \
    "$results/rust-xml-vertical-off-solve"

"$java_dir/run.sh" generate "$testdata_dir/xml-vertical-off.xml" --seed 42 \
    > "$results/java-xml-vertical-off-generate"
./run.sh generate "$testdata_dir/xml-vertical-off.xml" --seed 42 \
    > "$results/rust-xml-vertical-off-generate"
cmp "$results/java-xml-vertical-off-generate" \
    "$results/rust-xml-vertical-off-generate"

"$java_dir/run.sh" generate "$testdata_dir/xml-seed.xml" --seed 42 \
    > "$results/java-xml-seed-generate"
./run.sh generate "$testdata_dir/xml-seed.xml" --seed 42 \
    > "$results/rust-xml-seed-generate"
cmp "$results/java-xml-seed-generate" "$results/rust-xml-seed-generate"

"$java_dir/run.sh" solve "$testdata_dir/xml-multiple-groups.xml" \
    > "$results/java-xml-multiple-groups"
./run.sh solve "$testdata_dir/xml-multiple-groups.xml" \
    > "$results/rust-xml-multiple-groups"
cmp "$results/java-xml-multiple-groups" \
    "$results/rust-xml-multiple-groups"

mkdir "$results/java-helper"
"$javac_bin" -encoding UTF-8 -cp "$java_dir/build/classes" \
    -d "$results/java-helper" \
    "$java_dir/verify/RewriteXmlConstraintDriver.java"
"$java_bin" -cp "$java_dir/build/classes:$results/java-helper" \
    jp.gr.puzzle.npgen2007.RewriteXmlConstraintDriver \
    blocks "$testdata_dir/xml-diagonal-order.xml" \
    > "$results/java-xml-diagonal-order"
./target/release/xml_constraint_driver \
    "$testdata_dir/xml-diagonal-order.xml" \
    > "$results/rust-xml-diagonal-order"
cmp "$results/java-xml-diagonal-order" "$results/rust-xml-diagonal-order"

"$java_dir/run.sh" solve "$testdata_dir/xml-hint-comment.xml" \
    --out "$results/java-xml-hint-comment.xml"
./run.sh solve "$testdata_dir/xml-hint-comment.xml" \
    --out "$results/rust-xml-hint-comment.xml"
cmp "$results/java-xml-hint-comment.xml" \
    "$results/rust-xml-hint-comment.xml"

"$java_dir/run.sh" solve "$testdata_dir/xml-no-hint.xml" \
    --out "$results/java-xml-no-hint.xml"
./run.sh solve "$testdata_dir/xml-no-hint.xml" \
    --out "$results/rust-xml-no-hint.xml"
cmp "$results/java-xml-no-hint.xml" "$results/rust-xml-no-hint.xml"

"$java_dir/run.sh" solve "$testdata_dir/xml-vertical-off.xml" \
    --no-horizontal --out "$results/java-xml-no-lines.xml"
./run.sh solve "$testdata_dir/xml-vertical-off.xml" \
    --no-horizontal --out "$results/rust-xml-no-lines.xml"
cmp "$results/java-xml-no-lines.xml" "$results/rust-xml-no-lines.xml"

"$java_dir/run.sh" solve "$testdata_dir/xml-vertical-off.xml" --unique none \
    > "$results/java-unique-none"
./run.sh solve "$testdata_dir/xml-vertical-off.xml" --unique none \
    > "$results/rust-unique-none"
cmp "$results/java-unique-none" "$results/rust-unique-none"

set +e
"$java_dir/run.sh" solve "$testdata_dir/no-answer.txt" \
    > "$results/java-no-answer.stdout" \
    2> "$results/java-no-answer.stderr"
java_no_answer_exit=$?
./run.sh solve "$testdata_dir/no-answer.txt" \
    > "$results/no-answer.stdout" 2> "$results/no-answer.stderr"
no_answer_exit=$?
"$java_dir/run.sh" random --hints 20 --seed 0 --attempts 1 \
    > "$results/java-attempts-limited.stdout" \
    2> "$results/java-attempts-limited.stderr"
java_attempts_limited_exit=$?
./run.sh random --hints 20 --seed 0 --attempts 1 \
    > "$results/attempts-limited.stdout" \
    2> "$results/attempts-limited.stderr"
attempts_limited_exit=$?
"$java_dir/run.sh" generate "$testdata_dir/pattern-heart.txt" \
    --seed 0 --attempts 1 \
    > "$results/java-attempts-limited-generate.stdout" \
    2> "$results/java-attempts-limited-generate.stderr"
java_attempts_limited_generate_exit=$?
./run.sh generate "$testdata_dir/pattern-heart.txt" \
    --seed 0 --attempts 1 \
    > "$results/attempts-limited-generate.stdout" \
    2> "$results/attempts-limited-generate.stderr"
attempts_limited_generate_exit=$?
./run.sh generate "$testdata_dir/pattern-heart.txt" \
    --seed 42 --attempts -1 > "$results/attempts-invalid.stdout" \
    2> "$results/attempts-invalid.stderr"
attempts_invalid_exit=$?
./run.sh solve "$results/missing.txt" \
    > "$results/input-error.stdout" 2> "$results/input-error.stderr"
input_error_exit=$?
./run.sh solve "$testdata_dir/problem-heart.txt" --dp-min 1 \
    > "$results/solve-dp.stdout" 2> "$results/solve-dp.stderr"
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
test ! -s "$results/no-answer.stdout"
cmp "$results/java-no-answer.stderr" "$results/no-answer.stderr"
test ! -s "$results/attempts-limited.stdout"
cmp "$results/java-attempts-limited.stderr" \
    "$results/attempts-limited.stderr"
test ! -s "$results/attempts-limited-generate.stdout"
cmp "$results/java-attempts-limited-generate.stderr" \
    "$results/attempts-limited-generate.stderr"

"$java_dir/run.sh" generate "$testdata_dir/pattern-heart.txt" \
    --seed 42 --attempts 0 > "$results/java-attempts-unlimited-generate"
./run.sh generate "$testdata_dir/pattern-heart.txt" \
    --seed 42 --attempts 0 > "$results/rust-attempts-unlimited-generate"
cmp "$results/java-attempts-unlimited-generate" \
    "$results/rust-attempts-unlimited-generate"
"$java_dir/run.sh" random --hints 20 --seed 40 --attempts 0 \
    > "$results/java-attempts-unlimited-random"
./run.sh random --hints 20 --seed 40 --attempts 0 \
    > "$results/rust-attempts-unlimited-random"
cmp "$results/java-attempts-unlimited-random" \
    "$results/rust-attempts-unlimited-random"

./run.sh bench --count 1 --seed 1 > "$results/bench.out"
grep -q '^COUNT 1$' "$results/bench.out"
grep -q '^SUCCEEDED 1$' "$results/bench.out"

echo "SOLVE_MATCHES $solve_count"
echo "GENERATE_MATCHES $generate_count"
echo "VARIANT_MATCHES size6=1 diagonal=1 random-block=1 free-block=1 random-size6=1"
echo "XML_ROUND_TRIP java-to-rust=1 rust-to-java=1 cli-byte-matches=2"
echo "RANDOM_MATCHES 1"
echo "SYMMETRY_MATCHES rot2=1 mirror-h=1 mirror-v=1 none=1"
echo "OPTION_MATCHES solve=2 generate=4 random=1"
echo "XML_FEATURE_MATCHES vertical=2 seed=1 groups=1 diagonal-order=1 metadata=3"
echo "EXIT_CODES no-answer=1 input-error=2 solve-dp=2"
echo "ATTEMPTS_CHECKS limited-generate=1 limited-random=1 unlimited-generate=1 unlimited-random=1 invalid=1"
echo "BENCH count=1: OK"
