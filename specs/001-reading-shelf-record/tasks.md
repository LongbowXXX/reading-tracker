---
description: "Task list for 読書記録と棚番号の管理（中核）"
---

# Tasks: 読書記録と棚番号の管理（中核）

**Input**: Design documents from `/specs/001-reading-shelf-record/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: テストタスクを含む。憲法 原則III が `:domain` のユニットテストを必須としており、マスタープロンプトも「ドメインロジックとそのユニットテストを、UI やカメラ連携よりも先に配置する」ことを指示しているため。

**Organization**: ユーザーストーリー単位でフェーズを分ける。ただし**ドメイン層とそのテストは Phase 2（Foundational）に置く**。全ストーリーの前提であり、かつ UI・カメラ連携より先に完成させる必要があるため。

**改訂**: 2026-08-30、`/speckit-analyze` の指摘（HIGH 3件・MEDIUM 7件）を反映して改訂した。主な変更は、中断→読了の更新経路を Phase 3（MVP）へ移動、Robolectric の設定追加、実店舗確認の Phase 1 への前倒しの3点。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 並列実行可能（別ファイル・未完了タスクへの依存なし）
- **[Story]**: 対応するユーザーストーリー（US1〜US4）
- パスはリポジトリルートからの相対。`…` は `io/github/longbowxxx/readingtracker` を表す

---

## Phase 1: Setup（共通基盤）

**Purpose**: Gradle マルチモジュールの骨格を作り、空のアプリがビルドできる状態にする。あわせて、UI の骨格に関わる未確定事項を先に潰す

- [X] T001 [P] 主導線の方針を確定した（2026-08-31）。**バーコード読み取りを主導線とする。** なお実店舗での観察（棚番号シールがバーコードを覆う頻度。要求定義書 9. 未確定事項）は**実施していない**。利用者の判断による決定であり、観察の結果ではない。実運用で覆われる頻度が高いと分かった場合は、T045 の導線を手入力主体へ組み替える
- [X] T002 Gradle Wrapper を生成し `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.properties` を配置する
- [X] T003 `settings.gradle.kts` に `:app` / `:data` / `:domain` を宣言し、ルート `build.gradle.kts` にプラグインの版を集約する
- [X] T004 `gradle/libs.versions.toml` にバージョンカタログを定義する（AGP, Kotlin, KSP, Compose BOM, Room, Hilt, OkHttp, kotlinx.serialization, play-services-code-scanner, JUnit 5, **Robolectric, JUnit 4**）。**（2026-09-01 追記）** `play-services-code-scanner` は Issue #1 対応（T077・T082、Phase 8）で削除され、CameraX とバンドル版 ML Kit（`com.google.mlkit:barcode-scanning`）に置き換わっている。もはや現在のカタログの内容ではない
- [X] T005 [P] `domain/build.gradle.kts` を作成する（`kotlin("jvm")`、JVM 17、JUnit 5。**Android 依存を一切追加しないこと** — 憲法 原則III）
- [X] T006 [P] `data/build.gradle.kts` を作成する（Android library、Room + KSP、Hilt、OkHttp、kotlinx.serialization、`:domain` に依存。**Robolectric と JUnit 4 を `testImplementation` に加え、`testOptions.unitTests.isIncludeAndroidResources = true` を設定する** — T028 の Room 制約テストを JVM 上で実行するため）
- [X] T007 [P] `app/build.gradle.kts` と `app/src/main/AndroidManifest.xml` を作成する（applicationId `io.github.longbowxxx.readingtracker`、minSdk 26、compileSdk/targetSdk 36、Compose、Hilt、`:data` と `:domain` に依存）
- [X] T008 [P] ktlint / spotless の設定をルート `build.gradle.kts` に追加し、コード整形の基準を固定する
- [X] T009 `./gradlew assembleDebug` が通ることを確認する（空のアプリ）

---

## Phase 2: Foundational（ブロッキング前提 / ドメイン層とデータ層）

**Purpose**: 全ユーザーストーリーが依存する土台。**UI とカメラ連携より先に完成させる**（憲法「開発ワークフローと品質ゲート」）

**⚠️ CRITICAL**: このフェーズが完了するまで、いかなるユーザーストーリーの実装にも着手しない

### ドメイン層のテスト（先に書き、失敗することを確認してから実装する）

- [X] T010 [P] `domain/src/test/kotlin/…/model/IsbnTest.kt` に ISBN のテストを書く（13桁の検証、10桁→13桁変換、末尾 X、ハイフン・空白の除去、桁数不正）
- [X] T011 [P] `domain/src/test/kotlin/…/shelf/ShelfNumberInheritanceTest.kt` に棚番号継承のテストを書く（**憲法 原則III 必須**: 直前の巻からの継承／31巻での変更が32巻以降へ継承／巻番号順の該当なし時に記録日時順へフォールバック／継承元が未入力なら結果も未入力／対象巻番号が NULL の場合）
- [X] T012 [P] `domain/src/test/kotlin/…/reading/NextVolumeResolverTest.kt` に次巻判定のテストを書く（中断中の巻が優先／読了最大巻+1／記録なしは Unknown／巻番号 NULL の扱い）
- [X] T013 [P] `domain/src/test/kotlin/…/title/VolumeTitleParserTest.kt` に作品照合のテストを書く（同一シリーズの異なる巻から同一の照合キーが得られること、巻数表記の複数パターンの抽出）
- [X] T014 [P] `domain/src/test/kotlin/…/shelf/ShelfNumberBulkUpdateTest.kt` に一括適用のテストを書く（**憲法 原則III 必須**: 戻り値が入力に含まれるレコードのみで構成され、他店舗のレコードを含まないこと）

### ドメイン層の実装

- [X] T015 [P] `domain/src/main/kotlin/…/model/Isbn.kt` に `Isbn` を実装する（[contracts/domain-api.md](./contracts/domain-api.md) の表に従う）
- [X] T016 [P] `domain/src/main/kotlin/…/model/` に **`Store` / `Work` / `Volume`** および `ReadingStatus` / `ShelfNumber` / `PlacementSnapshot` / `ReadingSnapshot` / `VolumeRef` を実装する（**`ReadingStatus` は READ と PAUSED の2値のみ** — 憲法 原則II）
- [X] T017 `domain/src/main/kotlin/…/shelf/ShelfNumberInheritance.kt` に `resolveInheritedShelfNumber()` を実装する（**店舗 ID を引数に取らないこと** — 他店舗へ到達しえない構造にする）
- [X] T018 `domain/src/main/kotlin/…/reading/NextVolumeResolver.kt` に `resolveNextVolume()` と `NextVolume` を実装する
- [X] T019 `domain/src/main/kotlin/…/title/VolumeTitleParser.kt` に `parseVolumeTitle()` を実装する
- [X] T020 `domain/src/main/kotlin/…/shelf/ShelfNumberBulkUpdate.kt` に `applyShelfNumberToWork()` を実装する（**UI からは呼ばない**。原則III のテスト要件のためだけに存在する — plan.md の Complexity Tracking）
- [X] T021 [P] `domain/src/main/kotlin/…/port/` に `BibliographySource` / `BarcodeScanner` / `ReadingRepository` のインターフェースと結果型を定義する（[contracts/](./contracts/) に従う）
- [X] T022 `domain/src/test/kotlin/…/fake/FakeReadingRepository.kt` に `ReadingRepository` のフェイク実装を作成する（後続のユースケーステスト T034・T035・T052・T060 が共用する。インメモリのコレクションで保持し、**Android にも Room にも依存しないこと**）
- [X] T023 `./gradlew :domain:test` が全て緑になることを確認する

### データ層

- [X] T024 [P] `data/src/main/kotlin/…/db/entity/` に Room エンティティを作成する（`StoreEntity` / `WorkEntity` / `VolumeEntity` / `ReadingRecordEntity` / `ShelfPlacementEntity`。**`ShelfPlacementEntity` に UNIQUE(storeId, volumeId)、`ReadingRecordEntity` に UNIQUE(volumeId)** — [data-model.md](./data-model.md)）
- [X] T025 [P] `data/src/main/kotlin/…/db/Converters.kt` に型コンバータを実装する（`Instant`、`ReadingStatus`）
- [X] T026 `data/src/main/kotlin/…/db/ReadingTrackerDatabase.kt` を作成し、スキーマ JSON のエクスポートを有効にする
- [X] T027 [P] `data/src/main/kotlin/…/db/dao/` に DAO を作成する（`StoreDao` / `WorkDao` / `VolumeDao` / `ReadingRecordDao` / `ShelfPlacementDao`）
- [X] T028 `data/src/test/kotlin/…/db/ConstraintTest.kt` に制約のテストを書く（**Robolectric で実行する。`@RunWith(RobolectricTestRunner::class)` とインメモリ Room データベースを用いる**）（同一店舗・同一巻の配架を2件作れない／同一巻の読書記録を2件作れない／`shelfNumber` が NULL でも保存できる／**A店の配架更新がB店の同一巻に影響しない**）
- [X] T029 `data/src/main/kotlin/…/repository/ReadingRepositoryImpl.kt` に `ReadingRepository` の実装を書く（DAO を束ね、ドメインの型へ変換する）
- [X] T030 `data/src/main/kotlin/…/di/DataModule.kt` に Hilt モジュールを作成する（Database、DAO、Repository の提供）

**Checkpoint**: ドメイン層とデータ層が完成し、テストが緑。ここからユーザーストーリーの実装に着手できる

---

## Phase 3: User Story 1 - 個室で読み終えた1冊を記録する (Priority: P1) 🎯 MVP

**Goal**: 本を手に持った状態から、バーコードまたは ISBN 手入力で作品を特定し、読書状態と棚番号を一連の操作で保存できる。**中断した巻を後日読み切った際の更新もここに含む**

**Independent Test**: 1冊分の記録を作成し、保存後にその記録を確認できる。次の巻を記録すると棚番号が初期値として入っている。中断として記録した巻を再度記録すると、読了へ更新される

### Tests for User Story 1 ⚠️

- [X] T031 [P] [US1] `data/src/test/kotlin/…/bibliography/ChainedBibliographySourceTest.kt` に連鎖規則のテストを書く（`Found` で打ち切り／`NotFound` で次経路へ／`Unavailable` で次経路へ／全て尽きた場合の戻り値。フェイク実装を用い**ネットワークへアクセスしないこと**）
- [X] T032 [P] [US1] `data/src/test/kotlin/…/bibliography/OpenBdParserTest.kt` に openBD 応答のパーステストを書く（サンプル JSON をリソースに固定。該当なしの `null` 要素を `NotFound` に写像すること）
- [X] T033 [P] [US1] `data/src/test/kotlin/…/bibliography/NdlParserTest.kt` に NDL SRU 応答のパーステストを書く（サンプル XML をリソースに固定。ヒット0件を `NotFound` に写像すること）
- [X] T034 [P] [US1] `domain/src/test/kotlin/…/usecase/RecordVolumeUseCaseTest.kt` にテストを書く（棚番号が初期値として提示される／棚番号未入力でも保存できる／既存記録がある場合は新規作成しない／**同一の巻を別の店舗で記録した場合、読書記録は1件のまま更新され、配架レコードが店舗ごとに作られること** — spec.md Edge Cases）
- [X] T035 [P] [US1] `domain/src/test/kotlin/…/usecase/UpdateRecordUseCaseTest.kt` にテストを書く（**同一店舗で同じ巻を再度記録すると、新規作成されず既存記録が編集対象になること** — FR-029／中断→読了の更新が次巻判定に反映されること／**READ ⇄ PAUSED の遷移に制限がないこと** — 憲法 原則III「読書状態の遷移」）

### Implementation for User Story 1

- [X] T036 [P] [US1] `data/src/main/kotlin/…/bibliography/OpenBdBibliographySource.kt` を実装する（`https://api.openbd.jp/v1/get?isbn=`、kotlinx.serialization、3秒タイムアウト）
- [X] T037 [P] [US1] `data/src/main/kotlin/…/bibliography/NdlBibliographySource.kt` を実装する（`https://ndlsearch.ndl.go.jp/api/sru?operation=searchRetrieve&query=isbn=`、`XmlPullParser`、3秒タイムアウト）
- [X] T038 [US1] `data/src/main/kotlin/…/bibliography/ChainedBibliographySource.kt` を実装する（openBD → NDL の順、全体6秒の上限。**例外を投げず `Unavailable` を返すこと**）
- [X] T039 [US1] `data/src/main/kotlin/…/di/BibliographyModule.kt` で `BibliographySource` を連鎖として提供する
- [X] T040 [P] [US1] `app/src/main/kotlin/…/scanner/GoogleCodeScannerBarcodeScanner.kt` に `BarcodeScanner` を実装する（`play-services-code-scanner`。EAN-13 の上段のみを対象とし、`192` で始まる日本図書コードは読み捨てる）。**（2026-09-01 追記）** この実装は Google Code Scanner が1回の起動につき1件の結果を返して終了する方式であったため、下段を読むとその1件でスキャンが終了してしまい、上段を読み直せない不具合（[issue #1](https://github.com/LongbowXXX/reading-tracker/issues/1)）を引き起こした。T081・T082（Phase 8）で CameraX + バンドル版 ML Kit の実装に置き換えられ、このファイル自体は削除された
- [X] T041 [US1] `domain/src/main/kotlin/…/usecase/RecordVolumeUseCase.kt` を実装する（作品の自動照合 → 巻の作成/取得 → 読書記録の保存 → 配架レコードの保存。**棚番号が未入力でも配架レコードを作ること** — FR-017 と FR-024 の両立）
- [X] T042 [US1] `domain/src/main/kotlin/…/usecase/UpdateRecordUseCase.kt` を実装する（既存記録の読書状態・棚番号・メモを更新する）
- [X] T043 [US1] `RecordVolumeUseCase` から `UpdateRecordUseCase` への分岐を `domain/src/main/kotlin/…/usecase/RecordVolumeUseCase.kt` に組み込む（既存記録がある場合は編集へ — FR-029）
- [X] T044 [US1] `app/src/main/kotlin/…/ui/record/RecordViewModel.kt` を実装する（入力状態、棚番号の初期値提示、保存）
- [X] T045 [US1] `app/src/main/kotlin/…/ui/record/RecordScreen.kt` を実装する（**バーコード読み取りと ISBN 手入力を1操作で相互に切り替えられること**。手入力をエラー経路として扱わない — FR-003, SC-002。**T001 の観察結果を反映して主導線を決めること**）
- [X] T046 [US1] `app/src/main/kotlin/…/ui/record/ConfirmScreen.kt` を実装する（書誌情報の確認・修正、読書状態の選択、棚番号の入力、メモ。取得失敗時は手入力へ直行する — FR-006, FR-007）
- [X] T047 [US1] `app/src/main/kotlin/…/ui/record/StorePickerSection.kt` を実装する（店舗の選択と、**選択欄からの店舗名入力による新規登録** — FR-030。編集・削除は作らない — FR-031）
- [X] T048 [US1] `app/src/main/kotlin/…/ui/record/CameraPermission.kt` にカメラ権限の要求を実装する（拒否時は手入力へ落とす）
- [X] T049 [US1] `app/src/main/kotlin/…/ui/NavGraph.kt` と `MainActivity.kt` に画面遷移を配線する
- [X] T050 [US1] `./gradlew assembleDebug` を確認し、**実機で** [quickstart.md](./quickstart.md) 4.1・4.2・4.4 を実施した（2026-08-31）。**不具合を1件検出し、[issue #1](https://github.com/LongbowXXX/reading-tracker/issues/1) として登録した**（下段のバーコードを読むとエラーで終了し、上段を読み直せない）。修正は本スコープでは行わない

**Checkpoint**: User Story 1 が単独で動作し、記録の作成と更新ができる（MVP）

---

## Phase 4: User Story 2 - 来店時に「この店で読める続き」を知る (Priority: P2)

**Goal**: 店舗を選ぶと、その店で読める読みかけ作品が棚番号つきで一覧され、次に取るべき巻が分かる

**Independent Test**: 事前に投入した記録データに対して店舗を選択し、一覧・棚番号・次に読むべき巻が正しく表示される

### Tests for User Story 2 ⚠️

- [X] T051 [P] [US2] `data/src/test/kotlin/…/db/VisitListQueryTest.kt` に一覧クエリのテストを書く（**選択店舗の記録のみが出る／他店舗のみで記録した作品が出ない／棚番号が未入力の作品も出る／巻番号が NULL の暫定記録のみの作品も一覧に現れ、次に読むべき巻が示されないこと** — FR-022, FR-023 末尾, FR-024, SC-007／記録0件でも破綻しない）
- [X] T052 [P] [US2] `domain/src/test/kotlin/…/usecase/VisitListUseCaseTest.kt` にユースケースのテストを書く（各作品に `resolveNextVolume()` の結果が付くこと）

### Implementation for User Story 2

- [X] T053 [US2] `data/src/main/kotlin/…/db/dao/ShelfPlacementDao.kt` に店舗ごとの作品集約クエリを追加する（配架レコードから作品を集約し、棚番号と読書記録を併せて取得する）
- [X] T054 [US2] `domain/src/main/kotlin/…/usecase/VisitListUseCase.kt` を実装する（作品ごとに棚番号と次に読むべき巻を組み立てる）
- [X] T055 [US2] `app/src/main/kotlin/…/ui/visit/VisitViewModel.kt` を実装する
- [X] T056 [US2] `app/src/main/kotlin/…/ui/visit/StoreSelectScreen.kt` を実装する（店舗の選択。**3操作以内で一覧へ到達すること** — SC-003）
- [X] T057 [US2] `app/src/main/kotlin/…/ui/visit/VisitListScreen.kt` を実装する（棚番号を併記し、**未入力であることが分かる表示**にする。次に読むべき巻を示す — FR-022, FR-023）
- [X] T058 [US2] `app/src/main/kotlin/…/ui/visit/EmptyState.kt` に記録0件時の表示を実装する
- [X] T059 [US2] `./gradlew assembleDebug` を確認し、**実機で** [quickstart.md](./quickstart.md) 4.5 を実施する

**Checkpoint**: User Story 1 と 2 がそれぞれ独立して動作する

---

## Phase 5: User Story 3 - バーコードも ISBN も使えない本を暫定名で記録する (Priority: P3)

**Goal**: バーコードのない巻を暫定名で記録し、後から正式な作品へ紐づけ直せる

**Independent Test**: 暫定名で記録を作成し、後から ISBN を指定して正式な作品へ紐づけ、読書状態・棚番号・メモが引き継がれることを確認する

### Tests for User Story 3 ⚠️

- [X] T060 [P] [US3] `domain/src/test/kotlin/…/usecase/LinkProvisionalWorkUseCaseTest.kt` にテストを書く（紐づけ後も読書状態・棚番号・メモが失われないこと — FR-008）
- [X] T061 [P] [US3] `data/src/test/kotlin/…/db/WorkRelinkTest.kt` にテストを書く（**`Volume.workId` と全 `ShelfPlacement.workId` が同時に更新され、不整合が残らないこと** — data-model.md の関連）

### Implementation for User Story 3

- [X] T062 [US3] `data/src/main/kotlin/…/db/dao/WorkDao.kt` に作品の付け替えクエリを追加する（`Volume` と `ShelfPlacement` を同一トランザクションで更新する）
- [X] T063 [US3] `domain/src/main/kotlin/…/usecase/LinkProvisionalWorkUseCase.kt` を実装する
- [X] T064 [US3] `app/src/main/kotlin/…/ui/record/ProvisionalInputSection.kt` を実装する（暫定の作品名と巻数の入力 — FR-008）
- [X] T065 [US3] `app/src/main/kotlin/…/ui/record/LinkWorkScreen.kt` を実装する（暫定記録を正式な作品へ紐づける導線）
- [X] T066 [US3] `./gradlew assembleDebug` を確認する

**Checkpoint**: バーコードのない本も記録でき、後から正式化できる

---

## Phase 6: User Story 4 - 保存した記録をその場で見直して直す (Priority: P3)

**Goal**: 保存済みの記録を一覧から開いて修正でき、巻ごとのメモを残せる

**Independent Test**: 保存済みの記録を開き、読書状態・棚番号・書誌情報・メモを変更して再保存し、変更が反映されることを確認する

**Note**: 更新のユースケースとそのテスト（T035, T042, T043）は MVP に必要なため Phase 3 へ移動済み。本フェーズは**それを操作するための画面**を担当する

### Implementation for User Story 4

- [X] T067 [US4] `app/src/main/kotlin/…/ui/record/RecordDetailScreen.kt` を実装する（記録の確認・修正 — FR-019。`UpdateRecordUseCase` を呼ぶ）
- [X] T068 [US4] `app/src/main/kotlin/…/ui/record/NoteEditor.kt` にメモの入力・編集を実装する（FR-020）
- [X] T069 [US4] `./gradlew assembleDebug` を確認する

**Checkpoint**: 全ユーザーストーリーがそれぞれ独立して動作する

---

## Phase 7: Polish & 実機検証

**Purpose**: 全ストーリーに横断する仕上げと、自動テストで代替できない確認

- [X] T070 [P] `README.md` の「進行状況」を更新し、実装済みスコープと未実装スコープ（A-9, B-4〜B-6, C群〜F群）を明記する
- [X] T071 [P] `domain/src/test/` のテストが憲法 原則III の必須3項目（継承／巻単位変更の以降への継承／一括更新の店舗独立性）と「読書状態の遷移」を網羅していることを確認し、不足があれば追加する
- [X] T072 禁止・制約要求の遵守をコードレビューで確認し、結果を記録する（FR-009 あいまい検索を提供しない／FR-011 読了位置を持たない／FR-012 購入・所蔵に相当する概念を持たない／FR-018 位置補足情報を持たない／FR-025 データが端末内に閉じている／FR-026 スコープ外機能を実装していない。**識別子・UI 文言・DB 列名を対象に検索する** — 憲法 原則II）
- [X] T073 暗所（個室相当の照明環境）での読み取りを実機で確認した（2026-08-31）。**問題なく動作**（quickstart.md 4.3。棚番号シールの被覆頻度は T001 で判断済み）
- [X] T074 SC-001（30秒以内・5タップ以内）、SC-002（手入力へ1操作で切り替えられ、記録完了までが読み取り時と同等）、SC-003（3操作以内・5秒以内）を実機で確認した（2026-08-31）。**いずれも基準を満たした**。ただし具体的な計測値は記録していない
- [X] T075 実機確認の結果を記録した（下記「実機確認の記録」を参照）。**未確認の項目が残っており、完了報告時に明示した**（憲法 原則IV）
- [X] T076 `./gradlew :domain:test :data:test assembleDebug` を通し、最終確認とする

---

## Phase 8: Issue #1 対応 — バーコード下段読み取りでのスキャン中断を修正

**Purpose**: T050 で検出した [issue #1](https://github.com/LongbowXXX/reading-tracker/issues/1)（バーコード下段を読むとエラーで終了し上段を読み直せない）を修正する。**元のユーザーストーリーのスコープには含まれない、不具合修正として追加されたフェーズ**である

**背景**: Google Code Scanner は1回の起動につき1件の結果を返して終了する方式のため、下段（読み捨てるべき値）を読んだ時点でスキャンそのものが終わってしまっていた。カメラプレビューを維持したままフレームを連続解析する方式（CameraX + ML Kit）へ差し替えることで解消する

- [X] T077 [P] [US1] `gradle/libs.versions.toml` と `app/build.gradle.kts` に CameraX（1.6.2）とバンドル版 `com.google.mlkit:barcode-scanning`（17.3.0）の依存を追加する（**Issue #1 対応**。Google Play 配信版ではなくバンドル版を採用し、初回スキャンでのモデルダウンロードを不要にする）
- [X] T078 [P] [US1] `app/src/main/kotlin/…/scanner/IsbnSelection.kt` に `selectIsbn()` を実装し、`app/src/test/kotlin/…/scanner/IsbnSelectionTest.kt` にテストを書く（**Issue #1 対応**。上段の ISBN（978/979）のみを採用し、下段の日本図書コード（192始まり）は `Isbn.parse` が拒否する値として黙って読み捨てることを固定する。contracts/barcode-scanner.md の受け入れ基準に対応）
- [X] T079 [US1] `app/src/main/kotlin/…/scanner/IsbnBarcodeAnalyzer.kt` にフレームを連続解析するアナライザを実装する（**Issue #1 対応**。カメラプレビューを止めずに解析を継続し、`selectIsbn()` が採用した値のみを通知する）
- [X] T080 [US1] `app/src/main/kotlin/…/scanner/ScanScreen.kt` にスキャン画面を実装する（**Issue #1 対応**。トーチ（ライト）の切り替えを追加する）
- [X] T081 [US1] `app/src/main/kotlin/…/scanner/ScanActivity.kt` と `app/src/main/kotlin/…/scanner/BarcodeScannerFactory.kt` を実装する（**Issue #1 対応**。`:domain` の `BarcodeScanner` ポートは変更せず、`suspend fun scan()` の契約を保ったまま CameraX 実装を提供する）
- [X] T082 [US1] `app/src/main/kotlin/…/ui/record/RecordScreen.kt` を `rememberBarcodeScanner()` の呼び出しへ1行だけ差し替え、`app/src/main/kotlin/…/scanner/GoogleCodeScannerBarcodeScanner.kt`（T040）を削除し、`play-services-code-scanner` を `gradle/libs.versions.toml`（T004）と `app/build.gradle.kts` から除去する（**Issue #1 対応**。旧実装からの入れ替え本体）
- [X] T083 [US1] `./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest spotlessCheck assembleDebug` が全て通ることを確認した（**Issue #1 対応のビルドゲート**）
- [X] T084 [P] [US1] `contracts/barcode-scanner.md`・`research.md`（R-003, R-005）・`plan.md` を CameraX + ML Kit 実装に合わせて更新する（**Issue #1 対応**。旧実装を現在時制で記述していた箇所を修正し、置き換えの経緯を記録として残す）

**Checkpoint**: Issue #1 の修正はコードとユニットテスト・ビルドゲートの上では完了している。**ただし実機での確認はまだ行っていない**（憲法 原則IV）。確認すべき内容は「実機確認の記録（T075）」内の「未確認のまま残る項目」を参照

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 依存なし。T001（実店舗観察）はコードに依存しないため最初から着手できる
- **Foundational (Phase 2)**: Phase 1 完了後。**全ユーザーストーリーをブロックする**
- **User Stories (Phase 3〜6)**: Phase 2 完了後。優先度順に P1 → P2 → P3
- **Polish (Phase 7)**: 対象とするストーリーが完了した後
- **Issue #1 対応 (Phase 8)**: Phase 3（US1、T050 での不具合検出）の後。元のスコープには含まれない不具合修正であり、Phase 7 の完了を待つ必要はない

### User Story Dependencies

- **US1 (P1)**: Phase 2 完了後に着手可能。他ストーリーへの依存なし。**T045（記録画面）は T001 の観察結果を前提とする**
- **US2 (P2)**: Phase 2 完了後に着手可能。US1 が無くてもテストデータを投入すれば単独で検証できる
- **US3 (P3)**: Phase 2 完了後に着手可能。US1 の記録画面に入力欄を足す形になるため、実務上は US1 の後が効率的
- **US4 (P3)**: **US1 の T042（`UpdateRecordUseCase`）に依存する。** 本フェーズは画面のみを担当する

### Within Each Story

- テストを先に書き、失敗することを確認してから実装する
- ドメイン（`:domain`） → データ（`:data`） → UI（`:app`）の順
- **UI とカメラ連携は、対応するドメインロジックとテストが緑になってから着手する**（憲法 原則III）

### Parallel Opportunities

- Phase 1 の T001 は全体と並列可能。T005〜T008 は T003・T004 の完了後に互いに並列可能
- Phase 2 のドメインテスト T010〜T014 は並列可能（それぞれ別のテストファイル）
- Phase 2 のドメイン実装 T015・T016・T021 は並列可能。T017〜T020 は T016 の型定義に、T022 は T021 に依存する
- Phase 3 のテスト T031〜T035 は並列可能。実装のうち T036・T037・T040 は別ファイルのため並列可能
- Phase 2 完了後は、US1〜US3 を別々の担当者が並行して進められる（US4 は T042 の完了待ち）

---

## Parallel Example: Phase 2 のドメインテスト

```text
# 5つのテストファイルは互いに独立しており、同時に着手できる
T010 domain/src/test/kotlin/…/model/IsbnTest.kt
T011 domain/src/test/kotlin/…/shelf/ShelfNumberInheritanceTest.kt
T012 domain/src/test/kotlin/…/reading/NextVolumeResolverTest.kt
T013 domain/src/test/kotlin/…/title/VolumeTitleParserTest.kt
T014 domain/src/test/kotlin/…/shelf/ShelfNumberBulkUpdateTest.kt
```

---

## Implementation Strategy

### MVP First（User Story 1 のみ）

1. Phase 1: Setup（**T001 の実店舗観察を早い段階で済ませる**）
2. Phase 2: Foundational（**ここが本プロジェクトの中核。棚番号の継承をテストで固定する**）
3. Phase 3: User Story 1
4. **停止して検証**: 実機で1冊記録し、次の巻で棚番号が継承されること、中断した巻を読了へ更新できることを確認する
5. この時点で「読んだ巻の履歴が残り、続きから読める」という価値が成立する

### Incremental Delivery

1. Setup + Foundational → 土台完成
2. US1 追加 → 実機検証 → MVP
3. US2 追加 → 実機検証 → 課題1・4 に対する解が揃う
4. US3 / US4 追加 → 記録の網羅性と操作性が上がる

---

## Notes

- `[P]` は別ファイルかつ依存なしを意味する
- **各タスクの完了時に `./gradlew assembleDebug` が通ることを確認する**（憲法「開発ワークフローと品質ゲート」）。フェーズ末の確認タスク（T009, T050, T059, T066, T069, T076, T083）は、その節目での明示的なゲート
- タスクごと、または論理的なまとまりごとにコミットする。コミットメッセージは日本語（憲法 原則VII）
- **`applyShelfNumberToWork()`（T020）を UI から呼ばないこと。** 憲法 原則III のテスト要件のためだけに存在し、A-9 は今回スコープ外（plan.md の Complexity Tracking）
- **`ReadingStatus` に第3の値を追加しないこと。** 「離脱」は作品単位の状態であり今回は保持しない（憲法 原則II）
- テストの実行基盤は `:domain` が JUnit 5、`:data` が JUnit 4 + Robolectric。`:domain` に Android 由来の依存を持ち込まないための使い分けであり、統一しないこと（憲法 原則III）
- 実機でしか検証できない項目（T001, T050, T059, T073, T074）は、自動テストの成功をもって完了としない（憲法 原則IV）。**Phase 8（Issue #1 対応、T077〜T084）も同様で、コード・テスト・ビルドゲートは完了しているが実機での確認はまだ行っていない**（「実機確認の記録（T075）」内の「未確認のまま残る項目」を参照）

---

## 実機確認の記録（T075）

憲法 原則IV に基づき、実機でしか検証できない項目の確認状況を記録する。
**自動テストの成功をもって完了とはしない。**

### 確認済み（2026-08-31）

| 項目 | 結果 |
| --- | --- |
| quickstart 4.1 記録の主導線（US1） | 問題なし |
| quickstart 4.2 バーコードから手入力への切り替え | 問題なし。ただし下記の不具合を検出 |
| quickstart 4.3 暗所での読み取り | 問題なし |
| quickstart 4.4 圏外・電波不良での動作 | 問題なし |
| quickstart 4.5 来店時の参照（US2） | 問題なし |
| SC-001 / SC-002 / SC-003 | いずれも基準を満たした（具体的な計測値は未記録） |

### 検出した不具合（修正済み・実機未確認）

- [issue #1](https://github.com/LongbowXXX/reading-tracker/issues/1)
  バーコード読み取りで下段（日本図書コード）を読むとエラーで終了し、上段を読み直せない。
  contracts/barcode-scanner.md が定める「下段は読み捨てる」を実装（`GoogleCodeScannerBarcodeScanner`、T040）が満たしていなかった。

  **2026-09-01 修正済み**：Google Code Scanner は1回の起動につき1件の結果を返して終了する方式のため、
  下段を読むとその1件（不採用の値）でスキャン自体が終わってしまい、上段の読み直しができなかった。
  これを解消するため、CameraX + バンドル版 ML Kit によるカメラプレビュー常時表示・フレーム連続解析方式へ
  実装を差し替えた（T077〜T082、Phase 8）。`selectIsbn()`（T078）が上段の ISBN（978/979）のみを採用し、
  下段の値（192始まり）は `Isbn.parse` に拒否されて黙って読み捨てられ、スキャンはそのまま継続する。
  ユニットテスト（`IsbnSelectionTest.kt`）で当該の受け入れ基準は固定したが、
  **実機での確認はまだ行っていない**（憲法 原則IV）。確認すべき内容は下記「未確認のまま残る項目」に記載した。

### 確認済み（2026-09-04, Issue #4）

端末: **Pixel 9 系**（Prompt API 対応、Gemini Nano v3）

| 項目 | 結果 |
| --- | --- |
| 続巻が同一作品にまとまること（Issue #4 の受け入れ基準） | 問題なし |
| オンデバイス AI 経路が実際に働くこと | **働いた**。`checkStatus()` が `AVAILABLE` を返し、推論結果が採用された |
| 日本語のタイトルに対して機能すること | 問題なし。プロンプトの指示文は英語、入出力は日本語という構成のまま動作した |

**未計測**: 推論のレイテンシ（2,500 ms の打ち切りに収まっているかの実測値）、および SC-001（30秒以内）への影響。
体感では問題が出ていないが、具体的な計測値は記録していない（2026-08-31 の SC-001〜003 と同じ扱い）。

**AI 非対応端末での動作は未確認**。Pixel 9 系でしか試しておらず、`FeatureStatus.UNAVAILABLE` から
規則ベースへ落ちる経路は実機で通っていない。ユニットテスト（`TitleAnalyzerChainTest.kt`）では固定済み。

### 未確認のまま残る項目

以下は実機で一度も動作させていない。**次に実機へ導入する際の確認対象**とする。

| 項目 | 内容 |
| --- | --- |
| US3 の画面 | 暫定名での記録、および「暫定記録」タブからの正式な作品への紐づけ。特に紐づけ後に読書状態・棚番号・メモが引き継がれること |
| US4 の画面 | 来店時の一覧から記録詳細を開き、中断→読了へ変更した後に、一覧の「次に読むべき巻」が繰り上がること |
| 書誌取得の実応答 | openBD と国立国会図書館サーチの実際の応答スキーマ。パースはリソースに固定したサンプルでしか検証していない（research.md の未解決事項） |
| Issue #1: 下段バーコードの読み取り | 下段（日本図書コード、192始まり）を読んでもカメラが閉じず、エラーが出ないこと（Issue #1 の受け入れ基準） |
| Issue #1: 下段から上段への遷移 | 下段を読んだ状態からカメラを上段へ動かした場合に、スキャンが完了すること |
| Issue #1: トーチと暗所での動作 | トーチ（ライト）の点灯・消灯が反映されること、および暗所での読み取りが問題なく行えること |
| Issue #1: 片手操作での使用感 | 片手操作でのスキャン成功率、および旧 Google Code Scanner 実装との体感比較 |
| Issue #1: 戻る操作での手入力への遷移 | スキャン画面で戻る操作をした際、エラーを出さずに ISBN 手入力へ落ちること |
| Issue #1: バンドル版 ML Kit のオフライン動作 | 端末を機内モードにした状態でも初回スキャンが機能すること（バンドル版採用の確認） |
| Issue #4: 区切りの無い数字の判別 | `拳児2`（2巻）と `ゴルゴ13`・`キャプテン2`（作品名の一部）を AI 経路が区別できること。**AI を導入した理由そのものであり、個別には未確認**（contracts/title-analyzer.md の A-8 / A-9） |
| Issue #4: AI 非対応端末でのフォールバック | Prompt API 非対応の端末で `FeatureStatus.UNAVAILABLE` から規則ベースへ落ち、記録が完了すること |
| Issue #4: モデル未ダウンロード時の初回動作 | `DOWNLOADABLE` の状態で記録を行った場合に、待たされずに規則ベースで完了し、ダウンロードが裏で進むこと |
| Issue #4: 推論のレイテンシ | 2,500 ms の打ち切りに収まること、および SC-001（30秒以内）を損なわないこと |

これらはいずれも自動テストで代替できない。ドメイン層とデータ層の振る舞いは
ユニットテストで固定済みだが、**画面の配線と実 API の疎通は未検証**である。

---

## Phase 9: Convergence — Issue #4 対応（作品同定へのオンデバイス AI 導入）

**Purpose**: [issue #4](https://github.com/LongbowXXX/reading-tracker/issues/4)（巻数抽出が外れ、続巻が別作品として登録される）への対応を tasks.md へ埋め戻し、残っている作業を明示する。**元のユーザーストーリーのスコープには含まれない、不具合修正として追加されたフェーズ**である

**背景**: 着手前に実データを収集した（openBD 1,535件 / NDL 230件）。issue 本文の推定「レーベル名の括弧付き接尾辞がタイトル末尾に付く」は openBD では確認できず、レーベル名は `summary.series` という別項目で返っていた。実際の原因は3つだった。

1. 末尾のピリオドで照合キーが割れる。コミック1,129件中19シリーズが分裂していた。openBD の `チェンソーマン 5` と NDL の `チェンソーマン. 5` は同じ本であり、経路が違うだけで別作品になる
2. 巻数表記の語彙不足（`巻94` / `巻ノ9` / `巻之3` / `vol.8` / `その1` / `#2`）。抽出率 83.3%
3. 区切りの無い数字は正規表現では原理的に判別できない（`拳児2` は2巻、`ゴルゴ13` は作品名の一部）

3 に対応するため、ML Kit GenAI Prompt API（オンデバイスの Gemini Nano）を第一経路として導入する方針を採用した（2026-09-04 承認）。**AI 経路は対応端末が限られる**（Pixel 9 以降、Galaxy S26 など。Galaxy S25 は Prompt API の対応表に無い）ため、規則ベースの経路を必ず残す

### 実装済み

- [X] T085 [P] `domain/src/main/kotlin/…/port/TitleAnalyzer.kt` に `TitleAnalyzer` ポートと `TitleAnalysis` を定義する（**Issue #4 対応**。ML Kit は Android 依存のため、`:domain` を純粋な Kotlin に保つ境界をここに引く — 憲法 原則III）
- [X] T086 [P] `domain/src/main/kotlin/…/title/VolumeTitleParser.kt` の照合キー正規化（末尾の区切り記号・並列書名・大文字小文字）と巻数表記の語彙を実データに合わせて強化し、`VolumeTitleParserTest.kt` に実データ由来の表記を追加する（**Issue #4 対応**。上記1と2。末尾記号による分裂 19→0、抽出率 83.3%→88.7%）
- [X] T087 [P] `domain/src/main/kotlin/…/title/TitleAnalysisPrompt.kt` と `TitleAnalysisResponseParser.kt` を純粋関数として実装し、テストを書く（**Issue #4 対応**。推論そのものは実機でしか試せないが、プロンプトと応答の解釈はユニットテストで仕様を固定する — 憲法 原則III。**元のタイトルから導けない作品名は捨てる**検証を含む）
- [X] T088 [P] `domain/src/main/kotlin/…/title/` に `RuleBasedTitleAnalyzer` / `ChainedTitleAnalyzer` / `CachingTitleAnalyzer` と `analyzeOrFallback()` を実装し、`TitleAnalyzerChainTest.kt` にテストを書く（**Issue #4 対応**。どの経路が落ちても記録は完了することを固定する — 憲法 原則VI）
- [X] T089 `data/src/main/kotlin/…/title/GenAiTitleAnalyzer.kt` と `data/src/main/kotlin/…/di/TitleModule.kt` を実装し、`gradle/libs.versions.toml` と `data/build.gradle.kts` に `com.google.mlkit:genai-prompt`（1.0.0-beta4）を追加する（**Issue #4 対応**。`checkStatus()` が `AVAILABLE` のときのみ推論し、`DOWNLOADABLE` なら裏でダウンロードして今回は規則ベースへ落とす）
- [X] T090 `RecordVolumeUseCase` / `LinkProvisionalWorkUseCase` / `app/…/di/DomainModule.kt` を `TitleAnalyzer` 経由へ差し替える（**Issue #4 対応**。既定引数を規則ベースにしてあるため既存のユニットテストは変更不要）
- [X] T091 `./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest spotlessCheck assembleDebug` が全て通ることを確認した（**Issue #4 対応のビルドゲート**。`:domain` 122件 全通過、うち新規46件）
- [X] T092 実機で動作を確認した（2026-09-04）。**確認内容の詳細は T096 で記録する**（憲法 原則IV）

### 残作業

- [X] T093 ~~plan.md の Complexity Tracking に AI 経路を逸脱として記録する~~ → **対応不要と判断した（2026-09-04）**。憲法 原則III がユニットテストを必須とするのは列挙された4つのロジック（棚番号の継承／読書状態の遷移／次に読むべき巻の判定／ISBN の妥当性検証）であり、タイトル解析はそこに含まれない。その4つは純粋 Kotlin のままテストで固定されている。**確率的に失敗しうることは許容する**（許容しないなら乱数を用いる実装も置けない）。技術選定の経緯は T094 で research.md R-002 に記録する
- [X] T094 `research.md` R-002 と `plan.md` の技術スタック・Constitution Check を現在の実装に合わせて更新する per plan: R-002 (contradicts) — R-002 は照合を正規表現の正規化のみと記述したままである。実データの測定結果、AI 経路を第一経路とする構成、対応端末の制約、および原則V の観点（推論は端末内で完結するが ML Kit は利用状況メトリクスを Google へ送る）を記録する。T084 が Issue #1 で行った更新と同種の作業 → **完了（2026-09-04）**。R-003 と同じ形式で Decision を書き換え、「更新（2026-09-04, Issue #4）」段落に実データの測定結果と AI 経路の制約を記し、既存の Rationale を「当初 正規表現のみを採用した際の判断根拠」と明示した。照合キーの正規化規則は実装に合わせて改め、半角カタカナの全角化は規則から外した。plan.md は Summary・Primary Dependencies・G5 の評価を更新した
- [X] T095 `contracts/` にタイトル解析経路の契約を追加する per plan: contracts (missing) — `BibliographySource` と同様に、`TitleAnalyzer` の「例外を投げない」「判定できなければ null を返す」契約と受け入れ基準を文書化する → **完了（2026-09-04）**。`contracts/title-analyzer.md` を新設し、契約・連鎖の規則・AI 経路と規則ベース経路の実装仕様・受け入れ基準 A-1〜A-9・テスト方針を記した。A-8 / A-9（`拳児2` と `ゴルゴ13` の区別）は実機確認項目とした。plan.md の contracts ツリーにも追加した
- [X] T096 tasks.md の「実機確認の記録（T075）」に Issue #4 の実機確認結果を追記する per Constitution 原則IV (missing) — 確認した端末、AI 経路が実際に働いたか（`FeatureStatus`）、日本語の指示が通ったか、SC-001（30秒以内）へのレイテンシの影響を記す。**Issue #1 の未確認項目は引き続き未確認として残すこと** → **完了（2026-09-04）**。「確認済み（2026-09-04, Issue #4）」節を追加し、端末（Pixel 9 系）・AI 経路が実際に働いたこと・日本語で機能したことを記録した。未計測のレイテンシと、未確認の4項目（区切りの無い数字の判別／非対応端末でのフォールバック／モデル未ダウンロード時／レイテンシ）は「未確認のまま残る項目」へ追加した。**Issue #1 の未確認項目はそのまま残してある**
- [X] T097 確認画面に自動照合の結果（どの既存作品へ紐づくか）を提示する per FR-027 (partial) — 現状の `ConfirmScreen.kt` はタイトルと巻数の編集欄のみで、照合先の作品を表示していない。FR-027 は「照合結果は確認画面に提示し、利用者が別の作品へ変更する、または新しい作品として分離することができなければならない」を求める。**表示していれば Issue #4 の分裂は記録の時点で気づけた**。追加の操作を強いてはならない（FR-028、憲法 原則VI） → **今回は対応しない（2026-09-04）**。Issue #4 のスコープ外とし、T098 とあわせて別 issue へ切り出す。**FR-027 の未達は解消していない**。[issue #6](https://github.com/LongbowXXX/reading-tracker/issues/6) として切り出した
- [X] T098 既に別作品として分裂して登録された記録を統合する導線を、別 issue として切り出す per Issue #4「既存データの扱い」(missing) — Issue #4 では対象外とすると判断した。自動での再照合と手動統合のどちらを採るかは未決であり、`LinkProvisionalWorkUseCase`（暫定作品の紐づけ）は正式な作品どうしの統合には使えない → **完了（2026-09-04）**。[issue #7](https://github.com/LongbowXXX/reading-tracker/issues/7) として切り出した。案 A（マイグレーションでの自動再照合）／案 B（手動統合の画面）／案 C（候補提示と承認）を併記し、着手前に分裂件数を数えることを条件として残した

**Checkpoint**: Issue #4 の対応は完了した。コード・ユニットテスト・ビルドゲート・実機確認（Pixel 9 系、AI 経路が実際に働くことを確認）、および仕様書への反映（research.md R-002 / plan.md / contracts/title-analyzer.md）まで済んでいる。

**このフェーズから外に出した項目**:

- [issue #6](https://github.com/LongbowXXX/reading-tracker/issues/6) 確認画面が自動照合の結果を提示していない（**FR-027 の未達は解消していない**）
- [issue #7](https://github.com/LongbowXXX/reading-tracker/issues/7) 修正前に分裂して登録された記録の統合

**未確認のまま残る実機項目**は「実機確認の記録（T075）」を参照。特に `拳児2` と `ゴルゴ13` の区別（AI を導入した理由そのもの）は個別に確認していない
