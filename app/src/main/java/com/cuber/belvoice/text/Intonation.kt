package com.cuber.belvoice.text

/**
 * Интонация вопроса и восклицания для белорусского голоса.
 *
 * Зачем: у модели CIS (наши `bel_*`) нет входа «тип предложения» — Silero такой вход есть только
 * у русской v5_5_ru (13 аргументов forward против 11). Поэтому вопрос звучал как утверждение,
 * а «!» до модели вообще не доходил (он в первых трёх служебных символах и вырезается).
 *
 * Как: высота задаётся моделью ПО КАЖДОМУ СИМВОЛУ (массив pitches в synthesize), поэтому контур
 * рисуется прямо здесь — подъём к концу фразы для вопроса, повышенный тон со спадом для
 * восклицания. Ничего не переобучается, работает на любом голосе этой модели.
 *
 * Сила — из настройки «Сила логического ударения» (1–3): отдельной ручки не заводим.
 * Очень короткие фразы («Так?», «Што?») не трогаем — подъём на двух слогах звучит визгливо.
 */
object Intonation {

    /** Предел силы: 1 — едва заметно, 5 — нарочито. Настройка «Сила выделения» доходит до 5. */
    const val MAX_LEVEL = 5

    /** Подъём тона к концу фразы по силе: доля от единицы. */
    private val RISE = floatArrayOf(0f, 0.16f, 0.28f, 0.40f, 0.52f, 0.64f)

    /** Восклицание: насколько выше вся фраза и насколько падает самый конец. */
    private val EXCL_BASE = floatArrayOf(0f, 1.05f, 1.11f, 1.17f, 1.23f, 1.29f)
    private val EXCL_DROP = floatArrayOf(0f, 0.10f, 0.20f, 0.30f, 0.40f, 0.50f)

    /** Короче этого числа символов контур не применяем. */
    private const val MIN_CHARS = 8

    /** Общие вопросы (да/нет), альтернативные и хвостики — подъём в конце. */
    private val RISING = setOf("general_q", "alternative_q", "tag_q")

    /**
     * Домножает [pitches] на контур. Порядок обратный записи: индекс 0 — служебный sos,
     * последний — eos, поэтому доля считается по позиции в массиве.
     */
    fun apply(type: String, strength: Int, pitches: FloatArray) {
        val n = pitches.size
        if (n < MIN_CHARS) return
        val level = strength.coerceIn(1, MAX_LEVEL)
        when {
            type in RISING -> rise(pitches, level)
            type == "exclam" -> exclaim(pitches, level)
            else -> return                      // повествование не трогаем
        }
    }

    /** Вопрос: плавный подъём на последней трети фразы. */
    private fun rise(pitches: FloatArray, level: Int) {
        val n = pitches.size
        val amp = RISE[level]
        val tail = maxOf(3, (n * 0.35f).toInt())
        val cut = n - tail
        for (i in cut until n) {
            val k = (i - cut + 1f) / tail          // 0…1 к концу
            pitches[i] *= 1f + amp * k * k          // ближе к концу — круче
        }
    }

    /** Восклицание: весь кусок выше, в самом конце — спад. */
    private fun exclaim(pitches: FloatArray, level: Int) {
        val n = pitches.size
        val base = EXCL_BASE[level]
        val drop = EXCL_DROP[level]
        for (i in 0 until n) pitches[i] *= base
        val last = maxOf(3, (n * 0.12f).toInt())
        for (i in n - last until n) {
            val k = (i - (n - last) + 1f) / last
            pitches[i] *= 1f - drop * k
        }
    }
}
