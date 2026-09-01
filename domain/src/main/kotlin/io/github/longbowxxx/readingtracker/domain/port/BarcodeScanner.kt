package io.github.longbowxxx.readingtracker.domain.port

/**
 * バーコードの読み取り。
 *
 * 実装は CameraX + ML Kit の自前のもので、プレビューを維持したまま連続解析する
 * （research.md R-003 / Issue #1）。当初は Google Code Scanner を用いていたが、
 * 書籍バーコード下段を読み捨てて読み取りを続けられず、差し替えた。
 *
 * その差し替えが UI とドメインへ波及しなかったのは、この契約を境界に置いていたためである。
 * カメラの都合は実装側に閉じ、呼び出し側は `suspend fun scan(): ScanResult` の形しか見ない。
 *
 * 実装は例外を投げてはならない。失敗は [ScanResult.Unavailable] として返す。
 */
interface BarcodeScanner {
    suspend fun scan(): ScanResult
}

/** 読み取り結果。 */
sealed interface ScanResult {
    /**
     * 読み取れた。[rawValue] は検証前の生の値であり、ISBN とは限らない。
     * 妥当性の判定は `Isbn.parse` が行う。
     */
    data class Scanned(val rawValue: String) : ScanResult

    /**
     * 利用者が閉じた、または手入力へ切り替えた。
     * **受け取った UI はエラーを出さずに ISBN 手入力へ遷移すること**（FR-003）。
     * 手入力は例外処理ではなく同格の経路である（憲法 原則VI）。
     */
    data object Cancelled : ScanResult

    /** カメラ権限なし、モジュール未配信など。手入力へ落とす。 */
    data class Unavailable(val cause: Throwable) : ScanResult
}
