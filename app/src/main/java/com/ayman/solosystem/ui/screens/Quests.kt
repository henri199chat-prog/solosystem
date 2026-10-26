package com.ayman.solosystem.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Stats
import com.ayman.solosystem.domain.quest.Countdown
import com.ayman.solosystem.domain.quest.GoalPlan
import com.ayman.solosystem.domain.rewards.RewardCatalog
import com.ayman.solosystem.domain.rewards.Rewards
import com.ayman.solosystem.ui.*
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*

@Composable
fun QuestsScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    val today = repo.today()
    var selected by remember { mutableStateOf<String?>(null) }
    val daily = s.quests.filter { it.day == today && it.type == QuestType.DAILY && it.status != QuestStatus.POSTPONED }
    val goals = daily.filter { GoalPlan.isGoal(it) }
    val side = if (goals.isEmpty()) emptyList() else daily.filterNot { GoalPlan.isGoal(it) }
    val penalties = s.quests.filter { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING }
    val emergencies = s.quests.filter { it.type == QuestType.EMERGENCY && (it.status == QuestStatus.OFFERED || it.status == QuestStatus.PENDING) }
    val mains = s.quests.filter { it.type == QuestType.MAIN && it.status == QuestStatus.PENDING }
    val missed = s.quests.filter { it.status == QuestStatus.MISSED && !it.reasonReviewed && it.day >= today - 14 }
    val upcoming = s.quests.filter { it.day > today && it.status == QuestStatus.PENDING && it.type != QuestType.PENALTY }
    val ringState = when { Countdown.allDone(s, today) -> 1; Countdown.armed(s, today) -> 0; else -> 2 }

    Screen(tr("المهام", "Quests"), nav) {
        // ---- the DAILY QUEST notification window
        SysPanel(Modifier.fillMaxWidth(), contentKey = daily.count { it.status == QuestStatus.COMPLETED }) {
            TitleBox(tr("إشعار", "NOTIFICATION"))
            Spacer(Modifier.height(10.dp))
            GlowText(tr("[وصلت المهمة اليومية: الاستعداد لتصبح أقوى]", "[Daily Quest: Preparing to become powerful has arrived]"), body(15.sp, FontWeight.Bold), Modifier.fillMaxWidth(), align = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            GoalHeading(today in s.secretDays)
            Spacer(Modifier.height(10.dp))
            if (goals.isEmpty()) Label(tr("لا مهام لهذا اليوم بعد.", "No quests for today yet."), align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            goals.forEach { q -> AnimeGoalRow(q, selected == q.id, repo, today) { selected = if (selected == q.id) null else q.id } }
            // legacy (pre-1.1) days without GOAL rows keep their old required list here
            if (goals.isEmpty()) daily.forEach { q -> GoalRow(q, selected == q.id, repo, nav, today) { selected = if (selected == q.id) null else q.id } }
            Spacer(Modifier.height(10.dp))
            WarningText()
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CountdownRing(Countdown.windowStart(s, today, repo.dayStartMs(today)), Countdown.deadlineMs(repo.dayStartMs(today), s.settings.deadlineMinutes), ringState) { repo.refreshDay() }
            }
            Label(when (ringState) {
                1 -> tr("اكتملت المهمة اليومية — توقف العداد.", "Daily quest complete — countdown stopped.")
                0 -> tr("المهلة: ", "Deadline: ") + "%02d:%02d".format(s.settings.deadlineMinutes / 60 % 24, s.settings.deadlineMinutes % 60) + tr(" • اضغط الحلقة لرؤية الوقت", " • tap the ring for time left")
                else -> tr("يوم تعافٍ أو معذور — لا عقاب عند انتهاء الوقت.", "Rest or excused day — no penalty at the deadline.")
            }, Modifier.fillMaxWidth(), align = TextAlign.Center, size = 12, color = if (ringState == 1) Sys.green else Sys.dim)
            val left = GameEngine.rerollsLeft(s, today); val tokens = s.inventory["token_reroll"] ?: 0
            if (left > 0 || tokens > 0) Label(tr("إعادات السحب المتاحة: $left • رموز: $tokens", "Rerolls left: $left • tokens: $tokens"), Modifier.fillMaxWidth(), align = TextAlign.Center, size = 12)
        }
        if (goals.isNotEmpty() && side.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false, contentKey = side.count { it.status == QuestStatus.COMPLETED }) {
            SectionHeader(tr("مهام جانبية", "Side tasks"))
            Label(tr("اختيارية: خبرة إضافية فقط، لا تؤثر على العداد ولا العقاب. التغذية في قسم ترياق الشفاء.", "Optional: extra XP only; they don't affect the ring or penalties. Nutrition lives in the Healing Elixir section."), size = 12)
            side.forEach { q -> GoalRow(q, selected == q.id, repo, nav, today) { selected = if (selected == q.id) null else q.id } }
        }
        penalties.forEach { PenaltyPanel(it, repo, today) }
        if (missed.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), accent = Sys.amber, brackets = false, flares = false) {
            SectionHeader(tr("مهام فائتة تحتاج سببًا", "Missed quests need a reason"))
            Label(tr("الأعذار الحقيقية لا تُحتسب ولا تصعّد العقاب.", "Real excuses don't count and never escalate."), size = 12)
            missed.forEach { q ->
                Spacer(Modifier.height(6.dp))
                Label("${q.title.get(ar)} • ${fmtDay(q.day, ar)}", color = Sys.text, size = 14, w = FontWeight.Bold)
                FlowRowCompat(Modifier.fillMaxWidth()) { MissReason.entries.forEach { r -> Chip(if (ar) r.ar else r.en, false) { repo.apply { st -> GameEngine.setReason(st, q.id, r, today, repo.now()) } } } }
            }
        }
        if (emergencies.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), accent = Sys.amber) {
            SectionHeader(tr("مهام طارئة", "Emergency quests"))
            emergencies.forEach { q ->
                if (q.status == QuestStatus.OFFERED) {
                    Label(q.title.get(ar), color = Sys.text, size = 15, w = FontWeight.Bold); Label(q.objective.get(ar), size = 13)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SysButton(tr("قبول", "Accept"), Modifier.weight(1f), small = true, sfx = com.ayman.solosystem.notify.Sfx.S.YES) { repo.apply { st -> GameEngine.emergencyRespond(st, q.id, "accept", today, repo.now()) } }
                        SysButton(tr("لاحقًا", "Later"), Modifier.weight(1f), small = true, accent = Sys.amber) { repo.apply { st -> GameEngine.emergencyRespond(st, q.id, "postpone", today, repo.now()) } }
                        SysButton(tr("رفض", "Decline"), Modifier.weight(1f), small = true, accent = Sys.dim, sfx = com.ayman.solosystem.notify.Sfx.S.NO) { repo.apply { st -> GameEngine.emergencyRespond(st, q.id, "decline", today, repo.now()) } }
                    }
                } else GoalRow(q, selected == q.id, repo, nav, today) { selected = if (selected == q.id) null else q.id }
            }
        }
        if (mains.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("المهام الرئيسية", "Main quests"))
            mains.forEach { q ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row { Label(q.title.get(ar), Modifier.weight(1f), Sys.text, 14, w = FontWeight.Bold); RankBadge(q.rank.name, size = 26.dp) }
                    Label(q.objective.get(ar), size = 12)
                    StatBar("", q.progress.toFloat(), q.target.toFloat(), LocalPalette.current.secondary)
                    if (q.templateId.startsWith("assess")) SysButton(tr("اختبار الرتبة", "Rank assessment"), small = true) { nav.go("tiers") }
                }
            }
        }
        WeeklyChallenges(s, today)
        if (upcoming.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("قادمة", "Upcoming"))
            upcoming.forEach { Label("• ${it.title.get(ar)} — ${fmtDay(it.day, ar)}", color = Sys.text, size = 13) }
        }
    }
}

@Composable
fun GoalRow(q: Quest, expanded: Boolean, repo: Repository, nav: Nav, today: Long, onTap: () -> Unit) {
    val ar = LocalArabic.current
    val done = q.status == QuestStatus.COMPLETED
    val canToggle = q.verify == Verify.MANUAL && (q.status == QuestStatus.PENDING || (done && q.day == today))
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.SELECT); onTap() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val prog = if (done) q.target else q.progress
            BasicText(q.title.get(ar), Modifier.weight(1f), style = body(15.sp, FontWeight.Medium, when (q.status) {
                QuestStatus.COMPLETED -> Sys.green; QuestStatus.MISSED -> Sys.red; QuestStatus.EXCUSED -> Sys.dim; else -> Sys.text }))
            BasicText("[$prog/${q.target}]", style = tech(15.sp, if (done) Sys.green else Sys.text))
            Spacer(Modifier.width(8.dp))
            SysCheck(done, enabled = canToggle || q.verify != Verify.MANUAL) {
                when {
                    q.verify == Verify.WORKOUT -> nav.go("training")
                    q.verify == Verify.CHECKIN -> nav.go("status")
                    done -> repo.apply { st -> GameEngine.uncheckQuest(st, q.id, today, repo.now()) }
                    else -> repo.apply { st -> GameEngine.completeQuest(st, q.id, today, repo.now()) }
                }
            }
        }
        if (expanded) Column(Modifier.fillMaxWidth().thinBox().padding(10.dp)) {
            Label(q.objective.get(ar), color = Sys.text, size = 14)
            Label(q.description.get(ar), size = 13)
            Row { Label("${q.category.let { if (ar) it.ar else it.en }} • ${tr("الرتبة", "Rank")} ${q.rank} • ${q.durationMin} ${tr("د", "min")}", Modifier.weight(1f), size = 12); BasicText("+${q.xp} XP", style = tech(14.sp, LocalPalette.current.secondary)) }
            if (q.optional) Label(tr("اختيارية — لا عقاب إن لم تكتمل.", "Optional — no penalty if skipped."), color = Sys.green, size = 12)
            when (q.status) {
                QuestStatus.EXCUSED -> Label(tr("معذورة", "Excused"), color = Sys.dim, size = 12)
                QuestStatus.MISSED -> Label(tr("فائتة", "Missed"), color = Sys.red, size = 12)
                else -> {}
            }
            if (q.status == QuestStatus.PENDING && q.day == today) {
                Spacer(Modifier.height(6.dp))
                FlowRowCompat(Modifier.fillMaxWidth()) {
                    if (q.verify == Verify.WORKOUT) SysButton(tr("ابدأ التدريب", "Start training"), small = true) { nav.go("training") }
                    if (q.verify == Verify.CHECKIN) SysButton(tr("سجّل الجاهزية", "Log readiness"), small = true) { nav.go("status") }
                    if (q.type != QuestType.MAIN && q.type != QuestType.PENALTY && q.postponeCount < 2) SysButton(tr("تأجيل للغد", "Postpone"), small = true, accent = Sys.amber) { repo.apply { st -> GameEngine.postpone(st, q.id, today, repo.now()) } }
                }
                Label(tr("مريض أو مصاب أو ظرف طارئ؟", "Ill, injured or an emergency?"), size = 12)
                FlowRowCompat(Modifier.fillMaxWidth()) { MissReason.entries.filter { it.excusable }.forEach { r -> Chip(if (ar) r.ar else r.en, false) { repo.apply { st -> GameEngine.setReason(st, q.id, r, today, repo.now()) } } } }
            }
        }
    }
}

@Composable
fun PenaltyPanel(q: Quest, repo: Repository, today: Long) {
    val ar = LocalArabic.current
    val physical = q.templateId == "penalty"
    SysPanel(Modifier.fillMaxWidth(), accent = if (physical) Sys.red else Sys.amber, contentKey = q.items.count { it.done }) {
        TitleBox(if (physical) tr("مهمة العقاب", "PENALTY QUEST") else q.title.get(ar), accent = if (physical) Sys.red else Sys.amber)
        Spacer(Modifier.height(8.dp))
        if (physical) { TierIndicator(q.penaltyTier); GlowText(q.title.get(ar), body(15.sp, FontWeight.Bold), Modifier.fillMaxWidth(), align = TextAlign.Center, glow = Sys.red) }
        Label(q.objective.get(ar), color = Sys.text, size = 14)
        if (q.day > today) Label(tr("مجدولة في: ", "Scheduled for: ") + fmtDay(q.day, ar) + tr(" (أيام التدريب فقط)", " (training days only)"), color = Sys.amber, size = 13)
        q.items.forEachIndexed { i, it ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(penaltyLine(it, ar), Modifier.weight(1f), style = body(15.sp, FontWeight.Medium, if (it.done) Sys.green else Sys.text))
                SysCheck(it.done, enabled = q.day <= today) { repo.apply { st -> GameEngine.togglePenaltyItem(st, q.id, i, today, repo.now()) } }
            }
        }
        if (q.items.isEmpty()) SysButton(tr("أكملت", "Done"), Modifier.fillMaxWidth(), small = true, enabled = q.day <= today) { repo.apply { st -> GameEngine.completeQuest(st, q.id, today, repo.now()) } }
        Label(q.description.get(ar), size = 12)
        if (physical) {
            Spacer(Modifier.height(6.dp))
            SysButton(tr("أشعر بألم — أوقف بأمان", "I feel pain — stop safely"), Modifier.fillMaxWidth(), small = true, accent = Sys.amber) { repo.apply { st -> GameEngine.stopPenaltyForPain(st, q.id, today, repo.now()) } }
            Spacer(Modifier.height(6.dp))
            WarningText(daily = false)
        }
    }
}

@Composable
private fun WeeklyChallenges(s: AppState, today: Long) {
    val ar = LocalArabic.current
    val ws = Stats.weekStart(today)
    val list = Rewards.challengesFor(ws)
    SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
        SectionHeader(tr("تحديات الأسبوع", "Weekly challenges"))
        list.forEach { c ->
            val p = c.progress(s, ws, ws + 6).coerceAtMost(c.target)
            val done = "wc:$ws:${c.id}" in s.grantedKeys
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(c.name.get(ar), Modifier.weight(1f), if (c.event) Sys.gold else Sys.text, 14, w = FontWeight.Bold)
                    RewardCatalog.get(c.box)?.let { RewardIcon(it, 22.dp) }
                }
                Label(c.desc.get(ar), size = 12)
                StatBar("", p.toFloat(), c.target.toFloat(), if (done) Sys.green else if (c.event) Sys.gold else LocalPalette.current.secondary, valueText = if (done) tr("مكتمل", "Done") else "$p/${c.target}")
            }
        }
    }
}

/** Glowing "GOAL" title with the anime double underline. */
@Composable
private fun GoalHeading(secret: Boolean = false) {
    val c = if (secret) com.ayman.solosystem.ui.SecretViolet else LocalPalette.current.secondary
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (secret) {
            com.ayman.solosystem.ui.GlitchBlock(intense = false) { com.ayman.solosystem.ui.GlitchText("SECRET QUEST", 26, tech = true) }
            Label(tr("مهمة سرّية — تم اكتشاف الشرط الخفي", "Secret Quest — hidden condition found"), color = com.ayman.solosystem.ui.SecretPink, size = 12)
        } else
            GlowText(tr("الهدف", "GOAL"), if (LocalArabic.current) body(26.sp, FontWeight.Bold) else tech(28.sp), glow = c)
        androidx.compose.foundation.Canvas(Modifier.width(if (LocalArabic.current) 96.dp else 120.dp).height(9.dp)) {
            for (y in listOf(2.dp.toPx(), 7.dp.toPx())) {
                drawLine(c.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 5.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.9f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.4.dp.toPx())
            }
        }
    }
}

/** Glowing anime-style square checkbox. */
@Composable
fun GoalCheck(checked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalPalette.current.secondary
    Box(modifier.size(40.dp).clickable(role = Role.Checkbox, onClick = onClick), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            val st = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx())
            drawRoundRect(c.copy(alpha = 0.25f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = 0.92f), cornerRadius = r, style = st)
            if (checked) {
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * .22f, size.height * .52f); lineTo(size.width * .44f, size.height * .74f); lineTo(size.width * .8f, size.height * .26f) }
                drawPath(p, c.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(p, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
    }
}

/** One anime GOAL row: name … [current/target] ☐ — current is bold; overachievement shows e.g. [200/100]. */
@Composable
fun AnimeGoalRow(q: Quest, expanded: Boolean, repo: Repository, today: Long, onTap: () -> Unit) {
    val ar = LocalArabic.current
    val row = GoalPlan.row(q.templateId) ?: return
    val done = q.status == QuestStatus.COMPLETED
    val over = q.progress >= q.target * GoalPlan.OVER_FACTOR
    val glow = when { q.status == QuestStatus.MISSED -> Sys.red; over -> Sys.gold; else -> LocalPalette.current.secondary }
    val v = GoalPlan.variant(row, q.variant)
    val unit = if (ar) v.unit.ar else v.unit.en
    val bracket = androidx.compose.ui.text.buildAnnotatedString {
        append("[")
        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Black, color = if (done) Color.White else Sys.text))
        append(GoalPlan.fmtV(v, q.progress)); pop()
        append(" / ${GoalPlan.fmtV(v, q.target)}")
        if (unit.isEmpty()) append("]")
        if (unit.isEmpty() && q.stretch > q.target) { pushStyle(androidx.compose.ui.text.SpanStyle(color = Sys.gold, fontSize = 12.sp)); append(" ↑${GoalPlan.fmtV(v, q.stretch)}"); pop() }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.SELECT); onTap() }.padding(start = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlowText(if (ar) v.ar else v.en, body(if (v.id.isEmpty()) 18.sp else 16.sp, FontWeight.Medium, when (q.status) { QuestStatus.MISSED -> Sys.red; QuestStatus.EXCUSED -> Sys.dim; else -> Sys.text }), Modifier.weight(1f), glow = glow)
            // Explicit LTR paragraph (no LRI/PDI inside a clipped single line) so "[0 / 1 كم]" always renders whole.
            val bStyle = tech(17.sp, SystemTheme.c(0xFFE6F4FF, 0xFFF3E8FF)).copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr)
            if (unit.isEmpty()) GlowTextAnnotated(bracket, bStyle, glow = glow, singleLine = true)
            else androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                // "[0 / 1 كم]" as three pieces laid out LTR: Rajdhani numbers + Arabic-face unit — never wraps, never clips.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlowTextAnnotated(bracket, bStyle, glow = glow, singleLine = true)
                    Spacer(Modifier.width(4.dp))
                    GlowText(unit, body(15.sp, FontWeight.Bold, SystemTheme.c(0xFFE6F4FF, 0xFFF3E8FF)), glow = glow, maxLines = 1)
                    GlowText("]", bStyle, glow = glow, maxLines = 1)
                }
            }
            Spacer(Modifier.width(4.dp))
            GoalCheck(done) {
                if (q.day != today) { com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.ERROR); return@GoalCheck }
                com.ayman.solosystem.notify.Sfx.play(if (done) com.ayman.solosystem.notify.Sfx.S.MINUS else com.ayman.solosystem.notify.Sfx.S.QUEST_TICK, rate = if (done) 1f else 1.35f)
                if (done) repo.apply { st -> GameEngine.logGoal(st, q.id, -q.progress, today, repo.now()) }
                else repo.apply { st -> GameEngine.completeQuest(st, q.id, today, repo.now()) }
            }
        }
        if (row != GoalPlan.Row.RUN && v.unit == GoalPlan.GUnit.REPS && !done && q.status == QuestStatus.PENDING)
            Label(GoalPlan.splitHint(q.target).get(ar), color = Sys.dim, size = 11, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
        if (expanded) Column(Modifier.fillMaxWidth().thinBox().padding(10.dp)) {
            val st = repo.state.collectAsState().value
            Label(q.description.get(ar), color = Sys.text, size = 13)
            GoalPlan.variantNote(st, row)?.let { Label(it.get(ar), color = Sys.amber, size = 12) }
            if (over) {
                Label(tr("تجاوز الهدف — حصلت على المكافأة الإضافية.", "Goal exceeded — bonus reward earned."), color = Sys.gold, size = 12)
                val capped = GoalPlan.intensity(v, q.progress) > row.cap || (row == GoalPlan.Row.RUN && GoalPlan.intensity(v, q.progress) > (GoalPlan.runEnergyCapM(st, q.day) ?: Int.MAX_VALUE))
                if (capped) Label(tr("تجاوزت سقفك الآمن — محسوب، لكن استمع لجسدك. لا شيء يطلب منك ذلك.", "Past your safe cap — it counts, but listen to your body. Nothing requires it."), color = Sys.amber, size = 12)
            }
            Label(tr("الهدف المطلوب هو [${GoalPlan.fmtV(v, q.target)}] فقط. توقّف فورًا عند أي ألم.", "Only [${GoalPlan.fmtV(v, q.target)}] is required. Stop immediately on any pain."), size = 12)
            if (row == GoalPlan.Row.RUN) {
                val kcal = com.ayman.solosystem.domain.nutrition.Nutrition.runAddBack(st, q.day)
                if (kcal > 0) Label(tr("طاقة الجري ~$kcal سعرة — أُضيفت تلقائيًا إلى هدف الترياق اليوم حتى لا تأكل الفائض.", "Run energy ~$kcal kcal — added to today's Elixir target so it doesn't eat your surplus."), color = Elixir, size = 12)
                GoalPlan.runEnergyCapM(st, q.day)?.let { cap -> Label(tr("سقف الجري الذكي اليوم: ${GoalPlan.fmtKm(cap)} كم (حسب وزنك وسعراتك).", "Smart run cap today: ${GoalPlan.fmtKm(cap)} km (from your weight & calories)."), size = 12) }
                GoalPlan.runCapReason(st, q.day)?.let { Label(it.get(ar), color = Sys.amber, size = 12) }
                st.goalTracks["run"]?.note?.let { Label(tr("آخر تعديل للمسافة: ", "Last distance change: ") + it.get(ar), size = 12, color = Sys.text) }
            }
            if (q.day == today && (q.status == QuestStatus.PENDING || done)) {
                Spacer(Modifier.height(6.dp))
                val steps = when (v.unit) { GoalPlan.GUnit.M -> if (v.factor > 1.5) listOf(100, 500) else listOf(500, 1000); GoalPlan.GUnit.SEC -> listOf(10, 30, 60); GoalPlan.GUnit.FLOORS -> listOf(1, 5); else -> if (q.target >= 40) listOf(1, 5, 10, 20) else listOf(1, 5, 10) }
                FlowRowCompat(Modifier.fillMaxWidth()) {
                    steps.forEach { n -> SysButton("+" + GoalPlan.fmtV(v, n) + unit, small = true, sfx = null) { com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.QUEST_TICK, rate = 0.9f + 0.45f * ((q.progress + n).toFloat() / q.target.coerceAtLeast(1)).coerceIn(0f, 1f)); repo.apply { st -> GameEngine.logGoal(st, q.id, n, today, repo.now()) } } }
                    SysButton(tr("−تراجع", "−Undo"), small = true, accent = Sys.dim, sfx = com.ayman.solosystem.notify.Sfx.S.MINUS) { repo.apply { st -> GameEngine.logGoal(st, q.id, -(steps.first()), today, repo.now()) } }
                }
                // ---- raise intensity (player may only go UP; the System alone lowers after pain/illness)
                Spacer(Modifier.height(6.dp))
                Label(tr("رفع الشدة (اختياري): الإنجاز يُحسب دائمًا على الهدف الأصلي، والزيادة تمنح XP إضافيًا.", "Raise intensity (optional): completion always counts against the original target; the extra earns bonus XP."), color = Sys.gold, size = 12)
                val curAmt = maxOf(q.stretch, q.target)
                val stepUp = steps.last().coerceAtMost(maxOf(steps.first(), curAmt / 5))
                FlowRowCompat(Modifier.fillMaxWidth()) {
                    SysButton("−", small = true, accent = Sys.dim, sfx = com.ayman.solosystem.notify.Sfx.S.MINUS) { repo.apply { st -> GameEngine.editGoal(st, q.id, v.id, (curAmt - steps.first()).coerceAtLeast(0), today, repo.now()) } }
                    SysButton(tr("رفع إلى ", "Raise to ") + GoalPlan.fmtV(v, curAmt + stepUp) + unit, small = true, accent = Sys.gold, sfx = com.ayman.solosystem.notify.Sfx.S.PLUS) { repo.apply { st -> GameEngine.editGoal(st, q.id, v.id, curAmt + stepUp, today, repo.now()) } }
                }
                FlowRowCompat(Modifier.fillMaxWidth()) {
                    GoalPlan.variants(row).filter { it.id != v.id }.forEach { nv ->
                        val amt = GoalPlan.toVariant(nv, kotlin.math.ceil(GoalPlan.intensity(v, curAmt)).toInt())
                        Chip((if (ar) nv.ar else nv.en) + " " + GoalPlan.fmtV(nv, amt) + (if (ar) nv.unit.ar else nv.unit.en) + (if (nv.factor < v.factor) " ↓" else " ↑"), false) {
                            repo.apply { st -> GameEngine.editGoal(st, q.id, nv.id, amt, today, repo.now()) }
                        }
                    }
                }
                SysButton(tr("ألم — توقّف الآن", "Pain — stop now"), Modifier.fillMaxWidth(), small = true, accent = Sys.red) { repo.apply { st -> GameEngine.goalPain(st, q.id, today, repo.now()) } }
                if (q.status == QuestStatus.PENDING) {
                    Label(tr("ألم، مرض أو ظرف طارئ؟ لا عقاب مع العذر الحقيقي — توقف فورًا عند الألم.", "Pain, illness or an emergency? No penalty with a real excuse — stop immediately on pain."), size = 12)
                    FlowRowCompat(Modifier.fillMaxWidth()) { MissReason.entries.filter { it.excusable }.forEach { r -> Chip(if (ar) r.ar else r.en, false) { repo.apply { st -> GameEngine.setReason(st, q.id, r, today, repo.now()) } } } }
                }
            }
        }
    }
}
