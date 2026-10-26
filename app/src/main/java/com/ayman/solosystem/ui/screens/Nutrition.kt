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
import com.ayman.solosystem.domain.nutrition.Foods
import com.ayman.solosystem.domain.nutrition.Nutrition
import com.ayman.solosystem.ui.*
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/** Potion accent for the Healing Elixir section (green-cyan). */
val Elixir = Color(0xFF2DF5C8)
private val ElixirSoft = Color(0xFF9FFBE6)
// themed blues (HOLO ↔ MONARCH); the mint Elixir identity of ترياق الشفاء stays in both themes
private val NutBlue get() = SystemTheme.c(0xFF7FD4FF, 0xFFD8B4FE)
private val NutWater get() = SystemTheme.c(0xFF67E8F9, 0xFFC4B5FD)
private val NutLabel get() = SystemTheme.c(0xFFBAE6FD, 0xFFE9D5FF)

@Composable
fun NutritionScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    val today = repo.today()
    val t = remember(s.profile, s.plan, s.bodyWeights, s.nutrition.kcalAdjust, s.quests, today) { Nutrition.targetsFor(s, today) }
    val inputs = remember(s.profile, s.plan, s.bodyWeights) { Nutrition.inputs(s) }
    val tot = remember(s.nutrition.meals, today) { Nutrition.totals(s, today) }
    Screen(tr("ترياق الشفاء", "Healing Elixir"), nav) {
        SysPanel(Modifier.fillMaxWidth(), accent = Elixir, contentKey = t?.kcal) {
            TitleBox(tr("ترياق الشفاء", "HEALING ELIXIR"), accent = Elixir)
            Spacer(Modifier.height(8.dp))
            Label(tr("أوامر التغذية لزيادة وزن صحية (عضل نظيف). منفصلة عن المهام: لا خبرة، لا مستويات، لا عقاب — التزام فقط.",
                "Nutrition orders for healthy lean gain. Separate from quests: no XP, no levels, no penalties — compliance only."), color = Sys.text, size = 13)
            Spacer(Modifier.height(4.dp))
            Label(tr("تقديري، ليس بديلاً عن طبيب/أخصائي تغذية.", "Estimate only — not a substitute for a doctor/dietitian."), color = Sys.amber, size = 12)
            Nutrition.warnings(inputs, s.profile).forEach { Label("⚠ " + it.get(ar), color = Sys.amber, size = 12) }
        }
        if (t == null) {
            SysPanel(Modifier.fillMaxWidth(), accent = Elixir, flares = false) {
                Label(tr("يحتاج النظام طولك ووزنك وجنسك ونشاطك اليومي.", "The System needs your height, weight, sex and daily activity."), color = Sys.text)
                Spacer(Modifier.height(8.dp))
                SysButton(tr("إكمال الملف الشخصي", "Complete profile"), Modifier.fillMaxWidth(), accent = Elixir) { nav.go("profile") }
            }
            // Meal logging works without targets — no typing of kcal needed.
            MealLogPanel(s, repo, today)
            return@Screen
        }
        TargetsPanel(t, inputs!!)
        ElixirWindowPanel(s, repo, today)
        ProgressPanel(s, repo, t, tot, today)
        MealLogPanel(s, repo, today)
        OrdersPanel(s, repo, today)
        WeighInPanel(s, repo, today, t)
        StatsPanel(s, t, today)
        SettingsPanel(s, repo)
    }
}

@Composable
private fun TargetsPanel(t: Nutrition.Targets, i: Nutrition.Inputs) {
    SysPanel(Modifier.fillMaxWidth(), accent = Elixir, flares = false, contentKey = t.kcal) {
        SectionHeader(tr("أهداف اليوم", "Today's targets"))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            GlowText("${t.kcal}", tech(40.sp), glow = Elixir)
            Spacer(Modifier.width(6.dp))
            Label(tr("كسل / يوم", "kcal / day"), color = ElixirSoft, size = 14)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Macro(tr("بروتين", "Protein"), UnitFmt.num("${t.protein}", UnitFmt.g(uAr())), Elixir)
            Macro(tr("ماء", "Water"), UnitFmt.num("%.1f".format(Locale.US, t.waterMl / 1000.0), UnitFmt.l(uAr())), NutWater)
        }
        Divider()
        KeyValue(tr("الكربوهيدرات / الدهون", "Carbs / fat"), UnitFmt.frac(t.carbs, t.fat, UnitFmt.g(uAr())))
        KeyValue(tr("الأيض الأساسي (Mifflin-St Jeor)", "BMR (Mifflin-St Jeor)"), UnitFmt.num("${t.bmr}", UnitFmt.kcal(uAr())))
        KeyValue(tr("الصيانة (نشاط + تدريب ${i.trainingDays}×${i.sessionMin}د)", "Maintenance (activity + ${i.trainingDays}×${i.sessionMin}m training)"), UnitFmt.num("${t.tdee}", UnitFmt.kcal(uAr())))
        KeyValue(tr("الفائض", "Surplus"), UnitFmt.num("+${t.surplus}", UnitFmt.kcal(uAr())), Elixir)
        if (t.runKcal > 0) {
            KeyValue(tr("طاقة الجري (أضيفت)", "Run energy (added)"), UnitFmt.num("+${t.runKcal}", UnitFmt.kcal(uAr())), Elixir)
            Label(tr("أضاف النظام طاقة جري اليوم (~1 كسل/كغ/كم) إلى هدفك حتى لا يأكل الجري فائض الزيادة.", "The System added today's run energy (~1 kcal/kg/km) to your target so running doesn't eat the gain surplus."), size = 12)
        }
        KeyValue(tr("البروتين / كغ", "Protein / kg"), UnitFmt.num("%.1f".format(Locale.US, t.proteinPerKg), UnitFmt.g(uAr())))
        KeyValue(tr("الزيادة المستهدفة أسبوعيًا", "Target gain / week"), UnitFmt.num("%.2f–%.2f".format(Locale.US, t.weeklyGainKg.first, t.weeklyGainKg.second), UnitFmt.kg(uAr())))
    }
}

@Composable
private fun Macro(label: String, v: String, c: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlowText(v, tech(18.sp, c), glow = c)
        Label(label, size = 12, color = NutLabel)
    }
}

@Composable
private fun ElixirWindowPanel(s: AppState, repo: Repository, today: Long) {
    val w = Nutrition.elixirWindow(s, today) ?: return
    var now by remember { mutableLongStateOf(repo.now()) }
    LaunchedEffect(w) { while (now < w.second) { delay(30_000); now = repo.now() } }
    if (now >= w.second) return
    val left = ((w.second - now) / 60000).toInt()
    SysPanel(Modifier.fillMaxWidth(), accent = Elixir, pulse = true) {
        TitleBox(tr("نافذة الترياق مفتوحة", "ELIXIR WINDOW OPEN"), accent = Elixir)
        Spacer(Modifier.height(6.dp))
        Label(tr("اكتملت الحصة. تناول وجبة التعافي (بروتين + كربوهيدرات) خلال الساعتين.", "Session complete. Take the recovery meal (protein + carbs) within 2 hours."), color = Sys.text)
        StatBar(tr("الوقت المتبقي", "Time left"), left.toFloat(), 120f, Elixir, valueText = "${left / 60}:${"%02d".format(left % 60)}")
    }
}

@Composable
private fun ProgressPanel(s: AppState, repo: Repository, t: Nutrition.Targets, tot: Nutrition.DayTotals, today: Long) {
    val water = s.nutrition.waterMl[today] ?: 0
    SysPanel(Modifier.fillMaxWidth(), accent = Elixir, flares = false, contentKey = tot.kcal) {
        SectionHeader(tr("تقدّم اليوم", "Today's progress"))
        StatBar(tr("السعرات", "Calories"), tot.kcal.toFloat(), t.kcal.toFloat(), Elixir, valueText = UnitFmt.frac(tot.kcal, t.kcal, UnitFmt.kcal(uAr())))
        StatBar(tr("البروتين", "Protein"), tot.protein.toFloat(), t.protein.toFloat(), Color(0xFF39F2C0), valueText = UnitFmt.frac(tot.protein.roundToInt(), t.protein, UnitFmt.g(uAr())))
        var details by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { details = !details }.padding(vertical = 6.dp)) {
            Label((if (details) "▾ " else "▸ ") + tr("التفاصيل: الكربوهيدرات والدهون", "Details: carbs & fat"), size = 12)
        }
        if (details) {
            StatBar(tr("الكربوهيدرات", "Carbs"), tot.carbs.toFloat(), t.carbs.toFloat(), NutBlue, valueText = UnitFmt.frac(tot.carbs.roundToInt(), t.carbs, UnitFmt.g(uAr())))
            StatBar(tr("الدهون", "Fat"), tot.fat.toFloat(), t.fat.toFloat(), Sys.amber, valueText = UnitFmt.frac(tot.fat.roundToInt(), t.fat, UnitFmt.g(uAr())))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatBar(tr("الماء", "Water"), water.toFloat(), t.waterMl.toFloat(), NutWater, Modifier.weight(1f), valueText = UnitFmt.frac(water, t.waterMl, UnitFmt.ml(uAr())))
            Spacer(Modifier.width(8.dp))
            SysButton("+250", small = true, accent = NutWater, sfx = com.ayman.solosystem.notify.Sfx.S.SOFT_WATER) { repo.set { Nutrition.addWater(it, today, 250) } }
        }
        val remain = t.kcal - tot.kcal
        if (remain > 0) Label(tr("أمر النظام: تبقّى \u2066$remain\u2069 كسل و\u2066${(t.protein - tot.protein).coerceAtLeast(0.0).roundToInt()}\u2069 غ بروتين اليوم.", "SYSTEM order: $remain kcal and ${(t.protein - tot.protein).coerceAtLeast(0.0).roundToInt()} g protein remain today."), color = ElixirSoft, size = 13)
        else Label(tr("اكتمل هدف السعرات اليوم.", "Calorie target reached today."), color = Elixir, size = 13)
    }
}

@Composable
private fun OrdersPanel(s: AppState, repo: Repository, today: Long) {
    val ar = LocalArabic.current
    val orders = remember(s.profile, s.plan, s.sessions, s.nutrition, s.settings.morningHour, s.settings.morningMinute, s.bodyWeights, today) {
        Nutrition.ordersFor(s, today, repo.now(), repo.dayStartMs(today))
    }
    val nowMin = remember(orders) { ((repo.now() - repo.dayStartMs(today)) / 60000).toInt() }
    val next = orders.firstOrNull { it.minute >= nowMin - 30 }
    val training = Nutrition.isTrainingDay(s, today)
    SysPanel(Modifier.fillMaxWidth(), accent = Elixir, flares = false) {
        SectionHeader(tr("أوامر اليوم", "Today's orders"))
        Label(if (training) tr("يوم تدريب — الحصة ${Nutrition.fmtTime(s.nutrition.trainHour * 60 + s.nutrition.trainMinute)}. لا وجبة ثقيلة قبل التدريب مباشرة.", "Training day — session at ${Nutrition.fmtTime(s.nutrition.trainHour * 60 + s.nutrition.trainMinute)}. No heavy meal right before training.")
              else tr("يوم راحة — البروتين موزّع على الوجبات للتعافي.", "Rest day — protein spread across meals for recovery."), size = 12)
        Spacer(Modifier.height(6.dp))
        orders.forEach { o ->
            val isNext = o == next
            val isElixir = o.kind == Nutrition.Kind.ELIXIR
            val c = if (isElixir) Elixir else if (isNext) NutBlue else Sys.panelLine
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).thinBox(c, if (isNext) Color(0x2214B8A6) else Color(0x55000000)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(Nutrition.fmtTime(o.minute), style = tech(16.sp, if (o.minute < nowMin - 30) Sys.dim else Sys.text))
                    Spacer(Modifier.width(10.dp))
                    GlowText(if (ar) o.kind.ar else o.kind.en, body(14.sp, FontWeight.Bold, if (isElixir) Elixir else Sys.text), Modifier.weight(1f), glow = c)
                    if (isNext) Label(tr("التالي", "NEXT"), color = NutBlue, size = 11, w = FontWeight.Bold)
                }
                Label("~" + UnitFmt.num("${o.kcal}", UnitFmt.kcal(ar)) + " • " + UnitFmt.num("${o.protein}", UnitFmt.g(ar)) + " ${tr("بروتين", "protein")} • " + UnitFmt.num("${o.carbs}", UnitFmt.g(ar)) + " ${tr("كربوهيدرات", "carbs")}", color = NutLabel, size = 12)
                Label(o.hint.get(ar), size = 12)
            }
        }
    }
}

@Composable
private fun WeighInPanel(s: AppState, repo: Repository, today: Long, t: Nutrition.Targets) {
    val ar = LocalArabic.current
    var txt by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val bad = tr("أدخل رقمًا صحيحًا", "Enter a valid number")
    val since = Nutrition.daysSinceWeighIn(s, today)
    val rate = Nutrition.weeklyRatePct(s.bodyWeights, today)
    SysPanel(Modifier.fillMaxWidth(), accent = Elixir, flares = false, contentKey = s.bodyWeights.size) {
        SectionHeader(tr("الوزن الأسبوعي والاتجاه", "Weekly weigh-in & trend"))
        val list = s.bodyWeights.sortedBy { it.day }.takeLast(20)
        if (list.size >= 2) LineChart(list.map { it.kg }, Modifier.fillMaxWidth().height(110.dp), Elixir)
        if (since == null || since >= 7) Label(tr("أمر النظام: سجّل وزنك (صباحًا، بعد الحمّام، قبل الأكل).", "SYSTEM order: log your weight (morning, after toilet, before eating)."), color = Sys.amber, size = 13)
        rate?.let {
            val ok = it in Nutrition.TARGET_LOW..Nutrition.TARGET_HIGH
            KeyValue(tr("الوتيرة (14 يومًا)", "Rate (14 days)"), "%.2f%% / ${tr("أسبوع", "wk")}".format(Locale.US, it), if (ok) Elixir else Sys.amber)
        } ?: Label(tr("يلزم 3 قياسات على الأقل خلال أسبوعين لحساب الاتجاه وتعديل السعرات تلقائيًا.", "At least 3 weigh-ins over 2 weeks are needed for the trend and auto-adjust."), size = 12)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextIn(txt, { txt = it; err = null }, tr("الوزن اليوم (كغ)", "Today's weight (kg)"), Modifier.weight(1f), number = true, error = err)
            Spacer(Modifier.width(8.dp))
            SysButton(tr("حفظ", "Save"), small = true, accent = Elixir, sfx = com.ayman.solosystem.notify.Sfx.S.SOFT_CONFIRM) {
                val v = txt.replace(',', '.').toDoubleOrNull()
                if (v == null || v !in 25.0..350.0) err = bad else { repo.set { GameEngine.addWeight(it, v, today) }; txt = "" }
            }
        }
        s.nutrition.adjustments.takeLast(3).reversed().forEach { a ->
            Label("${fmtDay(a.day, ar)}: ${a.reason.get(ar)}", color = if (a.delta != 0) ElixirSoft else Sys.dim, size = 12)
        }
    }
}

@Composable
private fun StatsPanel(s: AppState, t: Nutrition.Targets, today: Long) {
    val stats = remember(s.nutrition.meals, t, today) {
        val streak = Nutrition.streak(s, today)
        val orders = 5
        val week = (0L..6L).map { Nutrition.compliance(Nutrition.totals(s, today - it), Nutrition.targetsFor(s, today - it) ?: t, orders) }
        Triple(streak, week.first(), week.average().roundToInt())
    }
    SysPanel(Modifier.fillMaxWidth(), accent = Elixir, flares = false) {
        SectionHeader(tr("إحصائيات الترياق (منفصلة)", "Elixir stats (separate)"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Macro(tr("السلسلة", "Streak"), "${stats.first}", Elixir)
            Macro(tr("الأفضل", "Best"), "${maxOf(s.nutrition.bestStreak, stats.first)}", ElixirSoft)
            Macro(tr("التزام اليوم", "Today"), "${stats.second}%", NutBlue)
            Macro(tr("7 أيام", "7 days"), "${stats.third}%", NutBlue)
        }
        Label(tr("السلسلة = أيام متتالية بلغت ≥90٪ من السعرات والبروتين. لا تؤثر على الخبرة أو المستوى.", "Streak = consecutive days at ≥90% of kcal and protein. Does not affect XP or level."), size = 11, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SettingsPanel(s: AppState, repo: Repository) {
    val n = s.nutrition
    SysPanel(Modifier.fillMaxWidth(), accent = Elixir, flares = false) {
        SectionHeader(tr("إعدادات الترياق", "Elixir settings"))
        Stepper(tr("وقت التدريب المعتاد", "Usual training time"), Nutrition.fmtTime(n.trainHour * 60 + n.trainMinute),
            onMinus = { repo.set { val m = (n.trainHour * 60 + n.trainMinute - 30).coerceAtLeast(5 * 60); it.copy(nutrition = it.nutrition.copy(trainHour = m / 60, trainMinute = m % 60)) } },
            onPlus = { repo.set { val m = (n.trainHour * 60 + n.trainMinute + 30).coerceAtMost(23 * 60); it.copy(nutrition = it.nutrition.copy(trainHour = m / 60, trainMinute = m % 60)) } })
        Row(verticalAlignment = Alignment.CenterVertically) {
            SysCheck(n.remindersEnabled, color = Elixir) { repo.set { it.copy(nutrition = it.nutrition.copy(remindersEnabled = !it.nutrition.remindersEnabled)) } }
            Spacer(Modifier.width(8.dp))
            Label(tr("تذكيرات أوقات الوجبات (تتطلب تفعيل التذكيرات في الإعدادات)", "Meal-time reminders (requires reminders enabled in Settings)"), color = Sys.text, size = 13)
        }
        Label(tr("الطول، الوزن، الجنس، النشاط اليومي وأيام التدريب تُعدَّل من الملف الشخصي.", "Height, weight, sex, daily activity and training days are edited in the profile."), size = 11)
    }
}
