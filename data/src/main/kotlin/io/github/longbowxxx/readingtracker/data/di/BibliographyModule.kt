package io.github.longbowxxx.readingtracker.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.longbowxxx.readingtracker.data.bibliography.ChainedBibliographySource
import io.github.longbowxxx.readingtracker.data.bibliography.NdlBibliographySource
import io.github.longbowxxx.readingtracker.data.bibliography.OpenBdBibliographySource
import io.github.longbowxxx.readingtracker.domain.port.BibliographySource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 書誌情報の取得経路を提供する（research.md R-001）。
 *
 * openBD を第一経路、国立国会図書館サーチを第二経路とする連鎖を組む。
 * 経路の差し替えはこのモジュールの変更だけで済み、UI とドメインには波及しない。
 */
@Module
@InstallIn(SingletonComponent::class)
object BibliographyModule {
    /** 1経路あたりの上限。待たせず手入力へ落とすための値（FR-007）。 */
    private const val PER_SOURCE_TIMEOUT_SECONDS = 3L

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient
        .Builder()
        .callTimeout(PER_SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(PER_SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PER_SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideBibliographySource(openBd: OpenBdBibliographySource, ndl: NdlBibliographySource): BibliographySource =
        ChainedBibliographySource(listOf(openBd, ndl))
}
