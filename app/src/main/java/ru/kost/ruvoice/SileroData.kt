package ru.kost.ruvoice

import org.json.JSONObject
import ru.kost.ruvoice.bin.StressBin
import java.nio.ByteBuffer

/** Словарь из stress.bin (StressBin): по ключу, без разбора в память. Его и используют только так — get, in, isEmpty. */
class Lookup<V>(private val t: StressBin.Table, private val decode: (StressBin.Table, Int) -> V) {
    operator fun get(key: String): V? = t.find(key).let { if (it < 0) null else decode(t, it) }
    operator fun contains(key: String) = t.find(key) >= 0
    fun getValue(key: String): V = get(key) ?: throw NoSuchElementException(key)
    fun isEmpty() = t.size == 0
    val size get() = t.size
}

/** Данные штатной модели и ударений: stress.bin — mmap ассета в приложении, в JVM-тестах собран в памяти из json. */
class SileroData(bin: StressBin.File) {
    /** Из silero_ru.json (тесты): stress.bin в памяти, без словаря «ё». */
    constructor(json: String) : this(StressBin.File(ByteBuffer.wrap(StressBin.write(json, emptySequence()))))

    val sym: Symbols
    val symbolToId: Map<Char, Int> get() = sym.symbolToId
    val alphabet: Set<Char> get() = sym.alphabet
    val speakers: Map<String, Int>
    /** Основа (темп и интонация) новых голосов встроенной модели, как bases в паке. */
    val bases: Map<String, String>
    val exceptions: Lookup<IntArray>
    val homodict: Lookup<List<String>>
    /** Фразы Silero Stress: слово → [(фраза, вариант с «+»)], порядок важен — длинные фразы раньше. */
    val phrases: Lookup<List<Pair<String, String>>>
    /** Грамматические омографы (AOT): форма → {g: род. ед., p: им./вин. мн., l: второй предложный, n: сущ., v: глагол,
     * i: инфинитив несов. вида} с «+». */
    val gram: Lookup<Map<String, String>>
    val bertVocab: Lookup<Int>
    /** Словарь «ё» (eyo_safe.txt) из того же файла; YoDict поверх него (StressBin.yoRestore). */
    val yo: StressBin.Table
    val bertCls: Int
    val bertSep: Int
    val bertPad: Int
    val bertUnk: Int
    val bertHomoStart: Int
    val bertHomoEnd: Int
    val type2id: Map<String, Int>
    val whForms: Set<String>
    val leadingFillers: Set<String>
    val tagRe: Regex

    init {
        val o = JSONObject(bin.head)
        sym = Symbols.fromJson(o)
        speakers = o.getJSONObject("speakers").let { j -> j.keys().asSequence().associateWith { j.getInt(it) } }
        bases = o.optJSONObject("bases")?.let { j -> j.keys().asSequence().associateWith { j.getString(it) } } ?: emptyMap()
        fun strs(t: StressBin.Table, p: Int, n: Int): List<String> { var q = p; return List(n) { t.str(q).also { q = it.second }.first } }
        exceptions = Lookup(bin.table("exceptions")) { t, p -> intArrayOf(t.int(p), t.int(p, 1)) }
        homodict = Lookup(bin.table("homodict")) { t, p -> strs(t, p + 1, t.u8(p)) }
        phrases = Lookup(bin.table("phrases")) { t, p -> strs(t, p + 2, t.u16(p) * 2).chunked(2) { it[0] to it[1] } }
        gram = Lookup(bin.table("gram")) { t, p -> strs(t, p + 1, t.u8(p) * 2).chunked(2) { it[0] to it[1] }.toMap() }
        bertVocab = Lookup(bin.table("bert_vocab")) { t, p -> t.int(p) }
        yo = bin.table("yo")
        val bert = o.getJSONObject("bert")
        bertCls = bert.getInt("cls"); bertSep = bert.getInt("sep"); bertPad = bert.getInt("pad")
        bertUnk = bert.getInt("unk"); bertHomoStart = bert.getInt("homo_start"); bertHomoEnd = bert.getInt("homo_end")
        type2id = o.getJSONObject("type2id").let { j -> j.keys().asSequence().associateWith { j.getInt(it) } }
        whForms = o.getJSONArray("wh_forms").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        leadingFillers = o.getJSONArray("leading_fillers").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        tagRe = o.getJSONArray("tag_patterns").let { a ->
            Regex((0 until a.length()).joinToString("|") { "(?:${a.getString(it)})" }, RegexOption.IGNORE_CASE)
        }
    }

    /** Символы, допустимые во входе модели: без служебных `_~|`. */
    val allowed: String get() = sym.allowed

    fun sequence(accented: String): LongArray = sym.sequence(accented)
}
