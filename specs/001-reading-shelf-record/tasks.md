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
- [X] T004 `gradle/libs.versions.toml` にバージョンカタログを定義する（AGP, Kotlin, KSP, Compose BOM, Room, Hilt, OkHttp, kotlinx.serialization, play-services-code-scanner, JUnit 5, **Robolectric, JUnit 4**）
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
- [X] T040 [P] [US1] `app/src/main/kotlin/…/scanner/GoogleCodeScannerBarcodeScanner.kt` に `BarcodeScanner` を実装する（`play-services-code-scanner`。EAN-13 の上段のみを対象とし、`192` で始まる日本図書コードは読み捨てる）
- [X] T041 [US1] `domain/src/main/kotlin/…/usecase/RecordVolumeUseCase.kt` を実装する（作品の自動照合 → 巻の作成/取得 → 読書記録の保存 → 配架レコードの保存。**棚番号が未入力でも配架レコードを作ること** — FR-017 と FR-024 の両立）
- [X] T042 [US1] `domain/src/main/kotlin/…/usecase/UpdateRecordUseCase.kt` を実装する（既存記録の読書状態・棚番号・メモを更新する）
- [X] T043 [US1] `RecordVolumeUseCase` から `UpdateRecordUseCase` への分岐を `domain/src/main/kotlin/…/usecase/RecordVolumeUseCase.kt` に組み込む（既存記録がある場合は編集へ — FR-029）
- [X] T044 [US1] `app/src/main/kotlin/…/ui/record/RecordViewModel.kt` を実装する（入力状態、棚番号の初期値提示、保存）
- [X] T045 [US1] `app/src/main/kotlin/…/ui/record/RecordScreen.kt` を実装する（**バーコード読み取りと ISBN 手入力を1操作で相互に切り替えられること**。手入力をエラー経路として扱わない — FR-003, SC-002。**T001 の観察結果を反映して主導線を決めること**）
- [X] T046 [US1] `app/src/main/kotlin/…/ui/record/ConfirmScreen.kt` を実装する（書誌情報の確認・修正、読書状態の選択、棚番号の入力、メモ。取得失敗時は手入力へ直行する — FR-006, FR-007）
- [X] T047 [US1] `app/src/main/kotlin/…/ui/record/StorePickerSection.kt` を実装する（店舗の選択と、**選択欄からの店舗名入力による新規登録** — FR-030。編集・削除は作らない — FR-031）
- [X] T048 [US1] `app/src/main/kotlin/…/ui/record/CameraPermission.kt` にカメラ権限の要求を実装する（拒否時は手入力へ落とす）
- [X] T049 [US1] `app/src/main/kotlin/…/ui/NavGraph.kt` と `MainActivity.kt` に画面遷移を配線する
- [ ] T050 [US1] `./gradlew assembleDebug` を確認し、**実機で** [quickstart.md](./quickstart.md) 4.1・4.2・4.4 を実施する。**あわせて FR-028（自動照合が通常経路で追加操作を要求しないこと）を確認する**（暗所での読み取りは T073 で扱う）

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
- [ ] T059 [US2] `./gradlew assembleDebug` を確認し、**実機で** [quickstart.md](./quickstart.md) 4.5 を実施する

**Checkpoint**: User Story 1 と 2 がそれぞれ独立して動作する

---

## Phase 5: User Story 3 - バーコードも ISBN も使えない本を暫定名で記録する (Priority: P3)

**Goal**: バーコードのない巻を暫定名で記録し、後から正式な作品へ紐づけ直せる

**Independent Test**: 暫定名で記録を作成し、後から ISBN を指定して正式な作品へ紐づけ、読書状態・棚番号・メモが引き継がれることを確認する

### Tests for User Story 3 ⚠️

- [ ] T060 [P] [US3] `domain/src/test/kotlin/…/usecase/LinkProvisionalWorkUseCaseTest.kt` にテストを書く（紐づけ後も読書状態・棚番号・メモが失われないこと — FR-008）
- [ ] T061 [P] [US3] `data/src/test/kotlin/…/db/WorkRelinkTest.kt` にテストを書く（**`Volume.workId` と全 `ShelfPlacement.workId` が同時に更新され、不整合が残らないこと** — data-model.md の関連）

### Implementation for User Story 3

- [ ] T062 [US3] `data/src/main/kotlin/…/db/dao/WorkDao.kt` に作品の付け替えクエリを追加する（`Volume` と `ShelfPlacement` を同一トランザクションで更新する）
- [ ] T063 [US3] `domain/src/main/kotlin/…/usecase/LinkProvisionalWorkUseCase.kt` を実装する
- [ ] T064 [US3] `app/src/main/kotlin/…/ui/record/ProvisionalInputSection.kt` を実装する（暫定の作品名と巻数の入力 — FR-008）
- [ ] T065 [US3] `app/src/main/kotlin/…/ui/record/LinkWorkScreen.kt` を実装する（暫定記録を正式な作品へ紐づける導線）
- [ ] T066 [US3] `./gradlew assembleDebug` を確認する

**Checkpoint**: バーコードのない本も記録でき、後から正式化できる

---

## Phase 6: User Story 4 - 保存した記録をその場で見直して直す (Priority: P3)

**Goal**: 保存済みの記録を一覧から開いて修正でき、巻ごとのメモを残せる

**Independent Test**: 保存済みの記録を開き、読書状態・棚番号・書誌情報・メモを変更して再保存し、変更が反映されることを確認する

**Note**: 更新のユースケースとそのテスト（T035, T042, T043）は MVP に必要なため Phase 3 へ移動済み。本フェーズは**それを操作するための画面**を担当する

### Implementation for User Story 4

- [ ] T067 [US4] `app/src/main/kotlin/…/ui/record/RecordDetailScreen.kt` を実装する（記録の確認・修正 — FR-019。`UpdateRecordUseCase` を呼ぶ）
- [ ] T068 [US4] `app/src/main/kotlin/…/ui/record/NoteEditor.kt` にメモの入力・編集を実装する（FR-020）
- [ ] T069 [US4] `./gradlew assembleDebug` を確認する

**Checkpoint**: 全ユーザーストーリーがそれぞれ独立して動作する

---

## Phase 7: Polish & 実機検証

**Purpose**: 全ストーリーに横断する仕上げと、自動テストで代替できない確認

- [ ] T070 [P] `README.md` の「進行状況」を更新し、実装済みスコープと未実装スコープ（A-9, B-4〜B-6, C群〜F群）を明記する
- [ ] T071 [P] `domain/src/test/` のテストが憲法 原則III の必須3項目（継承／巻単位変更の以降への継承／一括更新の店舗独立性）と「読書状態の遷移」を網羅していることを確認し、不足があれば追加する
- [ ] T072 禁止・制約要求の遵守をコードレビューで確認し、結果を記録する（FR-009 あいまい検索を提供しない／FR-011 読了位置を持たない／FR-012 購入・所蔵に相当する概念を持たない／FR-018 位置補足情報を持たない／FR-025 データが端末内に閉じている／FR-026 スコープ外機能を実装していない。**識別子・UI 文言・DB 列名を対象に検索する** — 憲法 原則II）
- [ ] T073 [quickstart.md](./quickstart.md) 4.3 のうち、暗所（個室相当の照明環境）での読み取り可否を実機で確認する（棚番号シールの被覆頻度は T001 で確認済み）
- [ ] T074 SC-001（30秒以内・5タップ以内）、**SC-002（手入力へ1操作で切り替えられ、記録完了までが読み取り時と同等）**、SC-003（3操作以内・5秒以内）を実機で計測し、満たさない場合は導線を見直す
- [ ] T075 実機確認の結果を記録し、**未確認の項目が残る場合は完了報告時に「実機確認が必要」と明示する**（憲法 原則IV）
- [ ] T076 `./gradlew :domain:test :data:test assembleDebug` を通し、最終確認とする

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 依存なし。T001（実店舗観察）はコードに依存しないため最初から着手できる
- **Foundational (Phase 2)**: Phase 1 完了後。**全ユーザーストーリーをブロックする**
- **User Stories (Phase 3〜6)**: Phase 2 完了後。優先度順に P1 → P2 → P3
- **Polish (Phase 7)**: 対象とするストーリーが完了した後

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
- **各タスクの完了時に `./gradlew assembleDebug` が通ることを確認する**（憲法「開発ワークフローと品質ゲート」）。フェーズ末の確認タスク（T009, T050, T059, T066, T069, T076）は、その節目での明示的なゲート
- タスクごと、または論理的なまとまりごとにコミットする。コミットメッセージは日本語（憲法 原則VII）
- **`applyShelfNumberToWork()`（T020）を UI から呼ばないこと。** 憲法 原則III のテスト要件のためだけに存在し、A-9 は今回スコープ外（plan.md の Complexity Tracking）
- **`ReadingStatus` に第3の値を追加しないこと。** 「離脱」は作品単位の状態であり今回は保持しない（憲法 原則II）
- テストの実行基盤は `:domain` が JUnit 5、`:data` が JUnit 4 + Robolectric。`:domain` に Android 由来の依存を持ち込まないための使い分けであり、統一しないこと（憲法 原則III）
- 実機でしか検証できない項目（T001, T050, T059, T073, T074）は、自動テストの成功をもって完了としない（憲法 原則IV）
