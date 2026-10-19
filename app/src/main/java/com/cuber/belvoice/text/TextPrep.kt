package com.cuber.belvoice.text

import com.cuber.belvoice.SileroData

/**
 * Выбор текстового слоя по языку сборки.
 *
 * Русский [Normalizer] раскрывает сокращения, склоняет числа по таблицам AOT и переводит латиницу
 * в русские буквы — для белорусского текста это порча, поэтому у белорусской модели свой
 * [BelNormalizer]: числа словами, «!» → «.», апостроф убираем, остальное — фильтр по таблице символов.
 */
object TextPrep {
    fun prepare(text: String, d: SileroData, allowed: String, rules: Rules): String =
        if (d.lang == "be") BelNormalizer.prepare(text, allowed) else Normalizer.prepare(text, allowed, rules)
}
