package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Секция «Для TalkBack»: правила экранного чтеца поверх общих, книгам ничего не меняется. */
class ScreenReaderRulesTest {
    private val allowed = "абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ .,!?-–:;+«»()"

    @Test fun defaults() {
        val sr = Rules().screenReader()
        assertTrue(sr.on("symbol_names"))
        assertFalse(sr.on("speech"))
        assertFalse(sr.on("lead_in"))
        assertTrue(sr.on("fast_start"))
        // общие — как были
        assertFalse(Rules().on("symbol_names"))
        assertTrue(Rules().on("lead_in"))
        assertTrue(Rules().on("sr_pauses_off"))
        assertFalse(Rules().on("fast_start"))
    }

    @Test fun fastStartSeparateFromBooks() {
        // книжный fast_start включён, чтецовый выключен — у чтеца быстрого старта нет, и наоборот
        assertFalse(Rules(off = setOf("fast_start", "sr_fast_start")).screenReader().on("fast_start"))
        assertTrue(Rules(off = setOf("fast_start", "sr_fast_start")).on("fast_start"))
        assertTrue(Rules().screenReader().on("fast_start"))
    }

    @Test fun sectionSwitchesOff() {
        val r = Rules(off = setOf("sr_symbols", "sr_quote_off", "sr_lead_in_off")).screenReader()
        assertFalse(r.on("symbol_names"))
        assertTrue(r.on("speech"))
        assertTrue(r.on("lead_in"))
    }

    @Test fun keepsOtherSettings() {
        val base = Rules(off = setOf("phones", "lead_in"), maxLen = 400, focus = 2, srMaxLen = 150)
        val sr = base.screenReader()
        assertFalse(sr.on("phones"))
        // предел куска у чтеца свой (sr_max_len), у книг прежний
        assertEquals(150, sr.maxLen)
        assertEquals(400, base.maxLen)
        assertEquals(2, sr.focusLevel)
        // «Служебные символы везде» уже включены — остаются включены
        assertTrue(Rules(off = setOf("symbol_names")).screenReader().on("symbol_names"))
    }

    @Test fun srMaxLenCutsTail() {
        val long = (1..60).joinToString(" ") { "слово$it" }  // ~480 символов без точек
        assertTrue(Splitter.sentences(long, Rules(srMaxLen = 200).screenReader()).all { it.length <= 200 })
        assertEquals(2, Splitter.sentences(long, Rules(srMaxLen = 200)).size)  // книги — по 400
    }

    @Test fun booksVsTalkBack() {
        assertEquals("", Normalizer.prepare("* * *", allowed))
        assertEquals("ударение треугольник вниз", Normalizer.prepare("Ударение ▾", allowed, Rules().screenReader()))
    }
}
