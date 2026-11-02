package com.zohandam.character

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

data class BackupData(val anchor: LocalDate, val enabled: Boolean, val theme: Int,
                      val times: List<Int>, val traits: List<Trait>) {
    fun checked(): BackupData {
        require(anchor.dayOfWeek == DayOfWeek.SUNDAY) { "The starting date must be a Sunday." }
        require(theme in 0..2) { "The theme is invalid." }
        require(times.size in 1..Store.MAX_REMINDERS && times.all { it in 0..1439 } &&
            times.distinct().size == times.size) { "Choose 1–${Store.MAX_REMINDERS} distinct reminder times." }
        require(traits.size == 12) { "The backup must contain twelve traits." }
        require(traits.all { it.name.isNotBlank() && it.name.length <= 100 &&
            it.definition.isNotBlank() && it.definition.length <= 4000 }) { "A trait name or definition is invalid." }
        return copy(times = times.sorted(), traits = traits.map { Trait(it.name.trim(), it.definition.trim()) })
    }
}

object Backup {
    const val MAX_BYTES = 1024 * 1024

    fun encode(data: BackupData): String {
        val valid = data.checked()
        val times = JSONArray()
        valid.times.forEach { times.put(it) }
        return JSONObject().put("format", "character-backup").put("schemaVersion", 1)
            .put("appVersion", BuildConfig.VERSION_NAME).put("startingSunday", valid.anchor.toString())
            .put("remindersEnabled", valid.enabled).put("theme", valid.theme)
            .put("reminderMinutes", times).put("traits", traitArray(valid.traits)).toString(2)
    }

    fun decode(text: String): BackupData {
        require(text.length <= MAX_BYTES) { "The backup is too large." }
        val root = JSONObject(text)
        require(root.optString("format") == "character-backup" && root.get("schemaVersion") == 1) {
            "This is not a supported Character backup."
        }
        val rawTimes = root.getJSONArray("reminderMinutes")
        require(rawTimes.length() in 1..Store.MAX_REMINDERS) { "The reminder count is invalid." }
        val rawTraits = root.getJSONArray("traits")
        require(rawTraits.length() == 12) { "The backup must contain twelve traits." }
        return BackupData(
            LocalDate.parse(root.get("startingSunday") as? String ?: error("The starting date is invalid.")),
            root.get("remindersEnabled") as? Boolean ?: error("The reminder setting is invalid."),
            root.get("theme") as? Int ?: error("The theme is invalid."),
            List(rawTimes.length()) { rawTimes.get(it) as? Int ?: error("A reminder time is invalid.") },
            List(rawTraits.length()) { i ->
                val item = rawTraits.getJSONObject(i)
                Trait(item.get("name") as? String ?: error("A trait name is invalid."),
                    item.get("definition") as? String ?: error("A trait definition is invalid."))
            }
        ).checked()
    }

    fun traitArray(traits: List<Trait>): JSONArray = JSONArray().apply {
        traits.forEach { put(JSONObject().put("name", it.name).put("definition", it.definition)) }
    }
}
