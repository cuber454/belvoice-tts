package com.cuber.belvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Контур интонации: вопрос поднимается к концу, восклицание выше и падает в конце. */
class IntonationTest {

    private fun coefs(type: String, n: Int, strength: Int = 2): FloatArray =
        FloatArray(n) { 1f }.also { Intonation.apply(type, strength, it) }

    @Test
    fun questionRisesTowardsEnd() {
        val p = coefs("general_q", 40)
        assertTrue("начало не должно меняться", p[0] == 1f)
        assertTrue("к концу выше: ${p[39]}", p[39] > 1.2f)
        // монотонно в последней трети
        for (i in 30 until 39) assertTrue("подъём не монотонный на $i", p[i + 1] >= p[i])
    }

    @Test
    fun whQuestionLeftAlone() {
        val p = coefs("wh_q", 40)
        assertTrue("вопрос с вопросительным словом не трогаем", p.all { it == 1f })
    }

    @Test
    fun exclamationHigherAndFallsAtEnd() {
        val p = coefs("exclam", 40)
        assertTrue("начало выше: ${p[0]}", p[0] > 1.05f)
        assertTrue("конец ниже начала: ${p[39]} < ${p[0]}", p[39] < p[0])
    }

    @Test
    fun statementUntouched() {
        val p = coefs("st", 40)
        assertTrue(p.all { it == 1f })
    }

    @Test
    fun shortPhraseUntouched() {
        val p = coefs("general_q", 5)
        assertTrue("короткую фразу не трогаем", p.all { it == 1f })
    }

    @Test
    fun strengthScales() {
        val weak = coefs("general_q", 40, 1)[39]
        val strong = coefs("general_q", 40, 3)[39]
        assertTrue("сила 3 выше силы 1: $weak / $strong", strong > weak)
        assertEquals(1f, coefs("general_q", 40, 1)[0])
    }

    @Test
    fun fiveLevelsGrowAndStopAtFive() {
        val ends = (1..Intonation.MAX_LEVEL).map { coefs("general_q", 40, it)[39] }
        assertEquals(5, Intonation.MAX_LEVEL)
        for (i in 1 until ends.size) assertTrue("уровень ${i + 1} выше уровня $i: ${ends[i - 1]} / ${ends[i]}", ends[i] > ends[i - 1])
        // выше пяти не поднимаем: значение упирается в предел, а не растёт бесконечно
        assertEquals(ends.last(), coefs("general_q", 40, 9)[39])
    }

    @Test
    fun fifthLevelExclamation() {
        val p = coefs("exclam", 40, 5)
        assertTrue("пятый уровень заметно выше: ${p[0]}", p[0] > 1.25f)
        assertTrue("и падает в конце: ${p[39]}", p[39] < p[0])
    }

    @Test
    fun rulesAllowFive() {
        assertEquals(5, Rules(focus = 5).focusLevel)
        assertEquals(5, Rules(focus = 9).focusLevel)   // выше предела — предел
        assertEquals(1, Rules(focus = 0).focusLevel)
    }
}
