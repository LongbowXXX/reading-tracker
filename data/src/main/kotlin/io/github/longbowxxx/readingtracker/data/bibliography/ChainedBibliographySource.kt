package io.github.longbowxxx.readingtracker.data.bibliography

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.port.BibliographyResult
import io.github.longbowxxx.readingtracker.domain.port.BibliographySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * 複数の取得経路を直列に試す（contracts/bibliography-source.md）。
 *
 * 規則
 * 1. 先頭から順に問い合わせる
 * 2. `Found` が返った時点で確定し、以降の経路は呼ばない
 * 3. `NotFound` なら次の経路へ進む
 * 4. `Unavailable` も次の経路へ進む（一方が落ちていても他方で拾える）
 * 5. すべて尽きたとき、1件でも `Unavailable` があれば `Unavailable`、
 *    すべて `NotFound` なら `NotFound`
 *
 * 全体のタイムアウトは各経路の上限（3秒）とは独立に適用される。超過した時点で
 * 打ち切り、`Unavailable` を返す。**例外は投げない。**
 */
class ChainedBibliographySource(
    private val sources: List<BibliographySource>,
    private val overallTimeoutMillis: Long = DEFAULT_OVERALL_TIMEOUT_MILLIS,
) : BibliographySource {
    override suspend fun lookup(isbn: Isbn): BibliographyResult {
        if (sources.isEmpty()) return BibliographyResult.NotFound

        val outcome =
            withTimeoutOrNull(overallTimeoutMillis) {
                var lastUnavailable: BibliographyResult.Unavailable? = null

                for (source in sources) {
                    val result =
                        try {
                            source.lookup(isbn)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // 契約上 lookup は例外を投げないが、実装の綻びで落ちないよう受け止める
                            BibliographyResult.Unavailable(e)
                        }

                    when (result) {
                        is BibliographyResult.Found -> return@withTimeoutOrNull result
                        is BibliographyResult.Unavailable -> lastUnavailable = result
                        BibliographyResult.NotFound -> Unit
                    }
                }

                lastUnavailable ?: BibliographyResult.NotFound
            }

        return outcome ?: BibliographyResult.Unavailable(IOException("書誌情報の取得が ${overallTimeoutMillis}ms を超えました"))
    }

    companion object {
        /** 2経路合わせた上限。SC-001（記録完了30秒以内）に対する余裕を残す値。 */
        const val DEFAULT_OVERALL_TIMEOUT_MILLIS: Long = 6_000
    }
}
