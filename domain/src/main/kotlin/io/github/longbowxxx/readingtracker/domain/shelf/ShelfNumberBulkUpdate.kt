package io.github.longbowxxx.readingtracker.domain.shelf

import io.github.longbowxxx.readingtracker.domain.model.PlacementSnapshot
import io.github.longbowxxx.readingtracker.domain.model.ShelfNumber
import java.time.Instant

/**
 * 作品単位で棚番号を一括適用する。
 *
 * **UI からは呼ばないこと。** A-9（作品単位の一括更新）は今回のスコープ外である。
 * この関数は、憲法 原則III が必須とする「作品単位の一括更新が、他店舗の記録に
 * 影響しないこと」のテストを成立させるためだけに存在する
 * （plan.md の Complexity Tracking を参照）。
 *
 * **事前条件**: [placements] は単一の店舗・単一の作品に属するレコードだけで構成されること。
 * 入力に含まれるレコードのみを更新して返し、入力に無いレコードを生成も参照もしない。
 * 店舗 ID を受け取らないため、他店舗のレコードへ到達しえない。
 */
fun applyShelfNumberToWork(newShelfNumber: ShelfNumber, placements: List<PlacementSnapshot>, updatedAt: Instant): List<PlacementSnapshot> =
    placements.map { it.copy(shelfNumber = newShelfNumber, updatedAt = updatedAt) }
