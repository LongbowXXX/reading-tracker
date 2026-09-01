package io.github.longbowxxx.readingtracker.scanner

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import io.github.longbowxxx.readingtracker.domain.model.Isbn
import com.google.mlkit.vision.barcode.BarcodeScanner as MlKitBarcodeScanner

/**
 * 読み取り周りの logcat タグ。
 *
 * **記録してよいのは失敗の原因だけ**。読み取った値や ISBN は書かない。
 * 端末内に閉じるデータであり（憲法 原則V）、logcat に出た ISBN は利用者の読書履歴そのものになる。
 */
internal const val SCANNER_LOG_TAG = "ReadingTrackerScan"

/**
 * カメラの各フレームを解析し、採用できる ISBN が読めたときだけ [onIsbn] を呼ぶ。
 *
 * **採用できない値は無言で捨て、解析を続ける**（Issue #1）。書籍バーコード下段の
 * 日本図書コード（192 始まり）を読んでも、エラーを出さず読み取りを止めない。
 * 何が採用できるかの判定は [selectIsbn] が持つ。
 *
 * [onIsbn] の呼び出しスレッドは `analyze` を呼んだカメラの解析スレッドではない。`process` が返す
 * `Task` の `addOnSuccessListener` / `addOnCompleteListener` に Executor を渡していないため、
 * これらは既定でメインスレッド上で実行され、[onIsbn] もそこから呼ばれる。そのため呼び出し側で
 * 改めてメインスレッドへ移す必要は現状ない。ただし、これはリスナーへ Executor を渡していないことに
 * 依存する実装都合であり、将来 Executor 付きのリスナーに変更されると呼び出しスレッドは変わりうる。
 * 呼び出し側がメインスレッドへの移動を自前で行っておく（[ScanScreen] の
 * `mainExecutor.execute { onScanned(isbn) }` のように）のは、その変化に対して壊れないための安全策である。
 * 複数フレームで成立しうるため、**[onIsbn] は複数回呼ばれうる**。1度だけ扱う責任は呼び出し側にある。
 *
 * @param scanner ML Kit のバーコード検出器。生成と破棄は呼び出し側が持つ
 */
class IsbnBarcodeAnalyzer(private val scanner: MlKitBarcodeScanner, private val onIsbn: (Isbn) -> Unit) : ImageAnalysis.Analyzer {
    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // 解析を Task へ渡せたか。渡せた場合の close は addOnCompleteListener が持つ
        var handedOff = false
        try {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner
                .process(image)
                .addOnSuccessListener { barcodes ->
                    selectIsbn(barcodes.map { it.rawValue })?.let(onIsbn)
                }.addOnCompleteListener {
                    // 次のフレームを受け取るために必ず閉じる。閉じ忘れると解析が止まる
                    imageProxy.close()
                }
            handedOff = true
        } catch (e: Exception) {
            // fromMediaImage は未対応フォーマットで、process は検出器が閉じた後で、
            // それぞれ同期的に例外を投げうる。ここで閉じないと STRATEGY_KEEP_ONLY_LATEST では
            // 次のフレームが届かず、プレビューは映るのに二度と読み取れない状態になる。
            // 原因が分からないと実機で追えないため、値ではなく失敗の理由だけを残す
            Log.w(SCANNER_LOG_TAG, "フレームの解析を開始できませんでした", e)
        } finally {
            if (!handedOff) {
                imageProxy.close()
            }
        }
    }
}
