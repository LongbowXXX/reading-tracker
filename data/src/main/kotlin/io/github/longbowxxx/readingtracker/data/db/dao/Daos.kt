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
