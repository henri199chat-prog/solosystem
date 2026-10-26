package com.ayman.solosystem.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.progression.Stats
import com.ayman.solosystem.domain.rewards.RewardCatalog
import com.ayman.solosystem.domain.training.Readiness
import com.ayman.solosystem.domain.training.ReadinessAdvice
import com.ayman.solosystem.domain.training.Tiers
import com.ayman.solosystem.ui.Nav
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*

@Composable
fun StatusScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    val pal = LocalPalette.current
    val today = repo.today()
    val p = s.profile ?: return
    val li = Ledger.level(s)
    val checkIn = s.checkIns.firstOrNull { it.day == today }
    val readiness = checkIn?.let { Readiness.score(it) }
    val title = s.equippedTitle?.let { RewardCatalog.get(it)?.name } ?: Leveling.titleFor(li.level)
    val rank = Leveling.rankFor(li.level)
    val emblem = RewardCatalog.get(s.equippedEmblem)
    Screen(tr("الحالة", "Status"), nav) {
        V13Banners(s, today, ar, nav)
        SysPanel(Modifier.fillMaxWidth(), contentKey = li.level) {
            TitleBox(tr("الحالة", "STATUS"))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlowText("${li.level}", tech(52.sp))
                    BasicText("LEVEL", style = tech(14.sp, Sys.dim))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    KeyValue(tr("الاسم", "NAME"), p.name)
                    KeyValue(tr("المهنة", "JOB"), tr("لاعب", "Player") + " • " + tr("رتبة", "Rank") + " ${if (ar) rank.ar else rank.label}")
                    KeyValue(tr("اللقب", "TITLE"), title.get(ar), pal.secondary)
                    KeyValue(tr("رتبة التدريب", "TRAINING TIER"), Tiers.all[s.activeTier].let { if (ar) it.ar else it.label })
                }
                emblem?.let { EmblemIcon(it.shape, androidx.compose.ui.graphics.Color(it.primary), size = 44.dp) }
            }
            Spacer(Modifier.height(10.dp))
            StatBar("XP", li.xpInto.toFloat(), li.xpNeeded.toFloat(), pal.secondary, valueText = if (li.isMax) "MAX" else "${li.xpInto}/${li.xpNeeded}")
            Spacer(Modifier.height(8.dp))
            StatBar(tr("HP • الجاهزية", "HP • Readiness"), (readiness ?: 0).toFloat(), 100f, Sys.green, valueText = readiness?.let { "$it/100" } ?: tr("سجّل اليوم", "Log today"))
            Spacer(Modifier.height(8.dp))
            val cons = Stats.consistency(s, today)
            StatBar(tr("MP • الالتزام (28 يومًا)", "MP • Consistency (28 d)"), (cons ?: 0).toFloat(), 100f, pal.primary, valueText = cons?.let { "$it%" } ?: "—")
            Spacer(Modifier.height(8.dp))
            val fatigue = checkIn?.let { ((it.soreness - 1) * 25).coerceIn(0, 100) }
            StatBar(tr("الإرهاق", "FATIGUE"), (fatigue ?: 0).toFloat(), 100f, Sys.amber, valueText = fatigue?.let { "$it" } ?: "—")
            Spacer(Modifier.height(12.dp))
            Divider()
            AttributesGrid(s, repo, today)
            Divider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KeyStat(tr("الذهب", "Gold"), "${Ledger.goldBalance(s)}", Sys.gold)
                KeyStat(tr("السلسلة", "Streak"), "${Stats.streak(s, today)}", pal.secondary)
                KeyStat(tr("الأفضل", "Best"), "${Stats.bestStreak(s, today)}", pal.secondary)
                KeyStat(tr("خبرة مؤقتة", "Pending XP"), "${Stats.bonusPending(s)}", Sys.green)
            }
        }
        CheckInPanel(s, repo, today, checkIn)
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("اليوم", "Today"))
            val dq = s.quests.filter { it.day == today && it.type == QuestType.DAILY }
            KeyValue(tr("المهام اليومية", "Daily quests"), "${dq.count { it.status == QuestStatus.COMPLETED }}/${dq.size}")
            s.plan?.let { pl -> val dp = com.ayman.solosystem.domain.training.PlanGenerator.dayPlan(pl, today); KeyValue(tr("التدريب", "Training"), if (ar) dp.type.ar else dp.type.en) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SysButton(tr("المهام", "Quests"), Modifier.weight(1f), small = true) { nav.go("quests") }
                SysButton(tr("التدريب", "Training"), Modifier.weight(1f), small = true) { nav.go("training") }
            }
        }
    }
}

@Composable private fun KeyStat(k: String, v: String, c: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { GlowText(v, tech(20.sp), glow = c); Label(k, size = 12) }
}

@Composable
private fun AttributesGrid(s: AppState, repo: Repository, today: Long) {
    val pts = Ledger.pointsAvailable(s)
    val a = s.allocated
    val rows = listOf(Triple(Attr.STR, tr("القوة", "STR"), a.str), Triple(Attr.AGI, tr("الرشاقة", "AGI"), a.agi), Triple(Attr.VIT, tr("الحيوية", "VIT"), a.vit),
        Triple(Attr.END, tr("التحمل", "END"), a.end), Triple(Attr.DISC, tr("الانضباط", "DISC"), a.disc))
    Column(Modifier.fillMaxWidth()) {
        rows.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { (attr, name, v) ->
                    Row(Modifier.weight(1f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("$name: ", style = body(15.sp, FontWeight.Bold))
                        GlowText("${10 + v}", tech(18.sp))
                        Spacer(Modifier.width(6.dp))
                        if (pts > 0) SysButton("+", small = true) { repo.set { GameEngine.allocate(it, attr, today, repo.now()) } }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Label(tr("النقاط المتاحة: ", "Available points: "), color = Sys.text, size = 14)
            GlowText("$pts", tech(16.sp))
        }
        Spacer(Modifier.height(6.dp))
        Label(tr("السمات رموز لعب فقط — لا تغيّر أحمال التدريب.", "Attributes are game symbols only — they never change training loads."), Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp, start = 2.dp, end = 2.dp), size = 11)
    }
}

@Composable
private fun CheckInPanel(s: AppState, repo: Repository, today: Long, existing: CheckIn?) {
    var sleep by remember(existing) { mutableFloatStateOf(existing?.sleepHours ?: s.profile?.sleepHours ?: 7f) }
    var energy by remember(existing) { mutableIntStateOf(existing?.energy ?: 3) }
    var sore by remember(existing) { mutableIntStateOf(existing?.soreness ?: 2) }
    var pain by remember(existing) { mutableIntStateOf(existing?.pain ?: 0) }
    SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false, contentKey = existing) {
        SectionHeader(tr("تسجيل الجاهزية اليومي", "Daily readiness check-in"))
        Stepper(tr("ساعات النوم", "Sleep hours"), "%.1f".format(java.util.Locale.US, sleep), onMinus = { sleep = (sleep - 0.5f).coerceAtLeast(0f) }, onPlus = { sleep = (sleep + 0.5f).coerceAtMost(14f) })
        Stepper(tr("الطاقة (1–5)", "Energy (1–5)"), "$energy", onMinus = { energy = (energy - 1).coerceAtLeast(1) }, onPlus = { energy = (energy + 1).coerceAtMost(5) })
        Stepper(tr("ألم العضلات (1–5)", "Soreness (1–5)"), "$sore", onMinus = { sore = (sore - 1).coerceAtLeast(1) }, onPlus = { sore = (sore + 1).coerceAtMost(5) })
        Label(tr("ألم في مفصل أو إصابة", "Joint pain / injury"), color = Sys.text, size = 14)
        com.ayman.solosystem.ui.FlowRowCompat(Modifier.fillMaxWidth()) {
            listOf(tr("لا يوجد", "None"), tr("خفيف", "Mild"), tr("متوسط", "Moderate"), tr("شديد", "Severe")).forEachIndexed { i, t -> Chip(t, pain == i) { pain = i } }
        }
        val preview = CheckIn(today, sleep, sore, energy, pain)
        val adv = Readiness.advice(preview)
        Label(tr("الجاهزية المتوقعة: ", "Estimated readiness: ") + Readiness.score(preview) + " — " + when (adv) {
            ReadinessAdvice.FULL -> tr("تدريب كامل", "Full session")
            ReadinessAdvice.REDUCED -> tr("حصة مخففة", "Reduced session")
            ReadinessAdvice.RECOVERY_ONLY -> tr("تعافٍ فقط", "Recovery only")
        }, color = if (adv == ReadinessAdvice.FULL) Sys.green else Sys.amber, size = 13)
        Spacer(Modifier.height(6.dp))
        SysButton(if (existing == null) tr("حفظ التسجيل", "Save check-in") else tr("تحديث التسجيل", "Update check-in"), Modifier.fillMaxWidth()) {
            repo.apply { st -> GameEngine.checkIn(st, preview, today, repo.now()) }
        }
        Label(tr("تقدير ذاتي وليس قياسًا طبيًا. إن كان لديك ألم في الصدر أو دوخة، توقف واطلب المساعدة.", "A self-estimate, not a medical measure. Chest pain or dizziness: stop and seek help."), size = 11)
    }
}

/** v1.3.0: compact one-line System alerts — weekly boss alive / shadows ready for extraction. Tapping opens the screen. */
@Composable
private fun V13Banners(s: AppState, today: Long, ar: Boolean, nav: Nav) {
    val boss = com.ayman.solosystem.domain.boss.Boss.current(s, today)
    val ext = com.ayman.solosystem.domain.shadows.Shadows.extractionsPossible(s, today)
    if (boss != null && boss.defeatedMs == null) {
        val d = com.ayman.solosystem.domain.boss.Boss.def(boss.bossId)
        val left = ((boss.maxHp - boss.damage).coerceAtLeast(0) * 100f / boss.maxHp).toInt()
        Banner(Sys.red, "⚔ " + tr("زعيم الأسبوع: ${d.name.ar} — HP $left%", "WEEKLY BOSS: ${d.name.en} — HP $left%")) { nav.go("boss") }
    }
    if (ext > 0) Banner(ShadowViolet, "☾ " + tr("ظلال جاهزة للاستخراج: $ext — ARISE", "Shadows ready to extract: $ext — ARISE")) { nav.go("shadows") }
}

@Composable
private fun Banner(c: androidx.compose.ui.graphics.Color, text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).thinBox(c).clickable(onClick = { com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.SELECT); onClick() }).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Label(text, color = c, size = 13, w = FontWeight.Bold)
    }
}
