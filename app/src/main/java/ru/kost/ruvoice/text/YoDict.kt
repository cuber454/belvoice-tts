package ru.kost.ruvoice.text

import ru.kost.ruvoice.bin.StressBin

/**
 * Словарь бесспорной «ё» (assets/eyo_safe.txt — safe.txt из github.com/e2yo/eyo-kernel, MIT): слово через «е» →
 * то же с «ё». Формат и правила как в eyo: «Ёжиков(а|ой|у|ы)» — формы, «# …» — комментарий, строчное слово
 * подходит и с заглавной («Ежик» в начале фразы), «Ёжиков» с заглавной — только так, «_киёв» — только строчными
 * (Киев — город). Спорные слова (все/всё, небо/нёбо) в safe.txt не входят. Применяется в Stress.accentorPass
 * до модели: слово из словаря получает «ё» и ударение на ней, модель про него не спрашивают.
 */
class YoDict private constructor(private val lookup: (String) -> String?, val size: Int) {
    /** Из строк eyo (тесты); в приложении — таблица «yo» из stress.bin, те же правила (StressBin.yoEntries). */
    constructor(lines: Sequence<String>) : this(StressBin.yoEntries(lines))
    private constructor(map: Map<String, String>) : this({ map[it] }, map.size)
    constructor(table: StressBin.Table) : this({ StressBin.yoRestore(table, it) }, table.size)

    /** Слово с «ё», если оно в словаре, иначе null. Регистр как в тексте. */
    fun restore(word: String): String? = lookup(word)

    companion object {
        /** Общий на процесс, ставит SileroModels.data(); null — словарь выключен (JVM-тесты без ассета). */
        @Volatile var shared: YoDict? = null
    }
}
