package ru.kost.ruvoice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.kost.ruvoice.bin.StressBin
import java.io.File
import java.nio.ByteBuffer

/** stress.bin отдаёт ровно то, что лежит в silero_ru.json и eyo_safe.txt. */
class StressBinTest {
    private val json = File(TestData.root(), "app/src/main/assets/silero/silero_ru.json").readText()
    private val eyo = File(TestData.root(), "app/src/main/assets/eyo_safe.txt").readLines()
    private val bin = StressBin.File(ByteBuffer.wrap(StressBin.write(json, eyo.asSequence())))
    private val d = SileroData(bin)
    private val o = JSONObject(json)

    private fun keys(j: JSONObject) = j.keys().asSequence().toList()

    @Test fun tablesMatchJson() {
        o.getJSONObject("exceptions").let { j -> for (k in keys(j)) assertEquals(k, j.getJSONArray(k).let { listOf(it.getInt(0), it.getInt(1)) }, d.exceptions.getValue(k).toList()) }
        o.getJSONObject("homodict").let { j -> for (k in keys(j)) assertEquals(k, j.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } }, d.homodict.getValue(k)) }
        o.getJSONObject("bert").getJSONObject("vocab").let { j -> for (k in keys(j)) assertEquals(k, j.getInt(k), d.bertVocab.getValue(k)) }
        o.getJSONObject("phrases").let { j -> for (k in keys(j)) assertEquals(k,
            j.getJSONArray(k).let { a -> List(a.length()) { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } } }, d.phrases.getValue(k)) }
        o.getJSONObject("gram").let { j -> for (k in keys(j)) assertEquals(k, j.getJSONObject(k).let { e -> keys(e).associateWith { e.getString(it) } }, d.gram.getValue(k)) }
        assertEquals(keys(o.getJSONObject("exceptions")).size, d.exceptions.size)
        assertEquals(keys(o.getJSONObject("speakers")).toSet(), d.speakers.keys)
        for (miss in listOf("", "zzz", "ааааа", "замокк")) { assertNull(d.exceptions[miss]); assertNull(d.bertVocab[miss]) }
    }

    @Test fun userDictFromDiskSameAsParsed() {
        // кэш словарей ударений на диске (DictCache.stress, disk): первый раз разбор и запись, второй — чтение файла
        val src = File(TestData.root(), "app/src/main/assets/dicts/stress/Системный.txt")
        val disk = File.createTempFile("userdict", ".bin").apply { delete(); deleteOnExit() }
        val parsed = HashMap(DictCache.stress(listOf(src), disk = disk))
        val tmp = File.createTempFile("stress", ".txt").apply { deleteOnExit(); writeText(src.readText()) }
        tmp.setLastModified(src.lastModified())
        // другой файл — другая подпись: разбор заново, а не файл от прошлого
        assertEquals(parsed, HashMap(DictCache.stress(listOf(tmp), disk = disk)))
        val (tag, fromDisk) = StressBin.strings(ByteBuffer.wrap(disk.readBytes()))
        assertEquals(tmp.path, tag.substringBefore('|'))
        assertEquals(parsed.size, fromDisk.size)
        for ((k, v) in parsed) assertEquals(k, v, fromDisk[k])
        assertNull(fromDisk["нетакогослова"])
        assertEquals(parsed, HashMap(fromDisk))
    }

    @Test fun yoMatchesEyoRules() {
        val ref = StressBin.yoEntries(eyo.asSequence())
        for ((k, v) in ref) assertEquals(k, v, StressBin.yoRestore(d.yo, k))
        // не из словаря: соседние формы, заглавная у «только строчными», верхний регистр целиком
        for (k in ref.keys.take(20000)) for (w in listOf(k + "ы", k.uppercase(), k.drop(1))) assertEquals(w, ref[w], StressBin.yoRestore(d.yo, w))
    }
}
