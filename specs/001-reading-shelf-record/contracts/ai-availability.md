# Contract: オンデバイス AI の可用性（AiAvailability）

**Module**: インターフェースは `:domain`、ML Kit GenAI の実装は `:data`、起動ゲートの画面は `:app`
**関連**: [research.md](../research.md) R-008 / FR-032, FR-033, FR-034, FR-035 / SC-008, SC-009 / [issue #9](https://github.com/LongbowXXX/reading-tracker/issues/9)

---

## インターフェース

```kotlin
enum class AiAvailabilityStatus { AVAILABLE, PREPARING, UNSUPPORTED }

sealed interface AiPreparation {
    data object Started : AiPreparation
    data object Completed : AiPreparation
    data class Failed(val cause: Throwable?) : AiPreparation
}

interface AiAvailability {
    suspend fun status(): AiAvailabilityStatus
    fun prepare(): Flow<AiPreparation>
}
```

**インターフェースがドメイン層にある理由**: 判定の実体である ML Kit GenAI（`checkStatus()` / `download()`）は
**Android に依存する**ため、`:domain` を純粋な Kotlin に保つ（憲法 原則III）にはここに境界が要る。
これにより「判定結果から画面状態を導く」部分（`AiGateState`）を Android 非依存の純粋なロジックとして置け、
エミュレータなしで全分岐をユニットテストで固定できる。beta である ML Kit の API 変更も、
`:data` の `GenAiAvailability` 1箇所に閉じる（research.md R-008）。

---

## 契約

| 項目 | 規定 |
| --- | --- |
| 例外 | `status()` は例外を投げない。端末側の AI 基盤へ到達できないなど**判定できなかった場合は `UNSUPPORTED` を返す**（FR-033 が判定不能を非対応と同様に扱うため） |
| キャッシュ | `status()` は呼ばれるたびに実際の状態を返す。結果を保持してはならない。端末側の更新で可否が変わりうるため、起動のたびに判定する（FR-032） |
| `PREPARING` の意味 | 「対応端末だがモデルが未取得または取得中」であり、非対応ではない。**画面上で `UNSUPPORTED` と混同してはならない**（FR-034） |
| `prepare()` | `status()` が `PREPARING` のときのみ意味を持つ。`AVAILABLE` / `UNSUPPORTED` のときの呼び出しは契約外とし、呼び出し側が呼ばない |
| 自動開始の禁止 | `prepare()` の購読は**利用者の明示的な操作（「ダウンロードを開始」の選択）を受けてから**行う。ゲートが自動で取得を開始してはならない（FR-034） |
| `prepare()` の失敗 | 例外を投げず `Failed` を流して終了する。呼び出し側は再試行の手段を出す（FR-034） |
| `prepare()` の完了 | `Completed` を流した後は `status()` が `AVAILABLE` を返す。**利用者の追加操作なく本体へ進める**（SC-009） |
| 回避手段 | 開発ビルドでの続行（FR-035）は**ポートの責務ではない**。`AiAvailability` はビルド種別を知らず、続行の可否は `:app` の画面側が `BuildConfig.DEBUG` で判断する |

---

## 実装: ML Kit GenAI（GenAiAvailability）

`GenerativeModel` は DI で1インスタンス化し、[title-analyzer.md](./title-analyzer.md) の
`GenAiTitleAnalyzer` と共有する。判定と推論で別のクライアントを持たせない。

### `FeatureStatus` からの写像

| ML Kit `FeatureStatus` | `AiAvailabilityStatus` |
| --- | --- |
| `AVAILABLE` | `AVAILABLE` |
| `DOWNLOADABLE` | `PREPARING` |
| `DOWNLOADING` | `PREPARING` |
| `UNAVAILABLE` | `UNSUPPORTED` |
| 上記以外の値 | `UNSUPPORTED` |
| `checkStatus()` が例外を投げた | `UNSUPPORTED` |

`prepare()` は `download()` の `DownloadStatus` を `AiPreparation` へ写す。
取得の進捗は不定であり、進捗率は契約に含めない（画面は不定のインジケータを出す）。

---

## 起動ゲートの状態（AiGateState）

判定結果と準備結果から画面状態への写像。`:domain` の純粋なロジックとして置く。

| 状態 | 画面 | 遷移 |
| --- | --- | --- |
| 確認中（Checking） | 「AI の対応状況を確認しています」＋インジケータ | `status()` の完了で分岐する |
| 利用可（Available） | ゲートを外し、本体（`ReadingTrackerNavGraph`）を表示する | — |
| 準備待ち（PreparingIdle） | 「AI の準備が必要です」＋「ダウンロードを開始」の導線 | **自動では開始しない。** 利用者が開始を選ぶと取得中へ |
| 取得中（Downloading） | 「AI を準備しています」＋不定の進捗 | `Completed` で利用可へ / `Failed` で準備待ちへ戻す |
| 非対応（Unsupported） | 「この端末は非対応です」＋理由と再試行 | 先へ進めない（SC-008） |

- 準備待ちは「モデルが未取得である」ことを示すだけの状態であり、**この状態で `prepare()` を購読してはならない**。
  購読は利用者が「ダウンロードを開始」を選んだ時点で始める（FR-034）。
- 取得に失敗した場合は準備待ちへ戻し、**失敗した旨と再試行**（再度の「ダウンロードを開始」）を出す（FR-034）。
- 判定が例外で失敗した場合も**非対応と同じ状態**へ写す。文言のみ「確認できませんでした」と分ける（FR-033）。
- 非対応の状態には、`BuildConfig.DEBUG` のときに限り「非対応のまま続行（開発用）」を出す（FR-035）。
  配布ビルドではこの導線が存在してはならない。
- ゲートは**アプリ全体**に掛ける。AI を使わない画面（店舗選択・来店時一覧）も含めて止める（SC-008）。
- `AVAILABLE` のときは確認中から素通りし、準備完了後も追加の操作を求めない。
  **記録の主導線のタップ数を増やさない**（憲法 原則VI、SC-001, SC-009）。

**更新（2026-09-05, Issue #9）**: モデル未取得（`PREPARING`）を検知したときに、
**アプリが自動でダウンロードを開始することを禁止した**（利用者の承認済み）。起動は店舗外でも起こり、
**従量課金のモバイル回線で大容量の取得が始まりうる**ためである。当初は `PREPARING` を検知した時点で
`prepare()` を購読する規定（旧「準備中」の1状態）だったが、これを「準備待ち」と「取得中」の2状態へ分け、
購読は利用者が「ダウンロードを開始」を選んだ時点で始める。**回線種別の判定は行わない** — 権限と実装が増える割に
判断を自動化しきれないため、開始の判断は利用者に委ねる（research.md R-008）。
ポート `AiAvailability` のインターフェース定義は変更していない。変わったのは
**`prepare()` を誰がいつ購読するか**であり、購読の起点を `:app` の利用者操作に置くことが契約となる。

---

## 受け入れ基準

| # | 条件 | 期待 |
| --- | --- | --- |
| A-1 | `status()` が `AVAILABLE` | ゲートを外し、本体を表示する |
| A-2 | `status()` が `PREPARING` | 準備待ちの画面を出し、取得開始の導線を提示する。**この時点では `prepare()` を呼ばない**（FR-034） |
| A-3 | 「ダウンロードを開始」を選ぶ | ここで初めて `prepare()` を購読し、取得中の画面へ遷移する（FR-034） |
| A-4 | `prepare()` が `Completed` を流す | 追加操作なく利用可へ遷移する（SC-009） |
| A-5 | `prepare()` が `Failed` を流す | 準備待ちへ戻し、失敗した旨と再試行の手段を出す（FR-034） |
| A-6 | `status()` が `UNSUPPORTED` | 非対応の画面を出し、記録・参照の画面へ到達させない（SC-008） |
| A-7 | `checkStatus()` が例外を投げる | 非対応と同じ状態へ写し、文言のみ「確認できませんでした」とする（FR-033） |
| A-8 | 再試行を選ぶ | 確認中へ戻り、`status()` を再度呼ぶ（FR-032） |
| A-9 | 開発ビルドで非対応 | 「非対応のまま続行」で本体へ進める。配布ビルドではこの導線が無い（FR-035） |

---

## テスト方針

- A-1〜A-8 のうち、**判定結果・準備結果から画面状態への写像と遷移**は `:domain` のユニットテストで
  全分岐を網羅する（`AiAvailability` のフェイク実装を用いる。Android もネットワークも不要）。
  **準備待ちの間に `prepare()` が呼ばれないこと**も、フェイクの購読回数で固定する。
- **端末が実際に対応しているかの判定は JVM のユニットテストで検証できない。** AICore への接続が要るため、
  対応端末での取得開始の操作から本体への自動遷移、非対応端末での警告表示と到達不能、開発ビルドの続行導線は
  実機での確認項目とする（憲法 原則IV、[quickstart.md](../quickstart.md) 4.6）。

---

## 既存契約との関係

起動ゲートが「`AVAILABLE` でなければ本体に入れない」ことを保証するため、
[title-analyzer.md](./title-analyzer.md) の AI 実装（`GenAiTitleAnalyzer`）から
**モデルのダウンロード催促の責務が外れる**。`DOWNLOADABLE` を検知して裏で `download()` を開始する経路と、
その二重起動を防ぐためのフラグは不要になる。解析側に残るのは
「`AVAILABLE` なら推論し、それ以外・失敗時は `null` を返す」だけであり、
規則ベースへの連鎖（`ChainedTitleAnalyzer`）は推論の失敗・時間切れに対する保険として引き続き機能する。
