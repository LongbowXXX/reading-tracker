package io.github.longbowxxx.readingtracker.scanner

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import io.github.longbowxxx.readingtracker.domain.model.Isbn
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * バーコード読み取り画面（FR-001）。
 *
 * プレビューを維持したまま連続解析し、上段の ISBN が読めた時点で [onScanned] を呼ぶ。
 * 下段の日本図書コードを読んでも**何も起こらない**。エラーも出さず、カメラも閉じない（Issue #1）。
 *
 * 手入力への切り替えは戻る操作で行う。呼び出し元がキャンセルとして受け取り、
 * エラーを出さずに手入力へ落とす（FR-003、憲法 原則VI）。
 *
 * @param onScanned 採用できる ISBN が読めた。**1度しか呼ばれない**
 * @param onCameraUnavailable カメラを起動できなかった。呼び出し側は手入力へ落とす
 */
@Composable
fun ScanScreen(onScanned: (Isbn) -> Unit, onCameraUnavailable: (Throwable) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    // 複数フレームで成立しうるため、確定は1度だけに絞る
    val alreadyScanned = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val barcodeScanner =
            BarcodeScanning.getClient(
                BarcodeScannerOptions
                    .Builder()
                    // 書籍バーコードは EAN-13。他のシンボルは解析対象にしない（契約 対象シンボル）
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13)
                    .build(),
            )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null
        // Future の解決前に画面を離脱する競合に対応するための破棄フラグ。
        // addListener と onDispose はどちらも mainExecutor（メインスレッド）上で動くため、
        // このフラグへのアクセスに追加の同期は要らない
        var disposed = false

        providerFuture.addListener({
            if (disposed) {
                // 束縛前に効果が破棄済み。analysisExecutor は shutdown 済み、barcodeScanner は
                // close 済みのため、ここで束縛すると壊れた状態で bindToLifecycle を呼んでしまう。
                // 取得できたプロバイダは何も束縛せず、念のため解放だけして抜ける
                runCatching { providerFuture.get() }.getOrNull()?.unbindAll()
                return@addListener
            }
            try {
                val provider = providerFuture.get()
                boundProvider = provider

                val preview =
                    Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val analysis =
                    ImageAnalysis
                        .Builder()
                        // 解析が追いつかないフレームは捨てる。遅延を溜めない
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                analysis.setAnalyzer(
                    analysisExecutor,
                    IsbnBarcodeAnalyzer(barcodeScanner) { isbn ->
                        if (alreadyScanned.compareAndSet(false, true)) {
                            mainExecutor.execute { onScanned(isbn) }
                        }
                    },
                )

                provider.unbindAll()
                camera =
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
            } catch (e: Exception) {
                // カメラを開けない理由は端末とタイミングに依る。契約どおり例外は投げず、
                // 呼び出し側が手入力へ落とせるように通知する。ただし画面を離脱済みなら
                // 呼び出し先は既にないので通知しない
                // 実機でカメラが開けないときに追える手掛かりを残す。読み取り値は書かない
                Log.e(SCANNER_LOG_TAG, "カメラを起動できませんでした", e)
                if (!disposed) {
                    onCameraUnavailable(e)
                }
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            boundProvider?.unbindAll()
            analysisExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    // トーチは束縛が済んでからでないと操作できない
    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    // ボタンの表示は「点けたつもり」ではなく実際のトーチ状態に従わせる。
    // ホームへ抜けて戻ると、CameraX が使用ケースの束縛を解いた時点でライトは消えるが、
    // camera も torchOn も変化しないため、観測しないとボタンだけ「ライトを消す」のまま残り、
    // 点け直すのに2度押しが要る。点灯要求が端末側で通らなかった場合もここで実態へ戻る
    DisposableEffect(camera, lifecycleOwner) {
        val torchState = camera?.cameraInfo?.torchState
        val observer = Observer<Int> { state -> torchOn = state == TorchState.ON }
        torchState?.observe(lifecycleOwner, observer)
        onDispose { torchState?.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 背景はカメラの映像であり明るさが読めない。テーマ色ではなく、
                // 自前の暗幕と白文字で可読性を確保する
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(24.dp),
        ) {
            // 下段を読んでも無反応になるため、なぜ確定しないのかを伝える（憲法 原則VI）
            Text(
                text = "上段のバーコード（978/979 で始まる ISBN）に向けてください。下段の価格コードは読み取りません。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )

            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                Button(
                    onClick = { torchOn = !torchOn },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Text(if (torchOn) "ライトを消す" else "ライトを点ける")
                }
            }
        }
    }
}
