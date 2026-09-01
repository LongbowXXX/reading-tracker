# 設計: CameraX + ML Kit によるバーコード読み取りへの差し替え

**対象 Issue**: [#1 バーコード読み取り: 下段（日本図書コード）を読むとエラーで終了し、上段を読み直せない](https://github.com/LongbowXXX/reading-tracker/issues/1)
**関連**: FR-001, FR-003 / 憲法 原則IV・原則VI / research.md R-003 / contracts/barcode-scanner.md
**日付**: 2026-09-01

---

## 1. 背景と問題

契約 `specs/001-reading-shelf-record/contracts/barcode-scanner.md` は、対象シンボルの規定として
「上段のみを対象とし、下段（`192` で始まる日本図書コード）は**読み捨てる**」と定めている。

現行の `GoogleCodeScannerBarcodeScanner` は読み取った生値をそのまま `ScanResult.Scanned` として返し、
接頭辞の判定をドメイン層の `Isbn.parse` に委ねている。`Isbn.parse` が `InvalidPrefix` を返すと
`RecordViewModel.acceptIsbnInput` がエラー文言を表示し、手入力モードへ切り替える。
Google Code Scanner は `startScan()` 1回につき1件返して終了するため、この時点でカメラも閉じる。

結果として、下段を読んでしまうたびに「エラー表示 + カメラを開き直す操作」が発生する。
**実装が契約を満たしていない**状態であり、仕様変更ではなく実装側の不備である。

個室で本を片手に持った状態では意図せず下段を読むことがあり、そのたびに操作が増える。
これは憲法 原則VI（入力操作を最小手数に保つ）に反する。

## 2. 決定

**CameraX + ML Kit の自前実装へ差し替える。** research.md R-003 が想定していた差し替えを実施する。

プレビューを維持したまま連続解析し、`978` / `979` で始まる有効な ISBN が読めた時点で確定する。
下段を読んでも**何も起こらず**、プレビューはそのまま継続する。

### 棄却した案

| 案 | 棄却理由 |
| --- | --- |
| A: `GoogleCodeScannerBarcodeScanner` 内で無効値を破棄し `startScan()` を繰り返す | Google Code Scanner は値の重複排除を持たない。下段にカメラを向けたままだとスキャン UI が閉じて即座に開き直る動作を連続し、利用者には理由が分からない。エラー表示より体験が悪化しうる |
| B: `RecordViewModel` が `InvalidPrefix` のときだけ自動再スキャンする | 案 A と同じ UI 開き直しが起きる。加えて、契約が実装側の責務と定めた「下段の読み捨て」がドメイン層に残る |

### 副次的な効果

契約書が「実機での確認が必要な項目」として積み残していた**トーチ（ライト）制御**が可能になる。
個室の照明環境（暗所）での読み取りは要求定義書 3.3 が挙げる制約であり、同時に解消する。

## 3. 依存関係の変更

### 追加

- CameraX: `camera-core` / `camera-camera2` / `camera-lifecycle` / `camera-view`
- ML Kit バーコード解析: **`com.google.mlkit:barcode-scanning`（バンドル版）**

バンドル版を選ぶ。Play Services 版（`play-services-mlkit-barcode-scanning`）は Google Play 開発者
サービス経由でモデルが配信されるため、現行と同じく**初回利用時のダウンロードが必要**であり、
圏外の個室で初回スキャンが失敗する問題（R-003 が挙げていた懸念、および現行実装が
`ScanResult.Unavailable` で手入力へ落としている経路）がそのまま残る。
バンドル版は APK が 2〜3MB 増えるが、端末内に閉じた個人用アプリ（憲法 原則V）であり、
サイズよりオフラインでの確実性を取る。

### 削除

- `play-services-code-scanner`
- `GoogleCodeScannerBarcodeScanner`

差し替え後は未使用となるため削除する。フォールバックとしては残さない。
`BarcodeScanner` インターフェースは維持されるため、必要になれば再実装できる。

## 4. コンポーネント構成

```
RecordScreen
  └─ rememberBarcodeScanner()          ← 既存の remember { GoogleCodeScanner… } 1行を置換
       └─ CameraXMlKitBarcodeScanner : BarcodeScanner
            ├─ ActivityResultLauncher で ScanActivity を起動
            └─ CompletableDeferred で結果を待ち ScanResult を返す
                 ↑ ActivityResult
            ScanActivity（Compose + CameraX PreviewView）
              ├─ ImageAnalysis で連続解析（EAN-13 のみ）
              ├─ Isbn.parse(raw).isSuccess の値だけ採用 → finish(RESULT_OK, value)
              ├─ 失敗値は無言で破棄しプレビューを継続    ← 本 Issue の本題
              ├─ トーチ切替ボタン
              └─ ガイド文言と走査枠
```

### 変更しないもの

`BarcodeScanner` 契約、`ScanResult`、`RecordViewModel`、`:domain` モジュールは**一切変更しない**。
契約書が「本契約を満たす限り、UI とドメインの変更は不要であること」と規定した構造をそのまま用いる。

### 各ユニットの責務

| ユニット | 責務 | 依存 |
| --- | --- | --- |
| `ScanActivity` | カメラのライフサイクル管理、連続解析、採用値の判定、トーチ制御。結果を ActivityResult として返す | CameraX, ML Kit, `Isbn`（:domain） |
| `CameraXMlKitBarcodeScanner` | `BarcodeScanner` 契約の実装。Activity の起動と結果の `ScanResult` への変換のみ | `ScanActivity`, `BarcodeScanner`（:domain） |
| `rememberBarcodeScanner()` | Composable として `ActivityResultLauncher` を保持し、上記実装を組み立てて返す | Compose, 上記 |

`ScanActivity` はカメラの詳細を、`CameraXMlKitBarcodeScanner` は契約への適合を、それぞれ単独で持つ。
呼び出し側は `BarcodeScanner` の形しか見ない。

### ファイル配置

| ファイル | 位置 |
| --- | --- |
| `ScanActivity.kt` | `app/src/main/kotlin/io/github/longbowxxx/readingtracker/scanner/` |
| `CameraXMlKitBarcodeScanner.kt` | 同上 |
| `RememberBarcodeScanner.kt`（`rememberBarcodeScanner()`） | 同上。UI 層ではなく scanner パッケージに置き、`RecordScreen` からは1行で参照する |
| `GoogleCodeScannerBarcodeScanner.kt` | 削除 |

`ScanActivity` は `AndroidManifest.xml` に `android:exported="false"` で登録する。
Hilt による注入は不要（`Isbn` は純粋関数、カメラは自前で組む）。

### 採用値の判定

判定ロジックを新設せず、**`Isbn.parse(raw).isSuccess` をそのまま採用条件とする。**

`Isbn.parse` は接頭辞（978/979）に加えてチェックディジットも検証するため、
下段の読み捨てと同時に誤読の排除もできる。192 始まりを拒否することは
`domain/src/test/.../IsbnTest.kt` の既存テストが固定済みであり、
判定条件を二重に定義しない。`:domain` は Android 非依存の純 Kotlin であり `:app` から参照できる。

### `suspend fun scan()` の形の維持

`ActivityResultLauncher` は Composable のライフサイクルに紐づくため、`CameraXMlKitBarcodeScanner`
が単独で保持することはできない。`rememberBarcodeScanner()` が launcher を `remember` し、
`scan()` は `CompletableDeferred<ScanResult>` の完了を待つ。
launcher のコールバックがその Deferred を完了させる。

これにより、ポートの形（`suspend fun scan(): ScanResult`）を変えずに、
プレビューを持つ実装へ差し替えられる。

## 5. データフローと各分岐の帰結

```
RecordScreen「バーコードを読み取る」
  → rememberCameraPermissionRequest（既存・変更なし）
       ├─ 拒否 → viewModel.switchInputMode()（既存の挙動）
       └─ 許可 → viewModel.scan(scanner)
                   → scanner.scan()
                        → ScanActivity
```

| 事象 | ScanActivity の結果 | ScanResult | 後続 |
| --- | --- | --- | --- |
| 上段（978/979、チェックディジット一致） | `RESULT_OK` + ISBN | `Scanned` | 既存の書誌取得へ（変更なし） |
| 下段（192 始まり） | — | — | **何も起きない。**プレビュー継続（Issue の期待動作） |
| チェックディジット不一致の誤読 | — | — | 同上。破棄して継続 |
| 戻る操作 | `RESULT_CANCELED` | `Cancelled` | エラーを出さず手入力へ（FR-003） |
| カメラ初期化失敗 | `RESULT_FIRST_USER` | `Unavailable` | 既存の文言で手入力へ |

### 手入力への切り替え

スキャン画面には「手入力に切り替える」ボタンを置かない。
戻る操作が `Cancelled` を返し、`RecordScreen` の既存の切替導線
（「シールで隠れている場合は ISBN を手入力」）と合わせて FR-003 / SC-002 を満たす。

### ガイド表示

「上段のバーコードに向けてください」という常時文言と走査枠を表示する。
下段を読んでも無反応になるため、なぜ確定しないのかを利用者に伝える必要がある。

## 6. エラー処理

`BarcodeScanner` 契約の規定どおり、`scan` は例外を投げない。
カメラの初期化失敗（プロバイダ取得失敗、バインド失敗）は `ScanActivity` 内で捕捉し、
`RESULT_FIRST_USER` として返して `Unavailable` に変換する。
`RecordViewModel` は既存の「カメラを使えませんでした。ISBN を手入力してください。」を表示する。

カメラ権限は従来どおり呼び出し側（`rememberCameraPermissionRequest`）が扱う。変更しない。

## 7. テスト方針

`:domain` に変更がないため、既存のドメインテストがそのまま回帰テストとして機能する。
採用フィルタは `Isbn.parse` の再利用であり、192 始まりの拒否は `IsbnTest` が既に固定している。

CameraX と ML Kit の実解析は自動テストで検証できない（憲法 原則IV）。
契約書のテスト方針「読み取りそのものの自動テストは書かない」を踏襲する。

### 自動で保証する範囲

- `.\gradlew.bat :domain:test` / `:data:testDebugUnitTest` が通ること
- `.\gradlew.bat spotlessCheck` が通ること
- `.\gradlew.bat assembleDebug` が通ること（憲法 ビルドゲート）

### 実機確認が必要な項目（憲法 原則IV）

完了報告時に未確認として明示する。

- 下段のバーコードを読んでもカメラが閉じず、エラーも出ないこと（本 Issue の受け入れ条件）
- 下段に向けたまま上段へずらすと確定すること
- トーチの点灯・消灯と、暗所での読み取り可否および所要時間
- 片手保持での読み取り成功率と、Google Code Scanner と比べた体感
- 戻る操作で手入力へ落ちること
- オフライン状態での初回スキャン（バンドル版の効果確認）

## 8. ドキュメントの更新

| ファイル | 更新内容 |
| --- | --- |
| `specs/001-reading-shelf-record/contracts/barcode-scanner.md` | 実装セクションを `CameraXMlKitBarcodeScanner` に差し替える。Google Code Scanner は採用しなかった実装として記録する。対象シンボルの規定（下段の読み捨て）は変更しない |
| `specs/001-reading-shelf-record/research.md` R-003 | Decision を CameraX + ML Kit に更新し、Google Code Scanner を初期実装として試した経緯と差し替えの理由を残す |
| `specs/001-reading-shelf-record/tasks.md` | 実装後に `/speckit-converge` で埋め戻す |

## 9. スコープ外

- `Isbn.parse` の接頭辞検証（`InvalidPrefix`）は手入力経路でも必要なため、そのまま維持する
- フォーカス調整やズームの作り込みは行わない。CameraX の既定挙動に任せ、実機確認の結果で判断する
- 記録画面へのプレビューのインライン埋め込みは行わない（`BarcodeScanner` 契約の変更を伴うため）
