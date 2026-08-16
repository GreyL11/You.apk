package com.example.domain

import com.example.data.Meal
import com.example.data.Profile
import kotlin.math.roundToInt

object Nutrition {

    data class Food(
        val id: String,
        val label: String,
        val cat: String = "Other",
        val serving: String = "",
        val ml: Double? = null,
        val kcal: Double? = null,
        val prot: Double? = null,
        val carb: Double? = null,
        val fat: Double? = null
    )

    // Ported verbatim from the legacy app's FOODS table (www/nutrition.js) — same ids, same
    // per-serving macros, same categories. Legacy's own comment explains why this is a static
    // table rather than a food API or a model: no network/account in this app, a lifter eats the
    // same fifteen things, and a confidently wrong number is worse than a visible, editable one.
    val FOODS = listOf(
        // Protein
        Food("chickenBreast", "Chicken breast", cat = "Protein", serving = "100 g", kcal = 165.0, prot = 31.0, carb = 0.0, fat = 3.6),
        Food("chickenThigh", "Chicken thigh", cat = "Protein", serving = "100 g", kcal = 209.0, prot = 26.0, carb = 0.0, fat = 11.0),
        Food("egg", "Egg", cat = "Protein", serving = "1 large", kcal = 72.0, prot = 6.3, carb = 0.4, fat = 4.8),
        Food("eggWhite", "Egg white", cat = "Protein", serving = "1", kcal = 17.0, prot = 3.6, carb = 0.2, fat = 0.0),
        Food("whey", "Whey scoop", cat = "Protein", serving = "30 g", kcal = 120.0, prot = 24.0, carb = 3.0, fat = 1.5),
        Food("paneer", "Paneer", cat = "Protein", serving = "100 g", kcal = 296.0, prot = 18.0, carb = 3.4, fat = 22.0),
        Food("tofu", "Tofu, firm", cat = "Protein", serving = "100 g", kcal = 144.0, prot = 17.0, carb = 3.0, fat = 9.0),
        Food("soyaChunks", "Soya chunks, dry", cat = "Protein", serving = "50 g", kcal = 172.0, prot = 26.0, carb = 16.0, fat = 0.5),
        Food("greekYogurt", "Greek yogurt", cat = "Protein", serving = "100 g", kcal = 59.0, prot = 10.0, carb = 3.6, fat = 0.4),
        Food("curd", "Curd / plain yogurt", cat = "Protein", serving = "100 g", kcal = 61.0, prot = 3.5, carb = 4.7, fat = 3.3),
        Food("milk", "Milk, whole", cat = "Protein", serving = "250 ml", kcal = 149.0, prot = 7.7, carb = 12.0, fat = 8.0, ml = 250.0),
        Food("cottageCheese", "Cottage cheese", cat = "Protein", serving = "100 g", kcal = 98.0, prot = 11.0, carb = 3.4, fat = 4.3),
        Food("salmon", "Salmon", cat = "Protein", serving = "100 g", kcal = 208.0, prot = 20.0, carb = 0.0, fat = 13.0),
        Food("tuna", "Tuna, canned in water", cat = "Protein", serving = "100 g", kcal = 116.0, prot = 26.0, carb = 0.0, fat = 1.0),
        Food("beefMince", "Beef mince, lean", cat = "Protein", serving = "100 g", kcal = 250.0, prot = 26.0, carb = 0.0, fat = 15.0),
        Food("prawns", "Prawns", cat = "Protein", serving = "100 g", kcal = 99.0, prot = 24.0, carb = 0.2, fat = 0.3),
        Food("dal", "Dal, cooked", cat = "Protein", serving = "1 cup", kcal = 200.0, prot = 12.0, carb = 33.0, fat = 3.0),
        Food("rajma", "Rajma / kidney beans", cat = "Protein", serving = "1 cup", kcal = 225.0, prot = 15.0, carb = 40.0, fat = 1.0),
        Food("chickpeas", "Chickpeas, cooked", cat = "Protein", serving = "1 cup", kcal = 269.0, prot = 15.0, carb = 45.0, fat = 4.0),
        // Carbs
        Food("rice", "White rice, cooked", cat = "Carbs", serving = "1 cup", kcal = 205.0, prot = 4.3, carb = 45.0, fat = 0.4),
        Food("brownRice", "Brown rice, cooked", cat = "Carbs", serving = "1 cup", kcal = 218.0, prot = 5.0, carb = 46.0, fat = 1.6),
        Food("roti", "Roti / chapati", cat = "Carbs", serving = "1", kcal = 104.0, prot = 3.0, carb = 20.0, fat = 2.0),
        Food("bread", "Bread", cat = "Carbs", serving = "1 slice", kcal = 79.0, prot = 3.0, carb = 14.0, fat = 1.0),
        Food("oats", "Oats, dry", cat = "Carbs", serving = "50 g", kcal = 190.0, prot = 6.6, carb = 33.0, fat = 3.4),
        Food("potato", "Potato, boiled", cat = "Carbs", serving = "100 g", kcal = 87.0, prot = 2.0, carb = 20.0, fat = 0.1),
        Food("sweetPotato", "Sweet potato", cat = "Carbs", serving = "100 g", kcal = 90.0, prot = 2.0, carb = 21.0, fat = 0.2),
        Food("pasta", "Pasta, cooked", cat = "Carbs", serving = "1 cup", kcal = 220.0, prot = 8.0, carb = 43.0, fat = 1.3),
        Food("idli", "Idli", cat = "Carbs", serving = "1", kcal = 58.0, prot = 2.0, carb = 12.0, fat = 0.4),
        Food("dosa", "Dosa, plain", cat = "Carbs", serving = "1", kcal = 133.0, prot = 3.0, carb = 22.0, fat = 4.0),
        Food("banana", "Banana", cat = "Carbs", serving = "1 medium", kcal = 105.0, prot = 1.3, carb = 27.0, fat = 0.4),
        Food("apple", "Apple", cat = "Carbs", serving = "1 medium", kcal = 95.0, prot = 0.5, carb = 25.0, fat = 0.3),
        // Fats
        Food("peanutButter", "Peanut butter", cat = "Fats", serving = "1 tbsp", kcal = 94.0, prot = 4.0, carb = 3.0, fat = 8.0),
        Food("almonds", "Almonds", cat = "Fats", serving = "30 g", kcal = 173.0, prot = 6.0, carb = 6.0, fat = 15.0),
        Food("oliveOil", "Olive oil", cat = "Fats", serving = "1 tbsp", kcal = 119.0, prot = 0.0, carb = 0.0, fat = 13.5),
        Food("ghee", "Ghee", cat = "Fats", serving = "1 tsp", kcal = 45.0, prot = 0.0, carb = 0.0, fat = 5.0),
        Food("avocado", "Avocado", cat = "Fats", serving = "half", kcal = 160.0, prot = 2.0, carb = 9.0, fat = 15.0),
        // Veg
        Food("mixedVeg", "Mixed veg / sabzi", cat = "Veg", serving = "100 g", kcal = 35.0, prot = 2.0, carb = 7.0, fat = 0.3),
        Food("spinach", "Spinach, cooked", cat = "Veg", serving = "100 g", kcal = 23.0, prot = 2.9, carb = 3.6, fat = 0.4),
        Food("broccoli", "Broccoli", cat = "Veg", serving = "100 g", kcal = 35.0, prot = 2.4, carb = 7.0, fat = 0.4),
        Food("salad", "Salad, undressed", cat = "Veg", serving = "1 bowl", kcal = 25.0, prot = 1.5, carb = 5.0, fat = 0.2),
        // Other — `ml` is fluid that counts toward the day's water (see Nutrition.fluid). Alcohol
        // entries deliberately carry no `ml`: it's a diuretic, so counting it toward hydration
        // would be actively wrong (kept faithful to the legacy comment/behavior).
        Food("water", "Water", cat = "Other", serving = "250 ml", kcal = 0.0, prot = 0.0, carb = 0.0, fat = 0.0, ml = 250.0),
        Food("coffee", "Black coffee / tea", cat = "Other", serving = "1 cup", kcal = 2.0, prot = 0.3, carb = 0.0, fat = 0.0, ml = 200.0),
        Food("chai", "Chai with milk & sugar", cat = "Other", serving = "1 cup", kcal = 105.0, prot = 2.5, carb = 14.0, fat = 4.0, ml = 200.0),
        Food("softDrink", "Soft drink", cat = "Other", serving = "330 ml", kcal = 139.0, prot = 0.0, carb = 35.0, fat = 0.0, ml = 330.0),
        Food("beer", "Beer", cat = "Other", serving = "330 ml", kcal = 143.0, prot = 1.6, carb = 11.0, fat = 0.0),
        Food("wine", "Wine", cat = "Other", serving = "150 ml", kcal = 125.0, prot = 0.1, carb = 4.0, fat = 0.0),
        Food("spirit", "Spirit, neat", cat = "Other", serving = "30 ml", kcal = 70.0, prot = 0.0, carb = 0.0, fat = 0.0)
    )

    val FOOD_CATS = listOf("Protein", "Carbs", "Veg", "Fats", "Other")

    fun waterTarget(profile: Profile?): Int {
        if (profile == null) return 0
        // round100(bodyweight_kg * 35 + 500 * daysPerWeek / 7)
        val raw = profile.bodyweight * 35.0 + 500.0 * profile.daysPerWeek / 7.0
        return (raw / 100.0).roundToInt() * 100
    }

    fun fluid(entries: List<Pair<Meal, Double?>>): Int {
        // fluid(entries) = round(Σ ml × qty)
        var totalFluid = 0.0
        for ((meal, ml) in entries) {
            if (ml != null) {
                totalFluid += ml * meal.qty
            }
        }
        return totalFluid.roundToInt()
    }
}
