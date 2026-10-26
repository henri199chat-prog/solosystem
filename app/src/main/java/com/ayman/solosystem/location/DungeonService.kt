package com.ayman.solosystem.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ayman.solosystem.MainActivity
import com.ayman.solosystem.R
import com.ayman.solosystem.SoloApp
import com.ayman.solosystem.domain.dungeon.Dungeon
import com.ayman.solosystem.domain.dungeon.Fix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Live state of the running gate (process-wide; the UI observes it, the service writes it). */
data class LiveRun(val rank: Int, val targetM: Int, val limitMin: Int, val startMs: Long, val meters: Double = 0.0, val fixes: Int = 0,
                   val accM: Float? = null, val lastFixMs: Long? = null, val gpsOff: Boolean = false)

object DungeonLive {
    private val _run = MutableStateFlow<LiveRun?>(null)
    val run: StateFlow<LiveRun?> = _run
    internal var tracker = Dungeon.Tracker()
    internal fun set(r: LiveRun?) { _run.value = r }
    fun hasPermission(ctx: Context) = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun gpsEnabled(ctx: Context) = runCatching { (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

    fun start(ctx: Context, gate: Dungeon.Gate) {
        tracker = Dungeon.Tracker()
        set(LiveRun(gate.rank, gate.meters, gate.limitMin, System.currentTimeMillis()))
        ContextCompat.startForegroundService(ctx, Intent(ctx, DungeonService::class.java).setAction(DungeonService.START))
    }
    /** Stops GPS and the service; returns the final run snapshot (null if none). */
    fun stop(ctx: Context): LiveRun? {
        val r = _run.value
        ctx.startService(Intent(ctx, DungeonService::class.java).setAction(DungeonService.STOP))
        set(null)
        return r
    }
}

/**
 * Foreground service (type=location) that reads the on-device GPS every ~2 s while a gate is active. No network, no map tiles;
 * stops itself (and the GPS) on finish. Visible ongoing notification while running.
 */
class DungeonService : Service(), LocationListener {
    companion object { const val START = "dungeon.start"; const val STOP = "dungeon.stop"; private const val NID = 4242 }
    private var lm: LocationManager? = null
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || DungeonLive.run.value == null || !DungeonLive.hasPermission(this)) { shutdown(); return START_NOT_STICKY }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        runCatching { ServiceCompat.startForeground(this, NID, notification(), type) }.onFailure { shutdown(); return START_NOT_STICKY }
        if (lm == null) {
            lm = getSystemService(LOCATION_SERVICE) as LocationManager
            runCatching { lm!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this, Looper.getMainLooper()) }
                .onFailure { DungeonLive.run.value?.let { DungeonLive.set(it.copy(gpsOff = true)) } }
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(l: Location) {
        val r = DungeonLive.run.value ?: return
        DungeonLive.tracker.add(Fix(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else 99f, System.currentTimeMillis()))
        DungeonLive.set(r.copy(meters = DungeonLive.tracker.meters, fixes = r.fixes + 1, accM = if (l.hasAccuracy()) l.accuracy else null, lastFixMs = System.currentTimeMillis(), gpsOff = false))
        val now = System.currentTimeMillis()
        if (now - lastNotified > 10_000) { lastNotified = now; runCatching { getSystemService(android.app.NotificationManager::class.java).notify(NID, notification()) } }
    }
    override fun onProviderDisabled(provider: String) { DungeonLive.run.value?.let { DungeonLive.set(it.copy(gpsOff = true)) } }
    override fun onProviderEnabled(provider: String) { DungeonLive.run.value?.let { DungeonLive.set(it.copy(gpsOff = false)) } }
    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

    private fun notification(): Notification {
        val r = DungeonLive.run.value
        val ar = (application as? SoloApp)?.repo?.state?.value?.settings?.arabic ?: true
        val pi = PendingIntent.getActivity(this, 42, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open", "dungeon"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val m = r?.meters?.toInt() ?: 0
        return NotificationCompat.Builder(this, SoloApp.CH_SILENT).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("[SYSTEM] " + if (ar) "بوابة GPS نشطة" else "GPS Gate active")
            .setContentText(if (ar) "$m / ${r?.targetM ?: 0} م" else "$m / ${r?.targetM ?: 0} m")
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).setCategory(NotificationCompat.CATEGORY_WORKOUT).build()
    }

    private fun shutdown() {
        runCatching { lm?.removeUpdates(this) }; lm = null
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }
    override fun onDestroy() { runCatching { lm?.removeUpdates(this) }; lm = null; super.onDestroy() }
}
