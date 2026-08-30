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

**抽象化の目的**: 初期実装は Google Code Scanner（`play-services-code-scanner`）を用いるが、暗所での読み取り精度に問題が出た場合に CameraX + ML Kit の自前実装へ差し替える。差し替えが UI とドメインへ波及しないよう、この契約を境界とする（マスタープロンプトの指定）。

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

## 実装: GoogleCodeScannerBarcodeScanner（初期実装）

- `play-services-code-scanner` が提供するスキャン UI をそのまま用いる。自前のカメラプレビューは持たない。
- Google Play 開発者サービス経由でモジュールが配信されるため、**初回利用時にダウンロードが発生しうる**。オフラインの個室で初回スキャンを行うと失敗する可能性があり、その場合は `Unavailable` を返して手入力へ落とす。
- トーチ（ライト）の制御はこの実装では行えない。暗所での挙動は実機で確認する。

## 差し替え候補: CameraXMlKitBarcodeScanner（未実装）

- 実機検証で読み取り精度に問題が出た場合に着手する。CameraX のプレビューと ML Kit の解析を自前で組み、トーチ制御とフォーカス調整を加える。
- 本契約を満たす限り、UI とドメインの変更は不要であること。

---

## 実機での確認が必要な項目（憲法 原則IV）

自動テストでは検証できない。完了報告時に「実機確認が必要」と明示する。

- 個室の照明環境（暗所）での読み取り可否と所要時間
- 棚番号シールがバーコードを覆う頻度（要求定義書 9. 未確定事項）。頻度が高い場合、手入力を主導線へ組み替える判断が必要になる
- 片手保持での読み取り成功率
- 読み取り画面から手入力への切り替え操作が1操作で行えているか（FR-003, SC-002）
- Google Play 開発者サービスのモジュール初回配信時の挙動

## テスト方針

- フェイク実装（`Scanned` / `Cancelled` / `Unavailable` を返す）を用いて、**呼び出し側の分岐**をユニットテストで検証する。
- 読み取りそのものの自動テストは書かない。エミュレータで検証できないため（憲法 原則IV）。
