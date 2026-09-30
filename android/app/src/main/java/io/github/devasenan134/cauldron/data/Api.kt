package io.github.devasenan134.cauldron.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The session is missing or expired: the app goes back to the sign-in screen. */
class SignedOutException : IOException("signed out")

/** The server said no (a 4xx/5xx other than 401). */
class ApiException(val code: Int, message: String) : IOException(message)

/** The Cauldron server's /api. Every call throws IOException when offline. */
class Api(baseUrl: String, private val token: () -> String?, private val onSignedOut: () -> Unit) {
    private val base = baseUrl.trimEnd('/') + "/api"
    /** The server's address, for photos stored on it ("/api/images/…"). */
    val origin = baseUrl.trimEnd('/')
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()

    // --- auth
    suspend fun signIn(googleIdToken: String): Me = post("/auth/google", buildJsonObject {
        put("credential", googleIdToken)
        put("want_token", true)
    })
    suspend fun me(): Me = get("/auth/me")
    suspend fun setKcalGoal(kcal: Int): Me = patch("/auth/me", buildJsonObject { put("kcal_goal", kcal) })
    suspend fun setTheme(theme: String): Me = patch("/auth/me", buildJsonObject { put("theme", theme) })
    suspend fun setCatalogView(view: String): Me = patch("/auth/me", buildJsonObject { put("catalog_view", view) })
    suspend fun signOut() { post<JsonElement>("/auth/logout", JsonObject(emptyMap())) }
    /** Deletes the account and everything in it. There's no undo. */
    suspend fun deleteAccount() { delete<JsonElement>("/auth/me") }
    /** All your data, as the JSON text to save to a file. */
    suspend fun exportData(): String = call("GET", "/auth/me/export", null, emptyArray())

    // --- recipes
    suspend fun recipes(q: String = ""): List<RecipeSummary> = get("/recipes", "q" to q)
    suspend fun recipes(f: RecipeFilter): List<RecipeSummary> = get(
        "/recipes",
        *(listOf("q" to f.q.trim(), "sort" to f.sort, "mine" to if (f.mine) "true" else "", "prep" to if (f.prep) "true" else "",
            "max_minutes" to (f.maxMinutes?.toString() ?: ""),
            "min_kcal" to (f.kcal?.min?.toString() ?: ""), "max_kcal" to (f.kcal?.max?.toString() ?: "")) +
            f.cuisines.map { "cuisine" to it } + f.categories.map { "category" to it } + f.tags.map { "tag" to it }).toTypedArray(),
    )
    suspend fun createRecipe(body: RecipeIn): RecipeDetail = post("/recipes", json.encodeToJsonElement(RecipeIn.serializer(), body) as JsonObject)
    suspend fun updateRecipe(id: Int, body: RecipeIn): RecipeDetail = put("/recipes/$id", json.encodeToJsonElement(RecipeIn.serializer(), body) as JsonObject)
    suspend fun deleteRecipe(id: Int) { delete<JsonElement>("/recipes/$id") }

    /** Upload a photo; returns its URL on the server. */
    suspend fun uploadImage(bytes: ByteArray, type: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$base/images").apply {
            token()?.let { header("Authorization", "Bearer $it") }
            post(bytes.toRequestBody(type.toMediaType()))
        }.build()
        http.newCall(request).execute().use { res ->
            if (res.code == 401) { onSignedOut(); throw SignedOutException() }
            val text = res.body.string()
            if (!res.isSuccessful) throw ApiException(res.code, detail(text) ?: "Upload failed (${res.code})")
            (json.parseToJsonElement(text) as JsonObject)["url"].toString().trim('"')
        }
    }
    suspend fun facets(): Facets = get("/recipes/facets")
    suspend fun recipe(id: Int): RecipeDetail = get("/recipes/$id")
    /** Set grams and/or food; pass JsonNull to clear. Returns the whole updated recipe. */
    suspend fun patchIngredient(id: Int, patch: JsonObject): RecipeDetail = patch("/ingredients/$id", patch)
    suspend fun foods(q: String): List<Food> = get("/foods", "q" to q)
    suspend fun setPrep(id: Int, patch: JsonObject): RecipeDetail = patch("/recipes/$id/prep", patch)
    suspend fun preps(): List<RecipeSummary> = get("/recipes", "prep" to "true")

    // --- ingredients (foods) and their macros
    suspend fun foodLibrary(): List<Food> = get("/foods/library")
    suspend fun food(id: Int): Food = get("/foods/$id")
    suspend fun saveFood(id: Int, body: FoodIn): Food = put("/foods/$id", json.encodeToJsonElement(FoodIn.serializer(), body) as JsonObject)
    suspend fun addFood(body: FoodIn): Food = post("/foods", json.encodeToJsonElement(FoodIn.serializer(), body) as JsonObject)
    /** Back to the standard values (or delete a food you added). */
    suspend fun resetFood(id: Int) { delete<JsonElement>("/foods/$id") }
    suspend fun unlinked(): List<Unlinked> = get("/foods/unlinked")
    suspend fun assignFood(id: Int, name: String) { post<JsonElement>("/foods/$id/assign", buildJsonObject { put("name", name) }) }

    // --- profile and catalog
    suspend fun profile(): Profile = get("/profile")
    suspend fun cooked(): List<Cooked> = get("/cooked")
    suspend fun catalog(): Catalog = get("/catalog")
    suspend fun setFavorite(recipeId: Int, on: Boolean) {
        if (on) put<JsonElement>("/favorites/$recipeId", JsonObject(emptyMap())) else delete<JsonElement>("/favorites/$recipeId")
    }
    suspend fun createFolder(name: String): FolderSummary = post("/folders", buildJsonObject { put("name", name) })
    suspend fun renameFolder(id: Int, name: String): FolderSummary = patch("/folders/$id", buildJsonObject { put("name", name) })
    suspend fun deleteFolder(id: Int) { delete<JsonElement>("/folders/$id") }
    suspend fun folder(id: Int): FolderDetail = get("/folders/$id")
    suspend fun setInFolder(folderId: Int, recipeId: Int, on: Boolean) {
        if (on) put<JsonElement>("/folders/$folderId/recipes/$recipeId", JsonObject(emptyMap()))
        else delete<JsonElement>("/folders/$folderId/recipes/$recipeId")
    }
    /** Copies the recipe as your own; returns the copy's id. */
    suspend fun makeVariation(recipeId: Int, body: RecipeIn): Int =
        (post<JsonObject>("/recipes/$recipeId/variation", json.encodeToJsonElement(RecipeIn.serializer(), body) as JsonObject)["id"].toString()).toInt()

    // --- recipe libraries (the owner): "everyone" or "guests"
    suspend fun library(name: String): List<LibraryRecipe> = get("/admin/libraries/$name")
    suspend fun setLibraryHidden(id: Int, hidden: Boolean): LibraryRecipe = patch("/admin/libraries/recipes/$id", buildJsonObject { put("hidden", hidden) })
    suspend fun addToLibrary(name: String, id: Int): LibraryRecipe = post("/admin/libraries/$name/recipes/$id", JsonObject(emptyMap()))
    suspend fun takeBackFromLibrary(id: Int) { delete<JsonElement>("/admin/libraries/recipes/$id") }

    // --- feedback
    /** Yours; for the owner, everyone's. */
    suspend fun feedback(): List<Feedback> = get("/feedback")
    suspend fun sendFeedback(type: String, title: String, body: String, meta: Map<String, String>): Feedback = post("/feedback", buildJsonObject {
        put("type", type); put("title", title); put("body", body)
        put("meta", buildJsonObject { meta.forEach { (k, v) -> put(k, v) } })
    })
    suspend fun setFeedbackStatus(id: Int, status: String): Feedback = patch("/feedback/$id", buildJsonObject { put("status", status) })

    // --- importing from videos, recipe pages and files
    suspend fun importStatus(): ImportStatus = get("/import/status")
    suspend fun startImport(url: String): ImportJob = post("/import", buildJsonObject { put("url", url) })
    /** A PDF, a photo of a recipe, or a recipe file (YAML, JSON, text). */
    suspend fun importFile(bytes: ByteArray, type: String, name: String): ImportJob = withContext(Dispatchers.IO) {
        val url = "$base/import/file".toHttpUrl().newBuilder().addQueryParameter("name", name).build()
        val request = Request.Builder().url(url).apply {
            token()?.let { header("Authorization", "Bearer $it") }
            post(bytes.toRequestBody(type.toMediaType()))
        }.build()
        http.newCall(request).execute().use { res ->
            if (res.code == 401) { onSignedOut(); throw SignedOutException() }
            val text = res.body.string()
            if (!res.isSuccessful) throw ApiException(res.code, detail(text) ?: "Upload failed (${res.code})")
            json.decodeFromString(text)
        }
    }
    suspend fun importJob(id: Int): ImportJob = get("/import/$id")

    // --- grocery templates
    suspend fun templates(): List<GroceryTemplate> = get("/grocery/templates")
    suspend fun saveTemplate(id: Int?, name: String, items: List<TemplateItem>): GroceryTemplate {
        val body = buildJsonObject {
            put("name", name)
            put("items", json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(TemplateItem.serializer()), items))
        }
        return if (id == null) post("/grocery/templates", body) else put("/grocery/templates/$id", body)
    }
    suspend fun templateFromList(name: String): GroceryTemplate = post("/grocery/templates/from-list", buildJsonObject { put("name", name) })
    suspend fun deleteTemplate(id: Int) { delete<JsonElement>("/grocery/templates/$id") }
    suspend fun applyTemplate(id: Int): List<GroceryItem> = post("/grocery/templates/$id/apply", JsonObject(emptyMap()))

    // --- planner
    suspend fun plan(start: String, days: Int = 7): Plan = get("/plan", "start" to start, "days" to days.toString())
    suspend fun addEntry(body: JsonObject): PlanEntry = post("/plan", body)
    suspend fun updateEntry(id: Int, patch: JsonObject): PlanEntry = patch("/plan/$id", patch)
    suspend fun deleteEntry(id: Int) { delete<JsonElement>("/plan/$id") }
    suspend fun batches(): List<PlanEntry> = get("/batches")
    suspend fun prepStock(): List<PrepStock> = get("/prep-stock")

    // --- grocery
    suspend fun grocery(): List<GroceryItem> = get("/grocery")
    suspend fun generateGrocery(start: String, end: String, includeQueue: Boolean): List<GroceryItem> =
        post("/grocery/generate", buildJsonObject {
            put("start", start); put("end", end); put("include_queue", includeQueue)
        })
    suspend fun addGrocery(name: String, amount: String): GroceryItem =
        post("/grocery", buildJsonObject { put("name", name); put("amount", amount) })
    suspend fun updateGrocery(id: Int, patch: JsonObject): GroceryItem = patch("/grocery/$id", patch)
    suspend fun deleteGrocery(id: Int) { delete<JsonElement>("/grocery/$id") }
    suspend fun clearChecked() { delete<JsonElement>("/grocery", "checked_only" to "true") }

    // --- app updates
    /** The newest app version the server has, or null. */
    suspend fun latestApp(): AppRelease? = get("/app/latest")
    /** Every app version on the server with its notes, newest first. */
    suspend fun appReleases(): List<AppRelease> = get("/app/releases")

    /** Download an app version into [file], reporting progress from 0 to 1. */
    suspend fun downloadApp(version: String, file: java.io.File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$base/app/download/$version").apply {
            token()?.let { header("Authorization", "Bearer $it") }
        }.build()
        http.newBuilder().callTimeout(0, TimeUnit.SECONDS).build().newCall(request).execute().use { res ->
            if (res.code == 401) { onSignedOut(); throw SignedOutException() }
            if (!res.isSuccessful) throw ApiException(res.code, "Download failed (${res.code})")
            val total = res.body.contentLength().takeIf { it > 0 }
            res.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        total?.let { onProgress(done.toFloat() / it) }
                    }
                }
            }
        }
    }

    // --- plumbing

    private suspend inline fun <reified T> get(path: String, vararg query: Pair<String, String>): T =
        json.decodeFromString(call("GET", path, null, query))

    private suspend inline fun <reified T> post(path: String, body: JsonObject): T =
        json.decodeFromString(call("POST", path, body, emptyArray()))

    private suspend inline fun <reified T> patch(path: String, body: JsonObject): T =
        json.decodeFromString(call("PATCH", path, body, emptyArray()))

    private suspend inline fun <reified T> put(path: String, body: JsonObject): T =
        json.decodeFromString(call("PUT", path, body, emptyArray()))

    private suspend inline fun <reified T> delete(path: String, vararg query: Pair<String, String>): T =
        json.decodeFromString(call("DELETE", path, null, query))

    private suspend fun call(method: String, path: String, body: JsonObject?, query: Array<out Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val url = (base + path).toHttpUrl().newBuilder().apply {
                query.filter { it.second.isNotEmpty() }.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            val request = Request.Builder().url(url).apply {
                token()?.let { header("Authorization", "Bearer $it") }
                method(method, body?.let { it.toString().toRequestBody(JSON) })
            }.build()
            http.newCall(request).execute().use { res ->
                val text = res.body.string()
                when {
                    res.code == 401 -> { onSignedOut(); throw SignedOutException() }
                    !res.isSuccessful -> throw ApiException(res.code, detail(text) ?: "HTTP ${res.code}")
                    else -> text
                }
            }
        }

    private fun detail(text: String): String? =
        runCatching { (json.parseToJsonElement(text) as JsonObject)["detail"]?.toString()?.trim('"') }.getOrNull()

    companion object {
        private val JSON = "application/json".toMediaType()

        @OptIn(ExperimentalSerializationApi::class)
        val json = Json {
            ignoreUnknownKeys = true
            namingStrategy = JsonNamingStrategy.SnakeCase
            explicitNulls = false
            coerceInputValues = true
        }
    }
}
