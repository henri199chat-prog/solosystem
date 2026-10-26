package com.ayman.solosystem.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.dungeon.Dungeon
import com.ayman.solosystem.domain.game.Raids
import com.ayman.solosystem.location.DungeonLive
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.Nav
import com.ayman.solosystem.ui.SysWhite
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs

private val GateBlue = Color(0xFF3FA9FF)

@Composable
fun DungeonScreen(repo: Repository, nav: Nav) {
    val st by repo.state.collectAsState()
    val ar = LocalArabic.current
    val ctx = LocalContext.current
    val live by DungeonLive.run.collectAsState()
    var perm by remember { mutableStateOf(DungeonLive.hasPermission(ctx)) }
    var denied by remember { mutableStateOf(false) }
    var chosen by remember { mutableIntStateOf(2) }
    val gates = Dungeon.gates(st)
    val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        perm = DungeonLive.hasPermission(ctx); denied = !perm
        if (perm) DungeonLive.start(ctx, gates[chosen])
    }
    Screen(tr("بوابة GPS", "GPS DUNGEON"), nav) {
        val r = live
        if (r == null) {
            SysPanel(Modifier.fillMaxWidth(), accent = GateBlue) {
                TitleBox(tr("اختر البوابة", "CHOOSE A GATE"), accent = GateBlue)
                Spacer(Modifier.height(6.dp))
                gates.forEachIndexed { i, g ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).thinBox(if (i == chosen) GateBlue else Sys.panelLine).clickable { Sfx.play(Sfx.S.SELECT); chosen = i }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlowText(Raids.rankLabel(g.rank), tech(26.sp), Modifier.width(36.dp), glow = if (i == chosen) GateBlue else Sys.dim)
                        Column(Modifier.weight(1f)) {
                            Label(tr("${fmtKm(g.meters)} كم • حد ${g.limitMin} د", "${fmtKm(g.meters)} km • limit ${g.limitMin} min"), color = Sys.text, size = 15, w = FontWeight.Bold)
                            Label(tr("${g.meters / Dungeon.WAYPOINT_M} نقاط عبور • +${Dungeon.xpFor(g.rank)} XP عند التصفية", "${g.meters / Dungeon.WAYPOINT_M} waypoints • +${Dungeon.xpFor(g.rank)} XP on clear"), size = 12)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Label(tr("GPS الهاتف فقط — بلا إنترنت وبلا خرائط. تحديث كل ~2 ث، ويتوقف فور الإنهاء. المسافة تُسجَّل في هدف الجري اليومي وتُضاف سعراتها للترياق.",
                    "Phone GPS only — no internet, no maps. Updates every ~2 s and stop as soon as you finish. The distance goes to today's RUN goal and its kcal to the Elixir."), size = 12)
                SafetyNote(tr("إحماء 5 دقائق بالمشي. انتبه للطريق والسيارات. ألم = توقّف.", "5-min walking warm-up. Watch the road and traffic. Pain = stop."))
                if (denied) Label(tr("لا يمكن فتح البوابة دون إذن الموقع. يمكنك منحه من الإعدادات.", "The gate can't open without location permission. You can grant it in Settings."), color = Sys.amber, size = 13)
                if (perm && !DungeonLive.gpsEnabled(ctx)) {
                    Label(tr("GPS متوقف على الهاتف.", "GPS is off on the phone."), color = Sys.amber, size = 13)
                    SysButton(tr("فتح إعدادات الموقع", "Open location settings"), Modifier.fillMaxWidth(), small = true, accent = Sys.dim) { runCatching { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
                }
                Spacer(Modifier.height(8.dp))
                SysButton(tr("ادخل البوابة", "Enter the gate"), Modifier.fillMaxWidth(), accent = GateBlue, sfx = Sfx.S.KEY) {
                    if (DungeonLive.hasPermission(ctx)) DungeonLive.start(ctx, gates[chosen])
                    else req.launch(buildList { add(android.Manifest.permission.ACCESS_FINE_LOCATION); add(android.Manifest.permission.ACCESS_COARSE_LOCATION); if (Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS) }.toTypedArray())
                }
            }
        } else LiveHud(repo, r)
        val past = st.dungeonRuns.takeLast(10).reversed()
        if (past.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("سجل البوابات", "Gate log"))
            past.forEach { x ->
                val pace = Dungeon.paceSecPerKm(x.meters, x.endMs - x.startMs)
                KeyValue("${fmtDay(x.day, ar)} • ${Raids.rankLabel(x.rank)} • ${fmtKm(x.meters)}" + tr(" كم", " km") + (pace?.let { " • ${mmss(it)}/" + tr("كم", "km") } ?: ""),
                    if (x.cleared) tr("صُفّيت +${x.xp}", "Cleared +${x.xp}") else tr("بلا عقاب", "No penalty"), if (x.cleared) Sys.green else Sys.dim)
            }
        }
    }
}

@Composable
private fun LiveHud(repo: Repository, r: com.ayman.solosystem.location.LiveRun) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(r.startMs) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val wp = (r.meters / Dungeon.WAYPOINT_M).toInt()
    var lastWp by remember(r.startMs) { mutableIntStateOf(0) }
    LaunchedEffect(wp) { if (wp > lastWp) { lastWp = wp; Sfx.play(if (r.meters >= r.targetM) Sfx.S.QUEST_COMPLETE else Sfx.S.PING) } }
    val el = ((now - r.startMs) / 1000).coerceAtLeast(0)
    val lim = r.limitMin * 60L
    val left = lim - el
    val gate = Dungeon.Gate(r.rank, r.targetM, r.limitMin)
    fun finish(pain: Boolean) {
        val f = DungeonLive.stop(ctx) ?: return
        val end = System.currentTimeMillis()
        repo.apply { s -> Dungeon.finish(s, gate, f.startMs, end, f.meters.toInt(), repo.today(), end, pain) }
        Sfx.play(if (!pain && f.meters >= f.targetM) Sfx.S.REWARD_RARE else Sfx.S.WIN_CLOSE)
    }
    SysPanel(Modifier.fillMaxWidth(), accent = GateBlue, pulse = true) {
        TitleBox(tr("بوابة الرتبة ${Raids.rankLabel(r.rank)}", "RANK ${Raids.rankLabel(r.rank)} GATE"), accent = GateBlue)
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) {
                val sw = 14.dp.toPx()
                val f = (r.meters / r.targetM).toFloat().coerceIn(0f, 1f)
                drawArc(Sys.panelLine.copy(alpha = 0.35f), -90f, 360f, false, style = Stroke(sw))
                drawArc(GateBlue, -90f, 360f * f, false, style = Stroke(sw, cap = StrokeCap.Round))
                // waypoint ticks
                val n = (r.targetM / Dungeon.WAYPOINT_M).coerceAtLeast(1)
                for (i in 1..n) {
                    val a = Math.toRadians(-90.0 + 360.0 * i / n)
                    val rr = size.minDimension / 2
                    val c = Offset(size.width / 2 + (rr * Math.cos(a)).toFloat(), size.height / 2 + (rr * Math.sin(a)).toFloat())
                    drawCircle(if (i <= wp) SysWhite else Sys.panelLine, 5.dp.toPx(), c)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlowText(fmtKm(r.meters.toInt()), tech(46.sp, SysWhite), glow = GateBlue)
                Label(tr("من ${fmtKm(r.targetM)} كم", "of ${fmtKm(r.targetM)} km"), size = 13)
                Label(tr("نقطة العبور $wp/${r.targetM / Dungeon.WAYPOINT_M}", "Waypoint $wp/${r.targetM / Dungeon.WAYPOINT_M}"), color = GateBlue, size = 13)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                GlowText("%02d:%02d".format(java.util.Locale.US, abs(left) / 60, abs(left) % 60), tech(28.sp, if (left >= 0) SysWhite else Sys.amber))
                Label(if (left >= 0) tr("الوقت المتبقي", "Time left") else tr("انتهى الوقت", "Time up"), size = 12)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                GlowText(Dungeon.paceSecPerKm(r.meters.toInt(), now - r.startMs)?.let { mmss(it) } ?: "--:--", tech(28.sp))
                Label(tr("الوتيرة /كم", "Pace /km"), size = 12)
            }
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(6.dp)) { drawRect(Sys.panelLine.copy(alpha = 0.3f)); drawRect(GateBlue, size = Size(size.width * (el.toFloat() / lim).coerceIn(0f, 1f), size.height)) }
        Label(when {
            r.gpsOff -> tr("GPS متوقف — فعّله لمتابعة القياس", "GPS off — turn it on to keep measuring")
            r.lastFixMs == null -> tr("جارٍ البحث عن إشارة GPS… (في الخارج أفضل)", "Searching for GPS… (works best outdoors)")
            else -> tr("GPS: دقة ±${r.accM?.toInt() ?: "?"} م • ${r.fixes} قراءة", "GPS: ±${r.accM?.toInt() ?: "?"} m • ${r.fixes} fixes")
        }, Modifier.fillMaxWidth(), color = if (r.gpsOff || r.lastFixMs == null) Sys.amber else Sys.green, size = 12, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        SysButton(tr("إنهاء البوابة", "Finish gate"), Modifier.fillMaxWidth(), accent = GateBlue, sfx = Sfx.S.YES) { finish(false) }
        Spacer(Modifier.height(6.dp))
        SysButton(tr("ألم — توقّف الآن (بلا عقاب)", "Pain — stop now (no penalty)"), Modifier.fillMaxWidth(), small = true, accent = Sys.red) { finish(true) }
    }
}

fun fmtKm(m: Int) = "%.2f".format(java.util.Locale.US, m / 1000.0)

