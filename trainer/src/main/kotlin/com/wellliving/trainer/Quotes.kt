package com.wellliving.trainer

/**
 * A line shown before a session. [text] is Hebrew; for non-Hebrew sources it is our own
 * translation and [translated] is true. Every source was checked before inclusion; see
 * app-planning/quotes-sources.md in the project files for the verification notes.
 */
data class Quote(
    val text: String,
    val author: String,
    val source: String,
    val translated: Boolean,
)

val QUOTES = listOf(
    Quote(
        "וְאַל תֹּאמַר לִכְשֶׁאִפָּנֶה אֶשְׁנֶה, שֶׁמָּא לֹא תִפָּנֶה.",
        "הלל", "משנה, אבות ב, ד", translated = false,
    ),
    Quote(
        "לֹא עָלֶיךָ הַמְּלָאכָה לִגְמֹר, וְלֹא אַתָּה בֶן חוֹרִין לִבָּטֵל מִמֶּנָּה.",
        "רבי טרפון", "משנה, אבות ב, טז", translated = false,
    ),
    Quote(
        "אִם אֵין אֲנִי לִי, מִי לִי? וּכְשֶׁאֲנִי לְעַצְמִי, מָה אֲנִי? וְאִם לֹא עַכְשָׁיו, אֵימָתָי?",
        "הלל", "משנה, אבות א, יד", translated = false,
    ),
    Quote(
        "לְפוּם צַעֲרָא אַגְרָא.",
        "בן הא הא", "משנה, אבות ה, כג", translated = false,
    ),
    Quote(
        "כֹּל אֲשֶׁר תִּמְצָא יָדְךָ לַעֲשׂוֹת בְּכֹחֲךָ עֲשֵׂה.",
        "קהלת", "קהלת ט, י", translated = false,
    ),
    Quote(
        "היכולת להשיב מרצון, שוב ושוב, קשב שנדד, היא שורש השיפוט, האופי והרצון.",
        "ויליאם ג'יימס", "The Principles of Psychology (1890), פרק 11: Attention", translated = true,
    ),
    Quote(
        "הקשב הוא הצורה הנדירה והטהורה ביותר של נדיבות.",
        "סימון וייל", "מכתב לז'ו בוסקה, 13 באפריל 1942", translated = true,
    ),
    Quote(
        "פיתוח כושר הקשב הוא התכלית האמיתית, וכמעט העניין היחיד, של הלימודים.",
        "סימון וייל", "\"הרהורים על השימוש הנכון בלימודי בית הספר\"", translated = true,
    ),
    Quote(
        "מי שנמצא בכל מקום, אינו נמצא בשום מקום.",
        "סנקה", "מכתבים מוסריים ללוקיליוס, מכתב 2", translated = true,
    ),
    Quote(
        "לא מעט זמן יש לנו; הרבה ממנו אנו מאבדים.",
        "סנקה", "על קוצר החיים, 1", translated = true,
    ),
    Quote(
        "האופן שבו אנו מבלים את ימינו הוא, כמובן, האופן שבו אנו מבלים את חיינו.",
        "אנני דילארד", "The Writing Life (1989)", translated = true,
    ),
    Quote(
        "הרגעים הטובים ביותר מתרחשים בדרך כלל כאשר גופו או נפשו של אדם נמתחים עד קצה גבולם, במאמץ רצוני להשיג דבר קשה ובעל ערך.",
        "מיהאי צ'יקסנטמיהאיי", "Flow (1990)", translated = true,
    ),
)

object QuotePicker {
    /** A random quote index, never the same as [previous] when there is a choice. */
    fun next(previous: Int?, count: Int = QUOTES.size, random: (Int) -> Int = { kotlin.random.Random.nextInt(it) }): Int {
        if (count <= 1) return 0
        if (previous == null || previous !in 0 until count) return random(count)
        val pick = random(count - 1)
        return if (pick >= previous) pick + 1 else pick
    }
}
