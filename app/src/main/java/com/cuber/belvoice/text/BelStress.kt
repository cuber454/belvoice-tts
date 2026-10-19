package com.cuber.belvoice.text

/**
 * Белорусские ударения поверх акцентора silero-stress (`accentor-bel.pt`, MIT).
 *
 * Почему свой класс, а не [Stress]: русский конвейер ставит ударения в четыре прохода —
 * таблица AOT, омографы по BERT, словарь «ё» eyo и акцентор. Для белорусского первых трёх нет
 * (AOT и eyo русские, омографов в белорусском акценторе не бывает), остаётся акцентор и словарь.
 *
 * Правила поверх акцентора:
 *  - служебные слова и предлоги («ў», «на», «з», «да») не метим: акцентор ставит на них «+»,
 *    и модель тянет слог — для белорусского это лишнее;
 *  - односложные слова не метим: ударение там единственное;
 *  - слова из словаря пользователя не трогаем — словарь главнее акцентора;
 *  - слова, где «+» уже стоит (из замены, словаря или от руки), не трогаем.
 */
class BelStress(private val models: StressModels, private val userDict: Map<String, String> = emptyMap()) {

    private val vowels = "аеёіоуыэюя"

    /** Служебные слова: ударение им не нужно, а «+» тянет слог. */
    private val noMark = setOf(
        "ў", "у", "з", "с", "на", "да", "па", "за", "ад", "аб", "для", "пры", "між", "пад", "над",
        "без", "праз", "пра", "а", "і", "не", "ні", "бы", "жа", "ці", "то", "як", "што", "хто",
        "дзе", "калі", "так", "ну", "вось", "што", "чым", "каб", "але", "або", "усе", "усё")

    private val wordRe = Regex("[а-яёіў]+", RegexOption.IGNORE_CASE)

    /** Ставит «+» перед ударной гласной: `дзякуй` → `дз+якуй`. */
    fun apply(sentence: String): String {
        val words = wordRe.findAll(sentence).map { it.value.lowercase() }
            .filter { w -> '+' !in w && w !in userDict && w !in noMark && w.count { c -> c in vowels } >= 2 }
            .distinct().toList()

        val marked = HashMap<String, String>(words.size * 2)
        if (words.isNotEmpty()) {
            val probs = models.accentor(words).first
            for ((i, w) in words.withIndex()) {
                val p = probs.getOrNull(i) ?: continue
                val vowelsAt = w.indices.filter { w[it] in vowels }
                if (vowelsAt.isEmpty()) continue
                var best = 0
                for (k in vowelsAt.indices) {
                    val pk = if (k < p.size) p[k] else Float.NEGATIVE_INFINITY
                    val pb = if (best < p.size) p[best] else Float.NEGATIVE_INFINITY
                    if (pk > pb) best = k
                }
                val pos = vowelsAt[best]
                marked[w] = w.substring(0, pos) + "+" + w.substring(pos)
            }
        }
        if (marked.isEmpty() && userDict.isEmpty()) return sentence

        val out = StringBuilder(sentence.length + 8)
        var last = 0
        for (m in wordRe.findAll(sentence)) {
            out.appendRange(sentence, last, m.range.first)
            val w = m.value
            val lower = w.lowercase()
            val repl = if ('+' in w) w else (userDict[lower] ?: marked[lower] ?: w)
            out.append(if (w[0].isUpperCase() && repl.isNotEmpty()) repl.replaceFirstChar { it.uppercaseChar() } else repl)
            last = m.range.last + 1
        }
        out.appendRange(sentence, last, sentence.length)
        return out.toString()
    }
}
