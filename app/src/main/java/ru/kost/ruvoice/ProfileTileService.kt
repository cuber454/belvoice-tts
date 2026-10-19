package ru.kost.ruvoice

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat

/**
 * Плитка «Профиль RuVoice» в шторке: подпись — выбранный профиль. Нажатие: профилей два — переключает на
 * другой, больше — список на выбор, один — открывает вкладку «Профили». Выбор объявляется всплывающим
 * сообщением (TalkBack его читает), при запрещённых уведомлениях — объявлением доступности. Открытое окно
 * настроек само перечитает поля (ProfileWatch).
 */
@RequiresApi(24)
class ProfileTileService : TileService() {
    private val profiles by lazy { Profiles(this) }

    override fun onStartListening() = update()

    override fun onClick() {
        val list = profiles.list()
        when {
            list.size < 2 -> openProfiles()
            list.size == 2 -> pick(profiles.next())
            else -> {
                val active = profiles.active().id
                val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(R.string.tile_pick)
                    .setSingleChoiceItems(list.map { it.name }.toTypedArray(), list.indexOfFirst { it.id == active }) { d, i ->
                        d.dismiss(); pick(list[i])
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .create()
                showDialog(dialog)
            }
        }
    }

    private fun pick(p: Profiles.Profile) {
        profiles.switchTo(p.id)
        update()
        val msg = getString(R.string.profile_switched, p.name)
        // из шторки мы в фоне: при запрещённых уведомлениях Android 13 такой тост глушит молча
        // («Suppressing toast … by user request») — тогда объявление чтецу. С задержкой: сразу за нажатием чтец
        // читает новое состояние плитки и обрывает объявление (Jieshuo — через 27 мс, звука не было)
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        else android.os.Handler(mainLooper).postDelayed({
            getSystemService(AccessibilityManager::class.java)?.takeIf { it.isEnabled }?.sendAccessibilityEvent(
                @Suppress("DEPRECATION") AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                    packageName = this@ProfileTileService.packageName; className = javaClass.name; text.add(msg)
                })
        }, ANNOUNCE_DELAY_MS)
    }

    private fun openProfiles() {
        val intent = Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_TAB, SettingsActivity.TAB_PROFILES)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }

    private fun update() {
        val tile = qsTile ?: return
        val name = profiles.active().name
        tile.icon = Icon.createWithResource(this, R.drawable.ic_profile)
        // до Android 10 второй строки нет — имя профиля вместо названия
        if (Build.VERSION.SDK_INT >= 29) { tile.label = getString(R.string.tile_label); tile.subtitle = name } else tile.label = name
        tile.contentDescription = getString(R.string.tile_label) + ": " + name
        tile.state = if (profiles.list().size > 1) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private companion object { const val ANNOUNCE_DELAY_MS = 600L }
}
