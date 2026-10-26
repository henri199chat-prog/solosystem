package com.ayman.solosystem.domain.nutrition

import com.ayman.solosystem.domain.model.Txt

/** Quick-add foods common in Tunisia. Approximate values per typical portion (estimates). */
data class Food(val id: String, val name: Txt, val portion: Txt, val kcal: Int, val protein: Double, val carbs: Double, val fat: Double)

object Foods {
    private fun f(id: String, ar: String, en: String, pa: String, pe: String, k: Int, p: Double, c: Double, fa: Double) = Food(id, Txt(ar, en), Txt(pa, pe), k, p, c, fa)
    val all = listOf(
        f("khobz", "خبز (باقات)", "Bread (baguette)", "نصف باقات ~125غ", "½ baguette ~125 g", 310, 10.0, 62.0, 2.0),
        f("tabouna", "خبز طابونة", "Tabouna bread", "قطعة ~100غ", "piece ~100 g", 270, 8.0, 54.0, 2.5),
        f("couscous", "كسكسي بالدجاج والخضرة", "Couscous with chicken & veg", "صحن ~400غ", "plate ~400 g", 560, 32.0, 75.0, 14.0),
        f("couscous_plain", "كسكسي مطبوخ", "Cooked couscous", "كوب ~160غ", "cup ~160 g", 180, 6.0, 36.0, 0.5),
        f("eggs2", "بيض (2)", "Eggs (2)", "2 بيضات", "2 large", 145, 12.6, 0.8, 9.6),
        f("ojja", "عجة", "Ojja", "صحن (2 بيض)", "plate (2 eggs)", 330, 15.0, 14.0, 24.0),
        f("chicken", "صدر دجاج مشوي", "Grilled chicken breast", "150غ", "150 g", 250, 46.0, 0.0, 5.5),
        f("tuna", "تن (معلّب)", "Tuna (can, drained)", "علبة ~80غ", "can ~80 g", 160, 20.0, 0.0, 9.0),
        f("lablabi", "لبلابي", "Lablabi", "صحن مع خبز وبيضة", "bowl with bread & egg", 550, 22.0, 75.0, 18.0),
        f("dates", "تمر (5 حبات)", "Dates (5)", "~40غ", "~40 g", 115, 1.0, 30.0, 0.2),
        f("milk", "حليب كامل", "Whole milk", "كوب 250 مل", "glass 250 ml", 155, 8.0, 12.0, 8.0),
        f("rayeb", "رايب / لبن", "Rayeb / leben", "250 مل", "250 ml", 140, 8.0, 11.0, 7.0),
        f("yogurt", "ياغرت", "Yogurt", "علبة 125غ", "cup 125 g", 75, 4.0, 6.0, 4.0),
        f("rice", "أرز مطبوخ", "Cooked rice", "كوب ~180غ", "cup ~180 g", 235, 4.5, 52.0, 0.5),
        f("pasta", "مقرونة مطبوخة", "Cooked pasta", "~200غ", "~200 g", 315, 11.0, 62.0, 2.0),
        f("makrouna_sauce", "مقرونة بالصلصة واللحم", "Pasta with sauce & meat", "صحن ~350غ", "plate ~350 g", 600, 28.0, 80.0, 18.0),
        f("olive_oil", "زيت زيتون", "Olive oil", "ملعقة كبيرة", "1 tbsp", 120, 0.0, 0.0, 13.5),
        f("chickpeas", "حمص مطبوخ", "Cooked chickpeas", "كوب ~160غ", "cup ~160 g", 270, 14.5, 45.0, 4.2),
        f("lentils", "عدس مطبوخ", "Cooked lentils", "كوب ~200غ", "cup ~200 g", 230, 18.0, 40.0, 0.8),
        f("loubia", "لوبيا", "White beans stew", "صحن ~250غ", "plate ~250 g", 330, 18.0, 50.0, 7.0),
        f("beef", "لحم (علوش/بقري)", "Lamb / beef", "150غ مطبوخ", "150 g cooked", 330, 38.0, 0.0, 19.0),
        f("sardines", "سردينة", "Sardines", "100غ", "100 g", 210, 25.0, 0.0, 11.0),
        f("cheese", "جبن", "Cheese", "30غ", "30 g", 100, 6.0, 0.5, 8.0),
        f("almonds", "لوز / كاكاوية", "Almonds / peanuts", "حفنة 30غ", "handful 30 g", 175, 6.0, 6.0, 15.0),
        f("oats", "شوفان", "Oats", "50غ", "50 g", 190, 6.5, 33.0, 3.5),
        f("banana", "موز", "Banana", "حبة", "1 medium", 105, 1.3, 27.0, 0.4),
        f("potato", "بطاطا مسلوقة", "Boiled potatoes", "200غ", "200 g", 175, 4.0, 40.0, 0.2),
        f("brik", "بريك بالبيض", "Brik with egg", "حبة", "1 piece", 290, 9.0, 22.0, 18.0),
        f("fricasse", "فريكاسي", "Fricassé", "حبة", "1 piece", 300, 9.0, 30.0, 16.0),
        f("whey", "بروتين واي", "Whey protein", "سكوب 30غ", "scoop 30 g", 120, 24.0, 3.0, 1.5)
    )
    fun get(id: String) = all.firstOrNull { it.id == id }
}
