package com.cuber.belvoice

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Голоса белорусской сборки: встроенные bel_* и пак cis_be. */
class SpeakerTest {
    init { Speaker.builtin = true }

    private val belJson = File(TestData.root(), "app/src/main/assets/silero/silero_be.json").readText()
    private val d = SileroData(belJson)

    /** Пак cis_be: те же символы и голоса, что во встроенной модели. */
    private fun packJson(id: String = "cis_be", speakers: JSONObject = JSONObject(belJson).getJSONObject("speakers")): JSONObject {
        val j = JSONObject(belJson)
        return JSONObject().put("format", 2).put("id", id).put("title", "Голоса").put("license", "MIT").put("source", "x")
            .put("symbols", j.getString("symbols")).put("symbol_to_id", j.getJSONObject("symbol_to_id"))
            .put("sos", j.getString("sos")).put("eos", j.getString("eos"))
            .put("alphabet", j.getString("alphabet")).put("types", false)
            .put("speakers", speakers)
    }

    private val pack = Pack(packJson().toString(), File("."))
    private val otherPack = Pack(packJson("cis_other", JSONObject().put("ukr_igor", 21)).toString(), File("."))

    private fun lite(block: () -> Unit) { Speaker.builtin = false; try { block() } finally { Speaker.builtin = true } }

    @Test fun resolvesBuiltinAndPackVoices() {
        val x = Speaker.resolve("bel_larisa", d, listOf(pack))!!
        assertNull(x.pack); assertFalse(x.types); assertSame(d.sym, x.sym)
        assertEquals(d.speakers.getValue("bel_larisa"), x.id)

        val a = Speaker.resolve("cis_be/ukr_igor", d, listOf(pack))!!
        assertSame(pack, a.pack); assertFalse(a.types); assertSame(pack.sym, a.sym)
        assertEquals(pack.speakers.getValue("ukr_igor"), a.id)
    }

    @Test fun defaultIsBelLarisa() {
        assertNull(Speaker.resolve("cis_be/ukr_igor", d, emptyList()))   // пак удалён
        assertNull(Speaker.resolve("cis_be/nobody", d, listOf(pack)))
        assertNull(Speaker.resolve(null, d, listOf(pack)))
        assertEquals("bel_larisa", Speaker.default(d, listOf(pack))!!.name)
    }

    @Test fun namesAndSameEngine() {
        val names = Speaker.names(d, listOf(pack))
        val n = d.speakers.size
        assertEquals(d.speakers.keys.sorted(), names.take(n))
        assertEquals(n + d.speakers.size, names.size)
        assertEquals(d.speakers.keys.sorted(), Speaker.sameEngine(Speaker.default(d, listOf(pack))!!, d, listOf(pack)))
        assertEquals(names.drop(n), Speaker.sameEngine(Speaker.resolve("cis_be/ukr_igor", d, listOf(pack))!!, d, listOf(pack)))
    }

    @Test fun labels() {
        assertEquals("bel_larisa", Speaker.label("bel_larisa"))
        assertEquals("ukr_igor (cis_be)", Speaker.label("cis_be/ukr_igor"))
    }

    @Test fun ttsNamesCarryBeOnce() {
        assertEquals("larisa-be", Speaker.ttsName("bel_larisa"))
        assertEquals("larisa-be-cis-be", Speaker.ttsName("cis_be/bel_larisa"))
        assertEquals("ukr-igor-be-cis-be", Speaker.ttsName("cis_be/ukr_igor"))
        val packs = listOf(pack)
        assertEquals("bel_larisa", Speaker.fromTtsName("larisa-be", d, packs))
        assertEquals("cis_be/ukr_igor", Speaker.fromTtsName("ukr-igor-be-cis-be", d, packs))
        assertNull(Speaker.fromTtsName(null, d, packs))
        assertNull(Speaker.resolve(Speaker.fromTtsName("nobody-be", d, packs), d, packs))
    }

    @Test fun liteTakesPackWithOurId() = lite {
        assertTrue(Speaker.names(d, emptyList()).isEmpty())
        assertNull(Speaker.default(d, emptyList()))
        val x = Speaker.resolve("bel_larisa", d, listOf(pack))!!
        assertEquals("cis_be/bel_larisa", x.name); assertSame(pack, x.pack)
        // пак с другим id голые имена не получает
        assertNull(Speaker.resolve("bel_larisa", d, listOf(otherPack)))
    }

    @Test fun hasVoices() {
        assertTrue(Speaker.hasVoices(emptyList()))
        lite {
            assertFalse(Speaker.hasVoices(emptyList()))
            assertTrue(Speaker.hasVoices(listOf(pack)))
        }
    }

    @Test fun existsMatchesResolve() {
        val names = listOf("bel_larisa", "nobody", "cis_be/ukr_igor", "cis_be/nobody", "zz/bel_larisa", "", null)
        for (packs in listOf(emptyList(), listOf(pack), listOf(otherPack), listOf(pack, otherPack))) {
            for (n in names) assertEquals("$n $packs", Speaker.resolve(n, d, packs) != null, Speaker.exists(n, d.speakers.keys, packs))
            lite { for (n in names) assertEquals("lite $n", Speaker.resolve(n, d, packs) != null, Speaker.exists(n, d.speakers.keys, packs)) }
        }
    }
}
