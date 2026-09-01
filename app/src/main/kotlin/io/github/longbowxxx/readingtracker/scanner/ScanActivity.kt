package io.github.longbowxxx.readingtracker.scanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

/**
 * バーコード読み取り専用の全画面 Activity。
 *
 * 結果は ActivityResult として返す。呼び出し側は [CameraXMlKitBarcodeScanner] 越しに
 * `BarcodeScanner` としてのみ触れるため、この Activity は UI 層とドメイン層から見えない。
 *
 * 戻る操作は `RESULT_CANCELED` になる。呼び出し側はエラーを出さず手入力へ落とす（FR-003）。
 */
class ScanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScanScreen(
                        onScanned = { isbn ->
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_ISBN, isbn.value))
                            finish()
                        },
                        onCameraUnavailable = {
                            setResult(RESULT_CAMERA_UNAVAILABLE)
                            finish()
                        },
                    )
                }
            }
        }
    }

    companion object {
        /** 読み取れた ISBN（13桁・ハイフンなし）を載せる Intent の extra キー。 */
        const val EXTRA_ISBN = "io.github.longbowxxx.readingtracker.scanner.ISBN"

        /** カメラを起動できなかった。呼び出し側は手入力へ落とす（FR-003）。 */
        const val RESULT_CAMERA_UNAVAILABLE = Activity.RESULT_FIRST_USER
    }
}
