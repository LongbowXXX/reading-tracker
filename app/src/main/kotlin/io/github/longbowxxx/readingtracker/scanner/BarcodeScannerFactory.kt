package io.github.longbowxxx.readingtracker.scanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.longbowxxx.readingtracker.domain.port.BarcodeScanner
import io.github.longbowxxx.readingtracker.domain.port.ScanResult
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/** [ScanActivity] を起動し、その結果を [ScanResult] へ変換する。 */
class ScanContract : ActivityResultContract<Unit, ScanResult>() {
    override fun createIntent(context: Context, input: Unit): Intent = Intent(context, ScanActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): ScanResult = when (resultCode) {
        Activity.RESULT_OK -> {
            val raw = intent?.getStringExtra(ScanActivity.EXTRA_ISBN)
            if (raw.isNullOrBlank()) {
                ScanResult.Unavailable(IOException("バーコードの値を読み取れませんでした"))
            } else {
                ScanResult.Scanned(raw)
            }
        }

        ScanActivity.RESULT_CAMERA_UNAVAILABLE -> ScanResult.Unavailable(IOException("カメラを起動できませんでした"))

        // 戻る操作。エラーではなく、手入力への切り替えとして扱う（FR-003）
        else -> ScanResult.Cancelled
    }
}

/**
 * CameraX + ML Kit による読み取り（research.md R-003 の差し替え先）。
 *
 * プレビューを維持したまま連続解析し、978/979 で始まる ISBN が読めた時点で確定する。
 * 書籍バーコード下段（192 始まりの日本図書コード）を読んでも**エラーにせず読み取りを続ける**
 * （contracts/barcode-scanner.md 対象シンボル、Issue #1）。
 *
 * スキャン画面は Activity として起動するため、実際の起動は [rememberBarcodeScanner] が
 * 用意した関数に委ねる。この分離により `suspend fun scan(): ScanResult` という
 * ポートの形を変えずに済み、UI 層とドメイン層へ差し替えが波及しない。
 */
class CameraXMlKitBarcodeScanner(private val launchScan: (CompletableDeferred<ScanResult>) -> Unit) : BarcodeScanner {
    override suspend fun scan(): ScanResult {
        val result = CompletableDeferred<ScanResult>()
        launchScan(result)
        return result.await()
    }
}

/**
 * [BarcodeScanner] を Compose のライフサイクルに載せて組み立てる。
 *
 * Activity の起動には `ActivityResultLauncher` が要り、これは Composable が保持しなければ
 * ならない。launcher の結果で [CompletableDeferred] を完了させることで、
 * 呼び出し側からは suspend 関数1つに見せる。
 */
@Composable
fun rememberBarcodeScanner(): BarcodeScanner {
    // 起動と結果受け取りの間で待っている呼び出しを保持する
    val pending = remember { AtomicReference<CompletableDeferred<ScanResult>?>(null) }

    val launcher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            pending.getAndSet(null)?.complete(result)
        }

    return remember(launcher) {
        CameraXMlKitBarcodeScanner { deferred ->
            pending.set(deferred)
            launcher.launch(Unit)
        }
    }
}
