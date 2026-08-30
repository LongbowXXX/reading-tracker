package io.github.longbowxxx.readingtracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * アプリのエントリポイント。Hilt の依存グラフの起点となる。
 */
@HiltAndroidApp
class ReadingTrackerApplication : Application()
