# Contract: バーコード読み取り（BarcodeScanner）

**Module**: インターフェースは `:domain`、実装は `:data` ないし `:app`
**関連**: [research.md](../research.md) R-003 / FR-001, FR-003

---

## インターフェース

```kotlin
interface BarcodeScanner {
    suspend fun scan(): ScanResult
}

sealed interface ScanResult {
    data class Scanned(val rawValue: String) : ScanResult  // 検証前の生値。ISBN とは限らない
    data object Cancelled : ScanResult                     // 利用者が閉じた／手入力へ切り替えた
    data class Unavailable(val cause: Throwable) : ScanResult  // カメラ権限なし、モジュール未配信ほか
}
```

**抽象化の目的**: 読み取り実装が UI とドメイン層に影響を与えないよう、この契約を境界とする。当初は Google Code Scanner（`play-services-code-scanner`）の採用を想定していたが、下段の読み捨てと読み取り継続が実現できず、契約要件を満たすため CameraX + ML Kit の自前実装へ差し替えた。インターフェース境界を先に引いていたため、差し替えは `RecordScreen` のスキャナ生成1行に収まり、UI とドメインへ波及しなかった。

---

## 契約

| 項目 | 規定 |
| --- | --- |
| 戻り値 | `rawValue` は検証前の生の読み取り値。ISBN としての妥当性判定は `Isbn.parse`（[domain-api.md](./domain-api.md)）が行う |
| 例外 | `scan` は例外を投げない。失敗は `Unavailable` として返す |
| 対象シンボル | EAN-13。書籍バーコードは上段が ISBN、下段が価格・分類コード。**上段のみを対象とし、下段（`192` で始まる日本図書コード）は読み捨てる** |
| 権限 | カメラ権限が無い場合、権限要求は呼び出し側（UI）が行う。拒否された場合は `Unavailable` |
| 手入力への切り替え | `Cancelled` を受け取った UI は、**エラーを出さずに ISBN 手入力へ遷移する**。手入力は例外処理ではなく同格の経路（FR-003、憲法 原則VI） |

---

## 実装: CameraXMlKitBarcodeScanner

- CameraX のプレビューと ML Kit の解析を自前で組み、**プレビューを維持したまま連続解析する**。
- 採用条件は `Isbn.parse(rawValue).isSuccess`。接頭辞（978/979）とチェックディジットの両方を検証するため、下段の日本図書コード（`192` 始まり）と誤読を同時に排除できる。判定条件を実装側で二重に定義しない。
- **採用できない値は無言で捨て、読み取りを続ける**。エラーを出さず、カメラも閉じない（Issue #1）。
- ML Kit はバンドル版（`com.google.mlkit:barcode-scanning`）を用いる。モデルの初回ダウンロードが不要であり、圏外の個室でも初回から読み取れる。
- トーチ（ライト）の切替ボタンを持つ。暗所での効果は実機で確認する。
- スキャン画面は専用の `ScanActivity` として起動し、結果を `ActivityResult` で返す。`suspend fun scan()` の形は `CompletableDeferred` で保つ。

## 採用しなかった実装: GoogleCodeScannerBarcodeScanner

`play-services-code-scanner` のスキャン UI をそのまま用いる初期実装。実装量は小さかったが、`startScan()` 1回につき1件返して終了するため、**下段を読み捨てて読み取りを継続できない**。上の対象シンボルの規定を満たせず、差し替えた（Issue #1）。

Google Play 開発者サービス経由のモジュール配信を必要とする点も、オフラインの個室という利用環境に合わなかった。

---

## 実機での確認が必要な項目（憲法 原則IV）

- 下段のバーコードを読んでもカメラが閉じず、エラーも出ないこと（Issue #1 の受け入れ条件）
- 下段に向けた状態から上段へずらすと確定すること
- 個室の照明環境（暗所）での読み取り可否と所要時間、およびトーチの効果
- 棚番号シールがバーコードを覆う頻度（要求定義書 9. 未確定事項）。頻度が高い場合、手入力を主導線へ組み替える判断が必要になる
- 片手保持での読み取り成功率
- 戻る操作で、エラーを出さずに手入力へ落ちること（FR-003, SC-002）
- オフライン状態での初回スキャン（バンドル版 ML Kit の効果確認）

## テスト方針

- フェイク実装（`Scanned` / `Cancelled` / `Unavailable` を返す）を用いて、**呼び出し側の分岐**をユニットテストで検証する。
- 読み取りそのものの自動テストは書かない。エミュレータで検証できないため（憲法 原則IV）。
