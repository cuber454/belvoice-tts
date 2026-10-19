package ru.kost.ruvoice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.kost.ruvoice.text.Stress
import java.io.File

/** Свои списки перебивают системные и крест-накрест: настоящий системный список замен даёт «з+амок графа»,
 * своё «замок зам+ок» в ударениях или «замок = зам+ок» в заменах должно его перебить через весь путь до Stress. */
@RunWith(AndroidJUnit4::class)
class DictOrderDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun read(stress: String, replace: String): String {
        val root = File(ctx.cacheDir, "dict_order_test").apply { deleteRecursively() }
        for (kind in Dicts.Kind.values()) Dicts.dir(root, kind).mkdirs()
        Dicts.file(root, Dicts.Kind.STRESS, Dicts.SYSTEM).writeText(ctx.assets.open("dicts/stress/${Dicts.SYSTEM}.txt").bufferedReader().readText())
        Dicts.file(root, Dicts.Kind.REPLACE, Dicts.SYSTEM).writeText(ctx.assets.open("dicts/replace/${Dicts.SYSTEM}.txt").bufferedReader().readText())
        Dicts.file(root, Dicts.Kind.STRESS, Dicts.MAIN).writeText(stress)
        Dicts.file(root, Dicts.Kind.REPLACE, Dicts.MAIN).writeText(replace)
        val sf = Dicts.files(root, Dicts.Kind.STRESS)
        val m = SileroModels(ctx); m.ensureLoaded()
        val text = DictCache.replacements(Dicts.files(root, Dicts.Kind.REPLACE), stress = sf).apply("замок графа")
        return Stress(m.data, m, DictCache.stress(sf)).apply(text)
    }

    @Test fun systemReplaceAlone() = assertTrue(read("", "").startsWith("з+амок "))
    @Test fun userStressBeatsSystemReplace() = assertTrue(read("замок зам+ок\n", "").startsWith("зам+ок "))
    @Test fun userReplaceBeatsSystemPhrase() = assertTrue(read("", "замок = зам+ок\n").startsWith("зам+ок "))
}
