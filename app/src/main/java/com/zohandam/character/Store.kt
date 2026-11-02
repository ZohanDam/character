package com.zohandam.character

import android.content.Context
import org.json.JSONArray
import java.time.LocalDate

data class Trait(val name: String, val definition: String)

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("character", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains("anchor")) {
            prefs.edit().putString("anchor", Schedule.sunday(LocalDate.now()).toString()).apply()
        }
    }

    val anchor: LocalDate get() = LocalDate.parse(prefs.getString("anchor", null))
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    var theme: Int
        get() = prefs.getInt("theme", 0) // 0 system, 1 light, 2 dark
        set(value) { prefs.edit().putInt("theme", value).apply() }

    var times: List<Int>
        get() {
            val raw = prefs.getString("times", "480") ?: "480"
            return raw.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0..1439 }.distinct().sorted()
        }
        set(value) {
            val clean = value.filter { it in 0..1439 }.distinct().sorted()
            require(clean.isNotEmpty() && (clean.size <= MAX_REMINDERS || clean.size < times.size))
            prefs.edit().putString("times", clean.joinToString(",")).apply()
        }

    fun traits(): List<Trait> {
        val saved = prefs.getString("traits", null) ?: return defaults
        return runCatching {
            val array = JSONArray(saved)
            require(array.length() == defaults.size)
            List(array.length()) { i ->
                val entry = array.getJSONObject(i)
                Trait(entry.getString("name"), entry.getString("definition"))
            }
        }.getOrElse { defaults }
    }

    fun saveTrait(index: Int, trait: Trait) {
        require(trait.name.isNotBlank() && trait.definition.isNotBlank())
        val all = traits().toMutableList()
        all[index] = trait
        prefs.edit().putString("traits", Backup.traitArray(all).toString()).apply()
    }

    fun backup(): BackupData = BackupData(anchor, enabled, theme, times, traits()).checked()

    fun restore(data: BackupData) {
        val valid = data.checked()
        check(prefs.edit().putString("anchor", valid.anchor.toString()).putBoolean("enabled", valid.enabled)
            .putInt("theme", valid.theme).putString("times", valid.times.joinToString(","))
            .putString("traits", Backup.traitArray(valid.traits).toString()).commit()) { "Could not save the imported settings." }
    }

    companion object {
        const val MAX_REMINDERS = 24
        val defaults = listOf(
            Trait("Self-Control", "The ability to govern your physical desires, emotions, and immediate impulses rather than being controlled by them."),
            Trait("Order", "Intentional organization of your physical environment, time, and daily priorities so everything has its proper place and purpose."),
            Trait("Prudence", "Practical wisdom and discernment; the habit of pausing to weigh consequences and seek wisdom before speaking or acting."),
            Trait("Diligence", "Steadfast, focused, and energetic effort applied to your most important responsibilities without giving in to laziness or distraction."),
            Trait("Stewardship", "Careful, responsible management of the time, money, energy, and talents entrusted to you, avoiding waste so you can give generously to others."),
            Trait("Integrity", "Complete alignment between your values, words, and actions; being honest, fair to everyone, and faithful to your promises even when no one is watching."),
            Trait("Courage", "Moral and mental strength to stand up for what is right, face uncomfortable truths, and have difficult conversations despite fear or discomfort."),
            Trait("Humility", "Freedom from pride and arrogance; valuing others above yourself, listening more than speaking, and staying teachable when corrected."),
            Trait("Kindness", "Active goodwill, warmth, and compassion toward others; looking for practical ways to lift people up and make their day lighter."),
            Trait("Forgiveness (or Grace)", "Bearing patiently with people's flaws in the moment, and consciously releasing resentment, bitterness, and the desire to get even when wronged."),
            Trait("Gratitude", "A conscious posture of appreciation and thankfulness toward God and others for what you have, refusing to indulge in complaining or entitlement."),
            Trait("Endurance", "Steadfastness through hardship, fatigue, delay, and discouragement; continuing in what is right when progress is slow or results are not immediate.")
        )
    }
}
