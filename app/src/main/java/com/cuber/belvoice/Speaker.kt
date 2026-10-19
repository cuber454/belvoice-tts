package com.cuber.belvoice

/** Голос по имени из Prefs или от читалки: штатный («xenia») или из пака («cis_ru/ru_alexandr»).
 * В сервисе класс android.speech.tts.Voice, поэтому не Voice. */
class Speaker(val name: String, val pack: Pack?, val id: Int, val sym: Symbols, val types: Boolean,
              private val builtinBases: Map<String, String> = emptyMap()) {
    /** Штатный голос, от которого темп и интонация: у новых голосов — из bases пака (у встроенной модели — из
     * silero_ru.json), иначе сам голос. */
    val base: String get() = name.substringAfter('/').let { (pack?.bases ?: builtinBases)[it] ?: it }
    companion object {
        const val DEFAULT = "bel_larisa"
        /** Пак штатной модели для сборки lite: голые имена («xenia» из старых prefs) ищем в нём. */
        const val LITE_PACK = "cis_be"
        /** Есть ли модель в APK (сборка full). Тесты подменяют. */
        var builtin = BuildConfig.BUILTIN_MODEL
        private fun packName(pack: Pack, speaker: String) = "${pack.id}/$speaker"
        private fun of(pack: Pack, speaker: String): Speaker? = pack.speakers[speaker]?.let { Speaker(packName(pack, speaker), pack, it, pack.sym, pack.types) }

        fun resolve(name: String?, d: SileroData, packs: List<Pack>): Speaker? {
            if (name == null) return null
            val i = name.indexOf('/')
            if (i < 0) {
                if (builtin) return d.speakers[name]?.let { Speaker(name, null, it, d.sym, d.types, d.bases) }
                return packs.firstOrNull { it.id == LITE_PACK }?.let { of(it, name) }
            }
            val pack = packs.firstOrNull { it.id == name.substring(0, i) } ?: return null
            return of(pack, name.substring(i + 1))
        }

        /** Голос по умолчанию: «bel_larisa» (штатная модель сборки), иначе первый голос первого пака; null — голосов нет. */
        fun default(d: SileroData, packs: List<Pack>): Speaker? =
            resolve(DEFAULT, d, packs) ?: names(d, packs).firstOrNull()?.let { resolve(it, d, packs) }

        /** Есть ли хоть один голос: встроенная модель или пак с голосами. Без разбора silero_ru.json. */
        fun hasVoices(packs: List<Pack>) = builtin || packs.any { it.speakers.isNotEmpty() }

        /** Есть ли голос — то же, что resolve(...) != null, но по одним именам штатных голосов (SileroModels.speakers):
         * без разбора всего silero_ru.json. */
        fun exists(name: String?, builtinNames: Set<String>, packs: List<Pack>): Boolean {
            if (name == null) return false
            val i = name.indexOf('/')
            if (i < 0) return if (builtin) name in builtinNames else packs.firstOrNull { it.id == LITE_PACK }?.speakers?.containsKey(name) == true
            return packs.firstOrNull { it.id == name.substring(0, i) }?.speakers?.containsKey(name.substring(i + 1)) == true
        }

        /** Все имена: штатные по алфавиту (в full), затем по пакам. */
        fun names(d: SileroData, packs: List<Pack>): List<String> = names(d.speakers.keys, packs)
        fun names(builtinNames: Set<String>, packs: List<Pack>): List<String> =
            (if (builtin) builtinNames.sorted() else emptyList()) + packs.flatMap { p -> p.speakers.keys.sorted().map { packName(p, it) } }

        /** Голоса того же движка, что [main] — для прямой речи: в памяти одна тройка моделей,
         * перегружать 90 МБ на каждую реплику нельзя. */
        fun sameEngine(main: Speaker, d: SileroData, packs: List<Pack>): List<String> =
            main.pack?.let { p -> p.speakers.keys.sorted().map { packName(p, it) } } ?: d.speakers.keys.sorted()

        /** Имя для TTS API читалок: «xenia-ru», «marat-ru-cis». Без «/» и «_» (AlReaderX такие не опознаёт),
         * «ru» один раз: голос, язык, пак; в lite «ru/xenia» — «xenia-ru», как в full. */
        fun ttsName(name: String): String {
            val (pack, speaker) = name.split('/').let { if (it.size == 2) it else listOf("", it[0]) }
            fun parts(s: String) = s.split('_').filter { it.isNotEmpty() && it != "ru" && it != "bel" }
            return (parts(speaker) + "be" + parts(pack)).joinToString("-")
        }
        /** Обратно из [ttsName]; имя в старой форме «ru-ru-cis_ru/ru_marat» (до 0.15) — тоже. */
        fun fromTtsName(tts: String?, d: SileroData, packs: List<Pack>): String? = fromTtsName(tts, names(d, packs))
        fun fromTtsName(tts: String?, names: List<String>): String? =
            tts?.let { t -> names.firstOrNull { ttsName(it) == t } ?: t.removePrefix("be-be-") }

        /** Подпись в списке: «ru_alexandr (cis_ru)», штатные как есть. */
        fun label(name: String): String = name.indexOf('/').let { i -> if (i < 0) name else "${name.substring(i + 1)} (${name.substring(0, i)})" }
    }
}
