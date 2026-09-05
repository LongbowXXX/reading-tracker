package io.github.longbowxxx.readingtracker.data.di

import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.longbowxxx.readingtracker.data.ai.GenAiAvailability
import io.github.longbowxxx.readingtracker.domain.port.AiAvailability
import javax.inject.Singleton

/**
 * オンデバイス AI のクライアントと可用性の判定を提供する（Issue #9）。
 *
 * [GenerativeModel] を `@Singleton` で1インスタンスに絞るのは、
 * **判定（起動ゲート）と推論（タイトル解析）で別のクライアントを持たせないため**である。
 * 別々に生成すると、同じモデルに対して二重に接続を張ることになる。
 * タイトル解析側（[TitleModule]）も、ここで提供したものを受け取る。
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun provideGenerativeModel(): GenerativeModel = Generation.getClient()

    @Provides
    @Singleton
    fun provideAiAvailability(model: GenerativeModel): AiAvailability = GenAiAvailability(model)
}
