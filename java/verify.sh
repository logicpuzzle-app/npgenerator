#!/bin/sh
set -eu

cd "$(dirname "$0")"

if [ -x /opt/homebrew/opt/openjdk@17/bin/javac ]; then
    JAVAC=/opt/homebrew/opt/openjdk@17/bin/javac
    JAVA=/opt/homebrew/opt/openjdk@17/bin/java
else
    JAVAC=$(command -v javac)
    JAVA=$(command -v java)
fi

ORIGINAL=../../external/puzzle-generator-japan/wayback/extracted/NPGeneratorV2_0_2_src
VERIFY_BUILD=build/verify
VANILLA_CLASSES=$VERIFY_BUILD/vanilla
DETERMINISTIC_CLASSES=$VERIFY_BUILD/deterministic
DETERMINISTIC_SOURCE=$VERIFY_BUILD/original-source
RESULTS=$VERIFY_BUILD/results
REWRITE_TEST_CLASSES=$VERIFY_BUILD/rewrite-test

./build.sh
rm -rf "$VERIFY_BUILD"
mkdir -p "$VANILLA_CLASSES" "$DETERMINISTIC_CLASSES" "$DETERMINISTIC_SOURCE" \
    "$RESULTS" "$REWRITE_TEST_CLASSES"

"$JAVAC" -encoding UTF-8 -cp build/classes -d "$REWRITE_TEST_CLASSES" \
    verify/RandomCompatibilityDriver.java verify/RewriteBlockSplitDriver.java \
    verify/RewriteXmlDriver.java verify/RewriteXmlConstraintDriver.java
"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RandomCompatibilityDriver

find "$ORIGINAL/jp/gr/puzzle/npv2/core" "$ORIGINAL/jp/gr/puzzle/npv2/xml" \
    -name '*.java' -print | sort > "$VERIFY_BUILD/vanilla-sources.txt"
"$JAVAC" -encoding SJIS -nowarn -d "$VANILLA_CLASSES" \
    @"$VERIFY_BUILD/vanilla-sources.txt" 2>"$VERIFY_BUILD/vanilla-javac.log"
"$JAVAC" -encoding UTF-8 -cp "$VANILLA_CLASSES" -d "$VANILLA_CLASSES" \
    verify/OriginalSolveDriver.java

cp -R "$ORIGINAL/jp" "$DETERMINISTIC_SOURCE/"
GENERATOR_SOURCE=$DETERMINISTIC_SOURCE/jp/gr/puzzle/npv2/core/Generator.java
BLOCK_SPLIT_SOURCE=$DETERMINISTIC_SOURCE/jp/gr/puzzle/npv2/core/BlockSplit.java
UTILITY_SOURCE=$DETERMINISTIC_SOURCE/jp/gr/puzzle/npv2/core/Utility.java
perl -pi -e \
    's/Collections\.shuffle\(hintList\);/Collections.shuffle(hintList, ReferenceRandom.adapter());/' \
    "$GENERATOR_SOURCE"
perl -pi -e \
    's/Collections\.shuffle\(edge\);/Collections.shuffle(edge, ReferenceRandom.adapter());/' \
    "$BLOCK_SPLIT_SOURCE"
perl -0pi -e \
    's/double r = Math\.random\(\);\s*return \(int\)\(r \* n - 1e-10\) ;/return ReferenceRandom.bounded(n);/s' \
    "$UTILITY_SOURCE"
find "$DETERMINISTIC_SOURCE/jp/gr/puzzle/npv2/core" \
    "$DETERMINISTIC_SOURCE/jp/gr/puzzle/npv2/xml" -name '*.java' -print \
    | sort > "$VERIFY_BUILD/deterministic-sources.txt"
printf '%s\n' verify/ReferenceRandom.java >> "$VERIFY_BUILD/deterministic-sources.txt"
"$JAVAC" -encoding SJIS -nowarn -d "$DETERMINISTIC_CLASSES" \
    @"$VERIFY_BUILD/deterministic-sources.txt" 2>"$VERIFY_BUILD/deterministic-javac.log"
"$JAVAC" -encoding UTF-8 -cp "$DETERMINISTIC_CLASSES" \
    -d "$DETERMINISTIC_CLASSES" \
    verify/OriginalSolveDriver.java verify/OriginalGenerateDriver.java \
    verify/OriginalXmlDriver.java verify/OriginalXmlFeatureDriver.java

solve_count=0
for problem in testdata/problem-*.txt; do
    name=${problem##*/}
    ./run.sh solve "$problem" > "$RESULTS/rewrite-$name.out"
    "$JAVA" -cp "$VANILLA_CLASSES" jp.gr.puzzle.npv2.core.OriginalSolveDriver \
        "$problem" > "$RESULTS/original-$name.out"
    diff -u "$RESULTS/original-$name.out" "$RESULTS/rewrite-$name.out"
    expected="testdata/expected/solve-${name%.txt}.out"
    diff -u "$expected" "$RESULTS/rewrite-$name.out"
    solve_count=$((solve_count + 1))
done

generate_count=0
for pattern in testdata/pattern-*.txt; do
    name=${pattern##*/}
    ./run.sh generate "$pattern" --seed 42 > "$RESULTS/rewrite-$name.out"
    "$JAVA" -cp "$DETERMINISTIC_CLASSES" \
        jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
        generate "$pattern" 42 > "$RESULTS/original-$name.out"
    diff -u "$RESULTS/original-$name.out" "$RESULTS/rewrite-$name.out"
    generate_count=$((generate_count + 1))
done
diff -u testdata/expected/generate-heart-seed42.out \
    "$RESULTS/rewrite-pattern-heart.txt.out"

./run.sh generate testdata/variant-size6-pattern.txt \
    --size 6 --blocks 3x2 --seed 42 > "$RESULTS/rewrite-variant-size6.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    generate testdata/variant-size6-pattern.txt 42 \
    --size 6 --blocks 3x2 > "$RESULTS/original-variant-size6.out"
diff -u "$RESULTS/original-variant-size6.out" \
    "$RESULTS/rewrite-variant-size6.out"

./run.sh generate testdata/pattern-heart.txt --diagonal --seed 42 \
    > "$RESULTS/rewrite-variant-diagonal.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    generate testdata/pattern-heart.txt 42 --diagonal \
    > "$RESULTS/original-variant-diagonal.out"
diff -u "$RESULTS/original-variant-diagonal.out" \
    "$RESULTS/rewrite-variant-diagonal.out"

"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    blocksplit 6 42 > "$RESULTS/original-blocksplit-size6.out"
"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteBlockSplitDriver \
    6 42 > "$RESULTS/rewrite-blocksplit-size6.out"
diff -u "$RESULTS/original-blocksplit-size6.out" \
    "$RESULTS/rewrite-blocksplit-size6.out"

./run.sh generate testdata/variant-size6-pattern.txt \
    --size 6 --blocks random --seed 42 \
    > "$RESULTS/rewrite-variant-random-block.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    generate testdata/variant-size6-pattern.txt 42 \
    --size 6 --blocks random \
    > "$RESULTS/original-variant-random-block.out"
diff -u "$RESULTS/original-variant-random-block.out" \
    "$RESULTS/rewrite-variant-random-block.out"

./run.sh generate testdata/variant-size6-pattern.txt \
    --size 6 --blocks @testdata/variant-size6-blocks.txt --seed 42 \
    > "$RESULTS/rewrite-variant-free-block.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    generate testdata/variant-size6-pattern.txt 42 \
    --size 6 --blocks @testdata/variant-size6-blocks.txt \
    > "$RESULTS/original-variant-free-block.out"
diff -u "$RESULTS/original-variant-free-block.out" \
    "$RESULTS/rewrite-variant-free-block.out"

./run.sh random --size 6 --blocks 3x2 --hints 20 --seed 42 \
    > "$RESULTS/rewrite-variant-random-size6.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    random 20 42 --size 6 --blocks 3x2 \
    > "$RESULTS/original-variant-random-size6.out"
diff -u "$RESULTS/original-variant-random-size6.out" \
    "$RESULTS/rewrite-variant-random-size6.out"

"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteXmlDriver \
    write "$RESULTS/rewrite-roundtrip.xml"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalXmlDriver \
    read "$RESULTS/rewrite-roundtrip.xml" \
    > "$RESULTS/original-read-rewrite-xml.out"
"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteXmlDriver \
    read "$RESULTS/rewrite-roundtrip.xml" \
    > "$RESULTS/rewrite-read-rewrite-xml.out"
diff -u "$RESULTS/original-read-rewrite-xml.out" \
    "$RESULTS/rewrite-read-rewrite-xml.out"

"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalXmlDriver \
    write "$RESULTS/original-roundtrip.xml"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalXmlDriver \
    read "$RESULTS/original-roundtrip.xml" \
    > "$RESULTS/original-read-original-xml.out"
"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteXmlDriver \
    read "$RESULTS/original-roundtrip.xml" \
    > "$RESULTS/rewrite-read-original-xml.out"
diff -u "$RESULTS/original-read-original-xml.out" \
    "$RESULTS/rewrite-read-original-xml.out"

./run.sh generate testdata/variant-size6-pattern.txt \
    --size 6 --blocks 3x2 --seed 42 \
    --out "$RESULTS/cli-generated.xml"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalXmlDriver \
    read "$RESULTS/cli-generated.xml" \
    > "$RESULTS/original-read-cli-xml.out"
"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteXmlDriver \
    read "$RESULTS/cli-generated.xml" \
    > "$RESULTS/rewrite-read-cli-xml.out"
diff -u "$RESULTS/original-read-cli-xml.out" \
    "$RESULTS/rewrite-read-cli-xml.out"
./run.sh solve "$RESULTS/cli-generated.xml" \
    > "$RESULTS/rewrite-solve-cli-xml.out"
grep -q '^SOLUTION$' "$RESULTS/rewrite-solve-cli-xml.out"

./run.sh generate testdata/pattern-heart.txt --seed 42 \
    --out "$RESULTS/cli-default-block.xml"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalXmlDriver \
    read "$RESULTS/cli-default-block.xml" \
    > "$RESULTS/original-read-cli-default-xml.out"
"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteXmlDriver \
    read "$RESULTS/cli-default-block.xml" \
    > "$RESULTS/rewrite-read-cli-default-xml.out"
diff -u "$RESULTS/original-read-cli-default-xml.out" \
    "$RESULTS/rewrite-read-cli-default-xml.out"

./run.sh generate testdata/pattern-heart.txt --seed 42 \
    --use localization > "$RESULTS/rewrite-use-localization.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    generate testdata/pattern-heart.txt 42 \
    --use localization > "$RESULTS/original-use-localization.out"
diff -u "$RESULTS/original-use-localization.out" \
    "$RESULTS/rewrite-use-localization.out"

./run.sh generate testdata/pattern-heart.txt --seed 42 \
    --use localization,naked-pair,hidden-pair \
    > "$RESULTS/rewrite-use-pairs.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    generate testdata/pattern-heart.txt 42 \
    --use localization,naked-pair,hidden-pair \
    > "$RESULTS/original-use-pairs.out"
diff -u "$RESULTS/original-use-pairs.out" "$RESULTS/rewrite-use-pairs.out"

./run.sh generate testdata/pattern-heart.txt --seed 42 \
    --unique cell > "$RESULTS/rewrite-unique-cell.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    generate testdata/pattern-heart.txt 42 \
    --unique cell > "$RESULTS/original-unique-cell.out"
diff -u "$RESULTS/original-unique-cell.out" "$RESULTS/rewrite-unique-cell.out"

./run.sh generate testdata/pattern-heart.txt --seed 42 \
    --dp-min 13000 --dp-max -1 > "$RESULTS/rewrite-dp-range.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    generate testdata/pattern-heart.txt 42 \
    --dp-min 13000 --dp-max -1 > "$RESULTS/original-dp-range.out"
diff -u "$RESULTS/original-dp-range.out" "$RESULTS/rewrite-dp-range.out"

./run.sh generate testdata/pattern-heart.txt --seed 42 \
    --forbidden 9 > "$RESULTS/rewrite-forbidden.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    generate testdata/pattern-heart.txt 42 \
    --forbidden 9 > "$RESULTS/original-forbidden.out"
diff -u "$RESULTS/original-forbidden.out" "$RESULTS/rewrite-forbidden.out"

./run.sh generate testdata/use-none-pattern.txt --seed 42 \
    --use none > "$RESULTS/rewrite-use-none.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    generate testdata/use-none-pattern.txt 42 \
    --use none > "$RESULTS/original-use-none.out"
diff -u "$RESULTS/original-use-none.out" "$RESULTS/rewrite-use-none.out"

"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalXmlFeatureDriver \
    solve testdata/xml-vertical-off.xml \
    > "$RESULTS/original-xml-vertical-off-solve.out"
./run.sh solve testdata/xml-vertical-off.xml \
    > "$RESULTS/rewrite-xml-vertical-off-solve.out"
diff -u "$RESULTS/original-xml-vertical-off-solve.out" \
    "$RESULTS/rewrite-xml-vertical-off-solve.out"

"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalXmlFeatureDriver \
    generate testdata/xml-vertical-off.xml 42 \
    > "$RESULTS/original-xml-vertical-off-generate.out"
./run.sh generate testdata/xml-vertical-off.xml --seed 42 \
    > "$RESULTS/rewrite-xml-vertical-off-generate.out"
diff -u "$RESULTS/original-xml-vertical-off-generate.out" \
    "$RESULTS/rewrite-xml-vertical-off-generate.out"

"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalXmlFeatureDriver \
    generate testdata/xml-seed.xml 42 \
    > "$RESULTS/original-xml-seed-generate.out"
./run.sh generate testdata/xml-seed.xml --seed 42 \
    > "$RESULTS/rewrite-xml-seed-generate.out"
diff -u "$RESULTS/original-xml-seed-generate.out" \
    "$RESULTS/rewrite-xml-seed-generate.out"

"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalXmlFeatureDriver \
    solve testdata/xml-multiple-groups.xml \
    > "$RESULTS/original-xml-multiple-groups.out"
./run.sh solve testdata/xml-multiple-groups.xml \
    > "$RESULTS/rewrite-xml-multiple-groups.out"
diff -u "$RESULTS/original-xml-multiple-groups.out" \
    "$RESULTS/rewrite-xml-multiple-groups.out"

"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalXmlFeatureDriver \
    blocks testdata/xml-diagonal-order.xml \
    > "$RESULTS/original-xml-diagonal-order.out"
"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteXmlConstraintDriver \
    blocks testdata/xml-diagonal-order.xml \
    > "$RESULTS/rewrite-xml-diagonal-order.out"
diff -u "$RESULTS/original-xml-diagonal-order.out" \
    "$RESULTS/rewrite-xml-diagonal-order.out"

./run.sh solve testdata/xml-hint-comment.xml \
    --out "$RESULTS/xml-hint-comment-out.xml"
"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteXmlConstraintDriver \
    metadata "$RESULTS/xml-hint-comment-out.xml" \
    > "$RESULTS/xml-hint-comment-metadata.out"
grep -q '^HINT_PRESENT true$' "$RESULTS/xml-hint-comment-metadata.out"
grep -q '^HINT_COUNT 0$' "$RESULTS/xml-hint-comment-metadata.out"
grep -q '^COMMENT Keep & escape <this> exactly\.$' \
    "$RESULTS/xml-hint-comment-metadata.out"

./run.sh solve testdata/xml-no-hint.xml \
    --out "$RESULTS/xml-no-hint-out.xml"
"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteXmlConstraintDriver \
    metadata "$RESULTS/xml-no-hint-out.xml" \
    > "$RESULTS/xml-no-hint-metadata.out"
grep -q '^HINT_PRESENT true$' "$RESULTS/xml-no-hint-metadata.out"
grep -q '^HINT_COUNT 20$' "$RESULTS/xml-no-hint-metadata.out"

./run.sh solve testdata/xml-vertical-off.xml --no-horizontal \
    --out "$RESULTS/xml-no-lines-out.xml"
"$JAVA" -cp "build/classes:$REWRITE_TEST_CLASSES" \
    jp.gr.puzzle.npgen2007.RewriteXmlConstraintDriver \
    metadata "$RESULTS/xml-no-lines-out.xml" \
    > "$RESULTS/xml-no-lines-metadata.out"
grep -q '^VERTICAL false$' "$RESULTS/xml-no-lines-metadata.out"
grep -q '^HORIZONTAL false$' "$RESULTS/xml-no-lines-metadata.out"

./run.sh random --hints 20 --seed 1 > "$RESULTS/random-1-a.out"
./run.sh random --hints 20 --seed 1 > "$RESULTS/random-1-b.out"
diff -u "$RESULTS/random-1-a.out" "$RESULTS/random-1-b.out"
./run.sh random --hints 20 --seed 1 --symmetry rot4 \
    > "$RESULTS/random-1-explicit-rot4.out"
diff -u "$RESULTS/random-1-a.out" "$RESULTS/random-1-explicit-rot4.out"
"$JAVA" -cp "$DETERMINISTIC_CLASSES" \
    jp.gr.puzzle.npv2.core.OriginalGenerateDriver \
    random 20 1 > "$RESULTS/random-1-original.out"
diff -u "$RESULTS/random-1-original.out" "$RESULTS/random-1-a.out"

for symmetry in rot2 mirror-h mirror-v none; do
    ./run.sh random --size 5 --blocks 5x1 --hints 10 --seed 42 \
        --symmetry "$symmetry" > "$RESULTS/symmetry-$symmetry-a.out"
    ./run.sh random --size 5 --blocks 5x1 --hints 10 --seed 42 \
        --symmetry "$symmetry" > "$RESULTS/symmetry-$symmetry-b.out"
    diff -u "$RESULTS/symmetry-$symmetry-a.out" \
        "$RESULTS/symmetry-$symmetry-b.out"
done

set +e
./run.sh solve testdata/no-answer.txt > "$RESULTS/no-answer.stdout" \
    2> "$RESULTS/no-answer.stderr"
no_answer_exit=$?
./run.sh random --hints 20 --seed 0 --attempts 1 \
    > "$RESULTS/attempts-limited.stdout" \
    2> "$RESULTS/attempts-limited.stderr"
attempts_limited_exit=$?
./run.sh generate testdata/pattern-heart.txt --seed 0 --attempts 1 \
    > "$RESULTS/attempts-limited-generate.stdout" \
    2> "$RESULTS/attempts-limited-generate.stderr"
attempts_limited_generate_exit=$?
./run.sh generate testdata/pattern-heart.txt --seed 42 --attempts -1 \
    > "$RESULTS/attempts-invalid.stdout" \
    2> "$RESULTS/attempts-invalid.stderr"
attempts_invalid_exit=$?
./run.sh solve "$VERIFY_BUILD/missing.txt" > "$RESULTS/input-error.stdout" \
    2> "$RESULTS/input-error.stderr"
input_error_exit=$?
./run.sh solve testdata/problem-heart.txt --dp-min 1 \
    > "$RESULTS/solve-dp.stdout" 2> "$RESULTS/solve-dp.stderr"
solve_dp_exit=$?
set -e
[ "$no_answer_exit" -eq 1 ]
[ "$attempts_limited_exit" -eq 1 ]
[ "$attempts_limited_generate_exit" -eq 1 ]
[ "$attempts_invalid_exit" -eq 2 ]
[ "$input_error_exit" -eq 2 ]
[ "$solve_dp_exit" -eq 2 ]
printf 'RESULT NO_ANSWER\n' > "$RESULTS/no-answer.expected"
cmp "$RESULTS/no-answer.expected" "$RESULTS/no-answer.stderr"
test ! -s "$RESULTS/no-answer.stdout"
printf 'RESULT GENERATE_FAILED attempts=1\n' \
    > "$RESULTS/attempts-limited.expected"
cmp "$RESULTS/attempts-limited.expected" \
    "$RESULTS/attempts-limited.stderr"
test ! -s "$RESULTS/attempts-limited.stdout"
cmp "$RESULTS/attempts-limited.expected" \
    "$RESULTS/attempts-limited-generate.stderr"
test ! -s "$RESULTS/attempts-limited-generate.stdout"

./run.sh generate testdata/pattern-heart.txt --seed 42 --attempts 0 \
    > "$RESULTS/attempts-unlimited-generate.out"
diff -u testdata/expected/generate-heart-seed42.out \
    "$RESULTS/attempts-unlimited-generate.out"
./run.sh random --hints 20 --seed 40 --attempts 0 \
    > "$RESULTS/attempts-unlimited-random.out"
grep -q '^PATTERN$' "$RESULTS/attempts-unlimited-random.out"
grep -q '^PROBLEM$' "$RESULTS/attempts-unlimited-random.out"

./run.sh solve testdata/xml-vertical-off.xml --unique none \
    > "$RESULTS/solve-unique-none.out"

./run.sh bench --count 1 --seed 1 > "$RESULTS/bench.out"
grep -q '^COUNT 1$' "$RESULTS/bench.out"
grep -q '^SUCCEEDED 1$' "$RESULTS/bench.out"

echo "ORIGINAL_COMPILE core+xml: OK"
echo "LCG_COMPATIBILITY 60000"
echo "SOLVE_MATCHES $solve_count"
echo "GENERATE_MATCHES $generate_count"
echo "OPTION_GENERATE_MATCHES use=3 unique=1 dp-range=1 forbidden=1"
echo "VARIANT_MATCHES size6=1 diagonal=1 random-block=1 free-block=1 random-size6=1"
echo "BLOCK_SPLIT_MATCHES size6=1"
echo "XML_ROUND_TRIP original-to-rewrite=1 rewrite-to-original=1 cli=2"
echo "XML_FEATURE_MATCHES vertical=2 seed=1 groups=1 diagonal-order=1 metadata=3"
echo "RANDOM_MATCHES 1"
echo "SYMMETRY_MATCHES rot2=1 mirror-h=1 mirror-v=1 none=1"
echo "EXPECTED_OUTPUT_MATCHES 6"
echo "EXIT_CODES no-answer=1 input-error=2 solve-dp=2"
echo "ATTEMPTS_CHECKS limited-generate=1 limited-random=1 unlimited-generate=1 unlimited-random=1 invalid=1"
echo "BENCH count=1: OK"
