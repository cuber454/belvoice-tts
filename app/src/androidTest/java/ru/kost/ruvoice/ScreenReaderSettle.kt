package ru.kost.ruvoice

import android.os.Process
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.test.platform.app.InstrumentationRegistry

/**
 * При работающем экранном чтеце (Jieshuo) после перезапуска процесса движка — а прогон тестов его перезапускает —
 * чтец переподключается и шлёт CHECK_TTS_DATA: CheckVoiceDataActivity на миг выходит наверх своей задачей, и
 * активити теста в этот момент не RESUMED (NoActivityResumedException). Приходит в первые ~1,5 с жизни процесса;
 * первый UI-тест прогона ждёт, пока процессу не станет [MS], следующие — нет.
 */
object ScreenReaderSettle {
    private const val MS = 5_000L

    fun await() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        if (!ctx.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled) return
        val age = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        if (age < MS) SystemClock.sleep(MS - age)
    }
}
