package io.github.longbowxxx.readingtracker.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.longbowxxx.readingtracker.data.title.GenAiTitleAnalyzer
import io.github.longbowxxx.readingtracker.domain.port.TitleAnalyzer
import io.github.longbowxxx.readingtracker.domain.title.CachingTitleAnalyzer
import io.github.longbowxxx.readingtracker.domain.title.ChainedTitleAnalyzer
import io.github.longbowxxx.readingtracker.domain.title.RuleBasedTitleAnalyzer
import javax.inject.Singleton

/**
 * タイトル解析の経路を提供する（Issue #4）。
 *
 * オンデバイス AI を第一経路、正規表現を第二経路とする連鎖を組む。
 * AI が非対応の端末・推論に失敗した場合・時間切れの場合は第二経路が答えるため、
 * **どの端末でも記録は完了する**（憲法 原則VI）。
 *
 * 連鎖の外側に [CachingTitleAnalyzer] を置くのは、1冊の記録で同じタイトルが
 * 2回解析されるのを防ぐためと、推論結果の揺れで照合キーが変わるのを抑えるため。
 */
@Module
@InstallIn(SingletonComponent::class)
object TitleModule {
    @Provides
    @Singleton
    fun provideTitleAnalyzer(): TitleAnalyzer = CachingTitleAnalyzer(
        ChainedTitleAnalyzer(
            listOf(
                GenAiTitleAnalyzer(),
                RuleBasedTitleAnalyzer(),
            ),
        ),
    )
}
