package com.cuber.belvoice.text

/**
 * Латиница → белорусские буквы.
 *
 * Зачем: в таблице символов модели латиницы нет вообще (в алфавите только кириллица и
 * служебный «h»), поэтому английское слово раньше просто выпадало из фразы — «FilmBell»
 * не читалось. Теперь слово переводится в белорусскую запись и читается голосом.
 *
 * Порядок: сначала словарь известных слов (iphone → айфон), потом аббревиатура капсом по
 * буквам (USB → у-эс-бэ), потом английские диграфы (sh, ch, oo…), потом по буквам.
 * Это не фонетический разбор английского, а приближение — как в русском RuVoice.
 */
object Latin {

    /** Известные слова: так их и зовут по-белорусски. */
    private val WORDS = mapOf(
        "iphone" to "айф+он",
        "ipad" to "айп+ад",
        "android" to "андр+оід",
        "google" to "г+угл",
        "youtube" to "ют+уб",
        "telegram" to "тэлегр+ам",
        "whatsapp" to "вац+ап",
        "viber" to "в+айбер",
        "facebook" to "фэйсб+ук",
        "instagram" to "інстагр+ам",
        "tiktok" to "цікт+ок",
        "twitter" to "твіт+эр",
        "windows" to "вінд+оўз",
        "linux" to "л+інукс",
        "excel" to "экс+эль",
        "word" to "в+орд",
        "wifi" to "вайф+ай",
        "bluetooth" to "блут+уз",
        "sms" to "эс-эм-эс",
        "pdf" to "пэ-дэ-эф",
        "gps" to "джы-пі-эс",
        "tv" to "тэ-вэ",
        "ok" to "ок+ей",
        "film" to "ф+ільм",
        "filmbell" to "фільмб+ел",
        "netflix" to "нэтфл+ікс",
        "amazon" to "амаз+он",
        "microsoft" to "майкрас+офт",
        "email" to "ім+эйл",
        "online" to "анл+айн",
        "internet" to "інтэрн+эт",
        "site" to "с+айт",
        "web" to "в+эб",
        "podcast" to "п+одкаст",
        "chrome" to "хр+ом",
        "chromium" to "хр+оміум",
        "photoshop" to "фоташ+оп",
        "zoom" to "з+ум",
        "spotify" to "спат+ыфай",
        "github" to "гітх+аб",
        "chatgpt" to "чэтджып+іці",
        "ai" to "+эй-ай",
        "blog" to "бл+ог",
        "chat" to "ч+ат",
        "link" to "л+інк",
        "live" to "л+айв",
        "team" to "ц+ім",
        "video" to "в+ідэа",
        "audio" to "+аўдыё",
        "photo" to "ф+ота",
        "media" to "м+едыя",
        "forum" to "ф+орум",
        "login" to "л+огін",
        "password" to "пар+оль",
        "download" to "даўнл+оўд",
    )
    /** Диграфы: длинные раньше коротких. */
    private val DIGRAPHS = listOf(
        "tch" to "ч", "sch" to "ш", "igh" to "ай", "tion" to "шн", "sion" to "жн",
        "ph" to "ф", "th" to "т", "sh" to "ш", "chr" to "кр", "chl" to "кл", "chn" to "кн",
        "ch" to "ч", "ck" to "к", "qu" to "кв",
        "oo" to "у", "ee" to "і", "ea" to "і", "ai" to "эй", "ay" to "эй", "ey" to "эй",
        "oi" to "ой", "oy" to "ой", "ou" to "аў", "ow" to "оў", "oa" to "оў", "ie" to "і",
        "wh" to "ў", "ng" to "нг", "kn" to "н", "wr" to "р",
    )

    private val LETTERS = mapOf(
        'a' to "а", 'b' to "б", 'c' to "к", 'd' to "д", 'e' to "е", 'f' to "ф", 'g' to "г",
        'h' to "х", 'i' to "і", 'j' to "дж", 'k' to "к", 'l' to "л", 'm' to "м", 'n' to "н",
        'o' to "о", 'p' to "п", 'q' to "к", 'r' to "р", 's' to "с", 't' to "т", 'u' to "у",
        'v' to "в", 'w' to "ў", 'x' to "кс", 'y' to "ы", 'z' to "з",
    )

    /** Имена латинских букв — для аббревиатур капсом. */
    private val NAMES = mapOf(
        'a' to "а", 'b' to "бэ", 'c' to "цэ", 'd' to "дэ", 'e' to "е", 'f' to "эф", 'g' to "гэ",
        'h' to "аш", 'i' to "і", 'j' to "джэй", 'k' to "ка", 'l' to "эл", 'm' to "эм", 'n' to "эн",
        'o' to "о", 'p' to "пэ", 'q' to "кю", 'r' to "эр", 's' to "эс", 't' to "тэ", 'u' to "у",
        'v' to "вэ", 'w' to "дабл-ю", 'x' to "экс", 'y' to "уай", 'z' to "зэт",
    )

    private const val VOWELS = "аеёіоуыэюя"
    private const val EN_VOWELS = "aeiou"

    /** Одно латинское слово в белорусской записи. */
    fun word(raw: String): String {
        val low = raw.lowercase()
        WORDS[low]?.let { return it }
        if (raw.length in 2..5 && raw.all { it.isUpperCase() || !it.isLetter() } && raw.all { !it.isLetter() || it.isUpperCase() }) {
            return low.mapNotNull { NAMES[it] ?: LETTERS[it] }.joinToString("-")
        }
        val out = StringBuilder()
        var i = 0
        while (i < low.length) {
            var matched = false
            for ((d, r) in DIGRAPHS) {
                if (low.startsWith(d, i)) {
                    if ((d == "kn" || d == "wr") && i > 0) continue
                    out.append(r); i += d.length; matched = true; break
                }
            }
            if (matched) continue
            val c = low[i]
            when (c) {
                'c' -> out.append(if (i + 1 < low.length && low[i + 1] in "eiy") "с" else "к")
                'y' -> out.append(if (i == 0 || low[i - 1] in EN_VOWELS) "й" else "ы")
                else -> LETTERS[c]?.let { out.append(it) }
            }
            i++
        }
        var res = out.toString()
        // конечная «y» после согласной звучит [і]: galaxy → галаксі
        if (low.length > 1 && low.endsWith("y") && low[low.length - 2] !in EN_VOWELS && res.endsWith("ы")) {
            res = res.dropLast(1) + "і"
        }
        // двойные согласные схлопываем
        val clean = StringBuilder(res.length)
        for (ch in res) {
            if (clean.isNotEmpty() && clean[clean.length - 1] == ch && ch !in VOWELS) continue
            clean.append(ch)
        }
        return clean.toString()
    }

    /** Все латинские вставки в тексте → белорусские буквы; остальное не трогаем. */
    fun translate(text: String): String {
        val out = StringBuilder(text.length + 8)
        var i = 0
        while (i < text.length) {
            val isLatin = text[i].code < 128 && text[i].isLetter()
            if (!isLatin) { out.append(text[i]); i++; continue }
            var j = i
            while (j < text.length && text[j].code < 128 && text[j].isLetter()) j++
            out.append(word(text.substring(i, j)))
            i = j
        }
        return out.toString()
    }
}
