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
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()

    // --- auth
    suspend fun signIn(googleIdToken: String): Me = post("/auth/google", buildJsonObject {
        put("credential", googleIdToken)
        put("want_token", true)
    })
    suspend fun me(): Me = get("/auth/me")
    suspend fun setKcalGoal(kcal: Int): Me = patch("/auth/me", buildJsonObject { put("kcal_goal", kcal) })
    suspend fun signOut() { post<JsonElement>("/auth/logout", JsonObject(emptyMap())) }

    // --- recipes
    suspend fun recipes(q: String = "", cuisine: String = "", category: String = ""): List<RecipeSummary> =
        get("/recipes", "q" to q, "cuisine" to cuisine, "category" to category)
    suspend fun facets(): Facets = get("/recipes/facets")
    suspend fun recipe(id: Int): RecipeDetail = get("/recipes/$id")
    /** Set grams and/or food; pass JsonNull to clear. Returns the whole updated recipe. */
    suspend fun patchIngredient(id: Int, patch: JsonObject): RecipeDetail = patch("/ingredients/$id", patch)
    suspend fun foods(q: String): List<Food> = get("/foods", "q" to q)

    // --- planner
    suspend fun plan(start: String, days: Int = 7): Plan = get("/plan", "start" to start, "days" to days.toString())
    suspend fun addEntry(body: JsonObject): PlanEntry = post("/plan", body)
    suspend fun updateEntry(id: Int, patch: JsonObject): PlanEntry = patch("/plan/$id", patch)
    suspend fun deleteEntry(id: Int) { delete<JsonElement>("/plan/$id") }
    suspend fun batches(): List<PlanEntry> = get("/batches")

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
