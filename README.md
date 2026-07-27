# NPGenerator 2007 — 4言語リライト

PUZZLE GeneRator JaPan の **NPGenerator V2.0.2**(2007、Time Intermedia。Director: 藤原博文 / Puzzler: 稲葉直貴 / Programmer: Masaya Kiwada)の core エンジンを、Java / Rust / Python / TypeScript の4言語で忠実にリライトしたもの。原典が GPL-3 のため、本リライトも **GPL-3.0-or-later** を継承する。

原典ソース: `../external/puzzle-generator-japan/wayback/extracted/NPGeneratorV2_0_2_src/`(Wayback Machine から復元した唯一の入手手段)

## 構成

| ディレクトリ | 実装 | ビルド/実行 |
|---|---|---|
| java/ | Java 17 基準実装(原典と直接比較する検証ハーネス付き) | `./build.sh` → `./run.sh <cmd>` |
| rust/ | Rust(edition 2021、std のみ) | `cargo build --release` → `target/release/npgen <cmd>` |
| python/ | Python 3.12(stdlib のみ) | `python3 npgen.py <cmd>` |
| typescript/ | TypeScript(strict、tsx 実行) | `npm install` → `node --import tsx src/cli.ts <cmd>` |
| bench/ | 4言語ベンチマーク | `bench/run-bench.sh [count] [seed]` |

## リライトの範囲と方針

- **対象**: core 14 クラス(戦略ベース Solver、難易度 Evaluator、山登り Generator、Status/BlockConstraint/BlockSplit ほか)+ CLI。原典の GUI が持っていたエンジン系機能(手筋・難易度範囲・禁止数字・任意サイズ・ブロック形状・対角線・XML)をすべて CLI 化済み
- **対象外**: Swing GUI 本体・Applet(対話 UI。原典 jar は `external/` に保全済み)
- **V2.0.2 の挙動が正**(V2.0.1 の nakedPair 判定修正、V2.0.2 の NO_ANSWER 設定修正を含む)
- 乱数は java.util.Random 互換の 48bit LCG を4言語で自前実装し、**同一シードなら4言語の出力がバイト単位で一致**する

## CLI(4言語共通)

```
npgen solve <problem.txt|.xml>                 # 解答+難易度を出力(解なしは exit 1)
npgen generate <pattern.txt|.xml> [--seed N]   # ヒントパターンから問題生成
npgen random [--hints K] [--seed N] [--symmetry rot4|rot2|mirror-h|mirror-v|none]
                                                # 対称性を指定したランダム生成
npgen bench [--count N] [--seed N]             # 生成 N 回の所要時間を計測

共通オプション:
  --use <list|none>       使用手筋 (localization,naked-pair,hidden-pair,naked-triple,
                          hidden-triple,x-wing,swordfish)。省略時は全 ON、none で全 OFF
  --unique <list|none>    一意性判定 (vh,cell,block)
  --dp-min N / --dp-max N 難易度範囲(範囲外は捨てて再生成。solve では指定不可 = exit 2)
  --forbidden N           禁止数字(generate/random)
  --attempts N            生成リトライ上限(generate/random、既定100、0は無制限)
  --size N                盤面サイズ 2..25
  --blocks WxH|random|@file  長方形 / ランダム分割(BlockSplit) / 自由形状ラベル格子
  --diagonal              対角線制約
  --no-vertical / --no-horizontal  縦・横制約の無効化
  --format xml / --out f.xml       原典互換 XML(seed・複数 group・comment・hint 保持)
```

- CLI 既定は全手筋 ON(原典 GUI の既定は全 OFF — `--use none` で再現可)、dp-min 既定 0(GUI は 1)。生成リトライは既定 100 回で、`--attempts 0` は GUI と同じ無制限
- solve 失敗時と生成上限到達時は、stderr にそれぞれ `RESULT <解答状態>`、`RESULT GENERATE_FAILED attempts=N` を出力する
- 対角制約の構築順は、XML 入力時は原典 `Utility.makeBlockConstraint`(対角が最後)、CLI `--diagonal` 時は GUI/`ProblemBuilder.build`(対角が先)にそれぞれ一致させている
- `random --symmetry` は `rot4` が既定で、原典 `Random20.java` の出力を維持する。`rot2` / `mirror-h` / `mirror-v` / `none` は本リライト独自拡張。回転・鏡映モードは固定セルを除外し、`none` は 1 以上セル数未満の任意のヒント数を受け付ける

## 検証(2026-07-26〜27 実施)

1. **Java ↔ 原典**: 原典 core+xml を `javac -encoding SJIS` でそのままビルドし直接比較 — solve/generate/random、手筋制限・DP範囲・禁止数字、Size6・対角線・ランダム/自由形状ブロック・BlockSplit 生出力、XML round-trip 双方向、縦横 OFF・seed・複数 group・メタデータ、すべて一致。LCG は 60,000 値一致
2. **Rust / Python / TypeScript ↔ Java**: 全ケース(機能追補・variant 含む)バイト単位一致(各言語の verify.sh で再現可能)
3. 機能網羅性は原典 core 全 public API・GUI 全機能・sample 4種との突合監査済み(未移植は対話 UI のみ)

## ベンチマーク(2026-07-27 最終、Apple M4 Pro、bench --count 5 --seed 1)

| 実装 | 初期実装 | 最適化後 | 改善 |
|---|---|---|---|
| Java (openjdk 17) | 15.2 秒 | **4.1 秒** | 3.7x |
| Rust (release) | 26.2 秒 | **4.6 秒** | 5.8x |
| TypeScript (node 22 + tsx) | 25.4 秒 | **5.6 秒** | 4.6x |
| Python (CPython 3.12) | 437.7 秒 | **151.1 秒** | 2.9x |
| Python (**PyPy 7.3 / 3.10**) | — | **15.9 秒** | 27.5x |

- 4実装とも同一シードで同一の5問を生成(出力バイト一致)しての計測。ELAPSED_MS は各実装の内部計測(プロセス起動時間を含まない)
- 最適化は出力バイト一致を維持したままの表現変更のみ: ブロック内候補位置ビットマスク(candPositions)、Status のバッファ再利用/in-place コピー、配列の平坦化(Java は `Integer[]`→`int[]`、TS は `Int32Array` 化)、localization の交差判定マスク化、popcount のインクリメンタル管理
- 詳細な生値は `bench/results-count5-seed1.txt`
- Python 実装は stdlib のみのため **PyPy でコード変更なしに実行可能**(`pypy3 python/npgen.py ...`)。solve/generate/random とも CPython と出力一致を確認済み
