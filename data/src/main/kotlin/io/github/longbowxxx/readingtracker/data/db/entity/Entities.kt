package io.github.longbowxxx.readingtracker.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import java.time.Instant

/**
 * 店舗（data-model.md）。
 *
 * F-1（編集・削除）は本スコープ外のため更新系の列を持たない。
 * 新規登録は記録フローの店舗選択欄から行う（FR-030, FR-031）。
 */
@Entity(tableName = "stores")
data class StoreEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Instant)

/**
 * 作品。
 *
 * [matchKey] に UNIQUE 制約は付けない。同名の別作品を利用者が分離できる必要があるため
 * （spec.md の Edge Cases）。照合は索引による検索と利用者の確認で行う。
 */
@Entity(
    tableName = "works",
    indices = [Index(value = ["matchKey"])],
)
data class WorkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val matchKey: String,
    val author: String? = null,
    val publisher: String? = null,
    val isProvisional: Boolean = false,
    val createdAt: Instant,
)

/**
 * 巻。
 *
 * [volumeNumber] が NULL の行は UNIQUE(workId, volumeNumber) に拘束されない（SQLite の仕様）。
 * 巻数不明の暫定記録を同一作品に複数作れるのは意図した挙動（data-model.md）。
 * [isbn13] も同様に、NULL の行は UNIQUE に拘束されない。
 */
@Entity(
    tableName = "volumes",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workId"]),
        Index(value = ["isbn13"], unique = true),
        Index(value = ["workId", "volumeNumber"], unique = true),
    ],
)
data class VolumeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workId: Long,
    val volumeNumber: Int? = null,
    val isbn13: String? = null,
    val displayTitle: String,
    val publishedDate: String? = null,
    val createdAt: Instant,
)

/**
 * 読書記録。
 *
 * **UNIQUE(volumeId)** が「巻に対して読書状態は1つ」を担保する。これにより、
 * 同じ巻を再度記録しようとしても新規行は作れず、既存記録の編集になる（FR-029）。
 * ページ数・話数に相当する列は持たない（FR-011）。
 * 購入・所蔵に相当する列も持たない（憲法 原則II）。
 */
@Entity(
    tableName = "reading_records",
    foreignKeys = [
        ForeignKey(
            entity = VolumeEntity::class,
            parentColumns = ["id"],
            childColumns = ["volumeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["volumeId"], unique = true)],
)
data class ReadingRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volumeId: Long,
    val status: ReadingStatus,
    val note: String? = null,
    val recordedAt: Instant,
)

/**
 * 配架レコード（棚番号）。
 *
 * **UNIQUE(storeId, volumeId)** が「店舗 × 作品 × 巻で一意」を表現する（FR-013）。
 * `volumeId` から `workId` は一意に定まるため、この2列で足りる。
 *
 * **`storeId` が UNIQUE 制約の構成要素であること**が店舗独立性の担保である（FR-014）。
 * ある店舗の行を更新しても、別の `storeId` を持つ行には到達しない。
 *
 * [shelfNumber] が NULL でもこの行は作る。**行の存在が「その店舗でその巻を記録した」
 * 事実を表す**（FR-017 と FR-024 の両立）。
 */
@Entity(
    tableName = "shelf_placements",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VolumeEntity::class,
            parentColumns = ["id"],
            childColumns = ["volumeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["storeId", "volumeId"], unique = true),
        Index(value = ["storeId", "workId"]),
        Index(value = ["workId"]),
        Index(value = ["volumeId"]),
    ],
)
data class ShelfPlacementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeId: Long,
    val workId: Long,
    val volumeId: Long,
    val shelfNumber: String? = null,
    val updatedAt: Instant,
)
