package io.github.longbowxxx.readingtracker.domain.port

/**
 * バーコードの読み取り。
 *
 * 初期実装は Google Code Scanner を用いるが、暗所での読み取り精度に問題が出た場合に
 * CameraX + ML Kit の自前実装へ差し替える。差し替えが UI とドメインへ波及しないよう、
 * この契約を境界とする（research.md R-003）。
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
