package ru.kost.ruvoice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.speech.tts.TextToSpeech
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.TtsSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Обёртка binder из SileroTtsService.onBind (заготовка следующего куска). */
@RunWith(AndroidJUnit4::class)
class BinderWrapTest {
    private val desc = "android.speech.tts.ITextToSpeechService"

    /** Свой процесс (проба голоса) получает настоящий stub. 0.18.0–0.18.1: у обёртки дескриптор null,
     * на Android 6 queryLocalInterface падал NPE в mDescriptor.equals, на новых отдавал null. */
    @Test fun inProcessGetsLocalStub() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val got = CountDownLatch(1); var binder: IBinder? = null
        val conn = object : ServiceConnection {
            override fun onServiceConnected(n: ComponentName?, b: IBinder?) { binder = b; got.countDown() }
            override fun onServiceDisconnected(n: ComponentName?) {}
        }
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).setPackage(ctx.packageName)
        assertTrue(ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("bind timeout", got.await(30, TimeUnit.SECONDS))
            assertNotNull(binder!!.queryLocalInterface(desc))
        } finally { ctx.unbindService(conn) }
    }

    /** speak разбирается со спанами, а режим очереди читается следом: заготовка видит TtsSpan, как сам запрос. */
    @Test fun speakTextKeepsSpans() {
        val text = SpannableString("звоните 123")
        text.setSpan(TtsSpan.TelephoneBuilder("123").build(), 8, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val p = Parcel.obtain()
        try {
            // как ITextToSpeechService.Stub.Proxy.speak
            p.writeInterfaceToken(desc); p.writeStrongBinder(Binder()); p.writeInt(1)
            TextUtils.writeToParcel(text, p, 0); p.writeInt(TextToSpeech.QUEUE_ADD)
            p.setDataPosition(0)
            val got = SileroTtsService.speakText(p)
            assertEquals(TextToSpeech.QUEUE_ADD, p.readInt())
            assertEquals("звоните 123", got.toString())
            val spans = (got as Spanned).getSpans(0, got.length, TtsSpan::class.java)
            assertEquals(TtsSpan.TYPE_TELEPHONE, spans.single().type)
        } finally { p.recycle() }
    }
}
