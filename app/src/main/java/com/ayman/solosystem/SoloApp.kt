package com.ayman.solosystem

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.data.StateStore
import com.ayman.solosystem.notify.Reminders

class SoloApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()
        val t0 = android.os.SystemClock.elapsedRealtime()
        com.ayman.solosystem.notify.Sfx.init(this)
        val t1 = android.os.SystemClock.elapsedRealtime()
        // loads on a background thread; reminders are (re)scheduled from onChanged once the real state is in (async since 1.2.1)
        repo = Repository(StateStore(filesDir)) { s -> Reminders.reschedule(this, s); com.ayman.solosystem.widget.SoloWidget.requestUpdate(this) }
        android.util.Log.i("SoloStartup", "sfxInit=${t1 - t0}ms repoInit=${android.os.SystemClock.elapsedRealtime() - t1}ms")
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SILENT, getString(R.string.channel_quests), NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(false) })
        nm.createNotificationChannel(NotificationChannel(CH_SOUND, getString(R.string.channel_quests_sound), NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
        val attrs = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        fun raw(id: Int) = android.net.Uri.parse("android.resource://$packageName/$id")
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, if (java.util.Locale.getDefault().language == "ar") "تنبيهات النظام" else "System alerts", NotificationManager.IMPORTANCE_HIGH).apply { setSound(raw(R.raw.sys_ping), attrs); enableVibration(true) })
        nm.createNotificationChannel(NotificationChannel(CH_NOTICE, if (java.util.Locale.getDefault().language == "ar") "إشعارات النظام" else "System notices", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(raw(R.raw.sys_notice), attrs); enableVibration(false) })
    }

    companion object {
        const val CH_SILENT = "quests_silent_v1"
        const val CH_SOUND = "quests_sound_v1"
        const val CH_ALERT = "system_alert_v1"
        const val CH_NOTICE = "system_notice_v1"
        lateinit var instance: SoloApp
            private set
    }
}
