package io.github.longbowxxx.readingtracker.data.bibliography

import io.github.longbowxxx.readingtracker.domain.model.Isbn
import io.github.longbowxxx.readingtracker.domain.port.BibliographyResult
import io.github.longbowxxx.readingtracker.domain.port.BibliographySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/**
 * HTTP で書誌情報を取得する経路の共通実装。
 *
 * **例外を投げず、失敗はすべて [BibliographyResult.Unavailable] にする。**
 * 電波が届かない個室での失敗は例外ではなく通常経路であり、上位は手入力へ落とす（FR-007）。
 */
abstract class HttpBibliographySource(private val client: OkHttpClient) : BibliographySource {
    /** 問い合わせ先の URL を組み立てる。 */
    protected abstract fun buildUrl(isbn: Isbn): String

    /** 応答本文を解析する。 */
    protected abstract fun parseBody(body: String, isbn: Isbn): BibliographyResult

    override suspend fun lookup(isbn: Isbn): BibliographyResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(buildUrl(isbn)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use BibliographyResult.Unavailable(IOException("HTTP ${response.code}"))
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    BibliographyResult.Unavailable(IOException("応答が空です"))
                } else {
                    parseBody(body, isbn)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BibliographyResult.Unavailable(e)
        }
    }
}

/**
 * openBD から書誌情報を取得する（research.md R-001、第一経路）。
 *
 * 10桁 ISBN は受け付けないため、[Isbn] が13桁へ正規化済みであることが前提。
 */
class OpenBdBibliographySource
@Inject
constructor(client: OkHttpClient) : HttpBibliographySource(client) {
    override fun buildUrl(isbn: Isbn): String = "$ENDPOINT?isbn=${isbn.value}"

    override fun parseBody(body: String, isbn: Isbn): BibliographyResult = OpenBdResponseParser.parse(body, isbn)

    companion object {
        const val ENDPOINT: String = "https://api.openbd.jp/v1/get"
    }
}

/**
 * 国立国会図書館サーチ（SRU）から書誌情報を取得する（research.md R-001、第二経路）。
 *
 * API 側が10桁/13桁の双方に変換して完全一致検索するため、旧刊に対する保険になる。
 * 個人・非営利で利益を得ない利用は申請不要。
 */
class NdlBibliographySource
@Inject
constructor(client: OkHttpClient) : HttpBibliographySource(client) {
    override fun buildUrl(isbn: Isbn): String = "$ENDPOINT?operation=searchRetrieve&query=isbn=${isbn.value}"

    override fun parseBody(body: String, isbn: Isbn): BibliographyResult = NdlResponseParser.parse(body, isbn)

    companion object {
        const val ENDPOINT: String = "https://ndlsearch.ndl.go.jp/api/sru"
    }
}
