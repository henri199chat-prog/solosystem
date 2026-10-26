package com.ayman.solosystem.notify

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.ayman.solosystem.MainActivity
import com.ayman.solosystem.R
import com.ayman.solosystem.SoloApp
import com.ayman.solosystem.domain.model.AppState
import com.ayman.solosystem.domain.model.QuestStatus
import com.ayman.solosystem.domain.model.QuestType
import com.ayman.solosystem.domain.quest.Countdown
import com.ayman.solosystem.domain.nutrition.Nutrition
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Real local reminders via WorkManager one-time work (re-planned after every state change). */
object Reminders {
    private const val TAG = "solo_reminder"

    fun permissionGranted(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** True only if the user enabled reminders AND the OS allows notifications. */
    fun active(ctx: Context, s: AppState) = s.settings.remindersEnabled && permissionGranted(ctx)

    private fun msAt(day: LocalDate, h: Int, m: Int) = LocalDateTime.of(day, java.time.LocalTime.of(h, m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Volatile private var lastKey: String? = null

    /** v1.2.1: WorkManager enqueueing (and its first-use DB init) moved off the main thread; one ordered worker thread. */
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "SoloReminders").apply { priority = Thread.NORM_PRIORITY - 1 } }
    fun reschedule(ctx: Context, s: AppState, force: Boolean = false) {
        val app = ctx.applicationContext
        bg.execute { runCatching { rescheduleNow(app, s, force) } }
    }
    @Synchronized private fun rescheduleNow(ctx: Context, s: AppState, force: Boolean) {
        val key = "${s.settings.remindersEnabled}|${s.profile != null}|${s.settings.morningHour}:${s.settings.morningMinute}|${s.settings.eveningHour}:${s.settings.eveningMinute}|${s.settings.deadlineMinutes}|${LocalDate.now()}|${s.questIssuedAt[LocalDate.now().toEpochDay()]}|${s.nutrition.remindersEnabled}|${s.nutrition.trainHour}:${s.nutrition.trainMinute}|${s.sessions.count { it.finishedMs != null }}|${s.nutrition.kcalAdjust}|${s.plan?.difficultyLevel}"
        if (!force && key == lastKey) return
        lastKey = key
        val wm = try { WorkManager.getInstance(ctx) } catch (_: Exception) { return }
        if (!s.settings.remindersEnabled || s.profile == null) { wm.cancelAllWorkByTag(TAG); return }
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val st = s.settings
        val jobs = mutableListOf<Pair<String, Long>>()
        fun next(h: Int, m: Int): Long { val t = msAt(today, h, m); return if (t > now) t else msAt(today.plusDays(1), h, m) }
        jobs += "morning" to next(st.morningHour, st.morningMinute)
        jobs += "evening" to next(st.eveningHour, st.eveningMinute)
        val dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // ring window = issue time (persisted) → deadline; before issue, the morning notification time is when it will be issued
        val morningToday = msAt(today, st.morningHour, st.morningMinute)
        val start = Countdown.windowStart(s, today.toEpochDay(), dayStart, if (morningToday > now) morningToday else now)
        Countdown.alertTimes(start, Countdown.deadlineMs(dayStart, st.deadlineMinutes), now).forEach { (k, t) -> jobs += "ring_$k" to t }
        // tomorrow's ring alerts so the chain continues even if the app isn't opened (window starts at the morning notification)
        val tStart = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val tMorning = msAt(today.plusDays(1), st.morningHour, st.morningMinute).coerceAtMost(Countdown.deadlineMs(tStart, st.deadlineMinutes))
        if (jobs.none { it.first == "ring_half" }) Countdown.alertTimes(tMorning, Countdown.deadlineMs(tStart, st.deadlineMinutes), now).firstOrNull { it.first == "half" }?.let { jobs += "ring_half" to it.second }
        // Healing Elixir: next meal order today (chain re-plans after each one fires). Firm reminder only — never restriction.
        if (s.nutrition.remindersEnabled) {
            val ds = dayStart
            Nutrition.ordersFor(s, today.toEpochDay(), now, ds).firstOrNull { ds + it.minute * 60_000L > now + 30_000 }?.let { jobs += "meal" to ds + it.minute * 60_000L }
        }
        jobs.forEach { (kind, at) ->
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay((at - now).coerceAtLeast(1000), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("kind" to kind)).addTag(TAG).build()
            wm.enqueueUniqueWork("solo_$kind", ExistingWorkPolicy.REPLACE, req)
        }
    }

    fun show(ctx: Context, id: Int, title: String, body: String, s: AppState, alert: Boolean = false, open: String? = null) {
        if (!permissionGranted(ctx)) return
        val pi = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).apply { open?.let { putExtra("open", it) } }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val ch = when { s.settings.systemSounds -> if (alert) SoloApp.CH_ALERT else SoloApp.CH_NOTICE; s.settings.vibration -> SoloApp.CH_SOUND; else -> SoloApp.CH_SILENT }
        val n = NotificationCompat.Builder(ctx, ch).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pi).setAutoCancel(true)
            .setColor(0xFF2F8CFF.toInt()).setSilent(!s.settings.systemSounds && !s.settings.vibration).build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }

    fun test(ctx: Context, s: AppState) = show(ctx, 9, "[SYSTEM]", if (s.settings.arabic) "هذا إشعار تجريبي. التذكيرات تعمل." else "This is a test notification. Reminders work.", s)
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SoloApp
        val repo = app.repo
        val kind = inputData.getString("kind") ?: return Result.success()
        repo.refreshDay(issue = kind == "morning")
        val s = repo.state.value
        val ar = s.settings.arabic
        val today = repo.today()
        val pending = s.quests.count { it.day == today && it.type == QuestType.DAILY && !it.optional && it.status == QuestStatus.PENDING }
        val penalty = s.quests.any { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING && it.day == today }
        when (kind) {
            "morning" -> Reminders.show(applicationContext, 1, "[SYSTEM] " + (if (ar) "إشعار" else "NOTIFICATION"),
                if (ar) "وصلت المهمة اليومية. ${if (penalty) "مهمة عقاب بانتظارك اليوم." else "أهداف اليوم: $pending"}" else "Daily quest has arrived. ${if (penalty) "A penalty quest awaits today." else "Goals today: $pending"}" +
                    (if ((Nutrition.daysSinceWeighIn(s, today) ?: 7) >= 7) (if (ar) " سجّل وزنك الأسبوعي." else " Log your weekly weight.") else ""), s, alert = true).also {
                // v1.3.0: weekly private transformation photo (Monday, only once the user has started a photo log)
                if (java.time.LocalDate.ofEpochDay(today).dayOfWeek == java.time.DayOfWeek.MONDAY && s.transform.photos.isNotEmpty() &&
                    com.ayman.solosystem.domain.transform.Transform.reminderDue(s, today))
                    Reminders.show(applicationContext, 11, "[SYSTEM]", if (ar) "حان وقت صورة التحوّل الأسبوعية (خاصة، داخل التطبيق فقط)." else "Time for your weekly transformation photo (private, stays in the app).", s, open = "transform")
            }
            "evening" -> if (pending > 0) Reminders.show(applicationContext, 2, "[SYSTEM]", if (ar) "تبقّى $pending من أهداف المهمة اليومية." else "$pending daily goals remain.", s)
            "ring_half" -> if (Countdown.armed(s, today)) Reminders.show(applicationContext, 3, "[SYSTEM]", if (ar) "انقضى نصف وقت المهمة اليومية." else "Half of the daily quest time has passed.", s)
            "ring_ten" -> if (Countdown.armed(s, today)) Reminders.show(applicationContext, 4, "[SYSTEM] " + (if (ar) "تحذير" else "WARNING"), if (ar) "تبقّى 10٪ من الوقت. الفشل في إكمال المهمة اليومية سيؤدي إلى عقاب مناسب." else "10% of the time left. Failing the daily quest will result in an appropriate penalty.", s, alert = true)
            "meal" -> {
                val ds = repo.dayStartMs(today)
                val nowMin = ((System.currentTimeMillis() - ds) / 60000).toInt()
                Nutrition.ordersFor(s, today, System.currentTimeMillis(), ds).lastOrNull { it.minute <= nowMin + 1 && it.minute >= nowMin - 20 }?.let { o ->
                    val tot = Nutrition.totals(s, today)
                    val left = Nutrition.targetsFor(s, today)?.let { (it.kcal - tot.kcal).coerceAtLeast(0) } ?: 0
                    Reminders.show(applicationContext, 10, "[SYSTEM] " + (if (ar) "أمر الترياق" else "ELIXIR ORDER") + " — " + (if (ar) o.kind.ar else o.kind.en),
                        if (ar) "حان وقت الوجبة الآن: ~${o.kcal} سعرة • ${o.protein}غ بروتين. ${o.hint.ar}. المتبقي اليوم: $left سعرة." else "Meal time now: ~${o.kcal} kcal • ${o.protein} g protein. ${o.hint.en}. Remaining today: $left kcal.", s)
                }
            }
            "ring_end" -> {
                val issued = s.quests.lastOrNull { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING && it.templateId == "penalty" }
                if (issued != null && issued.day > today - 1) Reminders.show(applicationContext, 5, "[SYSTEM] " + (if (ar) "عقاب" else "PENALTY"), if (ar) "انتهى الوقت. ${issued.title.ar}" else "Time is up. ${issued.title.en}", s, alert = true)
            }
        }
        Reminders.reschedule(applicationContext, repo.state.value, force = true)
        return Result.success()
    }
}
