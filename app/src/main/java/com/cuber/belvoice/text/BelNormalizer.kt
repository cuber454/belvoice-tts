package com.cuber.belvoice.text

/**
 * Белорусская подготовка текста перед моделью.
 *
 * Зачем свой, а не [Normalizer]: русский нормализатор раскрывает русские сокращения, склоняет
 * по таблицам AOT и переводит латиницу в русские буквы — для белорусского это порча (и «ъ»,
 * которого в модели нет). Здесь только то, без чего модель не читает:
 *  - числа словами (в таблице символов модели цифр нет — они выпадали из речи);
 *  - «!» → «.» (восклицательный знак стоит в первых трёх служебных символах, Silero их вырезает,
 *    а токен «!» при обучении не использовался; интонацию восклицания делаем питчем отдельно);
 *  - апостроф убираем (белорусское «аб'ява» — апострофа в таблице символов нет);
 *  - скобки и кавычки → запятая (вставка читается с паузой, как в русском RuVoice);
 *  - всё, чего в таблице символов нет, выкидываем.
 */
object BelNormalizer {

    private val VOWELS = "аеёіоуыэюя"

    private val UNITS = arrayOf(
        "нуль", "адзін", "два", "тры", "чатыры", "пяць", "шэсць", "сем", "восем", "дзевяць",
        "дзесяць", "адзінаццаць", "дванаццаць", "трынаццаць", "чатырнаццаць", "пятнаццаць",
        "шаснаццаць", "сямнаццаць", "васямнаццаць", "дзевятнаццаць")
    private val TENS = arrayOf(
        "", "", "дваццаць", "трыццаць", "сорак", "пяцьдзясят", "шэсцьдзясят", "семдзесят",
        "восемдзесят", "дзевяноста")
    private val HUNDREDS = arrayOf(
        "", "сто", "дзвесце", "трыста", "чатырыста", "пяцьсот", "шэсцьсот", "семсот",
        "восемсот", "дзевяцьсот")
    /** Тысячи женского рода: «адна тысяча», «дзве тысячы». */
    private val THOUSAND_F = arrayOf(
        "нуль", "адна", "дзве", "тры", "чатыры", "пяць", "шэсць", "сем", "восем", "дзевяць",
        "дзесяць", "адзінаццаць", "дванаццаць", "трынаццаць", "чатырнаццаць", "пятнаццаць",
        "шаснаццаць", "сямнаццаць", "васямнаццаць", "дзевятнаццаць")

    /** Порядковые (мужской род): именительный, родительный, предложный. */
    private val ORD_NOM = arrayOf(
        "нулявы", "першы", "другі", "трэці", "чацвёрты", "пяты", "шосты", "сёмы", "восьмы",
        "дзявяты", "дзясяты", "адзінаццаты", "дванаццаты", "трынаццаты", "чатырнаццаты",
        "пятнаццаты", "шаснаццаты", "сямнаццаты", "васямнаццаты", "дзевятнаццаты")
    private val ORD_GEN = arrayOf(
        "нулявога", "першага", "другога", "трэцяга", "чацвёртага", "пятага", "шостага", "сёмага",
        "восьмага", "дзявятага", "дзясятага", "адзінаццатага", "дванаццатага", "трынаццатага",
        "чатырнаццатага", "пятнаццатага", "шаснаццатага", "сямнаццатага", "васямнаццатага",
        "дзевятнаццатага")
    private val ORD_LOC = arrayOf(
        "нулявым", "першым", "другім", "трэцім", "чацвёртым", "пятым", "шостым", "сёмым",
        "восьмым", "дзявятым", "дзясятым", "адзінаццатым", "дванаццатым", "трынаццатым",
        "чатырнаццатым", "пятнаццатым", "шаснаццатым", "сямнаццатым", "васямнаццатым",
        "дзевятнаццатым")

    private val TENS_ORD = mapOf(
        20 to Triple("дваццаты", "дваццатага", "дваццатым"),
        30 to Triple("трыццаты", "трыццатага", "трыццатым"),
        40 to Triple("саракавы", "саракавога", "саракавым"),
        50 to Triple("пяцідзясяты", "пяцідзясятага", "пяцідзясятым"),
        60 to Triple("шасцідзясяты", "шасцідзясятага", "шасцідзясятым"),
        70 to Triple("сямідзясяты", "сямідзясятага", "сямідзясятым"),
        80 to Triple("васьмідзясяты", "васьмідзясятага", "васьмідзясятым"),
        90 to Triple("дзевяносты", "дзевяностага", "дзевяностым"))

    private val HUNDREDS_ORD = mapOf(
        100 to Triple("соты", "сотага", "сотым"),
        200 to Triple("двухсоты", "двухсотага", "двухсотым"),
        300 to Triple("трохсоты", "трохсотага", "трохсотым"),
        400 to Triple("чатырохсоты", "чатырохсотага", "чатырохсотым"),
        500 to Triple("пяцісоты", "пяцісотага", "пяцісотым"),
        600 to Triple("шасцісоты", "шасцісотага", "шасцісотым"),
        700 to Triple("сямісоты", "сямісотага", "сямісотым"),
        800 to Triple("васьмісоты", "васьмісотага", "васьмісотым"),
        900 to Triple("дзевяцісоты", "дзевяцісотага", "дзевяцісотым"))

    /** Падеж последнего слова у порядкового числительного в году. */
    enum class Form { NOM, GEN, LOC }

    /** 0..999 в мужском роде. */
    private fun under1000(n: Int, fem: Boolean = false): String {
        val sb = StringBuilder()
        val h = n / 100
        if (h > 0) sb.append(HUNDREDS[h])
        val r = n % 100
        if (r > 0) {
            if (sb.isNotEmpty()) sb.append(' ')
            if (r < 20) sb.append(if (fem) THOUSAND_F[r] else UNITS[r])
            else {
                sb.append(TENS[r / 10])
                val u = r % 10
                if (u > 0) { sb.append(' '); sb.append(if (fem) THOUSAND_F[u] else UNITS[u]) }
            }
        }
        return sb.toString()
    }

    /** Количественное: 1234 → «адна тысяча дзвесце трыццаць чатыры». */
    fun cardinal(n: Long): String {
        if (n < 0) return "мінус " + cardinal(-n)
        if (n == 0L) return UNITS[0]
        val parts = ArrayList<String>()
        val millions = (n / 1_000_000).toInt()
        val thousands = ((n / 1000) % 1000).toInt()
        val rest = (n % 1000).toInt()
        if (millions > 0) parts.add(under1000(millions) + " мільён")
        if (thousands > 0) {
            val t = under1000(thousands, fem = true)
            val word = when {
                thousands % 10 == 1 && thousands % 100 != 11 -> "тысяча"
                thousands % 10 in 2..4 && thousands % 100 !in 12..14 -> "тысячы"
                else -> "тысяч"
            }
            parts.add("$t $word")
        }
        if (rest > 0) parts.add(under1000(rest))
        return parts.joinToString(" ")
    }

    /** Порядковое по последнему компоненту: 2026 → «дзве тысячы дваццаць шосты». */
    fun ordinal(n: Long, form: Form = Form.NOM): String {
        if (n == 0L) return pick(ORD_NOM[0], ORD_GEN[0], ORD_LOC[0], form)
        val head = StringBuilder()
        fun add(s: String) { if (s.isNotEmpty()) { if (head.isNotEmpty()) head.append(' '); head.append(s) } }
        val millions = (n / 1_000_000).toInt()
        val thousands = ((n / 1000) % 1000).toInt()
        val rest = (n % 1000).toInt()
        if (millions > 0) add(under1000(millions) + " мільён")
        if (thousands > 0) {
            val t = under1000(thousands, fem = true)
            val word = when {
                thousands % 10 == 1 && thousands % 100 != 11 -> "тысяча"
                thousands % 10 in 2..4 && thousands % 100 !in 12..14 -> "тысячы"
                else -> "тысяч"
            }
            add("$t $word")
        }
        if (rest == 0) {
            return (head.toString() + " " + pick("тысячны", "тысячнага", "тысячным", form)).trim()
        }
        val h = rest / 100
        val r = rest % 100
        if (h > 0) add(HUNDREDS[h])
        val tail = buildString {
            when {
                r == 0 -> append(pickOrd(h * 100, form))
                r < 20 -> append(pick(ORD_NOM[r], ORD_GEN[r], ORD_LOC[r], form))
                else -> {
                    // порядковым становится только последнее слово: 26 → «дваццаць шосты»,
                    // а не «дваццаты шосты»; десятки целиком (20, 30…) берём из ORD-таблицы
                    val u = r % 10
                    if (u == 0) {
                        val t = TENS_ORD[r]!!
                        append(pick(t.first, t.second, t.third, form))
                    } else {
                        append(TENS[r / 10])
                        append(' ')
                        append(pick(ORD_NOM[u], ORD_GEN[u], ORD_LOC[u], form))
                    }
                }
            }
        }
        // у составного числительного порядковым становится только последнее слово
        return (head.toString() + " " + tail).trim()
    }

    private fun pickOrd(v: Int, form: Form): String =
        HUNDREDS_ORD[v]?.let { pick(it.first, it.second, it.third, form) } ?: ""

    private fun pick(nom: String, gen: String, loc: String, form: Form) = when (form) {
        Form.NOM -> nom
        Form.GEN -> gen
        Form.LOC -> loc
    }

    private val YEAR_RE = Regex("(\\d{3,4})\\s*(год|года|годзе|гадах|гадоў)(?![а-яёіў])", RegexOption.IGNORE_CASE)
    private val DIGIT_GROUP_RE = Regex("\\d[\\d\\s\\u00A0\\u202F]*\\d|\\d")

    /** Число словами: 0–999 999 999; с годом — порядковым и в нужном падеже. */
    fun numberWords(text: String): String {
        var s = YEAR_RE.replace(text) { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return@replace m.value
            val word = m.groupValues[2].lowercase()
            val form = when (word) {
                "годзе" -> Form.LOC
                "года", "гадоў" -> Form.GEN
                else -> Form.NOM
            }
            ordinal(n, form) + " " + word
        }
        s = DIGIT_GROUP_RE.replace(s) { m ->
            val raw = m.value.replace(Regex("[\\s\\u00A0\\u202F]"), "")
            raw.toLongOrNull()?.let { cardinal(it) } ?: m.value
        }
        return s
    }

    /** Итоговая подготовка: латиница, числа, знаки, фильтр по таблице символов модели. */
    fun prepare(text: String, allowed: String): String {
        // латиницы в таблице символов модели нет вовсе — «FilmBell» раньше просто выпадало
        var s = Latin.translate(text).lowercase()
        s = s.replace(Regex("[’'`´ʼ]"), "")           // апостроф в таблице символов отсутствует
        // даты и время — до единиц: у них внутри цифры со знаками (12.05.2026, 21:30)
        s = BelNumbers.dates(s)
        s = BelNumbers.times(s)
        s = BelNumbers.abbreviations(s)               // «1999 г.» → «1999 год»
        s = BelNumbers.units(s)                       // «3 км» → «тры кіламетры»
        s = BelNumbers.standalone(s)                  // «руб.» без числа
        s = numberWords(s)
        s = s.replace('!', '.')                        // «!» модель не получает (служебный символ)
        s = s.replace(Regex("[«»„“”\"()\\[\\]{}<>]"), ", ")   // вставка — пауза, как в русском RuVoice
        s = s.replace('—', '-')
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c in allowed -> sb.append(c)
                c == '\n' || c == '\t' || c.isWhitespace() -> sb.append(' ')
                else -> sb.append(' ')
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}
