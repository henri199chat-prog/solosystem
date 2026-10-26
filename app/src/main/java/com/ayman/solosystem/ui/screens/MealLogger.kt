package com.ayman.solosystem.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.nutrition.FoodDb
import com.ayman.solosystem.domain.nutrition.FoodDb.Portion
import com.ayman.solosystem.domain.nutrition.Nutrition
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.*
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlin.math.roundToInt

private val MealSoft get() = SystemTheme.c(0xFFBAE6FD, 0xFFE9D5FF)

fun fmtQty(q: Double): String = "\u2066" + (when {
    q % 1.0 == 0.0 -> q.toInt().toString()
    q % 1.0 == 0.5 -> (if (q < 1.0) "" else q.toInt().toString()) + "½"
    else -> q.toString()
}) + "\u2069" // isolated so "1½" never renders as "½1" in RTL

/** "صحن متوسط ×1½" style portion description for a logged meal (null for manual/legacy). */
fun portionText(m: MealLog, ar: Boolean): String? {
    val p = m.portion?.let { n -> Portion.entries.firstOrNull { it.name == n } } ?: return null
    return (if (ar) p.ar else p.en) + if (m.qty != 1.0) " ×${fmtQty(m.qty)}" else ""
}

/**
 * v1.2.2 Elixir meal logging WITHOUT typing calories: search the offline Tunisian food DB (Arabic/Tunisian/English), favourites,
 * recent meals (one tap), "repeat yesterday", household portions with icons and quick quantity chips. Manual kcal entry is an
 * optional advanced fallback.
 */
@Composable
fun MealLogPanel(s: AppState, repo: Repository, today: Long) {
    val ar = LocalArabic.current
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<FoodDb.Cat?>(null) }
    var picked by remember { mutableStateOf<FoodDb.Item?>(null) }
    var manual by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf<String?>(null) }
    SysPanel(Modifier.fillMaxWidth(), accent = Elixir, flares = false, contentKey = s.nutrition.meals.size) {
        SectionHeader(tr("تسجيل الوجبات", "Log meals"))
        Label(tr("اختر ما أكلت والكمية — النظام يحسب السعرات والبروتين تلقائيًا.", "Pick what you ate and how much — the System computes calories and protein."), color = Sys.text, size = 12)
        flash?.let { Label("✓ $it", color = Elixir, size = 12, w = FontWeight.Bold) }
        Spacer(Modifier.height(6.dp))

        val yesterday = remember(s.nutrition.meals, today) { Nutrition.mealsOn(s, today - 1) }
        if (yesterday.isNotEmpty()) {
            val yk = yesterday.sumOf { it.kcal }
            SysButton("↻ " + tr("كرّر وجبات الأمس", "Repeat yesterday's meals") + " (${yesterday.size} · " + UnitFmt.num("$yk", UnitFmt.kcal(ar)) + ")",
                Modifier.fillMaxWidth(), small = true, accent = Elixir, sfx = Sfx.S.SOFT_CONFIRM) {
                repo.set { Nutrition.repeatMeals(it, yesterday, today, repo.now()) }
                flash = if (ar) "أُضيفت وجبات الأمس (${yesterday.size})" else "Added yesterday's meals (${yesterday.size})"
            }
            Spacer(Modifier.height(4.dp))
        }

        val results = remember(query, cat) { if (query.isNotBlank()) FoodDb.search(query, 25) else cat?.let { c -> FoodDb.all.filter { it.cat == c } } ?: emptyList() }
        // The portion picker opens right where the user tapped (under the row / under favourites) so it is never off-screen.
        val pickerFor: @Composable (FoodDb.Item) -> Unit = { f ->
            Spacer(Modifier.height(4.dp))
            PortionPicker(f, f.id in s.nutrition.favorites, onFav = { repo.set { Nutrition.toggleFavorite(it, f.id) } }, onClose = { picked = null }) { p, q ->
                repo.set { Nutrition.logFood(it, f, p, q, today, repo.now()) }
                flash = f.name.get(ar) + " · " + UnitFmt.num("${FoodDb.calc(f, p, q).kcal}", UnitFmt.kcal(ar))
                picked = null; query = ""
            }
            Spacer(Modifier.height(4.dp))
        }
        val favs = s.nutrition.favorites.mapNotNull { FoodDb.get(it) }
        if (favs.isNotEmpty()) {
            Label("★ " + tr("المفضّلة", "Favourites"), color = Sys.gold, size = 12, w = FontWeight.Bold)
            FlowRowCompat(Modifier.fillMaxWidth()) { favs.forEach { f -> Chip(f.ar.takeIf { ar } ?: f.en, picked?.id == f.id) { picked = f } } }
        }
        picked?.takeIf { p -> results.none { it.id == p.id } }?.let { pickerFor(it) }
        val rec = remember(s.nutrition.meals) { Nutrition.recents(s, 8) }
        if (rec.isNotEmpty()) {
            Label(tr("الأخيرة — لمسة واحدة للتسجيل", "Recent — one tap to log"), color = MealSoft, size = 12, w = FontWeight.Bold)
            FlowRowCompat(Modifier.fillMaxWidth()) {
                rec.forEach { m ->
                    val f = FoodDb.get(m.foodId) ?: return@forEach
                    val p = Portion.entries.firstOrNull { it.name == m.portion } ?: f.defaultPortion
                    Chip("${f.name.get(ar)} · ${if (ar) p.ar else p.en}${if (m.qty != 1.0) " ×" + fmtQty(m.qty) else ""}", false, sfx = Sfx.S.SOFT_CONFIRM) {
                        repo.set { Nutrition.logFood(it, f, p, m.qty, today, repo.now()) }
                        flash = f.name.get(ar) + " · " + UnitFmt.num("${FoodDb.calc(f, p, m.qty).kcal}", UnitFmt.kcal(ar))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        TextIn(query, { query = it.take(30); if (it.isNotBlank()) cat = null }, tr("ابحث: كسكسي، لبلابي، ملاوي، pizza…", "Search: couscous, lablabi, mlawi, pizza…"))
        FlowRowCompat(Modifier.fillMaxWidth()) {
            FoodDb.Cat.entries.forEach { c -> Chip(if (ar) c.ar else c.en, cat == c) { cat = if (cat == c) null else c; query = "" } }
        }
        if (query.isNotBlank() && results.isEmpty()) Label(tr("لا نتيجة. جرّب اسمًا آخر أو استعمل الإدخال اليدوي.", "No match. Try another name or use manual entry."), size = 12)
        results.forEach { f ->
            val c = FoodDb.calc(f, f.defaultPortion, 1.0)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).thinBox(if (picked?.id == f.id) Elixir else Sys.panelLine)
                .clickable(role = Role.Button) { Sfx.play(Sfx.S.SELECT); picked = f }.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                PortionIcon(f.defaultPortion, Modifier.size(26.dp), MealSoft)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Label(f.name.get(ar), color = Sys.text, size = 14, w = FontWeight.Bold)
                    Label((if (ar) f.defaultPortion.ar else f.defaultPortion.en) + " · " + UnitFmt.num("${c.kcal}", UnitFmt.kcal(ar)) + " · " + UnitFmt.num("${c.protein.roundToInt()}", UnitFmt.g(ar)) + " " + tr("بروتين", "protein"), size = 11)
                }
                if (f.id in s.nutrition.favorites) BasicText("★", style = tech(14.sp, Sys.gold))
            }
            if (picked?.id == f.id) pickerFor(f)
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { manual = !manual }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Label((if (manual) "▾ " else "▸ ") + tr("إدخال يدوي (متقدم — إذا كنت تعرف السعرات)", "Manual entry (advanced — if you know the calories)"), color = Sys.dim, size = 12)
        }
        if (manual) ManualMealForm(repo, today)

        Divider()
        val list = s.nutrition.meals.filter { it.day == today }.sortedByDescending { it.ms }
        if (list.isEmpty()) EmptyState(tr("لا توجد سجلات بعد", "No records yet"), tr("لا وجبات مسجّلة اليوم. الجسد يُبنى بما تأكله.", "No meals logged today. The body is built from what you eat."), icon = Ico.POTION)
        list.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(m.name.get(ar), color = Sys.text, size = 13)
                    portionText(m, ar)?.let { Label(it, size = 11) }
                }
                Label(UnitFmt.num("${m.kcal}", UnitFmt.kcal(ar)) + " • " + UnitFmt.num("${m.protein.roundToInt()}", UnitFmt.g(ar)), color = MealSoft, size = 12)
                Box(Modifier.size(44.dp).clickable(role = Role.Button) { Sfx.play(Sfx.S.SOFT_TOGGLE); repo.set { Nutrition.removeMeal(it, m.id) } }, contentAlignment = Alignment.Center) {
                    BasicText("×", style = tech(18.sp, Sys.red))
                }
            }
        }
    }
}

@Composable
private fun PortionPicker(f: FoodDb.Item, fav: Boolean, onFav: () -> Unit, onClose: () -> Unit, onAdd: (Portion, Double) -> Unit) {
    val ar = LocalArabic.current
    var portion by remember(f.id) { mutableStateOf(f.defaultPortion) }
    var qty by remember(f.id) { mutableDoubleStateOf(1.0) }
    var details by remember { mutableStateOf(false) }
    val c = FoodDb.calc(f, portion, qty)
    Column(Modifier.fillMaxWidth().thinBox(Elixir, Color(0x2214B8A6)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowText(f.name.get(ar), body(16.sp, FontWeight.Bold), Modifier.weight(1f), glow = Elixir)
            Box(Modifier.size(44.dp).clickable(role = Role.Button) { Sfx.play(Sfx.S.SELECT); onFav() }, contentAlignment = Alignment.Center) {
                BasicText(if (fav) "★" else "☆", style = tech(20.sp, if (fav) Sys.gold else Sys.dim))
            }
            Box(Modifier.size(44.dp).clickable(role = Role.Button) { onClose() }, contentAlignment = Alignment.Center) { BasicText("✕", style = tech(14.sp, Sys.dim)) }
        }
        Label(tr("الكمية بوحدات البيت:", "Portion (household units):"), size = 12)
        FlowRowCompat(Modifier.fillMaxWidth()) {
            f.portions.keys.forEach { p ->
                val sel = p == portion
                Row(Modifier.padding(3.dp).thinBox(if (sel) Elixir else Sys.panelLine, if (sel) Elixir.copy(alpha = 0.18f) else Color(0x44000000))
                    .clickable(role = Role.RadioButton) { Sfx.play(Sfx.S.SELECT); portion = p }.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    PortionIcon(p, Modifier.size(24.dp), if (sel) Elixir else MealSoft)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        BasicText(if (ar) p.ar else p.en, style = body(13.sp, if (sel) FontWeight.Bold else FontWeight.Normal, if (sel) Sys.text else Sys.dim))
                        if (p != Portion.G100) BasicText(UnitFmt.num("~${f.portions[p]}", if (f.cat == FoodDb.Cat.DRINK || p == Portion.GLASS) UnitFmt.ml(ar) else UnitFmt.g(ar)), style = body(10.sp, FontWeight.Normal, Sys.dim))
                    }
                }
            }
        }
        Label(tr("العدد:", "Quantity:"), size = 12)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FoodDb.QTY.forEach { q -> Chip(fmtQty(q), qty == q, Modifier.sizeIn(minWidth = 56.dp, minHeight = 48.dp)) { qty = q } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            SysButton("−", Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), small = true, sfx = Sfx.S.MINUS) { qty = (qty - 0.5).coerceAtLeast(0.5) }
            BasicText(fmtQty(qty), Modifier.widthIn(min = 72.dp), style = tech(16.sp).copy(textAlign = TextAlign.Center))
            SysButton("+", Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), small = true, sfx = Sfx.S.PLUS) { qty = (qty + 0.5).coerceAtMost(10.0) }
        }
        // main readout: kcal + protein
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlowText(UnitFmt.iso("${c.kcal}"), tech(28.sp), glow = Elixir); Label(UnitFmt.kcal(ar), color = MealSoft, size = 12)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlowText(UnitFmt.iso("${c.protein.roundToInt()}"), tech(28.sp), glow = Elixir); Label(UnitFmt.g(ar) + " " + tr("بروتين", "protein"), color = MealSoft, size = 12)
            }
        }
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { details = !details }.padding(vertical = 6.dp)) {
            Label((if (details) "▾ " else "▸ ") + tr("التفاصيل", "Details"), size = 12)
        }
        if (details) {
            KeyValue(tr("الوزن التقريبي", "Approx. weight"), UnitFmt.num("${c.grams}", UnitFmt.g(ar)))
            KeyValue(tr("كربوهيدرات", "Carbs"), UnitFmt.num("${c.carbs.roundToInt()}", UnitFmt.g(ar)))
            KeyValue(tr("دهون", "Fat"), UnitFmt.num("${c.fat.roundToInt()}", UnitFmt.g(ar)))
            Label(tr("قيم تقديرية من جداول التغذية القياسية (USDA / CIQUAL) ووصفات منزلية.", "Estimates from standard tables (USDA / CIQUAL) and home recipes."), size = 10)
        }
        SysButton(tr("سجّل", "Log it") + " · " + UnitFmt.num("${c.kcal}", UnitFmt.kcal(ar)), Modifier.fillMaxWidth(), accent = Elixir, sfx = Sfx.S.SOFT_CONFIRM) { onAdd(portion, qty) }
    }
}

@Composable
private fun ManualMealForm(repo: Repository, today: Long) {
    val ar = LocalArabic.current
    var kcal by remember { mutableStateOf("") }
    var prot by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val bad = tr("أدخل سعرات (0–5000) وبروتين (0–400)", "Enter kcal (0–5000) and protein (0–400)")
    TextIn(name, { name = it.take(40) }, tr("اسم الوجبة (اختياري)", "Meal name (optional)"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextIn(kcal, { kcal = it.filter(Char::isDigit).take(4); err = null }, UnitFmt.kcal(ar), Modifier.weight(1f), number = true, error = err)
        Spacer(Modifier.width(6.dp))
        TextIn(prot, { prot = it.take(5); err = null }, tr("بروتين غ", "Protein g"), Modifier.weight(1f), number = true)
        Spacer(Modifier.width(6.dp))
        SysButton(tr("إضافة", "Add"), small = true, accent = Elixir, sfx = Sfx.S.SOFT_CONFIRM) {
            val k = kcal.toIntOrNull(); val p = prot.replace(',', '.').toDoubleOrNull() ?: 0.0
            if (k == null || k !in 0..5000 || p !in 0.0..400.0) err = bad else {
                val n = name.trim().ifEmpty { if (ar) "وجبة" else "Meal" }
                repo.set { Nutrition.addMeal(it, MealLog("m${repo.now()}", today, repo.now(), Txt(n, n), k, p)) }
                kcal = ""; prot = ""; name = ""
            }
        }
    }
}

/** Simple line icons for household portions. */
@Composable
fun PortionIcon(p: Portion, modifier: Modifier, color: Color) {
    Canvas(modifier) { drawPortion(p, color) }
}

private fun DrawScope.drawPortion(p: Portion, c: Color) {
    val w = size.width; val h = size.height; val sw = 1.6.dp.toPx(); val st = Stroke(sw, cap = StrokeCap.Round)
    fun plate(scale: Float) {
        val pw = w * scale; val ph = pw * 0.42f; val x = (w - pw) / 2; val y = h * 0.62f - ph / 2
        drawOval(c, Offset(x, y), Size(pw, ph), style = st)
        drawArc(c.copy(alpha = 0.55f), 180f, 180f, true, Offset(x + pw * 0.22f, y - ph * 0.55f), Size(pw * 0.56f, ph * 1.3f))
    }
    when (p) {
        Portion.PLATE_S -> plate(0.62f); Portion.PLATE_M -> plate(0.8f); Portion.PLATE_L -> plate(0.98f)
        Portion.BOWL -> { drawArc(c, 0f, 180f, false, Offset(w * 0.1f, h * 0.15f), Size(w * 0.8f, h * 0.7f), style = st); drawLine(c, Offset(w * 0.08f, h * 0.5f), Offset(w * 0.92f, h * 0.5f), sw) }
        Portion.LADLE -> { drawArc(c, 0f, 180f, false, Offset(w * 0.08f, h * 0.35f), Size(w * 0.5f, h * 0.45f), style = st); drawLine(c, Offset(w * 0.58f, h * 0.57f), Offset(w * 0.92f, h * 0.1f), sw, StrokeCap.Round) }
        Portion.GLASS -> { val path = Path().apply { moveTo(w * 0.22f, h * 0.1f); lineTo(w * 0.32f, h * 0.92f); lineTo(w * 0.68f, h * 0.92f); lineTo(w * 0.78f, h * 0.1f) }
            drawPath(path, c, style = st); drawLine(c.copy(alpha = 0.55f), Offset(w * 0.26f, h * 0.4f), Offset(w * 0.74f, h * 0.4f), sw) }
        Portion.CUP -> { drawRoundRect(c, Offset(w * 0.15f, h * 0.3f), Size(w * 0.5f, h * 0.5f), CornerRadius(4f, 4f), style = st); drawArc(c, -90f, 180f, false, Offset(w * 0.55f, h * 0.38f), Size(w * 0.25f, h * 0.28f), style = st) }
        Portion.PIECE -> { drawCircle(c.copy(alpha = 0.35f), w * 0.3f); drawCircle(c, w * 0.3f, style = st) }
        Portion.SLICE -> { val path = Path().apply { moveTo(w * 0.15f, h * 0.85f); lineTo(w * 0.85f, h * 0.85f); lineTo(w * 0.5f, h * 0.12f); close() }; drawPath(path, c.copy(alpha = 0.3f)); drawPath(path, c, style = st) }
        Portion.HANDFUL -> listOf(0.3f to 0.55f, 0.5f to 0.4f, 0.7f to 0.55f, 0.4f to 0.72f, 0.6f to 0.72f).forEach { (x, y) -> drawCircle(c, w * 0.09f, Offset(w * x, h * y)) }
        Portion.BREAD_Q, Portion.BREAD_H, Portion.BREAD_W -> {
            val frac = when (p) { Portion.BREAD_Q -> 0.25f; Portion.BREAD_H -> 0.5f; else -> 1f }
            val bx = w * 0.05f; val bw = w * 0.9f; val by = h * 0.35f; val bh = h * 0.32f
            drawRoundRect(c.copy(alpha = 0.4f), Offset(bx, by), Size(bw * frac, bh), CornerRadius(bh / 2, bh / 2))
            drawRoundRect(c, Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2, bh / 2), style = st)
        }
        Portion.TBSP, Portion.TSP -> { val k = if (p == Portion.TBSP) 1f else 0.7f
            drawOval(c, Offset(w * 0.1f, h * (0.5f - 0.15f * k)), Size(w * 0.38f * k, h * 0.3f * k), style = st)
            drawLine(c, Offset(w * (0.1f + 0.38f * k), h * 0.5f), Offset(w * 0.92f, h * 0.5f), sw, StrokeCap.Round) }
        Portion.G100 -> { drawRoundRect(c, Offset(w * 0.12f, h * 0.3f), Size(w * 0.76f, h * 0.55f), CornerRadius(3f, 3f), style = st); drawLine(c, Offset(w * 0.35f, h * 0.3f), Offset(w * 0.5f, h * 0.12f), sw); drawLine(c, Offset(w * 0.65f, h * 0.3f), Offset(w * 0.5f, h * 0.12f), sw) }
    }
}
