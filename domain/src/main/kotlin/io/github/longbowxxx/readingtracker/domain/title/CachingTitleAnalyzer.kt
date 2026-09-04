package io.github.longbowxxx.readingtracker.domain.title

import io.github.longbowxxx.readingtracker.domain.port.TitleAnalysis
import io.github.longbowxxx.readingtracker.domain.port.TitleAnalyzer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 同じタイトルの解析結果を覚えておく。
 *
 * 1冊の記録で `suggestShelfNumber()` と `execute()` が同じタイトルを解析するため、
 * これが無いとオンデバイス推論が2回走る（憲法 原則VI）。
 *
 * **同じタイトルに同じ結果を返すことは、AI 経路では正しさにも効く。** 推論結果は
 * 決定的とは限らず、揺れると照合キーが変わって同じ作品が分裂するため、
 * 端末が起動している間だけでも結果を固定する。
 */
class CachingTitleAnalyzer(private val delegate: TitleAnalyzer, private val maxEntries: Int = DEFAULT_MAX_ENTRIES) : TitleAnalyzer {
    private val mutex = Mutex()

    // 解析できなかった（null）ことも覚える必要があるため、値は Optional 相当の箱で持つ
    private val cache =
        object : LinkedHashMap<String, Box>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Box>): Boolean = size > maxEntries
        }

    override suspend fun analyze(rawTitle: String): TitleAnalysis? {
        mutex.withLock { cache[rawTitle] }?.let { return it.value }

        val result = delegate.analyze(rawTitle)
        mutex.withLock { cache[rawTitle] = Box(result) }
        return result
    }

    private class Box(val value: TitleAnalysis?)

    companion object {
        /** 1回の来店で扱う冊数を十分に超える値。 */
        const val DEFAULT_MAX_ENTRIES: Int = 64
    }
}
