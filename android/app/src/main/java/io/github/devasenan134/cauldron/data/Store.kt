package io.github.devasenan134.cauldron.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * What the screens last loaded, kept in memory: a tab shows it at once and refreshes quietly
 * behind it. Planner edits change the cached plan first (so the screen reacts instantly) and
 * the server's answer replaces it after.
 */
class Store(private val api: Api) {
    private val _recipes = MutableStateFlow<Map<String, List<RecipeSummary>>>(emptyMap())
    val recipes: StateFlow<Map<String, List<RecipeSummary>>> = _recipes
    private val _facets = MutableStateFlow<Facets?>(null)
    val facets: StateFlow<Facets?> = _facets
    private val _recipe = MutableStateFlow<Map<Int, RecipeDetail>>(emptyMap())
    val recipe: StateFlow<Map<Int, RecipeDetail>> = _recipe
    private val _plans = MutableStateFlow<Map<String, Plan>>(emptyMap())
    val plans: StateFlow<Map<String, Plan>> = _plans
    private val _batches = MutableStateFlow<List<PlanEntry>?>(null)
    val batches: StateFlow<List<PlanEntry>?> = _batches
    private val _prepStock = MutableStateFlow<List<PrepStock>?>(null)
    val prepStock: StateFlow<List<PrepStock>?> = _prepStock

    fun recipesKey(f: RecipeFilter) = f.toString()

    suspend fun loadRecipes(f: RecipeFilter) {
        val list = api.recipes(f)
        _recipes.update { it + (recipesKey(f) to list) }
    }

    /** A recipe was created, changed or deleted: every cached list may be stale. */
    fun recipesChanged() = _recipes.update { emptyMap() }

    fun forgetRecipe(id: Int) = _recipe.update { it - id }

    suspend fun loadFacets() { _facets.value = api.facets() }

    suspend fun loadRecipe(id: Int) = api.recipe(id).also { putRecipe(it) }
    fun putRecipe(r: RecipeDetail) = _recipe.update { it + (r.id to r) }

    suspend fun loadPlan(week: String) {
        val plan = api.plan(week)
        _plans.update { it + (week to plan) }
    }

    /** Change a cached plan right away (the screen redraws), before the server has answered. */
    fun editPlan(week: String, change: (Plan) -> Plan) = _plans.update { plans ->
        plans[week]?.let { plans + (week to change(it)) } ?: plans
    }

    /** A plan entry's fields changed: show it everywhere it's cached. */
    fun editEntry(id: Int, change: (PlanEntry) -> PlanEntry) {
        _plans.update { plans ->
            plans.mapValues { (_, p) ->
                Plan(p.days.mapValues { (_, l) -> l.map { if (it.id == id) change(it) else it } }, p.queue.map { if (it.id == id) change(it) else it })
            }
        }
        _batches.update { list -> list?.map { if (it.id == id) change(it) else it } }
    }

    suspend fun loadBatches() { _batches.value = api.batches() }
    suspend fun loadPrepStock() { _prepStock.value = api.prepStock() }
    fun editPrepStock(change: (List<PrepStock>) -> List<PrepStock>) = _prepStock.update { it?.let(change) }

    /** The plan changed on the server: every cached week and the fridge may be stale. */
    suspend fun refreshPlans() {
        _plans.value.keys.forEach { runCatching { loadPlan(it) } }
        runCatching { loadBatches() }
        runCatching { loadPrepStock() }
    }

    fun clear() {
        _recipes.value = emptyMap(); _facets.value = null; _recipe.value = emptyMap()
        _plans.value = emptyMap(); _batches.value = null; _prepStock.value = null
    }
}
