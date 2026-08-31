package io.github.longbowxxx.readingtracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.longbowxxx.readingtracker.domain.port.ReadingRepository
import io.github.longbowxxx.readingtracker.domain.usecase.RecordVolumeUseCase
import io.github.longbowxxx.readingtracker.domain.usecase.UpdateRecordUseCase
import io.github.longbowxxx.readingtracker.domain.usecase.VisitListUseCase
import javax.inject.Singleton

/**
 * ドメイン層のユースケースを提供する。
 *
 * ドメイン層のクラスに DI の注釈を付けないのは、`:domain` を純粋な Kotlin のまま
 * 保つため（憲法 原則III）。配線はこの層で行う。
 *
 * バーコード読み取り（`BarcodeScanner`）はここで提供しない。スキャン UI が Activity を
 * 起動するため Activity のコンテキストが必要で、画面側で生成する（研究 R-003 の
 * 差し替え可能性は、画面側の1行を変えるだけで保たれる）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun provideUpdateRecordUseCase(repository: ReadingRepository): UpdateRecordUseCase = UpdateRecordUseCase(repository)

    @Provides
    @Singleton
    fun provideRecordVolumeUseCase(repository: ReadingRepository, updateRecordUseCase: UpdateRecordUseCase): RecordVolumeUseCase =
        RecordVolumeUseCase(repository, updateRecordUseCase)

    @Provides
    @Singleton
    fun provideVisitListUseCase(repository: ReadingRepository): VisitListUseCase = VisitListUseCase(repository)
}
