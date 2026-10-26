package com.ayman.solosystem.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.Metrics
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.rewards.*
import com.ayman.solosystem.domain.skills.Branch
import com.ayman.solosystem.domain.skills.SkillCatalog
import com.ayman.solosystem.ui.*
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*

@Composable
fun SkillsScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    val metrics = remember(s) { Metrics.compute(s, repo.today()) }
    var branch by remember { mutableIntStateOf(0) }
    Screen(tr("المهارات", "Skills"), nav) {
        val unlocked = SkillCatalog.all.count { it.level(metrics[it.metric] ?: 0) > 0 }
        SysPanel(Modifier.fillMaxWidth(), contentKey = branch) {
            TitleBox(tr("المهارات", "SKILLS"))
            Spacer(Modifier.height(6.dp))
            Label(tr("مفتوحة: ", "Unlocked: ") + "$unlocked/${SkillCatalog.all.size}", Modifier.fillMaxWidth(), Sys.text, 14, TextAlign.Center)
            Label(tr("المهارات تُفتح بسلوك حقيقي مسجل. تأثيراتها داخل اللعبة فقط.", "Skills unlock from real logged behaviour. Their effects are in-game only."), size = 12, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Tabs(Branch.entries.map { if (ar) it.ar else it.en }, branch) { branch = it }
        }
        SkillCatalog.all.filter { it.branch == Branch.entries[branch] }.forEach { sk ->
            val v = metrics[sk.metric] ?: 0
            SkillCard(sk, sk.level(v), v)
        }
    }
}

@Composable
fun InventoryScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    val today = repo.today()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(tr("الصناديق", "Boxes"), tr("الألقاب", "Titles"), tr("السمات", "Themes"), tr("الإطارات", "Frames"), tr("الشعارات", "Emblems"), tr("الشارات", "Badges"), tr("المتجر", "Shop"))
    Screen(tr("المخزن", "Inventory"), nav) {
        SysPanel(Modifier.fillMaxWidth(), contentKey = tab) {
            TitleBox(tr("المخزن", "INVENTORY"))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                SysIcon(Ico.GOLD, Sys.gold); GlowText("${Ledger.goldBalance(s)}", tech(22.sp), glow = Sys.gold)
                Label(tr("صناديق فُتحت: ", "Boxes opened: ") + s.loot.opened, size = 12)
            }
            Label(tr("ضمان: ملحمي خلال ", "Pity: epic within ") + (Loot.PITY_EPIC - s.loot.sinceEpic).coerceAtLeast(1) + tr(" • أسطوري خلال ", " • legendary within ") + (Loot.PITY_LEGENDARY - s.loot.sinceLegendary).coerceAtLeast(1), Modifier.fillMaxWidth(), size = 11, align = TextAlign.Center)
            Tabs(tabs, tab) { tab = it }
        }
        fun owned(id: String) = Rewards.owned(s, id)
        when (tab) {
            0 -> {
                val boxes = RewardCatalog.boxes.filter { (s.inventory[it.id] ?: 0) > 0 }
                val tokens = RewardCatalog.all.filter { it.kind == RewardKind.TOKEN && (s.inventory[it.id] ?: 0) > 0 }
                s.secretClaims.values.filter { !it.claimed }.forEach { c ->
                    SysPanel(Modifier.fillMaxWidth(), accent = com.ayman.solosystem.ui.SecretViolet) {
                        com.ayman.solosystem.ui.GlitchText(tr("مكافآت المهمة السرّية بانتظارك", "Secret Quest rewards waiting"), 16)
                        Label(java.time.LocalDate.ofEpochDay(c.day).toString(), Modifier.fillMaxWidth(), align = TextAlign.Center, size = 12)
                        SysButton(tr("تفقد مكافآتك", "Check rewards"), Modifier.fillMaxWidth(), small = true, accent = com.ayman.solosystem.ui.SecretViolet) {
                            repo.apply { st -> com.ayman.solosystem.domain.game.Outcome(st, listOf(com.ayman.solosystem.domain.game.UiEffect.SecretReward(c.day, direct = true))) } }
                    }
                }
                com.ayman.solosystem.domain.game.Raids.activeKeys(s, today).forEach { k ->
                    SysPanel(Modifier.fillMaxWidth(), accent = com.ayman.solosystem.ui.Crimson) {
                        com.ayman.solosystem.ui.KeyItemCard(k, today)
                        SysButton(tr("استخدم المفتاح", "Use key"), Modifier.fillMaxWidth(), small = true, accent = com.ayman.solosystem.ui.Crimson) { repo.apply { st -> GameEngine.raidGate(st, k.id, today) } }
                    }
                }
                if (s.raids.isNotEmpty() || s.raidKeys.isNotEmpty()) SysButton(tr("الغارات وسجلها", "Raids & log"), Modifier.fillMaxWidth(), small = true, accent = com.ayman.solosystem.ui.Crimson) { nav.go("raid") }
                if (boxes.isEmpty() && tokens.isEmpty()) Label(tr("لا صناديق الآن. أكمل المهام والأيام الكاملة لتحصل عليها.", "No boxes now. Complete quests and full days to earn them."), Modifier.fillMaxWidth(), align = TextAlign.Center)
                boxes.forEach { b -> RewardCard(b, extra = "×${s.inventory[b.id]}") {
                    Spacer(Modifier.height(6.dp)); SysButton(tr("افتح", "Open"), Modifier.fillMaxWidth(), small = true, accent = b.rarity.c()) { repo.apply { st -> GameEngine.openBox(st, b.id, today, repo.now()) } }
                } }
                tokens.forEach { t -> RewardCard(t, extra = "×${s.inventory[t.id]}") {
                    if (t.token != TokenType.REROLL) { Spacer(Modifier.height(6.dp)); SysButton(tr("استخدم", "Use"), Modifier.fillMaxWidth(), small = true) { repo.apply { st -> GameEngine.useToken(st, t.id, today, repo.now()) } } }
                    else Label(tr("يُستخدم تلقائيًا عند إعادة سحب مهمة.", "Used automatically when you reroll a quest."), size = 12)
                } }
                if (s.rewardLog.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
                    SectionHeader(tr("آخر المكافآت", "Recent rewards"))
                    s.rewardLog.takeLast(10).reversed().forEach { g -> RewardCatalog.get(g.rewardId)?.let { d ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) { RewardIcon(d, 24.dp); Spacer(Modifier.width(8.dp)); Label(d.name.get(ar) + (if (g.goldInstead > 0) " → +${g.goldInstead} " + tr("ذهب", "gold") else "") + " • " + g.source.get(ar), Modifier.weight(1f), Sys.text, 12) }
                    } }
                }
            }
            1 -> Cosmetics(s, repo, RewardCatalog.titles, s.equippedTitle, ::owned, allowNone = true)
            2 -> Cosmetics(s, repo, RewardCatalog.themes, s.equippedTheme, ::owned)
            3 -> Cosmetics(s, repo, RewardCatalog.frames, s.equippedFrame, ::owned)
            4 -> Cosmetics(s, repo, RewardCatalog.emblems, s.equippedEmblem, ::owned)
            5 -> {
                val got = RewardCatalog.badges.count { owned(it.id) }
                Label(tr("الشارات: ", "Badges: ") + "$got/${RewardCatalog.badges.size}", Modifier.fillMaxWidth(), Sys.text, 14, TextAlign.Center)
                val m = remember(s) { com.ayman.solosystem.domain.game.Metrics.compute(s, today) }
                RewardCatalog.badges.sortedBy { if (owned(it.id)) 0 else 1 }.forEach { b ->
                    val has = owned(b.id)
                    RewardCard(b, locked = !has, extra = if (has) tr("مكتسبة", "Earned") else if (b.hidden) null else (b.metric?.let { (if (ar) it.ar else it.en) + ": ${(m[it] ?: 0).coerceAtMost(b.threshold)}/${b.threshold}" }))
                }
            }
            else -> Shop(s, repo, today)
        }
    }
}

@Composable
private fun Cosmetics(s: AppState, repo: Repository, list: List<RewardDef>, equipped: String?, owned: (String) -> Boolean, allowNone: Boolean = false) {
    val have = list.filter { owned(it.id) }
    Label(tr("تملك ", "Owned ") + "${have.size}/${list.size}" + tr(" — الباقي من الصناديق والإنجازات", " — the rest drop from boxes and milestones"), Modifier.fillMaxWidth(), align = TextAlign.Center, size = 12)
    if (allowNone && equipped != null) SysButton(tr("إزالة اللقب (استخدم لقب المستوى)", "Unequip (use level title)"), Modifier.fillMaxWidth(), small = true, accent = Sys.dim) { repo.set { GameEngine.equip(it, null) } }
    have.forEach { d ->
        RewardCard(d) {
            Spacer(Modifier.height(6.dp))
            if (d.id == equipped) Label(tr("مُجهّز ✓", "Equipped ✓"), color = Sys.green, size = 13, w = FontWeight.Bold)
            else SysButton(tr("تجهيز", "Equip"), Modifier.fillMaxWidth(), small = true, accent = d.rarity.c()) { repo.set { GameEngine.equip(it, d.id) } }
        }
    }
    val locked = list.count { !owned(it.id) }
    if (locked > 0) Label(tr("$locked عنصر لم يُكتشف بعد.", "$locked items not discovered yet."), Modifier.fillMaxWidth(), align = TextAlign.Center, size = 12)
}

@Composable
private fun Shop(s: AppState, repo: Repository, today: Long) {
    val ar = LocalArabic.current
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var editMode by remember { mutableStateOf(false) }
    val locked = GameEngine.shopLocked(s, today)
    val badName = tr("أدخل اسمًا (من 1 إلى 40 حرفًا)", "Enter a name (1–40 chars)"); val badCost = tr("السعر من 1 إلى 100000 ذهب", "Price 1–100000")
    SysPanel(Modifier.fillMaxWidth(), accent = Sys.gold) {
        TitleBox(tr("متجر", "SHOP"), accent = Sys.gold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            SysIcon(Ico.GOLD, Sys.gold); Spacer(Modifier.width(6.dp)); GlowText("${Ledger.goldBalance(s)} G", tech(22.sp), glow = Sys.gold)
        }
        if (locked) Label(tr("المتجر مقفل حتى تُنهي مهمة العقاب أو تراجع المهام الفائتة.", "Shop locked until you clear the penalty quest or review missed quests."), Modifier.fillMaxWidth(), Sys.red, 13, TextAlign.Center)
        Label(tr("مكافآت حقيقية تحددها أنت (ترفيه، وجبة مفضلة...). اشترِها بالذهب المكتسب.", "Real rewards you define (fun, a favourite meal...). Buy them with earned gold."), size = 12)
        Spacer(Modifier.height(8.dp))
        if (s.rewards.isEmpty()) Label(tr("أضف أول مكافأة بالأسفل.", "Add your first reward below."), Modifier.fillMaxWidth(), align = TextAlign.Center)
        s.rewards.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { r ->
                    Column(Modifier.weight(1f)) {
                        ShopTile(r.name, "${r.cost} G", Modifier.fillMaxWidth(), enabled = !locked && Ledger.goldBalance(s) >= r.cost, accent = Sys.gold) { repo.apply { st -> GameEngine.buy(st, r.id, today, repo.now()) } }
                        if (editMode) SysButton(tr("حذف", "Remove"), Modifier.fillMaxWidth(), small = true, accent = Sys.red) { repo.set { GameEngine.removeShopReward(it, r.id) } }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (s.rewards.isNotEmpty()) SysButton(if (editMode) tr("تم", "Done") else tr("تعديل", "Edit"), small = true, accent = Sys.dim) { editMode = !editMode }
        Divider()
        TextIn(name, { name = it.take(40); err = null }, tr("اسم المكافأة", "Reward name"), Modifier.fillMaxWidth())
        TextIn(cost, { cost = it.filter(Char::isDigit).take(6); err = null }, tr("السعر بالذهب", "Price in gold"), Modifier.fillMaxWidth(), number = true, error = err)
        SysButton(tr("إضافة", "Add"), Modifier.fillMaxWidth(), small = true, accent = Sys.gold) {
            val c = cost.toIntOrNull()
            when { name.isBlank() -> err = badName; c == null || c !in 1..100000 -> err = badCost
                else -> { repo.set { GameEngine.addShopReward(it, name, c, repo.now()) }; name = ""; cost = "" } }
        }
    }
    if (s.purchases.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
        SectionHeader(tr("المشتريات", "Purchases"))
        s.purchases.takeLast(15).reversed().forEach { p -> Row { Label(p.name, Modifier.weight(1f), Sys.text, 13); Label("-${p.cost} G", color = Sys.gold, size = 13) } }
    }
}
