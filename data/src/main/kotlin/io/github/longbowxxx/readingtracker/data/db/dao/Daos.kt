package io.github.longbowxxx.readingtracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.longbowxxx.readingtracker.data.db.entity.ReadingRecordEntity
import io.github.longbowxxx.readingtracker.data.db.entity.ShelfPlacementEntity
import io.github.longbowxxx.readingtracker.data.db.entity.StoreEntity
import io.github.longbowxxx.readingtracker.data.db.entity.VolumeEntity
import io.github.longbowxxx.readingtracker.data.db.entity.WorkEntity

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores ORDER BY name")
    suspend fun listAll(): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun findById(id: Long): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(store: StoreEntity): Long
}

@Dao
interface WorkDao {
    @Query("SELECT * FROM works WHERE matchKey = :matchKey LIMIT 1")
    suspend fun findByMatchKey(matchKey: String): WorkEntity?

    @Query("SELECT * FROM works WHERE id = :id")
    suspend fun findById(id: Long): WorkEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(work: WorkEntity): Long

    @Query("UPDATE volumes SET workId = :newWorkId WHERE id = :volumeId")
    suspend fun updateVolumeWorkId(volumeId: Long, newWorkId: Long)

    @Query("UPDATE shelf_placements SET workId = :newWorkId WHERE volumeId = :volumeId")
    suspend fun updatePlacementWorkId(volumeId: Long, newWorkId: Long)

    /**
     * 暫定記録を正式な作品へ付け替える（FR-008）。
     * 巻と配架レコードの作品 ID を**同一トランザクションで**更新し、不整合を残さない。
     */
    @Transaction
    suspend fun relinkVolumeToWork(volumeId: Long, newWorkId: Long) {
        updateVolumeWorkId(volumeId, newWorkId)
        updatePlacementWorkId(volumeId, newWorkId)
    }
}

@Dao
interface VolumeDao {
    @Query("SELECT * FROM volumes WHERE id = :id")
    suspend fun findById(id: Long): VolumeEntity?

    @Query("SELECT * FROM volumes WHERE isbn13 = :isbn13 LIMIT 1")
    suspend fun findByIsbn(isbn13: String): VolumeEntity?

    @Query("SELECT * FROM volumes WHERE workId = :workId AND volumeNumber = :volumeNumber LIMIT 1")
    suspend fun findByNumber(workId: Long, volumeNumber: Int): VolumeEntity?

    @Query("SELECT * FROM volumes WHERE workId = :workId ORDER BY volumeNumber")
    suspend fun listByWork(workId: Long): List<VolumeEntity>

    /**
     * 巻の書誌情報を更新する（FR-008 の紐づけ、FR-019 の修正）。
     * **主キーは変えない。** 読書記録と配架レコードは巻 ID で結ばれているため。
     */
    @Query(
        """
        UPDATE volumes
        SET volumeNumber = :volumeNumber,
            isbn13 = :isbn13,
            displayTitle = :displayTitle,
            publishedDate = :publishedDate
        WHERE id = :id
        """,
    )
    suspend fun updateDetails(id: Long, volumeNumber: Int?, isbn13: String?, displayTitle: String, publishedDate: String?)

    /** 暫定名のまま残っている巻（FR-008 の紐づけ導線）。 */
    @Query(
        """
        SELECT v.id AS volumeId,
               v.workId AS workId,
               w.title AS workTitle,
               v.displayTitle AS displayTitle,
               v.volumeNumber AS volumeNumber
        FROM volumes v
        INNER JOIN works w ON w.id = v.workId
        WHERE w.isProvisional = 1
        ORDER BY w.title, v.id
        """,
    )
    suspend fun listProvisionalVolumes(): List<ProvisionalVolumeRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(volume: VolumeEntity): Long
}

@Dao
interface ReadingRecordDao {
    @Query("SELECT * FROM reading_records WHERE volumeId = :volumeId")
    suspend fun findByVolume(volumeId: Long): ReadingRecordEntity?

    @Query(
        """
        SELECT r.* FROM reading_records r
        INNER JOIN volumes v ON v.id = r.volumeId
        WHERE v.workId = :workId
        """,
    )
    suspend fun listByWork(workId: Long): List<ReadingRecordEntity>

    /**
     * 指定店舗で記録のある作品に属する、**すべての巻**の読書記録（B-3）。
     *
     * 読書状態は店舗をまたいで共有されるため、他店舗で読んだ巻も
     * 「次に読むべき巻」の判定に含める必要がある。
     */
    @Query(
        """
        SELECT r.volumeId AS volumeId,
               v.workId AS workId,
               v.volumeNumber AS volumeNumber,
               r.status AS status,
               r.note AS note,
               r.recordedAt AS recordedAt
        FROM reading_records r
        INNER JOIN volumes v ON v.id = r.volumeId
        WHERE v.workId IN (SELECT DISTINCT workId FROM shelf_placements WHERE storeId = :storeId)
        """,
    )
    suspend fun listReadingsForStoreWorks(storeId: Long): List<StoreReadingRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: ReadingRecordEntity): Long

    @Update
    suspend fun update(record: ReadingRecordEntity)

    /**
     * 既存記録があれば更新し、無ければ作成する（FR-029）。
     * UNIQUE(volumeId) があるため、重複行は作られない。
     */
    @Transaction
    suspend fun upsert(record: ReadingRecordEntity) {
        val existing = findByVolume(record.volumeId)
        if (existing == null) {
            insert(record)
        } else {
            update(record.copy(id = existing.id))
        }
    }
}

@Dao
interface ShelfPlacementDao {
    @Query("SELECT * FROM shelf_placements WHERE storeId = :storeId AND volumeId = :volumeId")
    suspend fun find(storeId: Long, volumeId: Long): ShelfPlacementEntity?

    /** 単一店舗・単一作品の配架レコード。棚番号の継承判定に渡す集合（FR-014）。 */
    @Query("SELECT * FROM shelf_placements WHERE storeId = :storeId AND workId = :workId")
    suspend fun listByStoreAndWork(storeId: Long, workId: Long): List<ShelfPlacementEntity>

    /** 指定店舗で記録のある作品の ID（B-1 の絞り込み。FR-024）。 */
    @Query("SELECT DISTINCT workId FROM shelf_placements WHERE storeId = :storeId")
    suspend fun listWorkIdsInStore(storeId: Long): List<Long>

    /**
     * 来店時の一覧のための集約（B-1, B-2）。
     *
     * 配架レコードに作品名と巻番号を突き合わせて返す。**指定店舗の行だけ**が対象であり、
     * 他店舗の棚番号は決して混ざらない（FR-014, FR-024）。
     * 棚番号が NULL の行も含める。行の存在がその店での記録を表すため（FR-017）。
     */
    @Query(
        """
        SELECT p.workId AS workId,
               w.title AS workTitle,
               p.volumeId AS volumeId,
               v.volumeNumber AS volumeNumber,
               p.shelfNumber AS shelfNumber,
               p.updatedAt AS updatedAt
        FROM shelf_placements p
        INNER JOIN works w ON w.id = p.workId
        INNER JOIN volumes v ON v.id = p.volumeId
        WHERE p.storeId = :storeId
        ORDER BY w.title, v.volumeNumber
        """,
    )
    suspend fun listStorePlacementRows(storeId: Long): List<StorePlacementRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(placement: ShelfPlacementEntity): Long

    @Update
    suspend fun update(placement: ShelfPlacementEntity)

    /**
     * 既存の配架レコードがあれば更新し、無ければ作成する。
     * **棚番号が NULL でも行を作る**（FR-017, FR-024）。
     */
    @Transaction
    suspend fun upsert(placement: ShelfPlacementEntity) {
        val existing = find(placement.storeId, placement.volumeId)
        if (existing == null) {
            insert(placement)
        } else {
            update(placement.copy(id = existing.id))
        }
    }
}
