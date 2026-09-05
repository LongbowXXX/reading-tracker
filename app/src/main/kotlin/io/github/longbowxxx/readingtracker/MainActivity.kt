package io.github.longbowxxx.readingtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.github.longbowxxx.readingtracker.ui.ReadingTrackerNavGraph
import io.github.longbowxxx.readingtracker.ui.ai.AiGateScreen

/**
 * アプリの各画面を受け持つ Activity。画面遷移は Compose Navigation で行う。
 * 例外はバーコード読み取りで、カメラのライフサイクルを画面と一致させるため
 * 専用の `ScanActivity` を起動し、結果を `ActivityResult` で受け取る。
 *
 * 本体は起動ゲート（`AiGateScreen`、Issue #9）で包む。**AI を使わない画面も含めて**
 * オンデバイス AI が使えるまで通さないため、包む位置はナビゲーションの外側になる（SC-008）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AiGateScreen {
                        ReadingTrackerNavGraph()
                    }
                }
            }
        }
    }
}
