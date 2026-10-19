package ru.kost.ruvoice

import org.junit.Assert.*
import org.junit.Test

class ProfileDataTest {
    private val snap: Map<String, Any> = mapOf("voice" to "baya", "sr" to 24000, "volume" to 1.5f, "idle_on" to false,
        "system_dicts_at" to 5L, "sr_force" to setOf("a", "b"), "rules_off" to "")

    @Test fun typesSurviveRoundTrip() {
        val back = ProfileData.decode(org.json.JSONObject(ProfileData.encode(snap).toString()))
        assertEquals(snap, back)
        assertTrue(back["sr"] is Int); assertTrue(back["volume"] is Float); assertTrue(back["system_dicts_at"] is Long)
    }

    @Test fun serviceAndScreenStateAreNotProfile() {
        assertTrue(ProfileData.isProfileKey("voice")); assertTrue(ProfileData.isProfileKey("stress_off")); assertTrue(ProfileData.isProfileKey("en_engine"))
        for (k in listOf("recent_callers", "setup_shown", "system_dicts_at", "sr_force", "dict_cur_stress", "dict_scope_replace", "preview_text"))
            assertFalse(k, ProfileData.isProfileKey(k))
    }

    @Test fun exportCarriesProfilesAndOldFilesHaveNone() {
        val json = SettingsJson.build(mapOf("voice" to "baya"), emptyMap(), emptyMap(), emptySet(), emptySet(), emptyMap(),
            listOf(SettingsJson.ProfileEntry("Основной", true, mapOf("voice" to "xenia")), SettingsJson.ProfileEntry("Книги", false, snap)), "Книги")
        val parsed = SettingsJson.parse(json)
        assertEquals("Книги", parsed.activeProfile)
        assertEquals(listOf("Основной" to true, "Книги" to false), parsed.profiles!!.map { it.name to it.main })
        assertEquals(snap, parsed.profiles!![1].prefs)
        assertEquals("baya", parsed.prefs["voice"])
        val old = SettingsJson.parse("""{"app":"ruvoice","prefs":{"voice":"baya"}}""")
        assertNull(old.profiles); assertNull(old.activeProfile)
    }

    @Test fun voiceBindingSurvivesExport() {
        val json = SettingsJson.build(emptyMap(), emptyMap(), emptyMap(), emptySet(), emptySet(), emptyMap(),
            listOf(SettingsJson.ProfileEntry("Основной", true, emptyMap()), SettingsJson.ProfileEntry("Книги", false, snap, "baya")), "Основной")
        assertEquals(listOf(null, "baya"), SettingsJson.parse(json).profiles!!.map { it.voice })
    }
}
