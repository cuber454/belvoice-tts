package ru.kost.ruvoice.bin

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * silero_ru.json (4,6 МБ) и eyo_safe.txt одним файлом stress.bin: большие словари — отсортированные таблицы,
 * mmap и бинарный поиск, мелочь — остаток json. Разбор json и словаря «ё» при запуске процесса занимал на
 * Galaxy A32 5–6 с, и первая фраза экранного чтеца ждала его.
 *
 * Файл собирает Gradle (buildSrc компилирует этот же файл — поэтому тут только org.json и java.nio), JVM-тесты —
 * в памяти из того же json. Числа little-endian. Файл: магия, длина и utf-8 остатка json, затем таблицы
 * [TABLES] по порядку, каждая с кратного 4 смещения: n, смещения n+1 записей от начала данных, данные. Запись:
 * u16 длина ключа, ключ utf-8, значение. Ключи отсортированы по байтам utf-8 без знака.
 */
object StressBin {
    const val MAGIC = 0x52565342 // RVSB
    /** Порядок таблиц в файле. */
    val TABLES = listOf("exceptions", "homodict", "bert_vocab", "phrases", "gram", "yo")

    // ---- запись ----

    fun write(json: String, eyo: Sequence<String>): ByteArray {
        val o = JSONObject(json)
        val tables = listOf<Map<String, (DataOutputStream) -> Unit>>(
            o.getJSONObject("exceptions").let { j -> keys(j).associateWith { k -> { out: DataOutputStream -> val a = j.getJSONArray(k); le(out, a.getInt(0)); le(out, a.getInt(1)) } } },
            o.getJSONObject("homodict").let { j -> keys(j).associateWith { k -> { out: DataOutputStream -> val a = j.getJSONArray(k); out.writeByte(a.length()); for (i in 0 until a.length()) str(out, a.getString(i)) } } },
            o.getJSONObject("bert").getJSONObject("vocab").let { j -> keys(j).associateWith { k -> { out: DataOutputStream -> le(out, j.getInt(k)) } } },
            o.getJSONObject("phrases").let { j -> keys(j).associateWith { k -> { out: DataOutputStream ->
                val a = j.getJSONArray(k); le16(out, a.length())
                for (i in 0 until a.length()) a.getJSONArray(i).let { p -> str(out, p.getString(0)); str(out, p.getString(1)) }
            } } },
            o.getJSONObject("gram").let { j -> keys(j).associateWith { k -> { out: DataOutputStream ->
                val e = j.getJSONObject(k); out.writeByte(e.length()); for (x in keys(e).sorted()) { str(out, x); str(out, e.getString(x)) }
            } } },
            // «ё»: не слово, а позиции «ё» в ключе и флаг «подходит и с заглавной» — дубли с заглавной не храним
            yoBase(eyo).mapValues { (key, v) -> { out: DataOutputStream ->
                val pos = key.indices.filter { key[it] != v.first[it] }
                out.writeByte(if (v.second) 1 else 0); out.writeByte(pos.size); for (i in pos) out.writeByte(i)
            } },
        )
        for (k in listOf("exceptions", "homodict", "phrases", "gram")) o.remove(k)
        o.getJSONObject("bert").remove("vocab")

        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        le(out, MAGIC)
        val head = o.toString().toByteArray(Charsets.UTF_8)
        le(out, head.size); out.write(head)
        for (t in tables) table(out, t)
        return bytes.toByteArray()
    }

    private fun table(out: DataOutputStream, t: Map<String, (DataOutputStream) -> Unit>) {
        while (out.size() % 4 != 0) out.writeByte(0)
        val keys = t.keys.map { it to it.toByteArray(Charsets.UTF_8) }.sortedWith { a, b -> compare(a.second, b.second) }
        val data = ByteArrayOutputStream(); val d = DataOutputStream(data)
        val offs = IntArray(keys.size + 1)
        for ((i, kv) in keys.withIndex()) {
            offs[i] = d.size()
            le16(d, kv.second.size); d.write(kv.second)
            t.getValue(kv.first)(d)
        }
        offs[keys.size] = d.size()
        le(out, keys.size); for (x in offs) le(out, x); out.write(data.toByteArray())
    }

    /** Одна таблица строка → строка после заголовка [tag] (u32 длина, utf-8): кэш словарей ударений на диске,
     * tag — подпись файлов, из которых собран. Читать — [strings]. */
    fun writeStrings(tag: String, map: Map<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream(); val out = DataOutputStream(bytes)
        val t = tag.toByteArray(Charsets.UTF_8)
        le(out, t.size); out.write(t)
        table(out, map.mapValues { (_, v) -> { o: DataOutputStream -> str(o, v) } })
        return bytes.toByteArray()
    }

    /** Подпись и таблица из [writeStrings]. */
    fun strings(buf: ByteBuffer): Pair<String, StringMap> {
        val b = buf.order(ByteOrder.LITTLE_ENDIAN)
        val n = b.getInt(0)
        return string(b, 4, n) to StringMap(Table(b, (4 + n + 3) and 3.inv()))
    }

    /** Map поверх таблицы строк: get и containsKey — бинарным поиском, перебор разбирает всё (его не зовут). */
    class StringMap(private val t: Table) : AbstractMap<String, String>() {
        override val size get() = t.size
        override fun isEmpty() = t.size == 0
        override fun get(key: String): String? = t.find(key).let { if (it < 0) null else t.str(it).first }
        override fun containsKey(key: String) = t.find(key) >= 0
        override val entries: Set<Map.Entry<String, String>> by lazy {
            (0 until t.size).associate { t.entry(it) }.entries
        }
    }

    /** Словарь «ё» по правилам eyo (см. YoDict): ключ через «е» → слово с «ё»; строчное слово — ещё и с заглавной.
     * Так он строился до stress.bin; в файле — [yoBase], поиск — [yoRestore], тест сверяет их с этим. */
    fun yoEntries(lines: Sequence<String>): Map<String, String> {
        val dict = HashMap<String, String>(1 shl 17)
        fun add(entry: String) {
            val lowerOnly = entry.startsWith("_")
            val word = entry.removePrefix("_")
            val key = word.replace('ё', 'е').replace('Ё', 'Е')
            dict[key] = word
            if (!lowerOnly && !word[0].isUpperCase()) dict[key.replaceFirstChar { it.uppercaseChar() }] = word.replaceFirstChar { it.uppercaseChar() }
        }
        for (raw in lines) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val open = line.indexOf('(')
            if (open < 0) add(line)
            else for (e in line.substring(open + 1, line.indexOf(')', open).coerceAtLeast(open + 1)).split('|')) add(line.substring(0, open) + e)
        }
        return dict
    }

    // keySet() в org.json Android скрыт, файл собирается и против android.jar
    private fun keys(j: JSONObject): List<String> = j.keys().asSequence().toList()
    /** Записи [yoEntries] без выводимых дублей с заглавной: ключ → (слово, подходит ли и с заглавной). Заглавный ключ
     * остаётся, только если из строчного его не вывести («_всплеснёмся» перебил «всплёснемся», а заглавная — от него). */
    private fun yoBase(lines: Sequence<String>): Map<String, Pair<String, Boolean>> {
        val ref = yoEntries(lines)
        fun cap(s: String) = s.replaceFirstChar { it.uppercaseChar() }
        fun capOk(k: String) = k.isNotEmpty() && !k[0].isUpperCase() && ref[cap(k)]?.let { it == cap(ref.getValue(k)) } == true
        val out = HashMap<String, Pair<String, Boolean>>(ref.size)
        for ((k, v) in ref) {
            require(k.length < 256)
            val lower = k.replaceFirstChar { it.lowercaseChar() }
            if (k[0].isUpperCase() && lower != k && lower in ref && capOk(lower)) continue
            out[k] = v to capOk(k)
        }
        return out
    }

    /** Слово с «ё» из таблицы «yo» или null; регистр как в [word]. */
    fun yoRestore(t: Table, word: String): String? {
        fun apply(key: String, p: Int): String {
            val c = key.toCharArray()
            for (i in 0 until t.u8(p + 1)) { val j = t.u8(p + 2 + i); c[j] = if (c[j] == 'Е') 'Ё' else 'ё' }
            return String(c)
        }
        t.find(word).let { if (it >= 0) return apply(word, it) }
        if (word.isEmpty() || !word[0].isUpperCase()) return null
        val lower = word.replaceFirstChar { it.lowercaseChar() }
        val p = t.find(lower)
        if (p < 0 || t.u8(p) and 1 == 0) return null
        return apply(lower, p).replaceFirstChar { it.uppercaseChar() }
    }

    private fun le(out: DataOutputStream, v: Int) = out.writeInt(Integer.reverseBytes(v))
    private fun le16(out: DataOutputStream, v: Int) { require(v in 0..0xFFFF); out.writeByte(v and 0xFF); out.writeByte(v ushr 8) }
    private fun str(out: DataOutputStream, s: String) { val b = s.toByteArray(Charsets.UTF_8); le16(out, b.size); out.write(b) }

    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) { val c = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF); if (c != 0) return c }
        return a.size - b.size
    }

    // ---- чтение ----

    /** Разобранный файл: остаток json строкой и таблицы по имени. */
    class File(buf: ByteBuffer) {
        private val b = buf.order(ByteOrder.LITTLE_ENDIAN)
        val head: String
        private val tables = HashMap<String, Table>()

        init {
            require(b.getInt(0) == MAGIC) { "stress.bin: не тот формат" }
            val len = b.getInt(4)
            head = string(b, 8, len)
            var pos = 8 + len
            for (name in TABLES) {
                pos = (pos + 3) and 3.inv()
                val t = Table(b, pos); tables[name] = t
                pos = t.end
            }
        }

        fun table(name: String): Table = tables.getValue(name)
    }

    /** Таблица: ключ → позиция значения в буфере. */
    class Table(private val b: ByteBuffer, base: Int) {
        val size = b.getInt(base)
        private val offs = base + 4
        private val data = offs + (size + 1) * 4
        val end = data + b.getInt(offs + size * 4)

        /** Позиция значения ключа или -1. */
        fun find(key: String): Int {
            val k = key.toByteArray(Charsets.UTF_8)
            var lo = 0; var hi = size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val p = data + b.getInt(offs + mid * 4)
                val len = u16(b, p)
                var c = 0
                for (i in 0 until minOf(len, k.size)) { c = (b.get(p + 2 + i).toInt() and 0xFF) - (k[i].toInt() and 0xFF); if (c != 0) break }
                if (c == 0) c = len - k.size
                when {
                    c < 0 -> lo = mid + 1
                    c > 0 -> hi = mid - 1
                    else -> return p + 2 + len
                }
            }
            return -1
        }

        /** Ключ и значение-строка записи номер i (перебор StringMap). */
        fun entry(i: Int): Pair<String, String> { val (k, p) = str(data + b.getInt(offs + i * 4)); return k to str(p).first }
        fun int(p: Int, i: Int = 0) = b.getInt(p + i * 4)
        fun u8(p: Int) = b.get(p).toInt() and 0xFF
        fun u16(p: Int) = u16(b, p)
        /** Строка в позиции p и позиция за ней. */
        fun str(p: Int): Pair<String, Int> { val n = u16(b, p); return string(b, p + 2, n) to p + 2 + n }
    }

    private fun u16(b: ByteBuffer, p: Int) = (b.get(p).toInt() and 0xFF) or ((b.get(p + 1).toInt() and 0xFF) shl 8)
    private fun string(b: ByteBuffer, p: Int, n: Int): String {
        val a = ByteArray(n)
        for (i in 0 until n) a[i] = b.get(p + i)
        return String(a, Charsets.UTF_8)
    }
}
