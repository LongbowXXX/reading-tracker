package io.github.longbowxxx.readingtracker.scanner

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.longbowxxx.readingtracker.domain.port.BarcodeScanner
import io.github.longbowxxx.readingtracker.domain.port.ScanResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Google Code Scanner による読み取り（research.md R-003、初期実装）。
 *
 * スキャン用 UI ごと提供されるため実装量が小さく、動作検証を早く始められる。
 * ただし**トーチ制御ができない**ため、暗所での読み取り精度に問題が出た場合は
 * CameraX + ML Kit の自前実装へ差し替える。差し替え先は [BarcodeScanner] を実装するだけでよい。
 *
 * Google Play 開発者サービス経由でモジュールが配信されるため、**初回利用時に
 * ダウンロードが発生しうる**。オフラインの個室で初回スキャンを行うと失敗するので、
 * その場合は [ScanResult.Unavailable] を返し、上位が手入力へ落とす（FR-003）。
 *
 * 読み取った値の妥当性判定はここでは行わない。書籍バーコード下段の日本図書コード
 * （192 始まり）は `Isbn.parse` が接頭辞で弾く。
 *
 * スキャン UI は Activity を起動するため、**Activity のコンテキストを渡すこと**。
 */
class GoogleCodeScannerBarcodeScanner(private val context: Context) : BarcodeScanner {
    override suspend fun scan(): ScanResult = suspendCancellableCoroutine { continuation ->
        try {
            val options =
                GmsBarcodeScannerOptions
                    .Builder()
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13)
                    .enableAutoZoom()
                    .build()

            GmsBarcodeScanning
                .getClient(context, options)
                .startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue
                    if (raw.isNullOrBlank()) {
                        continuation.resume(ScanResult.Unavailable(IOException("バーコードの値を読み取れませんでした")))
                    } else {
                        continuation.resume(ScanResult.Scanned(raw))
                    }
                }.addOnCanceledListener {
                    // 利用者が閉じた。エラーにせず手入力へ遷移させる（FR-003）
                    continuation.resume(ScanResult.Cancelled)
                }.addOnFailureListener { error ->
                    continuation.resume(ScanResult.Unavailable(error))
                }
        } catch (e: Exception) {
            continuation.resume(ScanResult.Unavailable(e))
        }
    }
}
