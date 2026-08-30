package io.github.longbowxxx.readingtracker.data.db

import androidx.room.TypeConverter
import io.github.longbowxxx.readingtracker.domain.model.ReadingStatus
import java.time.Instant

/**
 * Room の型コンバータ。
 *
 * [Instant] はエポックミリ秒で保持する。[ReadingStatus] は列挙の名前で保持し、
 * 序数に依存しない（値の追加・並べ替えで既存データが壊れないようにするため。
 * ただし憲法 原則II により第3の値は追加しない）。
 */
class Converters {
    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun readingStatusToName(value: ReadingStatus?): String? = value?.name

    @TypeConverter
    fun nameToReadingStatus(value: String?): ReadingStatus? = value?.let(ReadingStatus::valueOf)
}
