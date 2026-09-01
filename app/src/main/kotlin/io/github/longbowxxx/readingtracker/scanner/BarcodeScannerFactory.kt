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
import kotlinx.coroutines.CancellationException
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
 * 実行中のスキャンを1件だけ保持する枠。
 *
 * **意図的に Composition の外（プロセス全体で1つ）へ置いている。`remember` へ戻してはならない。**
 * `remember` にすると、スキャン中の構成変更（画面回転、ダークモード切替、フォントサイズ変更、
 * 分割画面のリサイズ）で背後の `MainActivity` が再生成されたときに Composition ごと破棄され、
 * 枠は空のものへ入れ替わる。`rememberLauncherForActivityResult` は同じキーで登録し直されるため
 * 結果自体は届くが、完了させるべき待ちが枠に無いので何も起こらない。利用者から見ると
 * 「読み取りに成功してスキャン画面は閉じたのに、記録画面が動かない」状態になり、
 * 古い [CompletableDeferred] を待っていたコルーチンもそのまま漏れる。
 *
 * アプリ全体で同時に走るスキャンは高々1件であり、プロセス全体で1つの枠で足りる。
 */
private val pendingScan = AtomicReference<CompletableDeferred<ScanResult>?>(null)

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
 *
 * [pending] は実行中のスキャンを1件だけ保持する。`scan()` はまず compare-and-set で
 * この枠を獲得しようとし、獲得できた呼び出しだけが実際に Activity を起動する。
 * 既に別の呼び出しが枠を占めている間に二重タップ等で `scan()` が再度呼ばれた場合、
 * 新しい Activity は起動せず、既存の呼び出しの結果を横から待つ。こうしないと、
 * 先に呼ばれた側の待ちが上書きされて永遠に完了しない（呼び出し元がハングする）。
 */
class CameraXMlKitBarcodeScanner(
    private val pending: AtomicReference<CompletableDeferred<ScanResult>?>,
    private val launchScan: () -> Unit,
) : BarcodeScanner {
    override suspend fun scan(): ScanResult {
        val result = CompletableDeferred<ScanResult>()
        while (true) {
            if (pending.compareAndSet(null, result)) {
                // 枠を獲得できた。自分が Activity を起動し、その結果を待つ
                try {
                    launchScan()
                    return result.await()
                } catch (e: CancellationException) {
                    // 待ちが打ち切られた（呼び出し元のスコープ破棄など）。枠を握ったままにすると
                    // 以降の scan() が誰も完了させない待ちへ入るため、自分の枠だけを解放する。
                    // 取り消しは握り潰さず、そのまま伝播させる
                    pending.compareAndSet(result, null)
                    throw e
                } catch (e: Exception) {
                    // launcher が登録解除済みなら launch() は IllegalStateException を投げる。
                    // 契約（domain/…/port/BarcodeScanner.kt）どおり scan は例外を投げない。
                    // 枠を解放してから Unavailable として返す。解放しないと以降の scan() が
                    // 完了しない待ちに入り、読み取りボタンがプロセスの終わりまで無反応になる
                    pending.compareAndSet(result, null)
                    return ScanResult.Unavailable(e)
                }
            }

            // 枠は他の呼び出しが占めている。新たに Activity は起動せず、
            // その呼び出しの結果を横から待つ（二重タップで呼び出し元を放置しないため）
            val inFlight = pending.get()
            if (inFlight != null) {
                return inFlight.await()
            }

            // ここに来るのは、直前の compareAndSet 失敗と pending.get() の間に
            // 実行中のスキャンが完了して枠が null に戻った場合のみ。
            // 既に何も実行中ではないので、ループして自分がスキャンを開始する
        }
    }
}

/**
 * [BarcodeScanner] を Compose のライフサイクルに載せて組み立てる。
 *
 * Activity の起動には `ActivityResultLauncher` が要り、これは Composable が保持しなければ
 * ならない。launcher の結果で [CompletableDeferred] を完了させることで、
 * 呼び出し側からは suspend 関数1つに見せる。
 *
 * 待ちを保持する枠だけは Composable の外（[pendingScan]）に置く。理由はそちらの説明を見ること。
 */
@Composable
fun rememberBarcodeScanner(): BarcodeScanner {
    val launcher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            // 構成変更で Composition が作り直されても、枠は同じものが残っている
            pendingScan.getAndSet(null)?.complete(result)
        }

    return remember(launcher) {
        CameraXMlKitBarcodeScanner(pendingScan) {
            launcher.launch(Unit)
        }
    }
}
