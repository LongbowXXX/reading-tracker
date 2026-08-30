package io.github.longbowxxx.readingtracker.domain.shelf

import io.github.longbowxxx.readingtracker.domain.model.PlacementSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber

/**
 * 新たな巻を記録する際に初期値として提示する棚番号を決める（FR-015, FR-016）。
 *
 * 決定順序は次のとおり。
 * 1. 対象巻より小さい巻番号を持つ記録済みの巻のうち、巻番号が最大のもの
 * 2. 1 に該当が無ければ、更新日時が最も新しいレコード
 * 3. どちらも無ければ null（初期値なし）
 *
 * 巻番号が不明なレコードは 1 の対象外とし、2 のフォールバックでのみ継承元になりうる。
 * 継承元の棚番号が未入力なら、戻り値も null（未入力が継承される）。
 *
 * **事前条件**: [placements] は単一の店舗・単一の作品に属するレコードだけで構成されること。
 * 絞り込みは呼び出し側の責務である。この関数は店舗 ID を受け取らないため、
 * 他店舗のレコードへ到達する手段を持たない（FR-014 の構造的な担保）。
 */
fun resolveInheritedShelfNumber(targetVolumeNumber: Int?, placements: List<PlacementSnapshot>): ShelfNumber? {
    if (placements.isEmpty()) return null

    if (targetVolumeNumber != null) {
        val previousByNumber =
            placements
                .filter { it.volumeNumber != null && it.volumeNumber < targetVolumeNumber }
                .maxByOrNull { checkNotNull(it.volumeNumber) }
        if (previousByNumber != null) return previousByNumber.shelfNumber
    }

    return placements.maxByOrNull { it.updatedAt }?.shelfNumber
}
