package ru.kost.ruvoice

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import ru.kost.ruvoice.bin.StressBin
import ru.kost.ruvoice.text.Replacements

/**
 * Разобранные словари на процесс: сервис TTS и настройки живут в одном процессе, поэтому
 * один снимок обслуживает и синтез, и прогрев из UI. Снимок привязан к сигнатуре набора
 * файлов (путь, mtime, размер) — правка в настройках или переключение списка меняют её,
 * и следующий запрос пересобирает. Запрос синтеза во время прогрева просто ждёт его.
 */
object DictCache {
    private class Snap<T>(val sig: List<String>, val value: T)
    @Volatile private var stressSnap: Snap<Map<String, String>>? = null
    @Volatile private var replaceSnap: Snap<Replacements>? = null

    private fun sig(files: List<File>) = files.map { "${it.path}|${it.lastModified()}|${it.length()}" }

    // у ударений и замен свои замки: прогрев сервиса разбирает их параллельно
    private val stressLock = Any()
    private val replaceLock = Any()

    /** Слияние включённых списков ударений: при одинаковом слове побеждает более поздний файл. */
    fun stress(files: List<File>, onProgress: ((Int) -> Unit)? = null, disk: File? = null): Map<String, String> = synchronized(stressLock) {
        val s = sig(files)
        stressSnap?.takeIf { it.sig == s }?.let { return it.value }
        val t = System.nanoTime()
        // Разобранное — ещё и на диске (disk): системный словарь — 100 тыс. строк, ~1,5–2,5 с разбора на A32 в
        // первой фразе после запуска процесса; с диска — mmap и бинарный поиск. Подпись в файле — та же sig.
        val tag = s.joinToString("\n")
        disk?.takeIf { it.exists() }?.let { f -> runCatching {
            val (fileTag, map) = StressBin.strings(RandomAccessFile(f, "r").channel.use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) })
            if (fileTag == tag) {
                android.util.Log.i("RuVoice", "словари ударений с диска: ${map.size} слов, ${(System.nanoTime() - t) / 1_000_000} мс")
                onProgress?.invoke(100)
                return map.also { stressSnap = Snap(s, it) }
            }
        } }
        val lines = files.flatMap { it.readLines() }
        val map = HashMap<String, String>(lines.size * 2)
        for ((i, line) in lines.withIndex()) {
            if (onProgress != null && i % 2000 == 0) onProgress(i * 100 / lines.size)
            val (w, v) = DictLines.parseStress(line) ?: continue
            map[w.lowercase()] = v.lowercase()
        }
        onProgress?.invoke(100)
        android.util.Log.i("RuVoice", "словари ударений: ${lines.size} строк, ${(System.nanoTime() - t) / 1_000_000} мс")
        disk?.let { f -> runCatching { File(f.path + ".tmp").apply { writeBytes(StressBin.writeStrings(tag, map)) }.renameTo(f) } }
        return map.also { stressSnap = Snap(s, it) }
    }

    private val wordRe = Regex("[\\p{L}\\d]+")

    /** Слова, о которых пользователь сказал сам: ключи своих списков ударений и однословные ключи своих замен. */
    private fun userWords(replace: List<File>, stress: List<File>): Set<String> {
        val out = HashSet<String>()
        for (f in stress) if (!Dicts.isSystem(Dicts.name(f))) f.forEachLine { l -> DictLines.parseStress(l)?.let { out += it.first.lowercase() } }
        for (f in replace) if (!Dicts.isSystem(Dicts.name(f))) f.forEachLine { l ->
            val k = Replacements.split(l)?.first?.removePrefix("$")?.lowercase() ?: return@forEachLine
            if (!k.startsWith("~") && wordRe.matches(k)) out += k
        }
        return out
    }

    /** Слияние включённых списков замен: строки всех файлов подряд, дальше Replacements.parse.
     * stress — включённые списки ударений: строка системных замен, в ключе которой слово из своего списка ударений
     * или своей однословной замены, выбрасывается — иначе «потом = п+отом» ставит «+», и словарь ударений слово
     * пропускает, а длинная системная фраза срабатывает раньше короткой своей замены. */
    fun replacements(files: List<File>, onProgress: ((Int) -> Unit)? = null, stress: List<File> = emptyList()): Replacements = synchronized(replaceLock) {
        val s = sig(files) + sig(stress)
        replaceSnap?.takeIf { it.sig == s }?.let { return it.value }
        // 62k строк: ~80 мс чтение + ~350–700 мс разбор на среднем телефоне (аллокации на ART)
        val t = System.nanoTime()
        val user = userWords(files, stress)
        val lines = files.flatMap { f ->
            if (user.isEmpty() || Dicts.name(f) != Dicts.SYSTEM) f.readLines()
            else f.readLines().filter { l ->
                val k = Replacements.split(l)?.first ?: return@filter true
                k.startsWith("~") || wordRe.findAll(k.lowercase()).none { it.value in user }
            }
        }
        val r = Replacements.parse(lines, onProgress?.let { cb -> { done -> cb(if (lines.isEmpty()) 100 else done * 100 / lines.size) } })
        android.util.Log.i("RuVoice", "словари замен: ${lines.size} строк, ${(System.nanoTime() - t) / 1_000_000} мс")
        return r.also { replaceSnap = Snap(s, it) }
    }

    /** Пересобрать снимок в фоне; колбэки зовутся из фонового потока. Возвращает поток —
     * тестам есть что join-ить, UI это не нужно. */
    fun warm(files: List<File>, kind: Dicts.Kind, onProgress: (Int) -> Unit, onDone: () -> Unit, stressFiles: List<File> = emptyList()): Thread =
        Thread {
            try { if (kind == Dicts.Kind.STRESS) stress(files, onProgress) else replacements(files, onProgress, stressFiles) }
            catch (e: Exception) { /* битый файл — сервис получит то же исключение при синтезе, тут молчим */ }
            onDone()
        }.apply { start() }
}
