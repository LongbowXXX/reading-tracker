# Contract: ドメイン層の公開 API

**Module**: `:domain`（純 Kotlin。Android 依存を持たない — 憲法 原則III）
**Package**: `io.github.longbowxxx.readingtracker.domain`

この契約は、UI 層・データ層がドメイン層に対して期待できる振る舞いを定める。シグネチャは実装時に調整しうるが、**入出力の意味と規則は変更しない**。規則の変更は spec の改訂を伴う。

---

## 値型

```kotlin
@JvmInline
value class Isbn private constructor(val value: String) {   // 常に13桁・ハイフンなし
    companion object {
        fun parse(raw: String): Result<Isbn>   // 10桁/13桁、ハイフン・空白混じりを受け付ける
    }
}

enum class ReadingStatus { READ, PAUSED }      // 読了 / 中断。第3の値を追加しないこと

@JvmInline
value class ShelfNumber(val value: String)     // 書式検証は行わない。未入力は ShelfNumber? の null で表す

data class VolumeRef(val volumeId: Long, val volumeNumber: Int?)
data class PlacementSnapshot(
    val volumeId: Long,
    val volumeNumber: Int?,
    val shelfNumber: ShelfNumber?,
    val updatedAt: Instant,
)
data class ReadingSnapshot(
    val volumeId: Long,
    val volumeNumber: Int?,
    val status: ReadingStatus,
)
```

### `Isbn.parse` の契約（FR-004, FR-005）

| 入力 | 期待 |
| --- | --- |
| 13桁でチェックディジットが正しい | `Result.success` |
| 13桁でチェックディジットが誤り | `Result.failure(InvalidCheckDigit)` |
| 10桁でチェックディジットが正しい | 13桁へ変換して `Result.success` |
| 10桁で末尾が `X` | 受け付ける（10桁 ISBN の仕様） |
| ハイフン・空白を含む | 除去してから判定する |
| 桁数が 10 でも 13 でもない | `Result.failure(InvalidLength)` |

---

## 棚番号の継承（FR-015, FR-016）

```kotlin
fun resolveInheritedShelfNumber(
    targetVolumeNumber: Int?,
    placements: List<PlacementSnapshot>,   // 同一店舗・同一作品のレコードのみを渡すこと
): ShelfNumber?
```

**事前条件**: `placements` は単一の店舗・単一の作品に属するレコードだけで構成される。**呼び出し側がこの絞り込みに責任を持つ。** ドメイン関数は店舗 ID を受け取らず、したがって他店舗のレコードへ到達する手段を持たない（FR-014 の構造的な担保）。

**規則**:

1. `targetVolumeNumber` が非 null のとき、`volumeNumber` がそれより小さいレコードのうち `volumeNumber` が最大のものの `shelfNumber` を返す
2. 1 に該当がなければ、`updatedAt` が最も新しいレコードの `shelfNumber` を返す
3. `placements` が空なら null を返す

**境界**:

| 状況 | 戻り値 |
| --- | --- |
| 30巻が `A-12`、31巻が `B-03` のとき、32巻を対象とする | `B-03` |
| 30巻が `A-12`、31巻が `B-03` のとき、31巻を対象とする | `A-12` |
| 5巻のみ記録済みのとき、3巻を対象とする | 5巻の棚番号（規則2のフォールバック） |
| 継承元の `shelfNumber` が null | null（未入力が継承される） |
| `targetVolumeNumber` が null | 規則1を評価せず規則2から始める |
| `volumeNumber` が null のレコード | 規則1の対象外。規則2でのみ継承元になる |

---

## 次に読むべき巻（FR-023）

```kotlin
sealed interface NextVolume {
    data class Paused(val volumeNumber: Int?, val volumeId: Long) : NextVolume
    data class Next(val volumeNumber: Int) : NextVolume     // 実在は保証しない
    data object Unknown : NextVolume
}

fun resolveNextVolume(readings: List<ReadingSnapshot>): NextVolume
```

**事前条件**: `readings` は単一の作品に属する記録のみ。

**規則**:

1. `PAUSED` の記録があれば、そのうち `volumeNumber` が最小のものを `Paused` として返す（`volumeNumber` が null のものは最後に評価する）
2. なければ、`READ` の記録のうち `volumeNumber` が最大のものに 1 を加えて `Next` を返す
3. `volumeNumber` を持つ記録が1件もなければ `Unknown` を返す

`Next` は次巻が実在するかを判定しない（新刊把握 D群はスコープ外）。

---

## 作品の照合（FR-027）

```kotlin
data class ParsedTitle(val matchKey: String, val workTitle: String, val volumeNumber: Int?)

fun parseVolumeTitle(rawTitle: String): ParsedTitle
```

**規則**: 全角英数字を半角へ、空白を除去、末尾の巻数表記（`12` / `（12）` / `第12巻` / `12巻` など）を抽出して除去する。抽出できない場合 `volumeNumber` は null とし、`matchKey` は正規化のみを適用した文字列とする。

**契約**: 同一シリーズの異なる巻から、同一の `matchKey` が得られること。この性質をテストで固定する。誤照合の完全な排除は目標としない（spec の Assumptions）。

---

## 作品単位の棚番号一括適用（憲法 原則III のテスト要件）

```kotlin
fun applyShelfNumberToWork(
    newShelfNumber: ShelfNumber,
    placements: List<PlacementSnapshot>,   // 同一店舗・同一作品のレコードのみ
): List<PlacementSnapshot>
```

**位置づけ**: A-9（一括更新）は今回スコープ外であり、**UI からの導線は作らない**。ただし憲法 原則III が「作品単位の一括更新が他店舗の記録に影響しないこと」のテストを要求するため、ドメイン関数としてのみ用意し、テストで店舗独立性を固定する。

**契約**: 入力に含まれるレコードのみを更新して返す。入力に無いレコードを生成・参照しない。関数は店舗 ID を受け取らないため、他店舗のレコードへ到達しえない。
