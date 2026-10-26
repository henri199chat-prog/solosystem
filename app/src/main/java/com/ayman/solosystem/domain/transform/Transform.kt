package com.ayman.solosystem.domain.transform

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*

/** Private transformation photos: metadata only lives in the state; image files stay in the app-private files dir (never MediaStore). */
object Transform {
    fun add(s: AppState, id: String, file: String, day: Long, now: Long): AppState {
        val w = s.bodyWeights.filter { it.day <= day }.maxByOrNull { it.day }?.kg
        val st = s.copy(transform = s.transform.copy(photos = (s.transform.photos + TransformPhoto(id, day, file, w)).sortedBy { it.day }))
        return GameEngine.hist(st, now, day, HistoryKind.PHOTO, Txt("صورة تحوّل جديدة (خاصة)", "New transformation photo (private)"))
    }
    /** Removes only the metadata entry; the caller deletes the private file after the user confirmed. */
    fun remove(s: AppState, id: String) = s.copy(transform = s.transform.copy(photos = s.transform.photos.filterNot { it.id == id }))
    fun setLock(s: AppState, on: Boolean) = s.copy(transform = s.transform.copy(lock = on))
    fun setReminder(s: AppState, on: Boolean) = s.copy(transform = s.transform.copy(reminder = on))
    fun first(s: AppState) = s.transform.photos.minByOrNull { it.day }
    fun latest(s: AppState) = s.transform.photos.maxByOrNull { it.day }
    /** Weekly reminder is due when reminders are on and the newest photo is ≥ 7 days old (or there is none yet). */
    fun reminderDue(s: AppState, today: Long) = s.transform.reminder && (latest(s)?.let { today - it.day >= 7 } ?: true)
}
