// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package com.cuber.belvoice.text

import org.junit.Assert.assertEquals
import org.junit.Test

/** Даты, время, единицы и сокращения по-белорусски. */
class BelNumbersTest {

    @Test
    fun datesFull() {
        assertEquals("дванаццатага мая дзве тысячы дваццаць шостага года",
            BelNumbers.dates("12.05.2026"))
    }

    @Test
    fun datesWithoutYear() {
        assertEquals("трэцяга верасня", BelNumbers.dates("3.09"))
    }

    @Test
    fun datesTwoDigitYear() {
        assertEquals("першага студзеня дзве тысячы дваццатага года",
            BelNumbers.dates("01.01.20"))
    }

    @Test
    fun timesFull() {
        assertEquals("дваццаць адна гадзіна трыццаць хвілін", BelNumbers.times("21:30"))
    }

    @Test
    fun timesRoundHour() {
        assertEquals("дзевяць гадзін роўна", BelNumbers.times("9:00"))
    }

    @Test
    fun unitsBase() {
        assertEquals("тры кіламетры", BelNumbers.units("3 км"))
        assertEquals("адзін кіламетр", BelNumbers.units("1 км"))
        assertEquals("пяць кіламетраў", BelNumbers.units("5 км"))
    }

    @Test
    fun unitsPercentAndMoney() {
        assertEquals("пяць працэнтаў", BelNumbers.units("5 %"))
        assertEquals("два рублі", BelNumbers.units("2 руб."))
        assertEquals("трыста долараў", BelNumbers.units("300 $"))
    }

    @Test
    fun thousandsWithSeparator() {
        assertEquals("дзве тысячы кіламетраў", BelNumbers.units("2 000 км"))
    }

    @Test
    fun yearAbbreviation() {
        assertEquals("1999 год", BelNumbers.abbreviations("1999 г."))
    }

    @Test
    fun standaloneMoney() {
        assertEquals("пяць тысяч рублёў", BelNumbers.standalone(BelNumbers.units("5 тыс. руб.")))
    }

    @Test
    fun unitOnlyAsSeparateToken() {
        // «г» и «т» — это граммы и тоны, но только отдельным знаком за числом: иначе «2026 год» становился
        // «2026 г(раммаў) + од», а «3 тамы» — «3 т(оны) + амы» (нашлось на живом паке 19.10.2026)
        assertEquals("2026 год", BelNumbers.units("2026 год"))
        assertEquals("3 тамы", BelNumbers.units("3 тамы"))
        assertEquals("пяць грамаў", BelNumbers.units("5 г."))
        assertEquals("дзве тоны", BelNumbers.units("2 т"))
        assertEquals("дзве штукі", BelNumbers.units("2 шт."))
        assertEquals("дзве капейкі", BelNumbers.units("2 кап."))
        assertEquals("дзве тысячы", BelNumbers.units("2 тыс."))
        assertEquals("на годзе жыцця", BelNumbers.units("на годзе жыцця"))
    }

    @Test
    fun yearReadsAsOrdinal() {
        // год — порядковым: «дзве тысячы дваццаць шосты год», а не «…шосты грамаўод»
        assertEquals("сёння дзве тысячы дваццаць шосты год, тры тамы", BelNormalizer.prepare("Сёння 2026 год, 3 тамы", "абвгдеёжзійклмнопрстуўфхцчшыьэюя,."))
    }

    @Test
    fun ordinalOnlyLastWord() {
        assertEquals("дзве тысячы дваццаць шосты", BelNormalizer.ordinal(2026))
        assertEquals("сто дваццаць шосты", BelNormalizer.ordinal(126))
        assertEquals("дваццаты", BelNormalizer.ordinal(20))
    }

    @Test
    fun pluralRules() {
        assertEquals("адзін", BelNormalizer.cardinal(1))
        assertEquals("два", BelNormalizer.cardinal(2))
        assertEquals("адзінаццаць", BelNormalizer.cardinal(11))
    }
}
