package io.github.longbowxxx.readingtracker.scanner

import io.github.longbowxxx.readingtracker.domain.model.Isbn

/**
 * 1フレームから読み取れた生値のうち、ISBN として採用できるものを選ぶ。
 *
 * 書籍バーコードは上段が ISBN、下段が日本図書コード（192 始まりの分類・価格コード）である。
 * 下段は EAN-13 として成立しチェックディジットも合うため、接頭辞で弾かなければ価格コードを
 * ISBN として取り込んでしまう（contracts/barcode-scanner.md）。
 *
 * 判定は [Isbn.parse] に委ねる。接頭辞に加えてチェックディジットも検証されるため、
 * 下段の読み捨てと誤読の排除が同時に効く。判定条件をここで二重に定義しない。
 *
 * @return 採用できる ISBN。1つも無ければ null。
 *   **null は失敗ではなく「このフレームでは確定しない」であり、呼び出し側は読み取りを継続する**（Issue #1）
 */
internal fun selectIsbn(rawValues: List<String?>): Isbn? = rawValues
    .asSequence()
    .filterNotNull()
    .mapNotNull { Isbn.parse(it).getOrNull() }
    .firstOrNull()
