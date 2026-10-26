package com.ayman.solosystem

import com.ayman.solosystem.data.StateStore
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.nutrition.FoodDb
import com.ayman.solosystem.domain.nutrition.FoodDb.Portion
import com.ayman.solosystem.domain.nutrition.Nutrition
import com.ayman.solosystem.domain.progression.HistoryFilter
import org.junit.Assert.*
import org.junit.Test

class FoodDbTest {
    private fun item(id: String) = FoodDb.get(id)!!

    @Test fun databaseIsLargeAndSane() {
        assertTrue("≥150 foods, got ${FoodDb.all.size}", FoodDb.all.size >= 150)
        assertEquals("unique ids", FoodDb.all.size, FoodDb.all.map { it.id }.toSet().size)
        FoodDb.all.forEach { f ->
            assertTrue(f.id, f.kcal100 in 0.0..900.0 && f.p100 >= 0 && f.c100 >= 0 && f.f100 >= 0)
            // Atwater consistency within a generous margin (composite dishes / fibre / alcohol-free drinks)
            val atw = f.p100 * 4 + f.c100 * 4 + f.f100 * 9
            assertEquals(f.id, f.kcal100, atw, maxOf(40.0, f.kcal100 * 0.2))
            assertTrue(f.id, f.portions.isNotEmpty() && f.portions.values.all { it > 0 } && Portion.G100 in f.portions)
            assertTrue(f.id, f.ar.isNotBlank() && f.en.isNotBlank())
        }
    }

    @Test fun portionMathScalesLinearly() {
        val egg = item("eggs2")
        val one = FoodDb.calc(egg, Portion.PIECE, 1.0)
        assertEquals(50, one.grams); assertEquals(78, one.kcal); assertEquals(6.5, one.protein, 1e-9)
        val two = FoodDb.calc(egg, Portion.PIECE, 2.0)
        assertEquals(100, two.grams); assertEquals(155, two.kcal); assertEquals(13.0, two.protein, 1e-9)
        val half = FoodDb.calc(item("khobz"), Portion.BREAD_H, 1.0)          // ½ baguette 125 g × 270 kcal/100 g
        assertEquals(125, half.grams); assertEquals(338, half.kcal)
        val q = FoodDb.calc(item("khobz"), Portion.BREAD_Q, 1.0)
        assertTrue(q.kcal < half.kcal)
        val plate = FoodDb.calc(item("couscous"), Portion.PLATE_M, 1.5)      // 350 g × 1.5 = 525 g
        assertEquals(525, plate.grams); assertEquals(788, plate.kcal); assertEquals(47.3, plate.protein, 1e-9)
        val g100 = FoodDb.calc(item("chicken"), Portion.G100, 1.0)
        assertEquals(165, g100.kcal); assertEquals(31.0, g100.protein, 1e-9)
        // portion sizes ordered
        val cc = item("couscous").portions
        assertTrue(cc.getValue(Portion.PLATE_S) < cc.getValue(Portion.PLATE_M) && cc.getValue(Portion.PLATE_M) < cc.getValue(Portion.PLATE_L))
    }

    @Test fun searchArabicTunisianEnglish() {
        fun top(q: String) = FoodDb.search(q).firstOrNull()?.id
        assertTrue(FoodDb.search("كسكسي").take(6).all { it.id.startsWith("couscous") })
        assertEquals("couscous", top("كسكسي بالدجاج"))
        assertEquals("couscous_lamb", top("كسكسي باللحم"))
        assertTrue(FoodDb.search("kosksi").any { it.id == "couscous" })
        assertEquals("lablabi", top("لبلابي")); assertEquals("lablabi", top("lablebi")); assertEquals("lablabi", top("Lablabi"))
        assertEquals("mlawi", top("ملاوي")); assertEquals("mlawi", top("mlewi"))
        assertEquals("ojja", top("عجة")); assertEquals("ojja", top("عجه"))      // ta-marbuta folded
        assertEquals("brik", top("بريك")); assertEquals("pizza", top("pizza")); assertEquals("shawarma", top("شاورما"))
        assertTrue(FoodDb.search("مقرونة").first().id.startsWith("makrouna"))
        assertTrue(FoodDb.search("أرز").any { it.id == "rice" })                 // hamza folded → ارز alias
        assertTrue(FoodDb.search("كَسْكَسِي").isNotEmpty())                      // diacritics stripped
        assertEquals("tea_sugar", top("شاي بالسكر")); assertEquals("coffee_sugar", top("قهوة"))
        assertTrue(FoodDb.search("").isEmpty()); assertTrue(FoodDb.search("zzzqqq").isEmpty())
    }

    @Test fun logFoodFavoritesRecentsRepeatYesterday() {
        val s0 = Fx.state()
        val d = 20_000L
        var s = Nutrition.logFood(s0, item("couscous"), Portion.PLATE_L, 1.0, d - 1, 1_000L)
        s = Nutrition.logFood(s, item("eggs2"), Portion.PIECE, 2.0, d - 1, 2_000L)
        val m = s.nutrition.meals.last()
        assertEquals("eggs2", m.foodId); assertEquals("PIECE", m.portion); assertEquals(2.0, m.qty, 0.0); assertEquals(100, m.grams); assertEquals(155, m.kcal)
        assertEquals(listOf("eggs2", "couscous"), Nutrition.recents(s).map { it.foodId })
        val y = Nutrition.mealsOn(s, d - 1)
        s = Nutrition.repeatMeals(s, y, d, 5_000L)
        val today = Nutrition.mealsOn(s, d)
        assertEquals(2, today.size)
        assertEquals(y.sumOf { it.kcal }, today.sumOf { it.kcal })
        assertEquals(4, s.nutrition.meals.map { it.id }.toSet().size)
        assertEquals(Nutrition.totals(s, d - 1).kcal, Nutrition.totals(s, d).kcal)
        s = Nutrition.toggleFavorite(s, "lablabi"); assertEquals(listOf("lablabi"), s.nutrition.favorites)
        s = Nutrition.toggleFavorite(s, "lablabi"); assertTrue(s.nutrition.favorites.isEmpty())
    }

    @Test fun legacyMealsDecodeIntact() {
        // a 1.1.0/1.2.x-shaped meal (no portion/grams/favorites keys) must load unchanged
        val s0 = Fx.state()
        val legacy = MealLog("m1", 100L, 5L, Txt("خبز طابونة", "Tabouna bread"), 270, 8.0, 54.0, 2.5, "tabouna")
        val manual = MealLog("m2", 100L, 6L, Txt("وجبة", "وجبة"), 640, 31.0)
        val json = StateStore.encode(s0.copy(nutrition = s0.nutrition.copy(meals = listOf(legacy, manual))))
            .replace(Regex(",\\s*\"favorites\":\\[\\]"), "")
        assertFalse(json.contains("\"portion\"")); assertFalse(json.contains("\"favorites\""))
        val back = StateStore.decode(json)
        assertEquals(listOf(legacy, manual), back.nutrition.meals)
        assertTrue(back.nutrition.favorites.isEmpty())
        assertNull(back.nutrition.meals[0].portion)
    }

    @Test fun historyQuestsTabShowsQuestCompletions() {
        val done = HistoryEvent(1, 1, HistoryKind.XP, Txt("أكملت: سكوات", "Completed: Squats"), 20)
        val missed = HistoryEvent(2, 1, HistoryKind.QUEST_MISSED, Txt("فاتت", "Missed"))
        val pen = HistoryEvent(3, 1, HistoryKind.PENALTY_DONE, Txt("عقوبة", "Penalty"))
        val workout = HistoryEvent(4, 1, HistoryKind.WORKOUT, Txt("حصة", "Workout"))
        val ach = HistoryEvent(5, 1, HistoryKind.ACHIEVEMENT, Txt("إنجاز", "Achievement"))
        val all = listOf(done, missed, pen, workout, ach)
        assertEquals(listOf(done, missed, pen), HistoryFilter.filter(all, HistoryFilter.Tab.QUESTS))
        assertEquals(listOf(workout), HistoryFilter.filter(all, HistoryFilter.Tab.TRAINING))
        assertEquals(listOf(ach), HistoryFilter.filter(all, HistoryFilter.Tab.REWARDS))
        assertEquals(all, HistoryFilter.filter(all, HistoryFilter.Tab.ALL))
        assertTrue(done in HistoryFilter.filter(all, HistoryFilter.Tab.XP))
        // a real engine run: completing a quest must surface in the quests tab
        val s0 = Fx.state()
        val q = s0.quests.first { it.day == Fx.MON && it.status == QuestStatus.PENDING }
        val s1 = GameEngine.completeQuest(s0, q.id, Fx.MON, Fx.NOW).state
        val fresh = s1.history.drop(s0.history.size)
        assertTrue(fresh.isNotEmpty() && HistoryFilter.filter(fresh, HistoryFilter.Tab.QUESTS).isNotEmpty())
    }
}
