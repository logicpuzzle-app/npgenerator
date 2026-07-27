#!/bin/sh
set -eu

cd "$(dirname "$0")"

npx tsc --noEmit

java_runner=../java/run.sh
typescript_runner="node --import tsx src/cli.ts"
if [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then
    java=/opt/homebrew/opt/openjdk@17/bin/java
    javac=/opt/homebrew/opt/openjdk@17/bin/javac
else
    java=$(command -v java)
    javac=$(command -v javac)
fi
verify_dir=$(mktemp -d "${TMPDIR:-/tmp}/npgen-typescript-verify.XXXXXX")
trap 'rm -rf "$verify_dir"' EXIT HUP INT TERM

for input in ../java/testdata/problem-*.txt; do
    name=${input##*/}
    "$java_runner" solve "$input" > "$verify_dir/java-solve-$name"
    $typescript_runner solve "$input" > "$verify_dir/typescript-solve-$name"
    cmp "$verify_dir/java-solve-$name" "$verify_dir/typescript-solve-$name"
done

for input in ../java/testdata/pattern-*.txt; do
    name=${input##*/}
    "$java_runner" generate "$input" --seed 42 > "$verify_dir/java-generate-$name"
    $typescript_runner generate "$input" --seed 42 > "$verify_dir/typescript-generate-$name"
    cmp "$verify_dir/java-generate-$name" "$verify_dir/typescript-generate-$name"
done

"$java_runner" generate ../java/testdata/variant-size6-pattern.txt \
    --size 6 --blocks 3x2 --seed 42 > "$verify_dir/java-variant-size6"
$typescript_runner generate ../java/testdata/variant-size6-pattern.txt \
    --size 6 --blocks 3x2 --seed 42 > "$verify_dir/typescript-variant-size6"
cmp "$verify_dir/java-variant-size6" "$verify_dir/typescript-variant-size6"

"$java_runner" generate ../java/testdata/pattern-heart.txt \
    --diagonal --seed 42 > "$verify_dir/java-variant-diagonal"
$typescript_runner generate ../java/testdata/pattern-heart.txt \
    --diagonal --seed 42 > "$verify_dir/typescript-variant-diagonal"
cmp "$verify_dir/java-variant-diagonal" "$verify_dir/typescript-variant-diagonal"

"$java_runner" generate ../java/testdata/variant-size6-pattern.txt \
    --size 6 --blocks random --seed 42 \
    > "$verify_dir/java-variant-random-block"
$typescript_runner generate ../java/testdata/variant-size6-pattern.txt \
    --size 6 --blocks random --seed 42 \
    > "$verify_dir/typescript-variant-random-block"
cmp "$verify_dir/java-variant-random-block" \
    "$verify_dir/typescript-variant-random-block"

"$java_runner" generate ../java/testdata/variant-size6-pattern.txt \
    --size 6 --blocks "@../java/testdata/variant-size6-blocks.txt" --seed 42 \
    > "$verify_dir/java-variant-free-block"
$typescript_runner generate ../java/testdata/variant-size6-pattern.txt \
    --size 6 --blocks "@../java/testdata/variant-size6-blocks.txt" --seed 42 \
    > "$verify_dir/typescript-variant-free-block"
cmp "$verify_dir/java-variant-free-block" \
    "$verify_dir/typescript-variant-free-block"

"$java_runner" random --size 6 --blocks 3x2 --hints 20 --seed 42 \
    > "$verify_dir/java-variant-random-size6"
$typescript_runner random --size 6 --blocks 3x2 --hints 20 --seed 42 \
    > "$verify_dir/typescript-variant-random-size6"
cmp "$verify_dir/java-variant-random-size6" \
    "$verify_dir/typescript-variant-random-size6"

"$java_runner" generate ../java/testdata/variant-size6-pattern.txt \
    --size 6 --blocks 3x2 --seed 42 \
    --out "$verify_dir/java-variant.xml"
$typescript_runner generate ../java/testdata/variant-size6-pattern.txt \
    --size 6 --blocks 3x2 --seed 42 \
    --out "$verify_dir/typescript-variant.xml"
cmp "$verify_dir/java-variant.xml" "$verify_dir/typescript-variant.xml"

"$java_runner" generate ../java/testdata/pattern-heart.txt --seed 42 \
    --out "$verify_dir/java-default.xml"
$typescript_runner generate ../java/testdata/pattern-heart.txt --seed 42 \
    --out "$verify_dir/typescript-default.xml"
cmp "$verify_dir/java-default.xml" "$verify_dir/typescript-default.xml"

"$java_runner" solve "$verify_dir/java-variant.xml" \
    > "$verify_dir/java-read-java-xml"
$typescript_runner solve "$verify_dir/java-variant.xml" \
    > "$verify_dir/typescript-read-java-xml"
cmp "$verify_dir/java-read-java-xml" "$verify_dir/typescript-read-java-xml"

"$java_runner" solve "$verify_dir/typescript-variant.xml" --format xml \
    > "$verify_dir/java-round-trip.xml"
$typescript_runner solve "$verify_dir/typescript-variant.xml" --format xml \
    > "$verify_dir/typescript-round-trip.xml"
cmp "$verify_dir/java-round-trip.xml" "$verify_dir/typescript-round-trip.xml"

"$java_runner" random --hints 20 --seed 1 > "$verify_dir/java-random"
$typescript_runner random --hints 20 --seed 1 > "$verify_dir/typescript-random"
cmp "$verify_dir/java-random" "$verify_dir/typescript-random"
$typescript_runner random --hints 20 --seed 1 --symmetry rot4 \
    > "$verify_dir/typescript-random-explicit-rot4"
cmp "$verify_dir/java-random" "$verify_dir/typescript-random-explicit-rot4"

for symmetry in rot2 mirror-h mirror-v none; do
    "$java_runner" random --size 5 --blocks 5x1 --hints 10 --seed 42 \
        --symmetry "$symmetry" > "$verify_dir/java-symmetry-$symmetry"
    $typescript_runner random --size 5 --blocks 5x1 --hints 10 --seed 42 \
        --symmetry "$symmetry" \
        > "$verify_dir/typescript-symmetry-$symmetry"
    cmp "$verify_dir/java-symmetry-$symmetry" \
        "$verify_dir/typescript-symmetry-$symmetry"
done

"$java_runner" solve ../java/testdata/problem-heart.txt \
    --use localization,naked-pair,hidden-pair --unique vh,cell,block \
    > "$verify_dir/java-option-solve"
$typescript_runner solve ../java/testdata/problem-heart.txt \
    --use localization,naked-pair,hidden-pair --unique vh,cell,block \
    > "$verify_dir/typescript-option-solve"
cmp "$verify_dir/java-option-solve" "$verify_dir/typescript-option-solve"

"$java_runner" generate ../java/testdata/pattern-heart.txt --seed 42 \
    --use localization > "$verify_dir/java-option-use"
$typescript_runner generate ../java/testdata/pattern-heart.txt --seed 42 \
    --use localization > "$verify_dir/typescript-option-use"
cmp "$verify_dir/java-option-use" "$verify_dir/typescript-option-use"

"$java_runner" generate ../java/testdata/pattern-heart.txt --seed 42 \
    --unique cell > "$verify_dir/java-option-unique"
$typescript_runner generate ../java/testdata/pattern-heart.txt --seed 42 \
    --unique cell > "$verify_dir/typescript-option-unique"
cmp "$verify_dir/java-option-unique" "$verify_dir/typescript-option-unique"

"$java_runner" generate ../java/testdata/use-none-pattern.txt --seed 42 \
    --use none > "$verify_dir/java-option-use-none"
$typescript_runner generate ../java/testdata/use-none-pattern.txt --seed 42 \
    --use none > "$verify_dir/typescript-option-use-none"
cmp "$verify_dir/java-option-use-none" \
    "$verify_dir/typescript-option-use-none"

"$java_runner" generate ../java/testdata/pattern-heart.txt --seed 42 \
    --dp-min 13000 --dp-max -1 > "$verify_dir/java-option-dp"
$typescript_runner generate ../java/testdata/pattern-heart.txt --seed 42 \
    --dp-min 13000 --dp-max -1 > "$verify_dir/typescript-option-dp"
cmp "$verify_dir/java-option-dp" "$verify_dir/typescript-option-dp"

"$java_runner" random --hints 20 --seed 1 --forbidden 9 \
    > "$verify_dir/java-option-random"
$typescript_runner random --hints 20 --seed 1 --forbidden 9 \
    > "$verify_dir/typescript-option-random"
cmp "$verify_dir/java-option-random" "$verify_dir/typescript-option-random"

"$java_runner" solve ../java/testdata/xml-vertical-off.xml \
    > "$verify_dir/java-xml-vertical-off-solve"
$typescript_runner solve ../java/testdata/xml-vertical-off.xml \
    > "$verify_dir/typescript-xml-vertical-off-solve"
cmp "$verify_dir/java-xml-vertical-off-solve" \
    "$verify_dir/typescript-xml-vertical-off-solve"

"$java_runner" generate ../java/testdata/xml-vertical-off.xml --seed 42 \
    > "$verify_dir/java-xml-vertical-off-generate"
$typescript_runner generate ../java/testdata/xml-vertical-off.xml --seed 42 \
    > "$verify_dir/typescript-xml-vertical-off-generate"
cmp "$verify_dir/java-xml-vertical-off-generate" \
    "$verify_dir/typescript-xml-vertical-off-generate"

"$java_runner" generate ../java/testdata/xml-seed.xml --seed 42 \
    > "$verify_dir/java-xml-seed-generate"
$typescript_runner generate ../java/testdata/xml-seed.xml --seed 42 \
    > "$verify_dir/typescript-xml-seed-generate"
cmp "$verify_dir/java-xml-seed-generate" \
    "$verify_dir/typescript-xml-seed-generate"

"$java_runner" solve ../java/testdata/xml-multiple-groups.xml \
    > "$verify_dir/java-xml-multiple-groups"
$typescript_runner solve ../java/testdata/xml-multiple-groups.xml \
    > "$verify_dir/typescript-xml-multiple-groups"
cmp "$verify_dir/java-xml-multiple-groups" \
    "$verify_dir/typescript-xml-multiple-groups"

mkdir -p "$verify_dir/java-helper"
"$javac" -encoding UTF-8 -cp ../java/build/classes \
    -d "$verify_dir/java-helper" \
    ../java/verify/RewriteXmlConstraintDriver.java
"$java" -cp "../java/build/classes:$verify_dir/java-helper" \
    jp.gr.puzzle.npgen2007.RewriteXmlConstraintDriver \
    blocks ../java/testdata/xml-diagonal-order.xml \
    > "$verify_dir/java-xml-diagonal-order"
node --import tsx --input-type=module - \
    ../java/testdata/xml-diagonal-order.xml \
    > "$verify_dir/typescript-xml-diagonal-order" <<'EOF'
import { readNumberPlaceFile } from "./src/xml.ts";
import { buildXmlVariant } from "./src/variant.ts";

const source = await readNumberPlaceFile(process.argv[2]);
const variant = buildXmlVariant(source, false, false, false);
console.log(`BLOCKS ${variant.block.blockCount}`);
for (let index = 0; index < variant.block.blockCount; index++) {
    console.log(Array.from(variant.block.getBlock(index)).join(" "));
}
EOF
cmp "$verify_dir/java-xml-diagonal-order" \
    "$verify_dir/typescript-xml-diagonal-order"

"$java_runner" solve ../java/testdata/xml-hint-comment.xml \
    --out "$verify_dir/java-xml-hint-comment.xml"
$typescript_runner solve ../java/testdata/xml-hint-comment.xml \
    --out "$verify_dir/typescript-xml-hint-comment.xml"
cmp "$verify_dir/java-xml-hint-comment.xml" \
    "$verify_dir/typescript-xml-hint-comment.xml"

"$java_runner" solve ../java/testdata/xml-no-hint.xml \
    --out "$verify_dir/java-xml-no-hint.xml"
$typescript_runner solve ../java/testdata/xml-no-hint.xml \
    --out "$verify_dir/typescript-xml-no-hint.xml"
cmp "$verify_dir/java-xml-no-hint.xml" \
    "$verify_dir/typescript-xml-no-hint.xml"

"$java_runner" solve ../java/testdata/xml-vertical-off.xml --no-horizontal \
    --out "$verify_dir/java-xml-no-lines.xml"
$typescript_runner solve ../java/testdata/xml-vertical-off.xml --no-horizontal \
    --out "$verify_dir/typescript-xml-no-lines.xml"
cmp "$verify_dir/java-xml-no-lines.xml" \
    "$verify_dir/typescript-xml-no-lines.xml"

"$java_runner" solve ../java/testdata/xml-vertical-off.xml --unique none \
    > "$verify_dir/java-unique-none"
$typescript_runner solve ../java/testdata/xml-vertical-off.xml --unique none \
    > "$verify_dir/typescript-unique-none"
cmp "$verify_dir/java-unique-none" "$verify_dir/typescript-unique-none"

set +e
"$java_runner" solve ../java/testdata/no-answer.txt \
    > "$verify_dir/java-no-answer.stdout" \
    2> "$verify_dir/java-no-answer.stderr"
java_no_answer_exit=$?
$typescript_runner solve ../java/testdata/no-answer.txt \
    > "$verify_dir/no-answer.stdout" 2> "$verify_dir/no-answer.stderr"
no_answer_exit=$?
"$java_runner" random --hints 20 --seed 0 --attempts 1 \
    > "$verify_dir/java-attempts-limited.stdout" \
    2> "$verify_dir/java-attempts-limited.stderr"
java_attempts_limited_exit=$?
$typescript_runner random --hints 20 --seed 0 --attempts 1 \
    > "$verify_dir/attempts-limited.stdout" \
    2> "$verify_dir/attempts-limited.stderr"
attempts_limited_exit=$?
"$java_runner" generate ../java/testdata/pattern-heart.txt \
    --seed 0 --attempts 1 \
    > "$verify_dir/java-attempts-limited-generate.stdout" \
    2> "$verify_dir/java-attempts-limited-generate.stderr"
java_attempts_limited_generate_exit=$?
$typescript_runner generate ../java/testdata/pattern-heart.txt \
    --seed 0 --attempts 1 \
    > "$verify_dir/attempts-limited-generate.stdout" \
    2> "$verify_dir/attempts-limited-generate.stderr"
attempts_limited_generate_exit=$?
$typescript_runner generate ../java/testdata/pattern-heart.txt \
    --seed 42 --attempts -1 > "$verify_dir/attempts-invalid.stdout" \
    2> "$verify_dir/attempts-invalid.stderr"
attempts_invalid_exit=$?
$typescript_runner solve "$verify_dir/missing.txt" \
    > "$verify_dir/input-error.stdout" 2> "$verify_dir/input-error.stderr"
input_error_exit=$?
"$java_runner" solve ../java/testdata/problem-heart.txt --dp-min 1 \
    > "$verify_dir/java-solve-dp.stdout" \
    2> "$verify_dir/java-solve-dp.stderr"
java_solve_dp_exit=$?
$typescript_runner solve ../java/testdata/problem-heart.txt --dp-min 1 \
    > "$verify_dir/typescript-solve-dp.stdout" \
    2> "$verify_dir/typescript-solve-dp.stderr"
typescript_solve_dp_exit=$?
set -e
test "$java_no_answer_exit" -eq 1
test "$no_answer_exit" -eq 1
test "$java_attempts_limited_exit" -eq 1
test "$attempts_limited_exit" -eq 1
test "$java_attempts_limited_generate_exit" -eq 1
test "$attempts_limited_generate_exit" -eq 1
test "$attempts_invalid_exit" -eq 2
test "$input_error_exit" -eq 2
test "$java_solve_dp_exit" -eq 2
test "$typescript_solve_dp_exit" -eq 2
cmp "$verify_dir/java-solve-dp.stdout" \
    "$verify_dir/typescript-solve-dp.stdout"
cmp "$verify_dir/java-solve-dp.stderr" \
    "$verify_dir/typescript-solve-dp.stderr"
test ! -s "$verify_dir/no-answer.stdout"
cmp "$verify_dir/java-no-answer.stderr" "$verify_dir/no-answer.stderr"
test ! -s "$verify_dir/attempts-limited.stdout"
cmp "$verify_dir/java-attempts-limited.stderr" \
    "$verify_dir/attempts-limited.stderr"
test ! -s "$verify_dir/attempts-limited-generate.stdout"
cmp "$verify_dir/java-attempts-limited-generate.stderr" \
    "$verify_dir/attempts-limited-generate.stderr"

"$java_runner" generate ../java/testdata/pattern-heart.txt \
    --seed 42 --attempts 0 > "$verify_dir/java-attempts-unlimited-generate"
$typescript_runner generate ../java/testdata/pattern-heart.txt \
    --seed 42 --attempts 0 \
    > "$verify_dir/typescript-attempts-unlimited-generate"
cmp "$verify_dir/java-attempts-unlimited-generate" \
    "$verify_dir/typescript-attempts-unlimited-generate"
"$java_runner" random --hints 20 --seed 40 --attempts 0 \
    > "$verify_dir/java-attempts-unlimited-random"
$typescript_runner random --hints 20 --seed 40 --attempts 0 \
    > "$verify_dir/typescript-attempts-unlimited-random"
cmp "$verify_dir/java-attempts-unlimited-random" \
    "$verify_dir/typescript-attempts-unlimited-random"

$typescript_runner bench --count 1 --seed 1 > "$verify_dir/bench"
grep -q '^COUNT 1$' "$verify_dir/bench"
grep -q '^SUCCEEDED 1$' "$verify_dir/bench"

echo "Variant and XML output matches the Java reference."
echo "SYMMETRY_MATCHES rot2=1 mirror-h=1 mirror-v=1 none=1"
echo "XML_FEATURE_MATCHES vertical=2 seed=1 groups=1 diagonal-order=1 metadata=3"
echo "EXIT_CODES no-answer=1 input-error=2 solve-dp=2"
echo "ATTEMPTS_CHECKS limited-generate=1 limited-random=1 unlimited-generate=1 unlimited-random=1 invalid=1"
echo "TypeScript output matches the Java reference."
