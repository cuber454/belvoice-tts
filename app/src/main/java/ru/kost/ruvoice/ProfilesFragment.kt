package ru.kost.ruvoice

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Вкладка «Профили»: выбрать, добавить, переименовать, удалить. Своих полей в Prefs у неё нет, поэтому не
 * PageFragment. Выбор, добавление и удаление выбранного меняют prefs целиком — окно пересоздаётся
 * (SettingsActivity.restartForProfile), как после импорта.
 */
class ProfilesFragment : Fragment(R.layout.fragment_profiles) {
    private val profiles by lazy { Profiles(requireContext()) }
    private val host get() = activity as SettingsActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.markHeadings()
        view.findViewById<Button>(R.id.profileAdd).setOnClickListener {
            nameDialog(R.string.profile_add_title, "", null, getString(R.string.profile_add_hint)) { name ->
                host.saveAllVisiblePages()
                val p = profiles.add(name)
                host.restartForProfile(getString(R.string.profile_switched, p.name))
            }
        }
        fill(view)
    }

    private fun fill(view: View) {
        val list = view.findViewById<RadioGroup>(R.id.profilesList)
        list.removeAllViews()
        val active = profiles.active().id
        val binds = profiles.binds()
        for (p in profiles.list()) {
            val row = layoutInflater.inflate(R.layout.item_profile, list, false)
            val voice = binds.entries.firstOrNull { it.value == p.id }?.key
            row.findViewById<RadioButton>(R.id.profileName).apply {
                text = p.name
                // TalkBack на выборе профиля сразу слышит и привязку
                contentDescription = voice?.let { getString(R.string.profile_radio_bound, p.name, Speaker.label(it)) }
                isChecked = p.id == active
                setOnClickListener {
                    if (p.id == profiles.active().id) return@setOnClickListener
                    host.saveAllVisiblePages()
                    profiles.switchTo(p.id)
                    host.restartForProfile(getString(R.string.profile_switched, p.name))
                }
            }
            // отдельной кнопки «Сохранить» нет: правки уходят в выбранный профиль сами (Profiles.switchTo, snapshot)
            row.findViewById<View>(R.id.profileAutosave).visibility = if (p.id == active) View.VISIBLE else View.GONE
            // TalkBack: у каждой строки свои кнопки — без имени профиля их не различить
            row.findViewById<Button>(R.id.profileRename).apply {
                contentDescription = getString(R.string.profile_rename_named, p.name)
                setOnClickListener {
                    nameDialog(R.string.profile_rename_title, p.name, p.id, null) { name -> profiles.rename(p.id, name); fill(view) }
                }
            }
            // TalkBack: имя профиля в подписи, как у «Переименовать»; видимый текст входит в неё (Voice Access)
            row.findViewById<Button>(R.id.profileBind).apply {
                text = voice?.let { getString(R.string.profile_bound, Speaker.label(it)) } ?: getString(R.string.profile_bind)
                contentDescription = voice?.let { getString(R.string.profile_bound_named, p.name, Speaker.label(it)) }
                    ?: getString(R.string.profile_bind_named, p.name)
                setOnClickListener { bindDialog(p, voice) { fill(view) } }
            }
            row.findViewById<Button>(R.id.profileDelete).apply {
                visibility = if (p.main) View.GONE else View.VISIBLE
                contentDescription = getString(R.string.profile_delete_named, p.name)
                setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.profile_delete_title)
                        .setMessage(getString(R.string.profile_delete_confirm, p.name))
                        .setPositiveButton(R.string.delete) { _, _ ->
                            if (p.id == profiles.active().id) {
                                // удаляем выбранный — включится «Основной», правки вкладок уходят в удаляемый
                                host.saveAllVisiblePages()
                                profiles.delete(p.id)
                                host.restartForProfile(getString(R.string.profile_switched, profiles.active().name))
                            } else {
                                profiles.delete(p.id); fill(view)
                                host.snack(getString(R.string.profile_deleted))
                            }
                        }
                        .setNegativeButton(R.string.cancel, null).show()
                }
            }
            list.addView(row)
        }
    }

    /** «Привязать к голосу»: «Не привязан» и голоса; голос другого профиля подписан, выбор забирает его себе. */
    private fun bindDialog(p: Profiles.Profile, current: String?, done: () -> Unit) {
        val ctx = requireContext()
        val installed = Speaker.names(SileroModels.speakers(ctx), Packs.installed(ctx.filesDir))
        // привязан голос удалённого пака — остаётся в списке, помеченный
        val voices = if (current != null && current !in installed) installed + current else installed
        if (voices.isEmpty()) { host.snack(getString(R.string.profile_bind_no_voices)); return }
        val binds = profiles.binds()
        val names = profiles.list().associate { it.id to it.name }
        val labels = listOf(getString(R.string.profile_bind_none)) + voices.map { v ->
            val owner = binds[v]?.takeIf { it != p.id }?.let { names[it] }
            when {
                owner != null -> getString(R.string.profile_bind_taken, Speaker.label(v), owner)
                v !in installed -> getString(R.string.profile_bind_missing, Speaker.label(v))
                else -> Speaker.label(v)
            }
        }
        var picked = current?.let { voices.indexOf(it) + 1 } ?: 0
        MaterialAlertDialogBuilder(ctx).setTitle(getString(R.string.profile_bind_title, p.name))
            .setSingleChoiceItems(labels.toTypedArray(), picked) { _, i -> picked = i }
            .setPositiveButton(R.string.save) { _, _ ->
                val v = if (picked == 0) null else voices[picked - 1]
                if (v == current) return@setPositiveButton
                profiles.bind(p.id, v); done()
                // строки пересобраны, фокус TalkBack с кнопки ушёл — итог словами
                host.snack(v?.let { getString(R.string.profile_bound_done, p.name, Speaker.label(it)) } ?: getString(R.string.profile_unbound, p.name))
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /** Имя профиля: непустое и не занятое другим профилем (без учёта регистра). */
    private fun nameDialog(titleRes: Int, initial: String, id: String?, message: String?, onOk: (String) -> Unit) {
        val ctx = requireContext()
        val layout = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.profile_name_hint)
            val pad = (12 * resources.displayMetrics.density).toInt(); setPadding(pad, pad / 2, pad, 0)
        }
        val field = TextInputEditText(layout.context).apply { setText(initial); setSelection(initial.length) }
        layout.addView(field)
        var posButton: Button? = null
        fun validate() {
            val name = field.text.toString().trim()
            layout.error = if (name.isNotEmpty() && !profiles.nameFree(name, id)) getString(R.string.profile_name_taken) else null
            posButton?.isEnabled = name.isNotEmpty() && name != initial && layout.error == null
        }
        field.doAfterTextChanged { validate() }
        val b = MaterialAlertDialogBuilder(ctx).setTitle(titleRes).setView(layout)
        if (message != null) b.setMessage(message)
        val dialog = b.setPositiveButton(R.string.save) { _, _ -> onOk(field.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null).create()
        dialog.setOnShowListener { posButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE); validate() }
        field.submits { posButton }
        dialog.show()
    }
}
