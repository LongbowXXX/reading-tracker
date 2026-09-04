# Contract: タイトルの解析（TitleAnalyzer）

**Module**: インターフェースは `:domain`、AI 実装は `:data`、規則ベース実装は `:domain`
**関連**: [research.md](../research.md) R-002 / FR-027, FR-028 / [issue #4](https://github.com/LongbowXXX/reading-tracker/issues/4)

---

## インターフェース

```kotlin
interface TitleAnalyzer {
    suspend fun analyze(rawTitle: String): TitleAnalysis?
}

data class TitleAnalysis(
    val workTitle: String,    // 巻数表記・レーベル名・並列書名を除いた作品名
    val volumeNumber: Int?,   // 読み取れた巻数。読み取れなければ null
)
```

**インターフェースがドメイン層にある理由**: 第一経路であるオンデバイス AI（ML Kit GenAI Prompt API）は
**Android に依存する**ため、`:domain` を純粋な Kotlin に保つ（憲法 原則III）にはここに境界が要る。
あわせて、対応端末が限られる AI 経路を規則ベースの経路へ差し替え可能にする役割も持つ。

---

## 契約

| 項目 | 規定 |
| --- | --- |
| 例外 | `analyze` は例外を投げない。推論の失敗・経路の障害は `null` として返す |
| `null` の意味 | 「判定できなかった」であり「巻数が無い」ではない。**呼び出し側は次の経路へ進む** |
| 巻数なし | 巻数表記を持たない本（ファンブック・短編集）は、`workTitle` を返し `volumeNumber` を `null` にする。これは `analyze` 自体が `null` を返すのとは異なる |
| 空文字 | 空・空白のみの `rawTitle` に対しては `null` を返してよい |
| 照合キー | `TitleAnalyzer` は照合キーを作らない。`workTitle` から `buildMatchKey()`（[domain-api.md](./domain-api.md)）が生成する。**経路によって照合キーが割れないよう、正規化を1箇所に集約する** |
| 冪等性 | 同じ `rawTitle` には同じ結果を返すことが望ましい。**揺れると照合キーが変わり、同一作品が巻ごとに分裂する**（Issue #4 の再発）。AI 経路は温度 0・シード固定とし、`CachingTitleAnalyzer` で結果を固定する |

---

## 実装: 連鎖（ChainedTitleAnalyzer）

```kotlin
class ChainedTitleAnalyzer(
    private val analyzers: List<TitleAnalyzer>,   // [GenAi, RuleBased] の順
    private val perAnalyzerTimeoutMillis: Long = 2_500,
) : TitleAnalyzer
```

**規則**:

1. 先頭から順に `analyze` する
2. `null` 以外が返った時点で確定し、以降の経路は呼ばない
3. `null` なら次の経路へ進む
4. 例外・時間切れも次の経路へ進む
5. すべて尽きたら `null` を返す

**1経路あたりのタイムアウト**: 2,500 ms。オンデバイス推論は Pixel 9 の実測で 0.8〜2.1 秒かかる
（Google 掲載値）。記録は本を手に持った状態で行うため、**待たせるくらいなら規則ベースの結果で進める**
（憲法 原則VI、SC-001 の 30 秒以内）。

**最終的なフォールバック**: `TitleAnalyzer.analyzeOrFallback()` が、連鎖が `null` を返した場合に
規則ベースの結果を用いる。**記録の主導線がタイトル解析の失敗で止まってはならない。**

---

## 実装: オンデバイス AI（GenAiTitleAnalyzer）

| 項目 | 内容 |
| --- | --- |
| 依存 | `com.google.mlkit:genai-prompt:1.0.0-beta4`（Gemini Nano / AICore） |
| 可用性 | `checkStatus()` が `AVAILABLE` のときのみ推論する。`DOWNLOADABLE` ならダウンロードを裏で開始し、その回は `null` を返す。それ以外は `null` |
| 対応端末 | Pixel 9 以降、Galaxy S26、Z Fold7 など。**Galaxy S25 は Prompt API の対応表に含まれない**。非対応端末では常に `null` が返る |
| 推論パラメータ | `temperature = 0` / `topK = 1` / `candidateCount = 1` / `seed` 固定 / `maxOutputTokens = 128` |
| プロンプト | `buildTitleAnalysisPrompt()`（`:domain` の純粋関数）。**指示文は英語**。Prompt API には対応言語の公式記載が無いため（research.md R-002） |
| 応答の解釈 | `parseTitleAnalysisResponse()`（`:domain` の純粋関数）。コードブロックや前後の説明が付いていても読む |
| 応答の妥当性検証 | **作品名が元のタイトルから文字を削っただけか**を確かめる。空白と記号を無視した部分列であることを条件とし、通らなければ `null` を返す。翻訳（`Attack on Titan`）・ローマ字化・創作を弾く |
| 巻数の範囲 | 1〜9999。範囲外は誤読とみなし `null` |
| プライバシー | 推論は端末内で完結し、入出力はネットワークへ出ない（憲法 原則V）。ML Kit は API の利用状況メトリクスを Google へ送る |
| 制限 | アプリがフォアグラウンドにあるときのみ推論できる。バックグラウンドからは不可 |

## 実装: 規則ベース（RuleBasedTitleAnalyzer）

`parseVolumeTitle()`（[domain-api.md](./domain-api.md)）を包む。**`null` を返さない**最終手段であり、
AI 非対応端末では実質的にこの経路が既定になる。巻数表記の語彙と照合キーの正規化規則は
research.md R-002 に記載。

---

## 受け入れ基準

| # | 条件 | 期待 |
| --- | --- | --- |
| A-1 | AI 経路が `TitleAnalysis` を返す | その結果を採用し、規則ベースは呼ばない |
| A-2 | AI 経路が `null` を返す | 規則ベースの結果を採用する |
| A-3 | AI 経路が例外を投げる | 記録は止まらず、規則ベースの結果を採用する |
| A-4 | AI 経路が 2,500 ms を超える | 打ち切り、規則ベースの結果を採用する |
| A-5 | すべての経路が `null` | `analyzeOrFallback()` が規則ベースの結果を返す |
| A-6 | 同じ `rawTitle` を2回解析する | 経路は1度しか呼ばれない（`CachingTitleAnalyzer`） |
| A-7 | AI が翻訳した作品名を返す | 妥当性検証で捨て、規則ベースへ落ちる |
| A-8 | `拳児2` を AI 経路が解析する | `workTitle = 拳児` / `volumeNumber = 2`（**2026-09-04 に Pixel 9 系で確認済み**） |
| A-9 | `ゴルゴ13` を AI 経路が解析する | `workTitle = ゴルゴ13` / `volumeNumber = null`（**2026-09-04 に Pixel 9 系で確認済み**） |

---

## テスト方針

- A-1〜A-7 は `TitleAnalyzer` のフェイク実装を並べて `:domain` のユニットテストで検証する
  （`TitleAnalyzerChainTest.kt`。Android もネットワークも不要）。
- プロンプトの組み立てと応答の解釈は `:domain` の純粋関数として、ユニットテストで仕様を固定する
  （`TitleAnalysisPromptTest.kt` / `TitleAnalysisResponseParserTest.kt`）。
- 巻数表記の語彙と照合キーの正規化は `VolumeTitleParserTest.kt` で固定する。表記の例は
  openBD 1,535 件・NDL 230 件の実データから採る。
- **推論そのものは JVM のユニットテストで検証できない。** AICore への接続が要るため、
  A-8 / A-9 は実機での確認項目とする（憲法 原則IV、[quickstart.md](../quickstart.md)）。**2026-09-04 に Pixel 9 系で確認済み**。
