package com.example.domain

/**
 * Turning "two rotis, dal and a glass of milk" into rows the app already knows how to store.
 *
 * The model's job here is TRANSLATION, not judgement: it maps words to ids that already exist in
 * [Nutrition.FOODS] and a quantity in servings. It does not invent foods, it does not estimate
 * calories, and nothing it returns is written until the person has seen it — see the confirm step in
 * `MealSheet`.
 *
 * WHY THIS IS ALLOWED WHEN CALORIE-GUESSING IS NOT: an id either exists in the table or it does not,
 * so a wrong answer is a *visible* wrong answer — "chickenBreast ×2" that you never ate is obvious on
 * screen and one tap to drop. A hallucinated 620 kcal is invisible and unfalsifiable. Every claim
 * this file lets through is checkable against a fixed table before it reaches you, which is the same
 * containment idea as [Validate], applied to ids instead of numbers.
 */
data class ParsedMeal(
    val foodId: String,
    val qty: Double,
    /** The food's own label, so the confirm list shows what the app understood, not the raw id. */
    val label: String,
)

data class MealParseResult(
    val items: List<ParsedMeal>,
    /** Words the model returned that are not real foods. Shown, not silently dropped. */
    val unknown: List<String>,
)

object MealParse {
    /** Servings per item. Above this it is far more likely a misparse than a real meal. */
    const val MAX_QTY = 20.0

    /**
     * Validate the model's output against the real food table.
     *
     * @param raw pairs of (id the model chose, quantity in servings)
     */
    fun validate(raw: List<Pair<String, Double>>): MealParseResult {
        val known = Nutrition.FOODS.associateBy { it.id }
        val items = mutableListOf<ParsedMeal>()
        val unknown = mutableListOf<String>()

        for ((id, qty) in raw) {
            val food = known[id]
            if (food == null) {
                unknown.add(id)
                continue
            }
            // A quantity has to be a real positive amount. Zero, negative and absurd all mean the
            // parse failed for that line, and a failed line is reported rather than rounded into
            // something plausible.
            if (qty <= 0.0 || qty > MAX_QTY || !qty.isFinite()) {
                unknown.add("${food.label} (quantity $qty)")
                continue
            }
            items.add(ParsedMeal(food.id, qty, food.label))
        }

        // The same food said twice ("rice, and more rice") is one row with the quantities added,
        // because two rows for one food would double the delete work and read as a parse bug.
        val merged = items
            .groupBy { it.foodId }
            .map { (id, same) -> same.first().copy(qty = same.sumOf { it.qty }) }

        return MealParseResult(merged, unknown)
    }

    /**
     * The id list handed to the model, as a compact prompt fragment.
     *
     * Sent every time rather than relying on the model knowing the table: the ids are this app's
     * invention (`soyaChunks`, `chickenThigh`), and a model guessing at them is exactly the failure
     * this whole file exists to catch.
     */
    fun foodCatalogue(): String =
        Nutrition.FOODS.joinToString("\n") { "${it.id} = ${it.label} (${it.serving})" }
}
