package com.ayman.solosystem.domain.nutrition

import com.ayman.solosystem.domain.model.Txt
import kotlin.math.roundToInt

/**
 * v1.2.2 offline food database — Tunisian / Maghrebi everyday food, logged WITHOUT typing calories.
 *
 * Values are per 100 g (or 100 ml) from standard composition tables (USDA FoodData Central SR-Legacy / CIQUAL-ANSES for single
 * foods; composite Tunisian dishes estimated from standard home recipes and cross-checked against published per-portion values).
 * Portions are household units (plate sizes, glass, ladle, piece, bread fraction, spoon…) mapped to grams per food.
 * The user picks food + portion + quantity; the System computes kcal and protein.
 */
object FoodDb {
    enum class Portion(val ar: String, val en: String) {
        PLATE_S("صحن صغير", "Small plate"), PLATE_M("صحن متوسط", "Medium plate"), PLATE_L("صحن كبير", "Large plate"),
        BOWL("زلافة", "Bowl"), LADLE("مغرفة", "Ladle"), GLASS("كاس", "Glass"), CUP("فنجان", "Cup"),
        PIECE("حبة", "Piece"), SLICE("شريحة", "Slice"), HANDFUL("حفنة", "Handful"),
        BREAD_Q("طرف خبز ¼", "¼ loaf"), BREAD_H("نصف خبزة ½", "½ loaf"), BREAD_W("خبزة كاملة", "Whole loaf"),
        TBSP("ملعقة كبيرة", "Tablespoon"), TSP("ملعقة صغيرة", "Teaspoon"), G100("100 غ", "100 g")
    }

    enum class Cat(val ar: String, val en: String) {
        DISH("أطباق", "Dishes"), SOUP("شربة ومرق", "Soups & stews"), FAST("أكل سريع", "Fast food"), BREAD("خبز ومعجنات", "Bread & pastry"),
        PROTEIN("لحوم وبيض وحوت", "Meat, eggs, fish"), DAIRY("حليب وأجبان", "Dairy"), SALAD("سلطات وخضر", "Salads & veg"),
        FRUIT("غلال", "Fruit"), NUTS("فواكه جافة", "Nuts & dried"), SWEET("حلويات", "Sweets"), DRINK("مشروبات", "Drinks"), EXTRA("إضافات", "Extras")
    }

    data class Item(val id: String, val ar: String, val en: String, val aliases: List<String>, val cat: Cat,
                    val kcal100: Double, val p100: Double, val c100: Double, val f100: Double, val portions: Map<Portion, Int>) {
        val name get() = Txt(ar, en)
        val defaultPortion: Portion get() = portions.keys.first()
    }

    data class Calc(val grams: Int, val kcal: Int, val protein: Double, val carbs: Double, val fat: Double)

    /** Core portion math: grams = portion grams × qty; nutrients scale linearly from per-100 g values. */
    fun calc(item: Item, portion: Portion, qty: Double): Calc {
        val g = (item.portions[portion] ?: if (portion == Portion.G100) 100 else item.portions.values.first()) * qty
        fun r1(x: Double) = (x * 10).roundToInt() / 10.0
        return Calc(g.roundToInt(), (item.kcal100 * g / 100.0).roundToInt(), r1(item.p100 * g / 100.0), r1(item.c100 * g / 100.0), r1(item.f100 * g / 100.0))
    }

    val QTY = listOf(0.5, 1.0, 1.5, 2.0)

    private val P = Portion.entries.associateBy { it.name }
    private fun por(spec: String): Map<Portion, Int> = spec.split(' ').filter { it.isNotBlank() }.associate { val (k, v) = it.split('='); P.getValue(k) to v.toInt() }
    private val DEF = mapOf(
        Cat.DISH to "PLATE_M=350 PLATE_S=250 PLATE_L=500 LADLE=150",
        Cat.SOUP to "BOWL=300 LADLE=200 PLATE_L=450",
        Cat.SALAD to "PLATE_S=150 PLATE_M=250 PLATE_L=350 TBSP=20",
        Cat.DRINK to "GLASS=250",
        Cat.NUTS to "HANDFUL=30 TBSP=10",
    )
    private fun d(id: String, ar: String, en: String, al: String, cat: Cat, k: Number, p: Number, c: Number, f: Number, portions: String? = null) =
        Item(id, ar, en, al.split('|').map { it.trim() }.filter { it.isNotEmpty() }, cat, k.toDouble(), p.toDouble(), c.toDouble(), f.toDouble(),
            por(portions ?: DEF[cat] ?: error("portions required for $id")) + (Portion.G100 to 100))

    val all: List<Item> = listOf(
        // ── Dishes ──
        d("couscous", "كسكسي بالدجاج", "Couscous with chicken", "كسكسى|كسكسي دجاج|kosksi|kousksi|couscous poulet|couscous chicken", Cat.DISH, 150, 9, 18, 4.5),
        d("couscous_lamb", "كسكسي باللحم", "Couscous with lamb/beef", "كسكسي علوش|كسكسي بالعلوش|kosksi lham|couscous agneau|couscous meat", Cat.DISH, 170, 9, 18, 7),
        d("couscous_fish", "كسكسي بالحوت", "Couscous with fish", "كسكسي حوت|kosksi hout|couscous poisson", Cat.DISH, 140, 9, 18, 3.5),
        d("couscous_veg", "كسكسي بالخضرة", "Vegetable couscous", "كسكسي خضرة|kosksi khodra", Cat.DISH, 125, 4, 21, 3),
        d("couscous_osban", "كسكسي بالعصبان", "Couscous with osban", "عصبان|osban|osbane", Cat.DISH, 175, 9, 17, 8),
        d("couscous_kadid", "كسكسي بالقديد", "Couscous with kadid", "قديد|kadid|guedid", Cat.DISH, 180, 9, 18, 8),
        d("couscous_plain", "كسكسي مطبوخ (سادة)", "Plain cooked couscous", "كسكسي سادة|couscous nature", Cat.DISH, 112, 3.8, 23, 0.2, "PLATE_M=250 PLATE_S=160 PLATE_L=350 LADLE=120"),
        d("masfouf", "مسفوف", "Masfouf (sweet couscous)", "msfouf|mesfouf", Cat.DISH, 210, 4.5, 35, 6, "BOWL=200 PLATE_M=300 PLATE_S=150"),
        d("mhamsa", "محمصة", "Mhamsa", "mhamsa|محمصه", Cat.DISH, 145, 6, 19, 5),
        d("makrouna_sauce", "مقرونة بالصلصة واللحم", "Pasta with sauce & meat", "مقرونة|مقرونه|makrouna|maqrouna|pasta|spaghetti|سباقيتي", Cat.DISH, 175, 8, 22, 6),
        d("makrouna_chicken", "مقرونة بالدجاج", "Pasta with chicken", "مقرونة دجاج|makrouna djej|pates poulet", Cat.DISH, 165, 9, 22, 4.5),
        d("makrouna_plain_sauce", "مقرونة بالصلصة (بلا لحم)", "Pasta in tomato sauce", "مقرونة صلصة|makrouna salsa", Cat.DISH, 150, 5, 25, 3.5),
        d("makrouna_four", "مقرونة في الفرن", "Baked pasta (gratin)", "مقرونة فرن|gratin|makrouna fel four", Cat.DISH, 180, 9, 20, 7),
        d("lasagna", "لازانيا", "Lasagna", "lasagne|lazanya", Cat.DISH, 135, 8, 13, 5.5),
        d("pasta", "مقرونة مسلوقة (سادة)", "Plain boiled pasta", "pates|pasta plain", Cat.DISH, 158, 5.8, 31, 0.9, "PLATE_M=250 PLATE_S=180 PLATE_L=350 LADLE=120"),
        d("rechta", "رشتة بالدجاج", "Rechta with chicken", "rechta|rachta", Cat.DISH, 150, 8, 22, 4),
        d("nwasser", "نواصر", "Nwasser", "nouasser|nwasser", Cat.DISH, 150, 8, 22, 4),
        d("rice", "روز مطبوخ (سادة)", "Plain cooked rice", "أرز|ارز|روز|rouz|riz|rice", Cat.DISH, 130, 2.7, 28, 0.3, "PLATE_M=250 PLATE_S=180 PLATE_L=350 LADLE=120"),
        d("rice_djerbi", "روز جربي", "Djerbian rice", "rouz jerbi|riz djerbien", Cat.DISH, 160, 8, 20, 5.5),
        d("rice_salad", "سلاطة روز", "Rice salad", "salade riz", Cat.DISH, 150, 5, 20, 5.5),
        d("mloukhia", "ملوخية", "Mloukhia", "mloukhia|mloukhiya|ملوخيه", Cat.DISH, 170, 9, 3, 14),
        d("kafteji", "كفتاجي", "Kafteji", "kafteji|kaftaji|كفتاجى", Cat.DISH, 190, 6, 12, 13),
        d("chakchouka", "شكشوكة", "Chakchouka", "chakchouka|shakshuka|شكشوكه", Cat.DISH, 110, 5, 7, 7.5),
        d("ojja", "عجة", "Ojja", "عجه|ojja|3ejja", Cat.DISH, 150, 7, 6, 11, "PLATE_M=300 PLATE_S=200 PLATE_L=420"),
        d("ojja_merguez", "عجة بالمرقاز", "Ojja with merguez", "عجة مرقاز|ojja merguez", Cat.DISH, 190, 9, 5, 15, "PLATE_M=300 PLATE_S=200 PLATE_L=420"),
        d("tajine", "طاجين (تونسي)", "Tunisian tajine (egg bake)", "طاجين مالح|tajine|tagine|طاجين دجاج", Cat.DISH, 220, 13, 6, 16, "SLICE=120 PIECE=120 PLATE_M=200"),
        d("kamounia", "كمونية", "Kamounia", "kamounia|كمونيه", Cat.DISH, 150, 15, 5, 8),
        d("loubia", "لوبيا", "White bean stew", "لوبية|loubia|haricot", Cat.DISH, 120, 6, 15, 4),
        d("lentils", "عدس (مرقة)", "Lentil stew", "عدس|adas|3des|lentilles", Cat.DISH, 110, 6.5, 15, 3),
        d("foul", "فول مدمس", "Foul (fava beans)", "فول|foul|fool", Cat.DISH, 140, 7, 15, 5.5),
        d("chickpeas", "حمص مطبوخ", "Cooked chickpeas", "حمص|homs|pois chiche", Cat.DISH, 164, 8.9, 27, 2.6, "BOWL=160 LADLE=120 PLATE_M=250"),
        d("potato", "بطاطا مسلوقة", "Boiled potatoes", "بطاطا|batata|pomme de terre", Cat.DISH, 87, 1.9, 20, 0.1, "PLATE_M=250 PLATE_S=150 PIECE=150"),
        d("potato_oven", "بطاطا في الفرن", "Oven potatoes", "batata four|potatoes oven", Cat.DISH, 120, 2, 18, 4.5, "PLATE_M=250 PLATE_S=150 PLATE_L=350"),
        d("calamari", "كلمار مقلي", "Fried calamari", "calamar|كلامار", Cat.DISH, 175, 18, 8, 7.5, "PLATE_S=150 PLATE_M=220 PLATE_L=300"),
        // ── Soups & stews ──
        d("lablabi", "لبلابي", "Lablabi", "لبلابى|lablabi|lablebi|leblebi", Cat.SOUP, 120, 6, 16, 3.5, "BOWL=450 PLATE_L=550 LADLE=200"),
        d("chorba_frik", "شربة فريك", "Frik soup", "شربة|شوربة|chorba|chorba frik|شربه", Cat.SOUP, 75, 4, 9, 2.5),
        d("chorba_fish", "شربة حوت", "Fish soup", "chorba hout|soupe poisson", Cat.SOUP, 70, 6, 7, 2),
        d("chorba_lsan", "شربة لسان عصفور", "Orzo soup", "lsan asfour|langue d'oiseau", Cat.SOUP, 80, 4, 11, 2),
        d("hlalem", "حلالم", "Hlalem", "hlalem", Cat.SOUP, 85, 4, 12, 2.5),
        d("veg_soup", "شربة خضرة", "Vegetable soup", "soupe legumes", Cat.SOUP, 40, 1.5, 6, 1.2),
        d("marka_chicken", "مرقة دجاج", "Chicken stew", "مرقة|مرقه|marka|marka djej", Cat.SOUP, 110, 8, 6, 6, "LADLE=200 BOWL=300 PLATE_M=350"),
        d("marka_lamb", "مرقة لحم", "Meat stew", "marka lham|مرقة علوش", Cat.SOUP, 140, 9, 6, 9, "LADLE=200 BOWL=300 PLATE_M=350"),
        d("marka_peas", "مرقة جلبانة", "Pea stew", "جلبانة|jelbana|petits pois", Cat.SOUP, 105, 6, 10, 4.5, "LADLE=200 BOWL=300 PLATE_M=350"),
        d("marka_potato", "مرقة بطاطا", "Potato stew", "marka batata", Cat.SOUP, 120, 4, 13, 6, "LADLE=200 BOWL=300 PLATE_M=350"),
        d("marka_veg", "مرقة خضرة", "Vegetable stew", "marka khodra", Cat.SOUP, 80, 2, 9, 4.5, "LADLE=200 BOWL=300 PLATE_M=350"),
        // ── Fast food & street food ──
        d("brik", "بريك بالبيض", "Brik with egg", "بريك|بريكة|brik|bric", Cat.FAST, 240, 7.5, 18, 15.5, "PIECE=120"),
        d("brik_tuna", "بريك بالتن", "Brik with tuna & egg", "brik thon", Cat.FAST, 250, 10, 17, 16, "PIECE=130"),
        d("fricasse", "فريكاسي", "Fricassé", "فريكاسى|fricasse|fricassé", Cat.FAST, 270, 8, 28, 14, "PIECE=110"),
        d("fatayer", "فطاير", "Ftayer (fried dough)", "فطاير|ftayer|ftair|سفنج|sfenj", Cat.FAST, 340, 6, 42, 17, "PIECE=80"),
        d("mlawi", "ملاوي", "Mlawi (plain)", "ملاوى|mlawi|mlewi|melloui", Cat.FAST, 310, 7, 45, 11, "PIECE=150 BREAD_H=75"),
        d("mlawi_filled", "ملاوي محشي", "Filled mlawi (tuna/egg/cheese)", "ملاوي تن|mlawi thon|mlawi m3abi", Cat.FAST, 260, 10, 28, 12, "PIECE=300 BREAD_H=150"),
        d("chapati", "شباتي", "Chapati (Tunisian sandwich)", "chapati|chapatti|شاباتي", Cat.FAST, 240, 10, 26, 10, "PIECE=250"),
        d("kaskrout_thon", "كسكروت تن", "Tuna baguette sandwich", "كسكروت|ساندويتش|casse-croute|kaskrout|sandwich thon", Cat.FAST, 230, 10, 28, 8.5, "PIECE=300 BREAD_H=300 BREAD_Q=150"),
        d("kaskrout_escalope", "كسكروت إسكالوب", "Escalope sandwich", "sandwich escalope|كسكروت دجاج", Cat.FAST, 230, 12, 24, 9.5, "PIECE=320 BREAD_Q=160"),
        d("kaskrout_omelette", "كسكروت أومليت فريت", "Omelette-fries sandwich", "omelette frites|كسكروت عجة", Cat.FAST, 240, 8, 28, 11, "PIECE=350 BREAD_Q=175"),
        d("makloub", "مقلوب", "Makloub", "makloub|ma9loub|مقلوبة", Cat.FAST, 245, 11, 25, 11, "PIECE=320"),
        d("shawarma", "شاورما", "Shawarma", "شوارما|chawarma|shawarma", Cat.FAST, 220, 12, 22, 9, "PIECE=300"),
        d("tacos", "تاكوس", "Tacos (French-style)", "tacos", Cat.FAST, 240, 11, 24, 11, "PIECE=350"),
        d("pizza", "بيتزا", "Pizza", "بيتزه|pizza", Cat.FAST, 270, 11, 32, 10.5, "SLICE=110 PIECE=550"),
        d("burger", "برقر", "Burger", "برغر|هامبرقر|burger|hamburger", Cat.FAST, 250, 13, 24, 11, "PIECE=230"),
        d("panini", "بانيني", "Panini", "panini", Cat.FAST, 250, 11, 30, 9, "PIECE=220"),
        d("fries", "بطاطا مقلية (فريت)", "French fries", "فريت|frites|fries", Cat.FAST, 312, 3.4, 41, 15, "PLATE_S=100 PLATE_M=150 PLATE_L=220"),
        d("fried_chicken", "دجاج مقلي", "Fried chicken", "poulet frit|chicken fried", Cat.FAST, 260, 25, 8, 14, "PIECE=150"),
        d("crepe", "كريب", "Crêpe (sweet)", "crepe|كريب نوتيلا", Cat.FAST, 300, 6, 38, 14, "PIECE=120"),
        // ── Bread & pastry ──
        d("khobz", "خبز باقات (باغيت)", "Baguette", "خبز|باقات|باغيت|khobz|baguette|pain", Cat.BREAD, 270, 9, 55, 1.5, "BREAD_Q=60 BREAD_H=125 BREAD_W=250"),
        d("tabouna", "خبز طابونة", "Tabouna bread", "طابونة|طابون|tabouna|taboun", Cat.BREAD, 280, 8, 55, 3, "BREAD_Q=90 BREAD_H=175 BREAD_W=350"),
        d("khobz_dar", "خبز دار (طاجين/مبسس)", "Homemade semolina bread", "خبز الدار|khobz dar|خبز طاجين|mbesses", Cat.BREAD, 290, 8, 50, 6, "BREAD_Q=80 BREAD_H=160 BREAD_W=320"),
        d("toast", "خبز توست (ميدي)", "Sliced bread", "pain de mie|toast|توست", Cat.BREAD, 265, 9, 49, 3.2, "SLICE=25"),
        d("croissant", "كرواسون", "Croissant", "croissant|كرواسان", Cat.BREAD, 406, 8, 46, 21, "PIECE=60"),
        d("cake", "قاطو / كيك", "Cake", "gateau|cake|كيكة", Cat.BREAD, 390, 5, 52, 18, "SLICE=70"),
        d("biscuits", "بسكويت", "Biscuits", "biscuit|بيسكوي", Cat.BREAD, 480, 6.5, 70, 20, "PIECE=8 HANDFUL=40"),
        d("cereals", "كورن فليكس / سيريال", "Breakfast cereal", "cereales|corn flakes|سيريال", Cat.BREAD, 380, 7, 84, 0.9, "BOWL=40"),
        d("oats", "شوفان", "Oats", "avoine|oats|شوفان", Cat.BREAD, 389, 16.9, 66, 6.9, "BOWL=50 TBSP=10"),
        // ── Meat, eggs, fish ──
        d("eggs2", "بيض مسلوق", "Boiled egg", "بيض|بيضة|عظم|oeuf|egg|3dham", Cat.PROTEIN, 155, 13, 1.1, 11, "PIECE=50"),
        d("egg_fried", "بيض مقلي", "Fried egg", "oeuf au plat|fried egg", Cat.PROTEIN, 196, 13.6, 0.8, 15, "PIECE=46"),
        d("omelette", "أومليت", "Omelette (2 eggs)", "omelette|اومليت", Cat.PROTEIN, 154, 11, 0.6, 12, "PIECE=120"),
        d("chicken", "صدر دجاج مشوي", "Grilled chicken breast", "دجاج|djej|poulet grille|chicken breast|escalope grillee", Cat.PROTEIN, 165, 31, 0, 3.6, "PIECE=150 PLATE_S=120 PLATE_M=200"),
        d("chicken_leg", "فخذ دجاج", "Chicken leg", "cuisse poulet|chicken leg", Cat.PROTEIN, 210, 26, 0, 11, "PIECE=130"),
        d("roast_chicken", "دجاج روتي (ربع)", "Roast chicken (quarter)", "poulet roti|روتي", Cat.PROTEIN, 215, 24, 0, 13, "PIECE=200"),
        d("escalope", "إسكالوب مقلي (بانيه)", "Breaded escalope", "escalope panee|اسكالوب", Cat.PROTEIN, 250, 18, 12, 14, "PIECE=150"),
        d("beef", "لحم بقري مطبوخ", "Cooked beef", "لحم|lham|viande|beef|بقري", Cat.PROTEIN, 250, 26, 0, 15, "PLATE_S=120 PIECE=100 PLATE_M=180"),
        d("lamb", "لحم علوش مشوي", "Grilled lamb", "علوش|3allouch|agneau|lamb|مشوي", Cat.PROTEIN, 290, 25, 0, 21, "PLATE_S=120 PIECE=100 PLATE_M=180"),
        d("kefta", "كفتة", "Kefta (meatballs/grilled)", "كفته|kefta|kofta", Cat.PROTEIN, 250, 17, 4, 18, "PIECE=40 PLATE_S=120 PLATE_M=200"),
        d("merguez", "مرقاز", "Merguez", "مرقاز|merguez|مرقاظ", Cat.PROTEIN, 300, 15, 2, 26, "PIECE=50"),
        d("liver", "كبدة", "Liver", "كبده|kebda|foie", Cat.PROTEIN, 175, 27, 5, 5, "PLATE_S=120 PLATE_M=180"),
        d("salami", "سلامي / مرتديلا", "Salami / mortadella", "كاشير|salami|mortadella|كاشيـر", Cat.PROTEIN, 310, 14, 3, 27, "SLICE=15"),
        d("fish", "حوت مشوي", "Grilled fish", "حوت|سمك|hout|poisson|dorade|loup|ورقة", Cat.PROTEIN, 130, 24, 0, 3.5, "PIECE=200 PLATE_M=250"),
        d("sardines", "سردينة", "Sardines", "سردين|sardine", Cat.PROTEIN, 230, 22, 5, 14, "PLATE_S=120 PLATE_M=200 PIECE=30"),
        d("tuna", "تن معلّب", "Canned tuna", "تن|thon|tuna|تونة", Cat.PROTEIN, 198, 29, 0, 8.2, "PIECE=80 TBSP=15"),
        d("shrimp", "قمبري", "Shrimp", "crevette|shrimp|جمبري", Cat.PROTEIN, 99, 24, 0.2, 0.3, "PLATE_S=150 PLATE_M=220"),
        d("whey", "بروتين واي", "Whey protein", "whey|بروتين", Cat.PROTEIN, 400, 80, 8, 6, "PIECE=30 TBSP=10"),
        // ── Dairy ──
        d("milk", "حليب", "Milk (whole)", "حليب|حليب كامل|7lib|lait|milk", Cat.DAIRY, 61, 3.2, 4.8, 3.3, "GLASS=250 CUP=100"),
        d("milk_choco", "حليب بالشكلاطة", "Chocolate milk", "lait chocolat|حليب شكلاطة", Cat.DAIRY, 83, 3.2, 12, 2.5, "GLASS=250"),
        d("rayeb", "رايب / لبن", "Rayeb / leben", "لبن|رايب|leben|lben|rayeb", Cat.DAIRY, 56, 3.3, 4.5, 3, "GLASS=250 BOWL=300"),
        d("yogurt", "ياغورت", "Yogurt", "ياغرت|ياورت|yaourt|yogurt|danone|دانون", Cat.DAIRY, 61, 3.5, 4.7, 3.3, "PIECE=125"),
        d("yogurt_sweet", "ياغورت بالسكر / غلال", "Sweetened yogurt", "yaourt sucre|ياغورت غلال", Cat.DAIRY, 95, 3.5, 14, 2.8, "PIECE=110"),
        d("cheese", "جبن أصفر", "Hard cheese (gouda)", "جبن|فرماج|fromage|cheese|gouda", Cat.DAIRY, 356, 25, 2, 27, "SLICE=20 PIECE=30"),
        d("cheese_portion", "فرماج مثلثات", "Cheese triangles", "vache qui rit|فرماج بقرة|portion", Cat.DAIRY, 240, 10, 6, 20, "PIECE=16"),
        d("ricotta", "ريكوتا", "Ricotta", "ricotta|ريكوطة", Cat.DAIRY, 174, 11, 3, 13, "TBSP=30 PLATE_S=100"),
        // ── Salads & veg ──
        d("mechouia", "سلاطة مشوية", "Grilled salad (mechouia)", "مشوية|mechouia|méchouia|slata mechouia", Cat.SALAD, 90, 1.5, 7, 6.5),
        d("salade_tounsia", "سلاطة تونسية", "Tunisian salad (tuna/egg)", "salade tunisienne|slata tounsia", Cat.SALAD, 95, 4, 5, 6.5),
        d("omek_houria", "أمك حورية", "Omek houria (carrot salad)", "omek houria|اومك حورية", Cat.SALAD, 90, 1.5, 9, 5.5),
        d("baba_ghanouj", "بابا غنوج", "Baba ghanoush", "بابا غنوش|baba ghanouj|baba ganoush", Cat.SALAD, 130, 2.5, 8, 10),
        d("houmous", "حمص (مهروس)", "Hummus", "hummus|houmous|حمص بالطحينة", Cat.SALAD, 166, 8, 14, 9.6),
        d("green_salad", "سلاطة خضراء", "Green salad", "salade verte|سلطة", Cat.SALAD, 70, 1.3, 4, 5.5),
        d("tomato", "طماطم", "Tomato", "طماطم|tomate|tomato", Cat.SALAD, 18, 0.9, 3.9, 0.2, "PIECE=120"),
        d("cucumber", "فقوس / خيار", "Cucumber", "فقوس|خيار|concombre", Cat.SALAD, 15, 0.7, 3.6, 0.1, "PIECE=200"),
        d("olives", "زيتون", "Olives", "زيتونة|olive", Cat.SALAD, 145, 1, 3.8, 15, "PIECE=4 HANDFUL=40"),
        // ── Fruit ──
        d("dates", "تمر", "Dates (deglet nour)", "تمر|دقلة|tmar|dattes|dates", Cat.FRUIT, 282, 2.5, 75, 0.4, "PIECE=8 HANDFUL=40"),
        d("banana", "موز", "Banana", "banane|banana|موزة", Cat.FRUIT, 89, 1.1, 23, 0.3, "PIECE=120"),
        d("apple", "تفاح", "Apple", "تفاحة|pomme|apple", Cat.FRUIT, 52, 0.3, 14, 0.2, "PIECE=180"),
        d("orange", "برتقال", "Orange", "برتقالة|بردقان|orange", Cat.FRUIT, 47, 0.9, 12, 0.1, "PIECE=150"),
        d("mandarin", "مندرين", "Mandarin", "mandarine|clementine|مندرينة", Cat.FRUIT, 53, 0.8, 13, 0.3, "PIECE=80"),
        d("grapes", "عنب", "Grapes", "عنب|raisin|grapes", Cat.FRUIT, 69, 0.7, 18, 0.2, "BOWL=150 HANDFUL=80"),
        d("watermelon", "دلاع", "Watermelon", "دلاعة|بطيخ احمر|pasteque|watermelon", Cat.FRUIT, 30, 0.6, 7.6, 0.2, "SLICE=300"),
        d("melon", "بطيخ", "Melon", "melon|بطيخة", Cat.FRUIT, 34, 0.8, 8, 0.2, "SLICE=200"),
        d("figs", "كرموس", "Fresh figs", "تين|karmous|figue", Cat.FRUIT, 74, 0.75, 19, 0.3, "PIECE=50"),
        d("prickly_pear", "هندي", "Prickly pear", "هندية|hendi|figue de barbarie", Cat.FRUIT, 41, 0.7, 10, 0.5, "PIECE=100"),
        d("pomegranate", "رمان", "Pomegranate", "رمانة|grenade|romman", Cat.FRUIT, 83, 1.7, 19, 1.2, "PIECE=250"),
        d("strawberries", "فراولو", "Strawberries", "فراولة|fraise|strawberry", Cat.FRUIT, 32, 0.7, 7.7, 0.3, "BOWL=150"),
        d("peach", "خوخ", "Peach", "خوخة|peche|peach", Cat.FRUIT, 39, 0.9, 10, 0.3, "PIECE=150"),
        d("pear", "إنجاص", "Pear", "انجاص|poire|pear", Cat.FRUIT, 57, 0.4, 15, 0.1, "PIECE=170"),
        d("apricot", "مشمش", "Apricot", "abricot|apricot", Cat.FRUIT, 48, 1.4, 11, 0.4, "PIECE=35"),
        d("fruit_salad", "سلاطة غلال", "Fruit salad", "salade de fruits", Cat.FRUIT, 60, 0.6, 15, 0.2, "BOWL=200"),
        // ── Nuts & dried ──
        d("almonds", "لوز", "Almonds", "لوز|amande|almond", Cat.NUTS, 579, 21, 22, 50),
        d("peanuts", "كاكاوية", "Peanuts", "كاكاويه|cacahuete|peanut|فول سوداني", Cat.NUTS, 567, 26, 16, 49),
        d("pistachios", "فستق", "Pistachios", "pistache|pistachio", Cat.NUTS, 560, 20, 28, 45),
        d("pine_nuts", "بوفريوة", "Pine nuts", "بوفريوه|zgougou|pignons", Cat.NUTS, 673, 14, 13, 68, "TBSP=10 HANDFUL=30"),
        d("raisins", "زبيب", "Raisins", "زبيب|raisins secs", Cat.NUTS, 299, 3, 79, 0.5),
        d("dried_figs", "شريحة (كرموس يابس)", "Dried figs", "شريحه|figues seches", Cat.NUTS, 249, 3.3, 64, 0.9, "PIECE=20 HANDFUL=40"),
        d("peanut_butter", "زبدة كاكاوية", "Peanut butter", "beurre cacahuete|peanut butter", Cat.NUTS, 588, 25, 20, 50, "TBSP=16 TSP=6"),
        d("bsissa", "بسيسة", "Bsissa (dry mix)", "bsissa|بسيسه", Cat.NUTS, 450, 13, 60, 16, "TBSP=12 BOWL=60"),
        // ── Sweets ──
        d("baklawa", "بقلاوة", "Baklava", "بقلاوه|baklawa|baklava", Cat.SWEET, 430, 7, 48, 24, "PIECE=40"),
        d("makroudh", "مقروض", "Makroudh", "makroud|makroudh|مقروض", Cat.SWEET, 400, 5, 60, 16, "PIECE=40"),
        d("ghraiba", "غريبة", "Ghraiba", "ghraiba|غريبه", Cat.SWEET, 470, 6, 58, 24, "PIECE=25"),
        d("bambalouni", "بمبالوني", "Bambalouni", "bambalouni|بنبالوني", Cat.SWEET, 370, 6, 45, 18, "PIECE=90"),
        d("yoyo", "يويو", "Yoyo", "yoyo", Cat.SWEET, 400, 5, 55, 18, "PIECE=50"),
        d("zlabia", "زلابية", "Zlabia", "zlabia|زلابيه", Cat.SWEET, 400, 3, 60, 17, "PIECE=40"),
        d("assida_zgougou", "عصيدة زقوقو", "Zgougou assida", "عصيدة|assida|zgougou", Cat.SWEET, 250, 4, 30, 13, "BOWL=200 TBSP=30"),
        d("assida_smid", "عصيدة سميد", "Semolina assida", "assida smid", Cat.SWEET, 180, 3, 30, 5, "BOWL=250"),
        d("halwa", "حلوى شامية", "Halva", "حلوة شامية|halwa|halva", Cat.SWEET, 470, 12, 55, 23, "SLICE=40 TBSP=20"),
        d("chocolate", "شكلاطة", "Chocolate", "شوكولا|chocolat|chocolate", Cat.SWEET, 540, 6, 58, 31, "PIECE=40 SLICE=10"),
        d("chips", "شيبس", "Crisps", "chips|شيبسي", Cat.SWEET, 536, 7, 53, 34, "PIECE=45"),
        d("ice_cream", "جلاط", "Ice cream", "glace|ice cream|مثلجات", Cat.SWEET, 207, 3.5, 24, 11, "PIECE=70 BOWL=100"),
        d("nutella", "نوتيلا", "Nutella", "nutella|شكلاطة دهن", Cat.SWEET, 539, 6.3, 57.5, 31, "TBSP=20 TSP=7"),
        d("honey", "عسل", "Honey", "عسل|miel|honey", Cat.SWEET, 304, 0.3, 82, 0, "TBSP=21 TSP=7"),
        d("jam", "معجون", "Jam", "confiture|jam|معجون", Cat.SWEET, 250, 0.4, 62, 0.1, "TBSP=20 TSP=7"),
        // ── Drinks ──
        d("coffee_sugar", "قهوة بالسكر", "Espresso with sugar", "قهوة|كافي|express|cafe|coffee", Cat.DRINK, 105, 0.1, 25, 0.2, "CUP=40 GLASS=80"),
        d("coffee_milk", "قهوة بالحليب (ديراكت)", "Coffee with milk (direct)", "direct|capucin|كابوسان|ديراكت|cafe au lait", Cat.DRINK, 60, 2, 8, 2, "CUP=150 GLASS=250"),
        d("tea_sugar", "شاي بالسكر", "Tea with sugar", "تاي|شاهي|the|tea|أتاي", Cat.DRINK, 100, 0, 25, 0, "CUP=60 GLASS=100"),
        d("tea_pine", "شاي بالبوفريوة", "Tea with pine nuts", "تاي بالبوفريوة|the aux pignons", Cat.DRINK, 140, 2, 18, 7, "CUP=100 GLASS=150"),
        d("orange_juice", "عصير برتقال", "Orange juice", "جوس برتقال|jus orange|juice", Cat.DRINK, 45, 0.7, 10.4, 0.2),
        d("citronnade", "سيترونادة", "Lemonade (citronnade)", "citronnade|عصير ليمون", Cat.DRINK, 50, 0.1, 12.5, 0),
        d("smoothie", "عصير بالحليب (موز/فراولو)", "Milk smoothie", "jus lait|jwez|عصير موز|milkshake", Cat.DRINK, 90, 2.5, 15, 2.5, "GLASS=300"),
        d("soda", "مشروب غازي", "Soda", "قازوز|gazouz|coca|كوكا|soda|boga|بوقا", Cat.DRINK, 42, 0, 10.6, 0, "GLASS=250 PIECE=330"),
        // ── Extras ──
        d("olive_oil", "زيت زيتون", "Olive oil", "زيت|zit|huile|olive oil", Cat.EXTRA, 884, 0, 0, 100, "TBSP=13 TSP=5"),
        d("butter", "زبدة", "Butter", "زبده|beurre|butter", Cat.EXTRA, 717, 0.9, 0.1, 81, "TBSP=14 TSP=5"),
        d("sugar", "سكر", "Sugar", "سكر|sucre|sugar", Cat.EXTRA, 387, 0, 100, 0, "TSP=4 TBSP=12"),
        d("harissa", "هريسة", "Harissa", "هريسه|harissa", Cat.EXTRA, 110, 3, 8, 7, "TBSP=15 TSP=5"),
        d("mayonnaise", "مايونيز", "Mayonnaise", "mayo|mayonnaise", Cat.EXTRA, 680, 1, 0.6, 75, "TBSP=14 TSP=5"),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String?) = id?.let { byId[it] }

    // ── Search: Arabic (diacritics/hamza/ta-marbuta/alef-maqsura folded), Tunisian Latin spellings, English/French ──
    fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            val c = when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'; 'ة' -> 'ه'; 'ى' -> 'ي'; 'ؤ' -> 'و'; 'ئ' -> 'ي'
                'é', 'è', 'ê', 'ë' -> 'e'; 'à', 'â' -> 'a'; 'ç' -> 'c'; 'î', 'ï' -> 'i'; 'ô' -> 'o'; 'û', 'ù' -> 'u'
                '-', '_', '\'', '’', '/', '(', ')', '.', ',' -> ' '
                else -> ch
            }
            if (c in '\u064B'..'\u065F' || c == '\u0670' || c == 'ـ') continue
            sb.append(c)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private val index: Map<String, List<String>> = all.associate { f -> f.id to (listOf(f.ar, f.en) + f.aliases).map(::norm) }

    /** Ranked search. Empty query → empty list. */
    fun search(q: String, limit: Int = 40): List<Item> {
        val n = norm(q); if (n.isEmpty()) return emptyList()
        val toks = n.split(' ')
        return all.mapNotNull { f ->
            val keys = index.getValue(f.id)
            val score = keys.maxOf { k ->
                when {
                    k == n -> 100
                    k.startsWith(n) -> 80
                    k.split(' ').any { it.startsWith(n) } -> 65
                    k.contains(n) -> 50
                    toks.size > 1 && toks.all { t -> k.contains(t) } -> 40
                    else -> 0
                }
            }
            if (score > 0) f to score else null
        }.sortedWith(compareByDescending<Pair<Item, Int>> { it.second }.thenBy { it.first.ar.length }).take(limit).map { it.first }
    }
}
