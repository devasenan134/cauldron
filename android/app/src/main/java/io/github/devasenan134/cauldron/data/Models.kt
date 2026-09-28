package io.github.devasenan134.cauldron.data

import kotlinx.serialization.Serializable

// Mirrors of the server's JSON (snake_case on the wire; see Api.json).

@Serializable
data class Me(val email: String, val name: String = "", val isOwner: Boolean = false, val kcalGoal: Int = 2200, val theme: String = "system", val token: String? = null)

@Serializable
data class Macros(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

@Serializable
data class RecipeSummary(
    val id: Int,
    val title: String,
    val imageUrl: String? = null,
    val source: String = "",
    val cuisine: String? = null,
    val category: String? = null,
    val totalMinutes: Int? = null,
    val servings: Double? = null,
    val tags: List<String> = emptyList(),
    val kcalPerServing: Double? = null,
)

@Serializable
data class TagGroup(val name: String, val tags: List<String>)

@Serializable
data class Facets(val cuisines: List<String>, val categories: List<String>, val tagGroups: List<TagGroup> = emptyList())

/** What the recipe list is filtered and sorted by. */
data class RecipeFilter(
    val q: String = "",
    val cuisines: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val maxMinutes: Int? = null,
    val kcal: KcalRange? = null,
    val mine: Boolean = false,
    val sort: String = "title",
) {
    /** How many filters are on (the search box and sort don't count). */
    val count get() = cuisines.size + categories.size + tags.size + listOfNotNull(maxMinutes, kcal).size + (if (mine) 1 else 0)
}

enum class KcalRange(val label: String, val min: Int?, val max: Int?) {
    Light("Under 400", null, 400), Medium("400–700", 400, 700), Hearty("Over 700", 700, null),
}

// A recipe of your own, as sent to the server (same shape as the library's).
@Serializable
data class IngredientIn(val group: String? = null, val name: String, val note: String = "", val label: String = "")

@Serializable
data class StepIn(val title: String = "", val text: String)

@Serializable
data class RecipeIn(
    val title: String,
    val description: String = "",
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val sourceUrl: String? = null,
    val servings: Double? = null,
    val yieldText: String? = null,
    val totalMinutes: Int? = null,
    val cuisine: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val ingredients: List<IngredientIn> = emptyList(),
    val steps: List<StepIn> = emptyList(),
)

@Serializable
data class Ingredient(
    val id: Int,
    val position: Int,
    val group: String? = null,
    val name: String,
    val note: String = "",
    val label: String = "",
    val grams: Double? = null,
    /** given | parts | portion | estimate | manual; null = not counted */
    val gramsSource: String? = null,
    val aisle: String? = null,
    val foodId: Int? = null,
    val foodName: String? = null,
    val nutrition: Macros? = null,
)

@Serializable
data class Step(val id: Int, val position: Int, val title: String = "", val text: String)

@Serializable
data class Nutrition(
    val total: Macros,
    val perServing: Macros? = null,
    val leftOut: List<String> = emptyList(),
    val estimated: List<String> = emptyList(),
)

@Serializable
data class SourceNutrition(val calories: Double? = null)

@Serializable
data class RecipeDetail(
    val id: Int,
    val title: String,
    val canEdit: Boolean = false,
    val imageUrl: String? = null,
    val source: String = "",
    val sourceUrl: String? = null,
    val videoUrl: String? = null,
    val author: String? = null,
    val description: String = "",
    val servings: Double? = null,
    val yieldText: String? = null,
    val totalMinutes: Int? = null,
    val category: String? = null,
    val cuisine: String? = null,
    val tags: List<String> = emptyList(),
    val sourceNutrition: SourceNutrition? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<Step> = emptyList(),
    val nutrition: Nutrition,
    val notes: String = "",
) {
    /** Your own recipe (not the shared library): you can rewrite or delete it. */
    val isMine get() = canEdit && source != "cookwell"
}

@Serializable
data class Food(val id: Int, val name: String, val source: String, val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

@Serializable
data class PlanEntry(
    val id: Int,
    /** "YYYY-MM-DD", or null while it waits in the queue */
    val day: String? = null,
    val position: Int = 0,
    val recipeId: Int? = null,
    val title: String = "",
    val imageUrl: String? = null,
    /** portions eaten at this meal */
    val servings: Double = 1.0,
    val kcalPerServing: Double? = null,
    val kcal: Double? = null,
    /** set when this meal cooks a batch */
    val cookPortions: Double? = null,
    val portionsLeft: Double? = null,
    val discarded: Double = 0.0,
    /** the batch this leftover meal eats from */
    val leftoverOf: Int? = null,
) {
    val isBatch get() = cookPortions != null
    val isLeftover get() = leftoverOf != null
}

@Serializable
data class Plan(val days: Map<String, List<PlanEntry>>, val queue: List<PlanEntry>)

@Serializable
data class GroceryItem(
    val id: Int,
    val name: String,
    val amount: String = "",
    val aisle: String = "Other",
    val checked: Boolean = false,
    val manual: Boolean = false,
    val sources: List<String> = emptyList(),
)

@Serializable
data class AppRelease(val version: String, val notes: String = "", val size: Long = 0)
