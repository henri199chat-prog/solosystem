package com.ayman.solosystem.ui.comp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.rewards.*
import com.ayman.solosystem.domain.skills.SkillDef
import com.ayman.solosystem.domain.training.ExerciseLibrary
import com.ayman.solosystem.ui.theme.*

fun Rarity.c() = Color(color)

@Composable
fun RewardIcon(d: RewardDef, size: Dp = 40.dp) {
    val c = d.rarity.c()
    when (d.kind) {
        RewardKind.TITLE -> SysIcon(Ico.TITLE, c, size = size)
        RewardKind.THEME -> Canvas(Modifier.size(size)) {
            drawCircle(Color(d.primary), this.size.minDimension * 0.42f); drawCircle(Color(d.secondary), this.size.minDimension * 0.22f)
        }
        RewardKind.FRAME -> SysIcon(Ico.FRAME, Color(d.primary), size = size)
        RewardKind.EMBLEM -> EmblemIcon(d.shape, Color(d.primary), size = size)
        RewardKind.BADGE -> SysIcon(Ico.BADGE, c, size = size)
        RewardKind.BOX -> SysIcon(Ico.BOX, c, size = size)
        RewardKind.GOLD -> SysIcon(Ico.GOLD, Sys.gold, size = size)
        RewardKind.TOKEN -> SysIcon(Ico.TOKEN, c, size = size)
    }
}

@Composable
fun RewardCard(d: RewardDef, modifier: Modifier = Modifier, locked: Boolean = false, extra: String? = null, footer: (@Composable ColumnScope.() -> Unit)? = null) {
    val ar = LocalArabic.current
    ItemCard(
        name = d.name.get(ar), category = (if (ar) "الفئة: " else "Category: ") + (if (ar) d.kind.ar else d.kind.en),
        rank = (if (ar) "الندرة: " else "Rarity: ") + (if (ar) d.rarity.ar else d.rarity.en),
        bonus = extra ?: d.bonusText(ar), flavour = if (locked && d.hidden) (if (ar) "إنجاز سري. استمر وستكتشفه." else "Secret achievement. Keep going to discover it.") else d.flavour.get(ar),
        modifier = modifier, accent = d.rarity.c(), locked = locked && d.hidden, footer = footer
    ) { if (locked && d.hidden) SysIcon(Ico.LOCK, Sys.dim, size = 36.dp) else RewardIcon(d) }
}

@Composable
fun SkillCard(sk: SkillDef, level: Int, progress: Int, modifier: Modifier = Modifier) {
    val ar = LocalArabic.current
    val next = sk.thresholds.getOrNull(level)
    val req = Txt("الشرط: ${sk.metric.ar}", "Requirement: ${sk.metric.en}").get(ar)
    ItemCard(
        name = sk.name.get(ar) + if (level > 0) "  Lv.$level" else "",
        category = (if (sk.active) (if (ar) "مهارة نشطة" else "Active skill") else (if (ar) "مهارة سلبية" else "Passive skill")) + " • " + (if (ar) sk.branch.ar else sk.branch.en),
        rank = (if (ar) "الرتبة: " else "Rank: ") + sk.rank.name,
        bonus = if (level > 0) sk.effectText(level, ar) else (if (ar) "مقفلة — " else "Locked — ") + sk.effectText(1, ar),
        flavour = sk.flavour.get(ar) + "\n" + req + " — " + (if (next != null) "$progress/$next" else if (ar) "المستوى الأقصى" else "MAX"),
        modifier = modifier, accent = if (level > 0) LocalPalette.current.secondary else Sys.dim
    ) { SysIcon(Ico.SKILLS, if (level > 0) LocalPalette.current.secondary else Sys.dim, size = 38.dp) }
}

fun penaltyLine(i: PenaltyItem, ar: Boolean): String {
    val n = ExerciseLibrary.get(i.exerciseId)?.name(ar) ?: i.exerciseId
    val u = when (i.unit) { PenaltyUnit.REPS -> ""; PenaltyUnit.SECONDS -> if (ar) " ث" else "s"; PenaltyUnit.MINUTES -> if (ar) " د" else " min" }
    val sets = if (i.sets > 1) (if (ar) " (${i.sets} مجموعات)" else " (${i.sets} sets)") else ""
    return "$n [${if (i.done) i.target else 0}/${i.target}$u]$sets"
}
