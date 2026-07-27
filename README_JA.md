# NPGenerator 2007 — 4言語リライト

PUZZLE GeneRator JaPan の **NPGenerator V2.0.2**(2007、Time Intermedia。Director: 藤原博文 / Puzzler: 稲葉直貴 / Programmer: Masaya Kiwada)のナンプレ自動生成エンジンを、**Java / Rust / Python / TypeScript** の4言語で忠実にリライトしたもの。原典が GPL-3 のため、本リライトも **GPL-3.0-or-later** を継承する(各実装の `LICENSE` 参照)。

English: [README.md](README.md)

## 特徴

- **4言語の出力がバイト単位で一致**。java.util.Random 互換の 48bit LCG を各言語で自前実装しており、同一シードなら同一の問題が生成される
- **原典との一致検証付き**。Java 実装は原典 2007 年ソース(Wayback Machine から復元、`javac -encoding SJIS` でそのままビルド)と直接比較し、他3言語は Java とバイト一致を検証。各実装に `verify.sh` を同梱
- **エンジン機能を完全網羅**。原典 GUI が持っていたエンジン系機能をすべて CLI 化(対話 UI と Applet のみ対象外)
- **最適化済み**。出力一致を保ったまま初期移植比 3〜6倍(候補位置ビットマスク、in-place コピー、配列平坦化)

## 構成

| ディレクトリ | 実装 | ビルド/実行 |
|---|---|---|
| `java/` | Java 17 基準実装(原典比較ハーネス付き) | `./build.sh` → `./run.sh <cmd>` |
| `rust/` | Rust(edition 2021、std のみ) | `cargo build --release` → `target/release/npgen <cmd>` |
| `python/` | Python 3.12(stdlib のみ。PyPy でも無変更で動作) | `python3 npgen.py <cmd>` |
| `typescript/` | TypeScript(strict、tsx 実行) | `npm install` → `node --import tsx src/cli.ts <cmd>` |
| `bench/` | 4言語ベンチマーク | `bench/run-bench.sh [count] [seed]` |

## CLI(4言語共通)

```
npgen solve <problem.txt|.xml>                 # 解答+難易度を出力(解なしは exit 1)
npgen generate <pattern.txt|.xml> [--seed N]   # ヒントパターンから問題生成
npgen random [--hints K] [--seed N] [--symmetry rot4|rot2|mirror-h|mirror-v|none]
npgen bench [--count N] [--seed N]

共通オプション:
  --use <list|none>       使用手筋 (localization,naked-pair,hidden-pair,naked-triple,
                          hidden-triple,x-wing,swordfish)。省略時は全 ON
  --unique <list|none>    一意性判定 (vh,cell,block)
  --dp-min N / --dp-max N 難易度範囲(範囲外は捨てて再生成。solve では指定不可 = exit 2)
  --forbidden N           禁止数字(generate/random)
  --attempts N            生成リトライ上限(既定100、0=無制限=原典GUI相当)
  --size N                盤面サイズ 2..25
  --blocks WxH|random|@file  長方形 / ランダム分割(BlockSplit) / 自由形状ラベル格子
  --diagonal              対角線制約
  --no-vertical / --no-horizontal  縦・横制約の無効化
  --format xml / --out f.xml       原典互換 XML(seed・複数 group・comment・hint 保持)
```

補足:

- CLI 既定は全手筋 ON(原典 GUI の既定は全 OFF — `--use none` で再現可)、dp-min 既定 0(GUI は 1)
- solve 失敗時は stderr に `RESULT NO_ANSWER` 等、生成上限到達時は `RESULT GENERATE_FAILED attempts=N`
- `random --symmetry` は `rot4` が既定で原典 `Random20.java` 互換。他モードは本リライト独自拡張
- 対角制約の構築順は、XML 入力時は原典 `Utility.makeBlockConstraint`(対角が最後)、CLI `--diagonal` 時は GUI/`ProblemBuilder.build`(対角が先)にそれぞれ一致

## 難易度の目安

エンジンは生の難易度ポイント(DP)を返し、原典コードに区分の閾値は無い。原典サイトの実例(20ヒント生成例 4,398pt=「中級のやや難し目」、超難問集 600万〜1,122万pt)に基づく対数スケールの実用目安: 〜1,500 入門 / 〜4,000 初級 / 〜10,000 中級 / 〜10万 上級 / 〜100万 難問 / 100万〜 超難問。

## 検証

- `java/verify.sh`: 復元した原典ソース(core+xml、Shift_JIS)をそのままビルドし、solve・シード付き生成・variant(6x6=3x2、対角線、ランダム/自由形状ブロック、BlockSplit 生出力)・手筋/難易度/禁止数字オプション・XML 双方向 round-trip を突合(原典ソースは本リポジトリには含まれない。puzzle.gr.jp の Wayback アーカイブから復元したもの)
- `rust/` `python/` `typescript/`: 全ケースで Java 基準実装とのバイト一致+決定性を検証

## ベンチマーク(Apple M4 Pro、bench --count 5 --seed 1)

| 実装 | 初期移植 | 最適化後 | 改善 |
|---|---|---|---|
| Java (openjdk 17) | 15.2 秒 | **4.1 秒** | 3.7x |
| Rust (release) | 26.2 秒 | **4.6 秒** | 5.8x |
| TypeScript (node 22 + tsx) | 25.4 秒 | **5.6 秒** | 4.6x |
| Python (CPython 3.12) | 437.7 秒 | **151.1 秒** | 2.9x |
| Python (PyPy 7.3) | — | **15.9 秒** | 27.5x |

同一シードで同一の5問を生成(出力バイト一致)しての内部計測(プロセス起動除く)。最適化はすべて出力不変の表現変更のみ。

## 来歴

原典 NPGenerator V2.0.2 は puzzle.gr.jp(PUZZLE GeneRator JaPan)で配布されていたが、サイトは現存しない。ソース・バイナリ(V1.0.1〜V2.0.2)は Internet Archive の Wayback Machine から復元し、本リライトはその復元ソースに対して検証した。Time Intermedia による後年の教育用再実装は [timedia/puzzle-generator](https://github.com/timedia/puzzle-generator) にある。
