package com.ayman.solosystem.data

import com.ayman.solosystem.domain.model.AppState
import kotlinx.serialization.json.Json
import java.io.File

/** Single-file JSON persistence with atomic replace (write tmp → fsync → rename). Pure JVM, testable. */
class StateStore(private val dir: File) {
    private val file get() = File(dir, "solo_state.json")
    private val backup get() = File(dir, "solo_state.bak.json")

    companion object {
        const val SCHEMA = 4
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; allowSpecialFloatingPointValues = true }
        fun encode(s: AppState): String = json.encodeToString(AppState.serializer(), s.copy(schemaVersion = SCHEMA))
        fun decode(text: String): AppState = migrate(json.decodeFromString(AppState.serializer(), text))
        /** 1→2: defaults only. 2→3 (1.2.1): GOAL strength tracks raised to the rank-E floor (never lowered); stored quests untouched,
         *  so today's already-issued quest (completed or not) keeps its target and new targets start with the next daily quest. */
        /** 3→4 (1.3.0): additive fields only (guild, shadows, dungeonRuns, bosses, transform) — defaults, nothing rewritten. */
        fun migrate(s: AppState): AppState {
            var x = s
            if (x.schemaVersion < 3) x = com.ayman.solosystem.domain.quest.GoalPlan.liftToFloor(x)
            return x.copy(schemaVersion = SCHEMA)
        }
    }

    @Synchronized fun load(): AppState {
        for (f in listOf(file, backup)) {
            if (!f.exists()) continue
            try { return decode(f.readText()) } catch (_: Exception) { }
        }
        return AppState()
    }

    @Synchronized fun save(s: AppState) {
        dir.mkdirs()
        val tmp = File(dir, "solo_state.tmp")
        java.io.FileOutputStream(tmp).use { out -> out.write(encode(s).toByteArray(Charsets.UTF_8)); out.fd.sync() }
        if (file.exists()) file.copyTo(backup, overwrite = true)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    @Synchronized fun clear() { file.delete(); backup.delete() }
}
