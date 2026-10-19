package ru.kost.ruvoice

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Новое за 27–28.09 на телефоне, через сервис как у читалки: профили и привязка к голосу, «Системный удалённые»,
 * мягкий перенос, «Подробный журнал», громкость до ×3. Prefs «ruvoice», «ruvoice_profiles» и файлы
 * «Системный удалённые» возвращаются как были.
 */
@RunWith(AndroidJUnit4::class)
class NewFeaturesDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val live = ctx.getSharedPreferences("ruvoice", Context.MODE_PRIVATE)
    private val store = ctx.getSharedPreferences("ruvoice_profiles", Context.MODE_PRIVATE)
    private lateinit var liveSaved: Map<String, *>
    private lateinit var storeSaved: Map<String, *>
    private val removedSaved = HashMap<File, String?>()

    @Before fun save() {
        liveSaved = HashMap(live.all); storeSaved = HashMap(store.all)
        for (kind in Dicts.Kind.values()) Dicts.file(ctx.filesDir, kind, Dicts.REMOVED).let { removedSaved[it] = it.takeIf { f -> f.exists() }?.readText() }
    }

    @After fun restore() {
        put(live, liveSaved); put(store, storeSaved)
        for ((f, t) in removedSaved) if (t == null) f.delete() else f.writeText(t)
        for (kind in Dicts.Kind.values()) Dicts.rebuildSystem(ctx.filesDir, kind)
    }

    private fun put(p: SharedPreferences, m: Map<String, *>) {
        val e = p.edit().clear()
        for ((k, v) in m) when (v) {
            is String -> e.putString(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v); is Boolean -> e.putBoolean(k, v)
            is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toSet())
        }
        e.commit()
    }

    /** Профиль с другой громкостью привязан к голосу: читалка сменила голос — профиль включился, prefs его;
     * тот же голос повторно не переключает; ручная смена профиля не откатывается прежним голосом читалки. */
    @Test fun profileFollowsReaderVoice() {
        val prefs = Prefs(ctx)
        val profiles = Profiles(ctx)
        val main = profiles.active()
        val baseVoice = live.getString("voice", "xenia")!!
        val other = if (baseVoice == "baya") "kseniya" else "baya"
        prefs.volume = 1f
        val p = profiles.add("Тест привязки")          // копия текущих, сразу активный
        prefs.volume = 2.5f
        profiles.switchTo(main.id)
        assertEquals(1f, prefs.volume, 0.001f)
        profiles.bind(p.id, other)
        assertEquals(other, profiles.boundVoice(p.id))

        val reader = "com.test.reader"
        assertNull("тот же голос, что в prefs", profiles.followVoice(reader, baseVoice))
        assertEquals(p.id, profiles.followVoice(reader, other)?.id)
        assertEquals(p.id, profiles.active().id)
        assertEquals(2.5f, prefs.volume, 0.001f)
        assertEquals(other, live.getString("voice", null))
        assertNull("повтор того же голоса", profiles.followVoice(reader, other))

        profiles.switchTo(main.id)                    // как плиткой
        assertNull("ручная смена не откатывается", profiles.followVoice(reader, other))
        assertEquals(main.id, profiles.active().id)

        // экспорт хранит привязку
        val json = prefs.exportJson()
        assertTrue(json, json.contains("voice_bind"))

        profiles.delete(p.id)
        assertNull(profiles.binds()[other])
        assertEquals(main.id, profiles.active().id)
    }

    /** Смахнули «нарочно = нар+ошно» в системном списке замен: сервис читает «нарочно» без замены; вернули — снова с ней. */
    @Test fun removedSystemLineReachesService() {
        val prefs = Prefs(ctx)
        prefs.setRule("verbose_log", true)
        val line = Dicts.file(ctx.filesDir, Dicts.Kind.REPLACE, Dicts.SYSTEM).readLines().first { it.startsWith("нарочно =") }
        tts { t ->
            val before = forModel(t, "Он сделал это нарочно.")
            assertTrue(before, before.contains("ошн"))
            Dicts.addRemoved(ctx.filesDir, Dicts.Kind.REPLACE, line); Dicts.rebuildSystem(ctx.filesDir, Dicts.Kind.REPLACE)
            assertFalse(Dicts.file(ctx.filesDir, Dicts.Kind.REPLACE, Dicts.SYSTEM).readLines().contains(line))
            val off = forModel(t, "Он сделал это нарочно.")
            assertFalse(off, off.contains("ошн"))
            Dicts.dropRemoved(ctx.filesDir, Dicts.Kind.REPLACE, line); Dicts.rebuildSystem(ctx.filesDir, Dicts.Kind.REPLACE)
            val back = forModel(t, "Он сделал это нарочно.")
            assertTrue(back, back.contains("ошн"))
        }
    }

    /** Мягкий перенос и WORD JOINER внутри слова: в модель уходит целое слово; журнал пишет запрос с кодами.
     * Правило выключено — строк «в модель» нет. */
    @Test fun softHyphenAndVerboseLog() {
        val prefs = Prefs(ctx)
        prefs.setRule("verbose_log", true)
        tts { t ->
            val m = forModel(t, "Мы шли по вос­тро⁠му краю.")
            assertTrue(m, m.replace("+", "").contains("востро"))
            val req = SileroTtsService.journal().lastOrNull { it.contains("голос") && it.contains("\\u00AD") }
            assertNotNull("запрос с кодом мягкого переноса в журнале", req)
            prefs.setRule("verbose_log", false)
            val n = SileroTtsService.journal().size
            synth(t, "Проверка без журнала.", "q")
            assertTrue(SileroTtsService.journal().drop(n).none { it.contains("в модель") })
        }
    }

    /** Громкость ×3 громче ×1 и не выше потолка 0,97. */
    @Test fun volumeUpToThree() {
        val prefs = Prefs(ctx)
        tts { t ->
            prefs.volume = 1f
            val one = peakRms(synth(t, "Поздним вечером старый смотритель запер тяжёлые ворота.", "v1"))
            prefs.volume = 3f
            val three = peakRms(synth(t, "Поздним вечером старый смотритель запер тяжёлые ворота.", "v3"))
            Log.i("RuVoiceTest", "volume ×1 peak=%.3f rms=%.4f, ×3 peak=%.3f rms=%.4f".format(one.first, one.second, three.first, three.second))
            assertTrue("×3 не громче", three.second > one.second * 1.8f)
            assertTrue("пик ${three.first}", three.first <= 0.975f)
        }
    }

    /** Хрипы на громкости: русский и кусок с английским на ×1, ×2, ×3 в cache/vol_*.wav (разбор на компьютере).
     * Здесь — потолок и отсутствие плоских вершин (≥3 сэмплов подряд у потолка = срезано). */
    @Test fun volumeNoClipping() {
        val prefs = Prefs(ctx)
        prefs.setRule("en_proxy_books", true)
        val texts = mapOf("ru" to "Стой! Кто там идёт?! Громко крикнул сторож и ударил в колокол.",
            "en" to "Откройте файл в Google Chrome, please open it right now, и нажмите кнопку.")
        tts { t ->
            for ((tag, text) in texts) for (v in floatArrayOf(1f, 2f, 3f)) {
                prefs.volume = v
                val f = synth(t, text, "vol_${tag}_${v.toInt()}")
                val (peak, rms) = peakRms(f)
                val flat = flatTops(f)
                Log.i("RuVoiceTest", "vol $tag ×$v peak=%.3f rms=%.4f flat=$flat".format(peak, rms))
                assertTrue("$tag ×$v пик $peak", peak <= 0.975f)
                assertEquals("$tag ×$v плоские вершины", 0, flat)
            }
        }
    }

    /** Та же запись ×1 через Pcm.gain ×2 и ×3 (синтез недетерминирован, сравнивать разные прогоны нельзя):
     * выход = вход × g × огибающая, огибающая в (0, 1] и за сэмпл меняется мало — форма волны не гнётся, щелчков нет.
     * Результат в cache/gain_*.raw (float32) для разбора на компьютере. */
    @Test fun gainKeepsWaveform() {
        val prefs = Prefs(ctx)
        prefs.volume = 1f
        tts { t ->
            for ((tag, text) in mapOf("ru" to "Стой! Кто там идёт?! Громко крикнул сторож и ударил в колокол.")) {
                val f = synth(t, text, "gain_src_$tag")
                val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
                val sr = b.getInt(24); b.position(44)
                val x = FloatArray(b.remaining() / 2) { b.short / 32768f }
                for (g in floatArrayOf(2f, 3f)) {
                    val y = x.copyOf(); ru.kost.ruvoice.audio.Pcm.gain(y, g, sr)
                    var minEnv = 1f; var maxEnv = 0f; var maxStep = 0f; var prev = Float.NaN; var peak = 0f
                    for (i in x.indices) {
                        peak = maxOf(peak, kotlin.math.abs(y[i]))
                        if (kotlin.math.abs(x[i]) < 0.01f) { prev = Float.NaN; continue }
                        val e = y[i] / (x[i] * g)
                        minEnv = minOf(minEnv, e); maxEnv = maxOf(maxEnv, e)
                        if (!prev.isNaN()) maxStep = maxOf(maxStep, kotlin.math.abs(e - prev))
                        prev = e
                    }
                    Log.i("RuVoiceTest", "gain $tag ×$g sr=$sr peak=%.3f env %.3f…%.3f maxStep/sample=%.5f".format(peak, minEnv, maxEnv, maxStep))
                    File(ctx.cacheDir, "gain_${tag}_${g.toInt()}.raw").writeBytes(ByteBuffer.allocate(y.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(y) }.array())
                    assertTrue(peak <= 0.97f + 1e-4f)
                    assertTrue("огибающая $minEnv…$maxEnv", minEnv > 0.2f && maxEnv <= 1.0001f)
                    assertTrue("скачок огибающей $maxStep", maxStep < 0.01f)
                }
            }
        }
    }

    /** Сколько раз ≥3 сэмпла подряд стоят на пике по модулю (признак жёсткого среза). */
    private fun flatTops(f: File): Int {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN); b.position(44)
        val s = ShortArray(b.remaining() / 2) { b.short }
        val peak = s.maxOf { kotlin.math.abs(it.toInt()) }
        if (peak < 30000) return 0
        var runs = 0; var run = 0
        for (x in s) if (kotlin.math.abs(x.toInt()) >= peak - 1) { run++; if (run == 3) runs++ } else run = 0
        return runs
    }

    // ---- помощники ----

    private fun tts(body: (TextToSpeech) -> Unit) {
        val ready = CountDownLatch(1); var status = -1
        val t = TextToSpeech(ctx, { s -> status = s; ready.countDown() }, "ru.kost.ruvoice")
        assertTrue(ready.await(60, TimeUnit.SECONDS)); assertEquals(TextToSpeech.SUCCESS, status)
        t.setLanguage(Locale("ru", "RU"))
        try { body(t) } finally { t.shutdown() }
    }

    private fun synth(t: TextToSpeech, text: String, id: String): File {
        val done = CountDownLatch(1); var err = false
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { done.countDown() }
            @Deprecated("") override fun onError(id: String?) { err = true; done.countDown() }
            override fun onError(id: String?, code: Int) { err = true; done.countDown() }
        })
        val f = File(ctx.cacheDir, "nf_$id.wav")
        // ruvoice.nodict не ставим: словари и нужны
        assertEquals(TextToSpeech.SUCCESS, t.synthesizeToFile(text, Bundle(), f, id))
        assertTrue("timeout $id", done.await(180, TimeUnit.SECONDS)); assertFalse("error $id", err)
        return f
    }

    /** Строка «в модель: …» журнала для этого текста. */
    private fun forModel(t: TextToSpeech, text: String): String {
        val n = SileroTtsService.journal().size
        synth(t, text, "m${System.nanoTime()}")
        val lines = SileroTtsService.journal().takeLast((SileroTtsService.journal().size - n).coerceAtLeast(0) + 5)
        Log.i("RuVoiceTest", lines.joinToString("\n"))
        return lines.lastOrNull { it.contains("в модель") } ?: fail("нет строки «в модель»: $lines").let { "" }
    }

    /** Пик и RMS 16-битного WAV (данные после 44-байтного заголовка). */
    private fun peakRms(f: File): Pair<Float, Float> {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        b.position(44)
        var peak = 0; var sum = 0.0; var n = 0
        while (b.remaining() >= 2) { val s = b.short.toInt(); peak = maxOf(peak, kotlin.math.abs(s)); sum += s.toDouble() * s; n++ }
        return peak / 32768f to kotlin.math.sqrt(sum / maxOf(n, 1)).toFloat() / 32768f
    }
}
