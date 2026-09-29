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

/** Nutrition the source states: Cook Well's (whole recipe), or a video creator's ([per] serving or recipe). */
@Serializable
data class SourceNutrition(
    val calories: Double? = null,
    val protein: Double? = null,
    val carbohydrates: Double? = null,
    val fat: Double? = null,
    val per: String? = null,
    val from: String? = null,
)

@Serializable
data class ImportJob(val id: Int, val url: String, val status: String, val message: String = "", val recipeId: Int? = null)

@Serializable
data class ImportStatus(val ready: Boolean, val instagramCookies: Boolean = false)

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
    val favorite: Boolean = false,
    val folderIds: List<Int> = emptyList(),
    val parentId: Int? = null,
    val parentTitle: String? = null,
    val variations: List<RecipeRef> = emptyList(),
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
    /** A note on the plan: text, no recipe. */
    val isNote get() = recipeId == null && leftoverOf == null
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

@Serializable
data class RecipeRef(val id: Int, val title: String)

@Serializable
data class Profile(
    val cooked: Int = 0,
    val recipesCooked: Int = 0,
    val mine: Int = 0,
    val favorites: Int = 0,
    val streak: Int = 0,
    /** "YYYY-MM-DD" -> meals cooked that day (last 20 weeks) */
    val days: Map<String, Int> = emptyMap(),
    val cuisines: List<List<kotlinx.serialization.json.JsonElement>> = emptyList(),
    val categories: List<List<kotlinx.serialization.json.JsonElement>> = emptyList(),
)

@Serializable
data class Cooked(val day: String, val entryId: Int, val servings: Double, val batch: Double? = null, val recipe: RecipeSummary)

@Serializable
data class FolderSummary(val id: Int, val name: String, val count: Int, val covers: List<String> = emptyList())

@Serializable
data class Catalog(val mine: List<RecipeSummary>, val favorites: List<RecipeSummary>, val folders: List<FolderSummary>)

@Serializable
data class FolderDetail(val id: Int, val name: String, val recipes: List<RecipeSummary>)

@Serializable
data class TemplateItem(val name: String, val amount: String = "", val aisle: String = "Other")

@Serializable
data class GroceryTemplate(val id: Int, val name: String, val items: List<TemplateItem>)
