# Implementation Plan: 読書記録と棚番号の管理（中核）

**Branch**: `main`（フィーチャーブランチを作成しない運用。理由は [spec.md](./spec.md) の Feature Branch 欄に記載） | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-reading-shelf-record/spec.md`

## Summary

漫画喫茶の個室で1冊読み終えた（または中断した）ときに、バーコード読み取りまたは ISBN 手入力で作品を特定し、読書状態と棚番号を一連の操作で記録する。次の来店時には店舗を選ぶだけで、その店で読める読みかけ作品が棚番号つきで一覧され、次に取るべき巻が分かる。

技術的な要点は3つ。第一に、棚番号の継承・次巻判定・ISBN 検証・作品照合を **`:domain` という Android 非依存の Gradle モジュール**へ隔離し、JUnit で仕様を固定する（憲法 原則III）。第二に、**棚番号を「店舗 × 巻」の UNIQUE 制約**で表現し、店舗独立性をスキーマで担保する。第三に、差し替えが予想される2箇所（書誌情報の取得元、バーコード読み取り方式）をインターフェースで抽象化し、実機検証の結果による方針転換が UI とドメインへ波及しないようにする。

## Technical Context

**Language/Version**: Kotlin 2.x / JVM ターゲット 17

**Primary Dependencies**: Jetpack Compose（BOM）、Room（KSP）、Kotlin Coroutines / Flow、Hilt、CameraX、`com.google.mlkit:barcode-scanning`（バンドル版）、OkHttp、kotlinx.serialization。依存の版は Gradle バージョンカタログ（`gradle/libs.versions.toml`）で一元管理する

**Storage**: Room（端末内 SQLite）。外部サーバは持たない（憲法 原則V）

**Testing**: `:domain` は JUnit 5（純 JVM、エミュレータ不要）。`:data` は Room の制約検証を含むテスト。`:app` の UI は今回自動テストの対象外とし、実機での手動確認とする（[quickstart.md](./quickstart.md)）

**Target Platform**: Android ネイティブ専用。minSdk 26（Android 8.0）、compileSdk / targetSdk 36。iOS 対応・マルチプラットフォーム化は行わない

**Project Type**: mobile-app（Android 単体。バックエンドなし）

**Performance Goals**: 記録の保存完了まで 30 秒以内・棚番号継承時は 5 タップ以内（SC-001）／店舗選択から一覧表示まで 3 操作以内・5 秒以内（SC-003）／書誌取得は 1 経路 3 秒・全体 6 秒でタイムアウトし、手入力へ落とす

**Constraints**: 電波が届かない個室でも記録の作成・保存・参照が行えること（SC-006）。片手操作。書誌取得以外の全機能がオフラインで完結すること

**Scale/Scope**: 利用者1名。想定データ量は店舗 10 件程度、作品数百件、巻数千件。画面数は5前後（記録入力／確認・修正／店舗選択／来店時一覧／巻の詳細）

**NEEDS CLARIFICATION**: なし。書誌データソースは利用者の承認により確定済み（[research.md](./research.md) R-001）

## Constitution Check

*GATE: Phase 0 の前に通過必須。Phase 1 の設計後に再評価する。*

憲法 v1.0.0（[.specify/memory/constitution.md](../../.specify/memory/constitution.md)）の各原則から導出したゲート。

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

### Phase 1 設計後の再評価

- **G1**: `applyShelfNumberToWork()` をドメイン関数として置く点が、スコープ外の A-9（一括更新）に触れる。**UI からの導線は作らず、関数とテストに留める。** これは憲法 原則III が当該テストを明示的に要求しているためであり、原則I との衝突を避けるための最小限の措置。詳細は下記 Complexity Tracking に記録した。
- **G3**: `resolveInheritedShelfNumber()` は店舗 ID を引数に取らず、呼び出し側が単一店舗・単一作品に絞ったレコードのみを渡す契約とした。**ドメイン関数が他店舗のレコードへ到達する手段を持たない**構造とすることで、店舗独立性をスキーマ（UNIQUE 制約）と関数シグネチャの二重で担保する。
- **G4**: [contracts/barcode-scanner.md](./contracts/barcode-scanner.md) と [quickstart.md](./quickstart.md) 4章に、実機でしか確認できない項目を列挙した。棚番号シールがバーコードを覆う頻度（要求定義書 9.）の確認も含む。
- **G6**: 書誌取得の失敗を例外扱いせず、`NotFound` / `Unavailable` の双方から手入力へ直行する契約とした（[contracts/bibliography-source.md](./contracts/bibliography-source.md)）。読み取り画面からの手入力切り替えも `Cancelled` として通常経路に含めた。

**結論: 全ゲート PASS。** 正当化を要する逸脱は Complexity Tracking の1件のみ。

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
│   └── barcode-scanner.md
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
│       │   ├── title/               # parseVolumeTitle（照合キーと巻数抽出）
│       │   └── port/                # BibliographySource, BarcodeScanner のインターフェース
│       └── test/kotlin/…            # JUnit 5。仕様書として機能させる
├── data/                            # Android ライブラリ
│   └── src/
│       ├── main/kotlin/…/data/
│       │   ├── db/                  # Room: Entity, DAO, Database, 型コンバータ
│       │   ├── bibliography/        # OpenBd / Ndl / Chained の各 BibliographySource 実装
│       │   └── repository/          # ドメインとの境界。DB と書誌取得を束ねる
│       └── test/kotlin/…            # Room の制約検証、連鎖規則の検証
└── app/                             # Android アプリ
    └── src/main/kotlin/…/app/
        ├── ui/record/               # 記録入力・確認・修正（User Story 1, 3, 4）
        ├── ui/visit/                # 店舗選択と来店時一覧（User Story 2）
        ├── ui/theme/
        ├── scanner/                 # GoogleCodeScanner による BarcodeScanner 実装
        └── di/                      # Hilt モジュール
```

**Structure Decision**: Gradle マルチモジュール（`:app` / `:data` / `:domain`）を採用する。依存方向は `:app → :data → :domain` および `:app → :domain` で、`:domain` は他のどのモジュールにも依存しない。この構成を選ぶ理由は、憲法 原則III の「Android 非依存」をレビューではなくビルド構成で強制するためである（[research.md](./research.md) R-004）。ルートパッケージは `io.github.longbowxxx.readingtracker`（R-007）。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| スコープ外の A-9（作品単位の棚番号一括更新）に相当する純粋関数 `applyShelfNumberToWork()` を `:domain` に置く | 憲法 原則III が「作品単位の一括更新が、他店舗の記録に影響しないこと」のテストを明示的に必須としている。対象の関数が存在しなければこのテストを書けない | 一括更新を丸ごと省くと原則III のテスト要件を満たせない。逆に UI 導線まで作ると A-9 をスコープへ引き込み原則I に反する。**関数とテストのみを置き、UI からは呼ばない**ことで双方を満たす |

## 次のフェーズ

- Phase 2（`/speckit-tasks`）でタスクへ分解する。**ドメインロジックとそのユニットテストを、UI やカメラ連携より先に配置する**（憲法「開発ワークフローと品質ゲート」）。
- 実装着手前に `/speckit-analyze` で spec / plan / constitution の整合性を確認する。
