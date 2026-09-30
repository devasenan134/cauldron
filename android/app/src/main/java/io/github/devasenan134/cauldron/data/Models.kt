package io.github.devasenan134.cauldron.data

import kotlinx.serialization.Serializable

// Mirrors of the server's JSON (snake_case on the wire; see Api.json).

@Serializable
data class Me(val email: String, val name: String = "", val isOwner: Boolean = false, val kcalGoal: Int = 2200, val theme: String = "system",
              /** grid | list: how Profile → Catalog shows recipes */ val catalogView: String = "grid", val token: String? = null)

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
    /** A prepped ingredient (cooked rice, a sauce) that other recipes use by weight. */
    val isPrep: Boolean = false,
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
    val prep: Boolean = false,
    val sort: String = "title",
) {
    /** How many filters are on (the search box and sort don't count). */
    val count get() = cuisines.size + categories.size + tags.size + listOfNotNull(maxMinutes, kcal).size + (if (mine) 1 else 0) + (if (prep) 1 else 0)
}

enum class KcalRange(val label: String, val min: Int?, val max: Int?) {
    Light("Under 400", null, 400), Medium("400–700", 400, 700), Hearty("Over 700", 700, null),
}

// A recipe of your own, as sent to the server (same shape as the library's).
@Serializable
data class IngredientIn(val group: String? = null, val name: String, val note: String = "", val label: String = "", val prepId: Int? = null)

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
    val isPrep: Boolean = false,
    val yieldGrams: Double? = null,
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
    /** Counted with your own version of the food. */
    val foodEdited: Boolean = false,
    /** Made from this prepped-ingredient recipe instead of a food. */
    val prepId: Int? = null,
    val prepTitle: String? = null,
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
    val grams: Double = 0.0,
    /** A prep: what it weighs when done, and per 100 g. */
    val yieldGrams: Double? = null,
    val per100g: Macros? = null,
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
    val isPrep: Boolean = false,
    val yieldGrams: Double? = null,
    /** A prep: your recipes that use it. */
    val usedIn: List<RecipeRef> = emptyList(),
    /** Who took the photo, when it isn't yours (the starter recipes' photos). */
    val imageCredit: ImageCredit? = null,
)

/** The shared libraries: the starter recipes everyone sees, and Cook Well for the guest list. Only the owner edits them, and they're hidden rather than deleted. */
val LIBRARY_SOURCES = setOf("starter", "cookwell")

/** A recipe in one of the owner's libraries (Settings → Recipe libraries): everyone = starter, guests = Cook Well. */
@Serializable
data class LibraryRecipe(
    val id: Int,
    val title: String,
    val imageUrl: String? = null,
    val source: String = "",
    val cuisine: String? = null,
    val kcalPerServing: Double? = null,
    val hidden: Boolean = false,
    /** A starter recipe changed in the app: recipes.json no longer rewrites it. */
    val edited: Boolean = false,
    /** One of the owner's recipes put here: it can go back to their recipes. */
    val yours: Boolean = false,
)

/** A bug report or feature request (Settings → Feedback). The owner sees who sent it. */
@Serializable
data class Feedback(
    val id: Int,
    val type: String, // bug | feature
    val title: String,
    val body: String = "",
    val meta: Map<String, String> = emptyMap(),
    val status: String = "open", // open | done
    val createdAt: String = "",
    val userName: String? = null,
    val userEmail: String? = null,
)

/** A photo's author and licence, and the page it came from. */
@Serializable
data class ImageCredit(val author: String = "", val license: String = "", val licenseUrl: String? = null, val sourceUrl: String? = null)

/** A food per 100 g, as you see it: your own version when you've edited it ([edited]), or one you added ([own]). */
@Serializable
data class Food(
    val id: Int,
    val name: String,
    val source: String = "",
    val brand: String = "",
    val notes: String = "",
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val sodiumMg: Double? = null,
    val edited: Boolean = false,
    val own: Boolean = false,
    /** recipes you can see that use it, and what they call it (library and detail only) */
    val recipes: Int = 0,
    val names: List<String> = emptyList(),
    /** detail only: the standard values when you have your own version, and where it's used */
    val default: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val usedIn: List<RecipeRef> = emptyList(),
)

@Serializable
data class FoodIn(
    val name: String,
    val brand: String = "",
    val notes: String = "",
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val sodiumMg: Double? = null,
)

/** An ingredient name with no food, so it adds nothing (in recipes you can edit). */
@Serializable
data class Unlinked(val name: String, val count: Int, val recipes: List<RecipeRef> = emptyList())

@Serializable
data class PrepUse(val entryId: Int, val day: String? = null, val title: String = "", val grams: Double)

/** A prep you made or will make: what's in the fridge now, and what's spare after the planned meals. */
@Serializable
data class PrepStock(
    val entryId: Int,
    val recipeId: Int,
    val title: String,
    val imageUrl: String? = null,
    val day: String? = null,
    val madeGrams: Double,
    val discarded: Double = 0.0,
    val gramsNow: Double = 0.0,
    val gramsLeft: Double = 0.0,
    val kcalPer100g: Double? = null,
    val uses: List<PrepUse> = emptyList(),
)

@Serializable
data class Shortfall(val prepId: Int, val title: String = "", val grams: Double)

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
    /** makes a prepped ingredient (madeGrams of it); nothing is eaten here */
    val isPrep: Boolean = false,
    val madeGrams: Double? = null,
    val gramsLeft: Double? = null,
    /** preps this meal needs that nothing planned covers (the grocery list buys their ingredients) */
    val short: List<Shortfall> = emptyList(),
    /** breakfast, lunch or dinner: the planner's panels */
    val meal: String = "dinner",
    /** logged: "eaten", or "out" (ate out instead; what was planned went to the fridge); null = not yet */
    val status: String? = null,
    val outKcal: Double? = null,
    /** what the log counts toward the day's calories */
    val eatenKcal: Double? = null,
) {
    val isOut get() = status == "out"
    val isBatch get() = cookPortions != null && !isOut
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
    /** days you logged eating out (red on the calendar) */
    val outDays: List<String> = emptyList(),
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

val MEALS = listOf("breakfast", "lunch", "dinner")
val MEAL_LABEL = mapOf("breakfast" to "Breakfast", "lunch" to "Lunch", "dinner" to "Dinner")
val MEAL_EMOJI = mapOf("breakfast" to "🍳", "lunch" to "🥪", "dinner" to "🍲")
