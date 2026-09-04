# Implementation Plan: 読書記録と棚番号の管理（中核）

**Branch**: `main`（フィーチャーブランチを作成しない運用。理由は [spec.md](./spec.md) の Feature Branch 欄に記載） | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-reading-shelf-record/spec.md`

## Summary

漫画喫茶の個室で1冊読み終えた（または中断した）ときに、バーコード読み取りまたは ISBN 手入力で作品を特定し、読書状態と棚番号を一連の操作で記録する。次の来店時には店舗を選ぶだけで、その店で読める読みかけ作品が棚番号つきで一覧され、次に取るべき巻が分かる。

技術的な要点は3つ。第一に、棚番号の継承・次巻判定・ISBN 検証・作品照合を **`:domain` という Android 非依存の Gradle モジュール**へ隔離し、JUnit で仕様を固定する（憲法 原則III）。第二に、**棚番号を「店舗 × 巻」の UNIQUE 制約**で表現し、店舗独立性をスキーマで担保する。第三に、差し替えが予想される3箇所（書誌情報の取得元、バーコード読み取り方式、タイトル解析方式）をインターフェースで抽象化し、実機検証の結果による方針転換が UI とドメインへ波及しないようにする。**タイトル解析の抽象化は Issue #4 でオンデバイス AI を導入した際に加えたもので、この境界により ML Kit（Android 依存）を `:domain` へ持ち込まずに済んでいる**（research.md R-002）。

本アプリは**オンデバイス AI の活用を前提に機能を拡張していく方針**であり、AI が動作しない端末はサポート対象外とする（[issue #9](https://github.com/LongbowXXX/reading-tracker/issues/9)、FR-032〜FR-035）。そのため起動時に AI の利用可否を判定する**起動ゲート**をアプリ全体に掛け、利用可能になるまで記録・参照の画面へ到達させない。判定は `:domain` のポート `AiAvailability` として抽象化し、モデル未取得（準備待ち・取得中）と非対応を画面で区別する。モデルの取得は従量課金回線での大容量通信を避けるため、利用者の明示操作で開始する（research.md R-008、[contracts/ai-availability.md](./contracts/ai-availability.md)）。

## Technical Context

**Language/Version**: Kotlin 2.x / JVM ターゲット 17

**Primary Dependencies**: Jetpack Compose（BOM）、Room（KSP）、Kotlin Coroutines / Flow、Hilt、CameraX、`com.google.mlkit:barcode-scanning`（バンドル版）、`com.google.mlkit:genai-prompt`（オンデバイス AI。Issue #4 / research.md R-002）、OkHttp、kotlinx.serialization。依存の版は Gradle バージョンカタログ（`gradle/libs.versions.toml`）で一元管理する

**Storage**: Room（端末内 SQLite）。外部サーバは持たない（憲法 原則V）

**Testing**: `:domain` は JUnit 5（純 JVM、エミュレータ不要）。`:data` は Room の制約検証を含むテスト。`:app` の UI は今回自動テストの対象外とし、実機での手動確認とする（[quickstart.md](./quickstart.md)）

**Target Platform**: Android ネイティブ専用。minSdk 26（Android 8.0）、compileSdk / targetSdk 36。iOS 対応・マルチプラットフォーム化は行わない

**Project Type**: mobile-app（Android 単体。バックエンドなし）

**Performance Goals**: 記録の保存完了まで 30 秒以内・棚番号継承時は 5 タップ以内（SC-001）／店舗選択から一覧表示まで 3 操作以内・5 秒以内（SC-003）／書誌取得は 1 経路 3 秒・全体 6 秒でタイムアウトし、手入力へ落とす

**Constraints**: 店舗の Wi-Fi が利用できることを前提とし、オフラインでの動作は要件としない（旧 SC-006 は 2026-09-05 に削除）。書誌情報の取得に失敗した場合は手入力へ落として記録を完了できること（FR-007）。片手操作。オンデバイス AI が動作しない端末はサポート対象外とする（FR-032〜FR-035）

**Scale/Scope**: 利用者1名。想定データ量は店舗 10 件程度、作品数百件、巻数千件。画面数は5前後（記録入力／確認・修正／店舗選択／来店時一覧／巻の詳細）

**NEEDS CLARIFICATION**: なし。書誌データソースは利用者の承認により確定済み（[research.md](./research.md) R-001）

## Constitution Check

*GATE: Phase 0 の前に通過必須。Phase 1 の設計後に再評価する。*

憲法 v1.1.0（[.specify/memory/constitution.md](../../.specify/memory/constitution.md)）の各原則から導出したゲート。

v1.1.0（2026-09-04 改訂）が「開発ワークフローと品質ゲート」へ追加した**図の作成・図の成果物**（Archify を用い、JSON IR と HTML の双方を `docs/diagrams/` へ置く）は、本フィーチャーでは図を作成していないため**非該当**である。

| # | ゲート | 判定基準 | Phase 0 前 | Phase 1 後 |
| --- | --- | --- | --- | --- |
| G1 | 要求定義書を唯一の正とする（原則I） | plan が spec に無い機能を持ち込んでいないこと。すべての設計判断が FR または要求 ID に紐づくこと | PASS | PASS |
| G2 | 購入・所蔵の概念を持ち込まない（原則II） | データモデルに所有・購入・蔵書に相当する列が無いこと。巻の状態が READ / PAUSED の2値のみであること | PASS | PASS |
| G3 | ドメインロジックの Android 非依存分離（原則III） | 棚番号の継承・状態遷移・次巻判定が `:domain`（純 Kotlin）にあり、JUnit テストを持つこと。棚番号が店舗 × 作品 × 巻で一意であることをスキーマで表現していること | PASS | PASS |
| G4 | 実機検証領域の明示（原則IV） | カメラ・暗所・位置情報に関わる項目が、自動テストではなく実機確認として明示されていること | PASS | PASS |
| G5 | サーバを持たない（原則V） | バックエンドを構築しないこと。データが端末内に閉じること。外部通信が書誌取得に限られること | PASS | PASS |
| G6 | 入力操作を最小手数に保つ（原則VI） | 記録の主導線が 5 タップ以内であること。バーコードと手入力が同格であること | PASS | PASS |
| G7 | 日本語でのやりとり（原則VII） | ドキュメント・コメント・コミットメッセージが日本語、識別子が英語であること | PASS | PASS |

### Phase 0 前の評価

- **G2**: [data-model.md](./data-model.md) に所有・購入・蔵書に相当する列を置いていない。`ReadingStatus` は `READ` / `PAUSED` の2値で、契約書（[contracts/domain-api.md](./contracts/domain-api.md)）に「第3の値を追加しないこと」と明記した。
- **G3**: `:domain` を `kotlin("jvm")` モジュールとするため、Android 依存を書くとコンパイルが通らない。原則違反が実装時に自動検出される（[research.md](./research.md) R-004）。
- **G5**: 外部通信は書誌取得の2経路のみ。通知（WorkManager）は D群がスコープ外のため今回登場しない。
- **G5 の更新（2026-09-04, Issue #4）**: タイトル解析にオンデバイス AI（ML Kit GenAI Prompt API / Gemini Nano）を追加した。**推論は端末内で完結し、入出力はネットワークへ出ない**ため原則V に適合する（AICore はインターネットへ直接アクセスせず、モデルの配信も Private Compute Services 経由）。ただし ML Kit は API の利用状況メトリクスを Google へ送る（ML Kit 利用規約 Privacy）。**送られるのは利用状況であり、読書記録そのものではない。** 自前の配信サーバは引き続き持たない。

### Phase 1 設計後の再評価

- **G1**: `applyShelfNumberToWork()` をドメイン関数として置く点が、スコープ外の A-9（一括更新）に触れる。**UI からの導線は作らず、関数とテストに留める。** これは憲法 原則III が当該テストを明示的に要求しているためであり、原則I との衝突を避けるための最小限の措置。詳細は下記 Complexity Tracking に記録した。
- **G3**: `resolveInheritedShelfNumber()` は店舗 ID を引数に取らず、呼び出し側が単一店舗・単一作品に絞ったレコードのみを渡す契約とした。**ドメイン関数が他店舗のレコードへ到達する手段を持たない**構造とすることで、店舗独立性をスキーマ（UNIQUE 制約）と関数シグネチャの二重で担保する。
- **G4**: [contracts/barcode-scanner.md](./contracts/barcode-scanner.md) と [quickstart.md](./quickstart.md) 4章に、実機でしか確認できない項目を列挙した。棚番号シールがバーコードを覆う頻度（要求定義書 9.）の確認も含む。
- **G6**: 書誌取得の失敗を例外扱いせず、`NotFound` / `Unavailable` の双方から手入力へ直行する契約とした（[contracts/bibliography-source.md](./contracts/bibliography-source.md)）。読み取り画面からの手入力切り替えも `Cancelled` として通常経路に含めた。
- **G1 の更新（2026-09-05, Issue #9）**: FR-032〜FR-035 は当初、要求定義書に根拠を持たなかった（`/speckit-analyze` の指摘 D1）。憲法 原則I が「文書に記載のない機能を推測で追加してはならない」「追加が必要なら承認を得てから改訂する」と定めるため、[docs/requirements.md](../../docs/requirements.md) の 3.2 前提条件（AI が動作する端末に限る／店舗の Wi-Fi を利用できる）、3.3 制約（オフライン前提の削除）、および **G 群（G-1, G-2）** を承認のうえ改訂し（2026-09-05）、FR-032〜FR-035 を G-1 / G-2 へ紐づけた。**改訂前に実装へ着手していない。**
- **G4 の更新（2026-09-05, Issue #9）**: オンデバイス AI の可用性判定と、非対応・準備待ちの表示は**実機でしか確認できない**。JVM のユニットテストからは端末の AI 基盤（AICore）へ接続できないため、取得開始の操作から本体への自動遷移、非対応端末での警告表示と記録・参照画面への到達不能、開発ビルドでの続行導線を [quickstart.md](./quickstart.md) 4.6 の実機確認項目として列挙した。**自動テストの対象は `:domain` に置く写像と状態遷移（`AiAvailabilityStatus` への写像、`AiGateState` の遷移）のみ**であり、判定そのものは対象外である（[contracts/ai-availability.md](./contracts/ai-availability.md)）。
- **G6 との関係（2026-09-05, Issue #9）**: 起動ゲートは**記録の主導線のタップ数を増やさない**。`AVAILABLE` の端末では確認中の表示を素通りして本体へ入る。モデル未取得の場合は取得を開始する操作を初回に1度だけ求めるが（FR-034。従量課金回線での自動取得を避けるための意図的な1タップ）、取得の完了後は追加の操作を求めない（SC-009）。そのほかにゲートが操作を求めるのは、非対応・判定不能・取得失敗という**本来先へ進めない状況での再試行だけ**である。

**結論: 全ゲート PASS。** 正当化を要する逸脱は Complexity Tracking の2件のみ。

## Project Structure

### Documentation (this feature)

```text
specs/001-reading-shelf-record/
├── plan.md              # このファイル
├── spec.md              # 仕様（フェーズ2・3の成果物）
├── research.md          # Phase 0 出力: 技術調査と決定
├── data-model.md        # Phase 1 出力: エンティティと制約
├── quickstart.md        # Phase 1 出力: 検証手順
├── contracts/           # Phase 1 出力: インターフェース契約
│   ├── domain-api.md
│   ├── bibliography-source.md
│   ├── barcode-scanner.md
│   ├── title-analyzer.md   # Issue #4 で追加
│   └── ai-availability.md   # Issue #9 で追加
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 出力（/speckit-tasks が作成。本コマンドでは作らない）
```

### Source Code (repository root)

```text
reading-tracker/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml          # 依存の版を一元管理
├── domain/                          # 純 Kotlin。Android 依存なし（憲法 原則III）
│   └── src/
│       ├── main/kotlin/io/github/longbowxxx/readingtracker/domain/
│       │   ├── model/               # Isbn, ReadingStatus, ShelfNumber, Work, Volume ほか
│       │   ├── shelf/               # resolveInheritedShelfNumber, applyShelfNumberToWork
│       │   ├── reading/             # resolveNextVolume
│       │   ├── title/               # parseVolumeTitle（照合キーと巻数抽出）、TitleAnalyzer の
│       │   │                        #   規則ベース・連鎖・キャッシュ実装（Issue #4）
│       │   ├── usecase/             # RecordVolume, UpdateRecord, VisitList,
│       │   │                        #   LinkProvisionalWork の各ユースケース
│       │   ├── ai/                  # AiGateState（判定結果 → 起動ゲートの画面状態。Issue #9）
│       │   └── port/                # BibliographySource, BarcodeScanner, ReadingRepository,
│       │                            #   TitleAnalyzer（Issue #4）, AiAvailability（Issue #9）
│       └── test/kotlin/…            # JUnit 5。仕様書として機能させる
├── data/                            # Android ライブラリ
│   └── src/
│       ├── main/kotlin/…/data/
│       │   ├── db/                  # Room: Entity, DAO, Database, 型コンバータ
│       │   ├── bibliography/        # OpenBd / Ndl / Chained の各 BibliographySource 実装
│       │   ├── title/               # GenAiTitleAnalyzer（Issue #4）
│       │   ├── ai/                  # GenAiAvailability（ML Kit GenAI での可否判定。Issue #9）
│       │   ├── repository/          # ドメインとの境界。DB と書誌取得を束ねる
│       │   └── di/                  # Hilt モジュール（DB・書誌取得・タイトル解析・AI 可用性）
│       └── test/kotlin/…            # Room の制約検証、連鎖規則の検証
└── app/                             # Android アプリ
    └── src/main/kotlin/io/github/longbowxxx/readingtracker/
        ├── MainActivity.kt          # 起動点。NavGraph を AiGateScreen で包む（Issue #9）
        ├── ui/                      # NavGraph（画面遷移の定義）
        ├── ui/record/               # 記録入力・確認・修正（User Story 1, 3, 4）
        ├── ui/visit/                # 店舗選択と来店時一覧（User Story 2）
        ├── ui/link/                 # 暫定記録を正式な作品へ紐づける画面（User Story 3、FR-008）
        ├── ui/ai/                   # 起動ゲートの画面と ViewModel（Issue #9）
        │                            #   AiGateScreen / AiGateViewModel
        ├── scanner/                 # CameraX + ML Kit による BarcodeScanner 実装
        │                            #   BarcodeScannerFactory / ScanActivity / ScanScreen
        │                            #   IsbnBarcodeAnalyzer / IsbnSelection
        └── di/                      # Hilt モジュール
```

**Structure Decision**: Gradle マルチモジュール（`:app` / `:data` / `:domain`）を採用する。依存方向は `:app → :data → :domain` および `:app → :domain` で、`:domain` は他のどのモジュールにも依存しない。この構成を選ぶ理由は、憲法 原則III の「Android 非依存」をレビューではなくビルド構成で強制するためである（[research.md](./research.md) R-004）。ルートパッケージは `io.github.longbowxxx.readingtracker`（R-007）。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| スコープ外の A-9（作品単位の棚番号一括更新）に相当する純粋関数 `applyShelfNumberToWork()` を `:domain` に置く | 憲法 原則III が「作品単位の一括更新が、他店舗の記録に影響しないこと」のテストを明示的に必須としている。対象の関数が存在しなければこのテストを書けない | 一括更新を丸ごと省くと原則III のテスト要件を満たせない。逆に UI 導線まで作ると A-9 をスコープへ引き込み原則I に反する。**関数とテストのみを置き、UI からは呼ばない**ことで双方を満たす |
| 配布ビルドと開発ビルドで起動時の振る舞いが変わる（FR-035） | AICore を持たないエミュレータで UI の確認を継続する必要がある | 回避手段が無いと非対応環境で UI 実装を検証できない。逆に配布ビルドへ残すと、非対応端末で機能を利用させないという FR-033 が骨抜きになる。**開発ビルドに限って続行の導線を出す**ことで双方を満たす |

## 次のフェーズ

- Phase 2（`/speckit-tasks`）でタスクへ分解する。**ドメインロジックとそのユニットテストを、UI やカメラ連携より先に配置する**（憲法「開発ワークフローと品質ゲート」）。
- 実装着手前に `/speckit-analyze` で spec / plan / constitution の整合性を確認する。
