# Phase 1: データモデル

**Feature**: 読書記録と棚番号の管理（中核）
**Date**: 2026-08-30
**Source**: [spec.md](./spec.md) の Key Entities と Functional Requirements

---

## 設計上の要点

1. **棚番号は「店舗 × 作品 × 巻」に紐づく**（FR-013）。読書状態は「作品 × 巻」に紐づき、店舗をまたいで1つである（spec の Assumptions）。この2つを別のテーブルに分けることが本モデルの中心。
2. **棚番号が未入力でも、その店舗でその巻を記録した事実は残る**（FR-017, FR-024）。したがって配架レコードは棚番号が `NULL` の状態で存在しうる。棚番号の有無を、店舗での記録の有無と混同してはならない。
3. **購入・所蔵に相当する概念を持たない**（憲法 原則II）。所有フラグ、購入日、蔵書区分といった列は存在しない。
4. **巻内の読了位置を持たない**（FR-011）。ページ数・話数の列は存在しない。
5. スコープ外の要求（A-9, B-4, B-5, E-1 ほか）を後から追加できるよう、更新日時と作品単位の状態を置く余地を残す（FR-026）。

---

## エンティティ

### Store（店舗）

| 列 | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| `id` | Long | PK, autoGenerate | |
| `name` | String | NOT NULL | 利用者が入力した店舗名（FR-030）。正規化・重複判定は行わない |
| `createdAt` | Instant | NOT NULL | |

- F-1（店舗の編集・削除）はスコープ外のため、更新系の列は持たせない。

### Work（作品）

| 列 | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| `id` | Long | PK, autoGenerate | |
| `title` | String | NOT NULL | 表示用の作品名。巻数表記を除いたもの |
| `matchKey` | String | NOT NULL, INDEX | 自動照合用の正規化文字列（FR-027, research.md R-002） |
| `author` | String? | | |
| `publisher` | String? | | |
| `isProvisional` | Boolean | NOT NULL, default false | 暫定名のみで作られた作品か（FR-008） |
| `createdAt` | Instant | NOT NULL | |

- `matchKey` に UNIQUE 制約は付けない。同名異作品を利用者が分離できる必要があるため（spec の Edge Cases）。照合は INDEX による検索と利用者の確認で行う。
- E-1（離脱）は将来ここに `abandonedAt: Instant?` を追加して表現する。今回は追加しない（FR-026）。

### Volume（巻）

| 列 | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| `id` | Long | PK, autoGenerate | |
| `workId` | Long | NOT NULL, FK → Work.id, INDEX | |
| `volumeNumber` | Int? | | 不明な場合は NULL（暫定記録） |
| `isbn13` | String? | UNIQUE（NULL は重複可） | 13桁に正規化して保持。10桁 ISBN も変換して格納（FR-005） |
| `displayTitle` | String | NOT NULL | 取得または入力されたままのタイトル |
| `publishedDate` | String? | | 発売日。書誌により粒度が異なるため文字列で保持 |
| `createdAt` | Instant | NOT NULL | |

- UNIQUE(`workId`, `volumeNumber`)。ただし `volumeNumber` が NULL の行は SQLite の仕様上この制約に拘束されないため、巻数不明の暫定記録は同一作品に複数作れる。これは意図した挙動。
- ISBN は正規化後の13桁のみを保持する。ハイフンは除去する。

### ReadingRecord（読書記録）

| 列 | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| `id` | Long | PK, autoGenerate | |
| `volumeId` | Long | NOT NULL, **UNIQUE**, FK → Volume.id | 巻に対して読書状態は1つ（FR-029、spec の Assumptions） |
| `status` | ReadingStatus | NOT NULL | `READ`（読了） / `PAUSED`（中断） の2値のみ（FR-010, FR-012） |
| `note` | String? | | 巻ごとのメモ（FR-020） |
| `recordedAt` | Instant | NOT NULL | 最後に記録・更新した日時 |

- `volumeId` の UNIQUE 制約が FR-029（再記録は既存記録の編集）をスキーマ側で担保する。
- ページ数・話数に相当する列は置かない（FR-011）。

### ShelfPlacement（配架 / 棚番号）

| 列 | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| `id` | Long | PK, autoGenerate | |
| `storeId` | Long | NOT NULL, FK → Store.id, INDEX | |
| `workId` | Long | NOT NULL, FK → Work.id, INDEX | 店舗×作品での絞り込みと将来の一括更新（A-9）のために保持 |
| `volumeId` | Long | NOT NULL, FK → Volume.id | |
| `shelfNumber` | String? | | **NULL は「棚番号未入力」**（FR-017）。書式検証は行わない |
| `updatedAt` | Instant | NOT NULL | B-4（棚番号がいつ時点の情報か）の下地。今回は表示しない |

- **UNIQUE(`storeId`, `volumeId`)**。`volumeId` から `workId` は一意に定まるため、この制約が「店舗 × 作品 × 巻で一意」（FR-013）を表現する。
- **`storeId` が UNIQUE 制約の構成要素であること**が、店舗独立性（FR-014, SC-005）のスキーマ上の担保である。ある店舗の行を更新しても、別の `storeId` を持つ行には到達しない。
- **この行は、棚番号が未入力でも作成される。** 「その店舗でその巻を記録した」という事実を表すのはこの行であり、`shelfNumber` の有無ではない（FR-024）。

---

## 関連

```text
Store 1 ──< ShelfPlacement >── 1 Volume
                  │
                  └──> Work（冗長保持。Volume.workId と常に一致）

Work 1 ──< Volume 1 ──1 ReadingRecord
```

- `ShelfPlacement.workId` は `Volume.workId` の冗長コピー。作品単位の絞り込み（B-1）と将来の一括更新（A-9）を1テーブルで完結させるために持つ。**書き込み時に必ず `Volume.workId` と一致させる**。暫定記録を正式な作品へ紐づけ直す際（FR-008）は、`Volume.workId` と全 `ShelfPlacement.workId` を同時に更新する。

---

## 状態遷移

`ReadingStatus` は2値のみを持ち、遷移に制限を設けない。

```text
（記録なし） ──> PAUSED（中断）
（記録なし） ──> READ（読了）
PAUSED ──> READ    （中断した巻を読み切った。FR-029 により既存記録の更新）
READ ──> PAUSED    （読了の誤記録を訂正。FR-019）
```

- 「離脱」は巻ではなく作品の状態であり、今回は保持しない（憲法 原則II、FR-026）。
- 購入・所蔵に相当する状態は存在しない（憲法 原則II）。

---

## ドメイン層が扱う値と規則

`:domain` モジュールに、Android 非依存の純粋な型と関数として置く（憲法 原則III）。

| 名前 | 責務 | 対応 FR |
| --- | --- | --- |
| `Isbn` | 13桁への正規化、チェックディジット検証、10桁→13桁変換 | FR-004, FR-005 |
| `ReadingStatus` | 読了 / 中断の2値 | FR-010, FR-012 |
| `ShelfNumber` | 未入力を表現できる棚番号（書式検証なし） | FR-017, FR-018 |
| `resolveInheritedShelfNumber()` | 棚番号の継承元決定（巻番号順 → 記録日時順 → なし） | FR-015, FR-016 |
| `resolveNextVolume()` | 次に読むべき巻の判定（中断中の巻 → 読了最大巻+1 → 不明） | FR-023 |
| `normalizeTitleToMatchKey()` / `extractVolumeNumber()` | 照合キーの生成と巻数抽出 | FR-027 |
| `applyShelfNumberToWork()` | 作品単位の棚番号一括適用（**戻り値が単一店舗に閉じることをテストで固定**。UI 導線は作らない） | 憲法 原則III、A-9 は今回スコープ外 |

### `resolveInheritedShelfNumber()` の規則（FR-015）

入力: 対象の店舗、対象の作品、対象の巻番号（NULL 可）、当該店舗・当該作品の配架レコード一覧

1. 対象巻より小さい `volumeNumber` を持つレコードのうち、`volumeNumber` が最大のものの `shelfNumber` を返す
2. 1 に該当がなければ、`updatedAt` が最も新しいレコードの `shelfNumber` を返す
3. どちらもなければ NULL（初期値なし）を返す

- `volumeNumber` が NULL のレコードは 1 の対象外。2 のフォールバックでのみ継承元になりうる。
- 継承元の `shelfNumber` が NULL の場合、戻り値も NULL となる（未入力が継承される）。
- 対象巻の `volumeNumber` が NULL の場合、1 は評価せず 2 から始める。

### `resolveNextVolume()` の規則（FR-023）

入力: 当該作品の巻と読書記録の一覧

1. `PAUSED` の巻があれば、そのうち `volumeNumber` が最小のものを返す
2. なければ、`READ` の巻のうち `volumeNumber` が最大のものに 1 を加えた巻番号を返す（実在は判定しない）
3. `volumeNumber` を持つ記録が1件もなければ「不明」を返す

---

## マイグレーション方針

- 初回リリースのため `version = 1` から開始する。破壊的変更が必要になった場合も、開発中はスキーマを作り直してよい。
- Room のスキーマ JSON をリポジトリに含め、将来のマイグレーション差分の根拠とする。
