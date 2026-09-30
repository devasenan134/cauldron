package io.github.devasenan134.cauldron.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.devasenan134.cauldron.data.LIBRARY_SOURCES
import io.github.devasenan134.cauldron.data.IngredientIn
import io.github.devasenan134.cauldron.data.RecipeDetail
import io.github.devasenan134.cauldron.data.RecipeIn
import io.github.devasenan134.cauldron.data.StepIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// The editor's working copy. Ingredients sit in sections ("Sauce", "Toppings"), like the library's.
// prepId: made from one of your prepped ingredients (dropped when the name changes; the server links
// a name that matches a prep by itself).
private class IngRow(name: String = "", label: String = "", note: String = "", prepId: Int? = null) {
    var name by mutableStateOf(name); var label by mutableStateOf(label); var note by mutableStateOf(note)
    var prepId by mutableStateOf(prepId)
}
private class Section(name: String = "", rows: List<IngRow> = listOf(IngRow())) {
    var name by mutableStateOf(name)
    val rows: SnapshotStateList<IngRow> = mutableStateListOf<IngRow>().apply { addAll(rows) }
}
private class StepRow(title: String = "", text: String = "") {
    var title by mutableStateOf(title); var text by mutableStateOf(text)
}

private class Draft {
    var title by mutableStateOf(""); var description by mutableStateOf("")
    var imageUrl by mutableStateOf<String?>(null)
    var minutes by mutableStateOf(""); var servings by mutableStateOf(""); var yieldText by mutableStateOf("")
    var cuisine by mutableStateOf(""); var category by mutableStateOf("")
    var tags by mutableStateOf(setOf<String>())
    var videoUrl by mutableStateOf(""); var sourceUrl by mutableStateOf(""); var notes by mutableStateOf("")
    var isPrep by mutableStateOf(false); var yieldGrams by mutableStateOf("")
    val sections = mutableStateListOf(Section())
    val steps = mutableStateListOf(StepRow())

    fun fill(r: RecipeDetail) {
        title = r.title; description = r.description; imageUrl = r.imageUrl
        minutes = r.totalMinutes?.toString() ?: ""; servings = r.servings?.let { num(it) } ?: ""; yieldText = r.yieldText ?: ""
        cuisine = r.cuisine ?: ""; category = r.category ?: ""; tags = r.tags.toSet()
        videoUrl = r.videoUrl ?: ""; sourceUrl = r.sourceUrl ?: ""; notes = r.notes
        isPrep = r.isPrep; yieldGrams = r.yieldGrams?.let { num(it) } ?: ""
        sections.clear()
        r.ingredients.groupBy { it.group.orEmpty() }.forEach { (g, ings) -> sections += Section(g, ings.map { IngRow(it.name, it.label, it.note, it.prepId) }) }
        if (sections.isEmpty()) sections += Section()
        steps.clear(); steps.addAll(r.steps.map { StepRow(it.title, it.text) }); if (steps.isEmpty()) steps += StepRow()
    }

    fun toBody() = RecipeIn(
        title = title.trim(), description = description.trim(), imageUrl = imageUrl,
        videoUrl = videoUrl.trim().ifEmpty { null }, sourceUrl = sourceUrl.trim().ifEmpty { null },
        servings = servings.replace(',', '.').toDoubleOrNull(), yieldText = yieldText.trim().ifEmpty { null },
        totalMinutes = minutes.toIntOrNull(), cuisine = cuisine.trim().ifEmpty { null }, category = category.trim().ifEmpty { null },
        tags = tags.toList(), notes = notes.trim(),
        isPrep = isPrep, yieldGrams = if (isPrep) yieldGrams.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } else null,
        ingredients = sections.flatMap { s -> s.rows.filter { it.name.isNotBlank() }.map { IngredientIn(s.name.trim().ifEmpty { null }, it.name.trim(), it.note.trim(), it.label.trim(), it.prepId) } },
        steps = steps.filter { it.text.isNotBlank() }.map { StepIn(it.title.trim(), it.text.trim()) },
    )
}

/** Write a recipe of your own, or edit one ([id]). [saved] gets the recipe's id. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeEditorScreen(id: Int?, back: () -> Unit, saved: (Int) -> Unit, deleted: () -> Unit, from: Int? = null) {
    // from: "Make my version" of that recipe: a new recipe that starts as its copy (made on save).
    val source = id ?: from
    val app = app()
    val store = app.store
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val draft = remember { Draft() }
    var loaded by remember { mutableStateOf(source == null) }
    // Library recipes aren't deleted, only hidden (Settings → Recipe libraries).
    var isLibrary by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val facets by store.facets.collectAsState()
    var preps by remember { mutableStateOf<List<io.github.devasenan134.cauldron.data.RecipeSummary>>(emptyList()) }
    LaunchedEffect(Unit) { preps = runCatching { app.api.preps() }.getOrDefault(emptyList()).filter { it.id != id } }
    fun stem(t: String) = t.trim().lowercase().removeSuffix("s")
    fun prepNamed(name: String) = preps.firstOrNull { stem(it.title) == stem(name) && name.isNotBlank() }
    // What the form held when it opened: leaving with changes asks first.
    var original by remember { mutableStateOf<RecipeIn?>(if (source == null) Draft().toBody() else null) }
    val dirty = loaded && original != null && draft.toBody() != original
    val leave = { if (dirty) confirmDiscard = true else back() }
    BackHandler(enabled = dirty) { confirmDiscard = true }

    LaunchedEffect(id) {
        runCatching { store.loadFacets() }
        if (source != null && !loaded) {
            try {
                val r = store.recipe.value[source] ?: store.loadRecipe(source)
                draft.fill(r)
                isLibrary = id != null && r.source in LIBRARY_SOURCES
                if (id == null) draft.title = "${r.title} (my version)"
                original = draft.toBody(); loaded = true
            } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            uploading = true
            try {
                val jpeg = withContext(Dispatchers.IO) { shrink(context, uri) }
                draft.imageUrl = app.api.uploadImage(jpeg, "image/jpeg")
            } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) } finally { uploading = false }
        }
    }

    fun save() {
        if (draft.title.isBlank()) { scope.launch { snackbar.showSnackbar("Give your recipe a title") }; return }
        saving = true
        scope.launch {
            try {
                val r = if (from != null && id == null) app.api.recipe(app.api.makeVariation(from, draft.toBody())).also { store.forgetRecipe(from) }
                    else if (id == null) app.api.createRecipe(draft.toBody()) else app.api.updateRecipe(id, draft.toBody())
                store.putRecipe(r); store.recipesChanged(); store.refreshPlans()
                saved(r.id)
            } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) } finally { saving = false }
        }
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
      Column(Modifier.fillMaxSize()) {
        // Pinned: Save stays in reach however far down the form you are.
                Row(Modifier.fillMaxWidth().background(C.bg).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FloatingCircle(leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.ink) }
                    Text(if (from != null && id == null) "My version" else if (id == null) "New recipe" else "Edit recipe", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    Button(onClick = ::save, enabled = !saving && !uploading && loaded, colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo)) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = C.onGo, strokeWidth = 2.dp) else Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
        LazyColumn(Modifier.weight(1f).windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
            if (!loaded) item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.go) } }
            else {
                // Photo
                item {
                    Box(
                        Modifier.padding(16.dp).fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(24.dp)).background(C.surfaceAlt)
                            .pressable({ pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, 0.98f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (draft.imageUrl != null) AsyncImage(thumb(draft.imageUrl, 1000, 750), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (uploading) CircularProgressIndicator(color = C.go)
                        else if (draft.imageUrl == null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, null, tint = C.muted, modifier = Modifier.size(36.dp))
                            Text("Add a photo", color = C.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        } else Text("Change photo", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Field(draft.title, { draft.title = it }, "Recipe title", big = true, capitalize = true)
                        Field(draft.description, { draft.description = it }, "A line about it (what makes it good?)", minLines = 2, capitalize = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Field(draft.minutes, { draft.minutes = it.filter(Char::isDigit).take(4) }, "Minutes", Modifier.weight(1f), number = true, label = "Time")
                            Field(draft.servings, { draft.servings = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, "Servings", Modifier.weight(1f), number = true, label = "Makes")
                        }
                        Field(draft.yieldText, { draft.yieldText = it }, "Yield as you'd say it, e.g. 3-4 burritos (optional)")
                        Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (draft.isPrep) C.goSoft else C.surface).padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("🫙 Prepped ingredient", fontWeight = FontWeight.SemiBold)
                                    Text("Cooked rice, pickled onions, a sauce: other recipes use it by weight, and what you make goes in the fridge.",
                                        fontSize = 13.sp, color = C.muted)
                                }
                                androidx.compose.material3.Switch(checked = draft.isPrep, onCheckedChange = { draft.isPrep = it },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = C.go, checkedThumbColor = C.onGo))
                            }
                            if (draft.isPrep) Field(draft.yieldGrams, { draft.yieldGrams = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                                "Blank: what the ingredients weigh", number = true, label = "Weighs when done (g)")
                        }
                        Group("Meal") {
                            val options = (facets?.categories.orEmpty() + listOf("Breakfast", "Lunch", "Dinner", "Side", "Snack", "Dessert")).distinct()
                            options.forEach { c -> Chip(c, draft.category == c) { draft.category = if (draft.category == c) "" else c } }
                        }
                        Field(draft.cuisine, { draft.cuisine = it }, "Cuisine, e.g. Indian", label = "Cuisine", capitalize = true)
                        facets?.cuisines?.filter { it.startsWith(draft.cuisine, ignoreCase = true) && !it.equals(draft.cuisine, true) }?.take(6)?.takeIf { draft.cuisine.isNotEmpty() && it.isNotEmpty() }?.let { hits ->
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { hits.forEach { h -> Chip(h, false) { draft.cuisine = h } } }
                        }
                        // Tags in Cook Well's families (difficulty, time, mood, protein, method, diet).
                        facets?.tagGroups?.filter { it.name != "More" }?.forEach { g ->
                            Group(g.name) { g.tags.forEach { t -> Chip(t, t in draft.tags) { draft.tags = draft.tags.toggle(t) } } }
                        }
                    }
                }
                // Ingredients
                item { SectionTitle("Ingredients", "Write amounts the way you'd say them: 200 g, 2 cloves, 1 tbsp, a drizzle. Calories are worked out when you save. Name one of your prepped ingredients (300 g cooked rice) to use it.") }
                itemsIndexed(draft.sections) { si, section ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Field(section.name, { section.name = it }, if (draft.sections.size > 1) "Section name, e.g. Sauce" else "Section (optional), e.g. Sauce", Modifier.weight(1f), capitalize = true, small = true)
                            if (draft.sections.size > 1) IconButtonSmall(Icons.Default.DeleteOutline, "Remove section") { draft.sections.removeAt(si) }
                        }
                        section.rows.forEachIndexed { ri, row ->
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 6.dp)) {
                                Field(row.label, { row.label = it }, "Amount", Modifier.width(96.dp), small = true)
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Field(row.name, { row.name = it; row.prepId = null }, "Ingredient", small = true, capitalize = true)
                                    val prep = row.prepId?.let { pid -> preps.firstOrNull { it.id == pid } } ?: prepNamed(row.name)
                                    if (prep != null || row.prepId != null) Text("🫙 uses your prep" + (prep?.let { ": ${it.title}" } ?: ""), color = C.goText,
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                                    // Suggest your preps while typing.
                                    if (row.prepId == null && row.name.length >= 2 && prepNamed(row.name) == null) {
                                        val hits = preps.filter { it.title.contains(row.name.trim(), ignoreCase = true) }.take(3)
                                        if (hits.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                            hits.forEach { h -> Chip("🫙 ${h.title}", false) { row.name = h.title; row.prepId = h.id } }
                                        }
                                    }
                                    Field(row.note, { row.note = it }, "Note, e.g. diced (optional)", small = true)
                                }
                                IconButtonSmall(Icons.Default.Close, "Remove ingredient") { if (section.rows.size > 1) section.rows.removeAt(ri) else { row.name = ""; row.label = ""; row.note = "" } }
                            }
                        }
                        TextButton(onClick = { section.rows += IngRow() }) { Icon(Icons.Default.Add, null, tint = C.ink); Text(" Add ingredient", color = C.ink, fontWeight = FontWeight.SemiBold) }
                    }
                }
                item {
                    TextButton(onClick = { draft.sections += Section(rows = listOf(IngRow())) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Default.Add, null, tint = C.muted); Text(" Add a section", color = C.muted, fontWeight = FontWeight.SemiBold)
                    }
                }
                // Steps
                item { SectionTitle("Steps", null) }
                itemsIndexed(draft.steps) { i, step ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(C.ink), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = C.bg, fontFamily = Display, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).padding(start = 10.dp).clip(RoundedCornerShape(20.dp)).background(C.surface).padding(10.dp)) {
                            Field(step.title, { step.title = it }, "Step title, e.g. Sear the chicken (optional)", small = true, capitalize = true)
                            Field(step.text, { step.text = it }, "What to do", minLines = 3, small = true, capitalize = true)
                        }
                        IconButtonSmall(Icons.Default.Close, "Remove step") { if (draft.steps.size > 1) draft.steps.removeAt(i) else { step.title = ""; step.text = "" } }
                    }
                }
                item {
                    TextButton(onClick = { draft.steps += StepRow() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Default.Add, null, tint = C.ink); Text(" Add a step", color = C.ink, fontWeight = FontWeight.SemiBold)
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle("More", null, padded = false)
                        Field(draft.videoUrl, { draft.videoUrl = it }, "Video link (YouTube, Reels…)", label = "Video")
                        Field(draft.sourceUrl, { draft.sourceUrl = it }, "Where it's from (a link, optional)", label = "Source")
                        Field(draft.notes, { draft.notes = it }, "Notes for next time", label = "Notes", minLines = 2, capitalize = true)
                        if (id != null && !isLibrary) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 16.dp)) {
                            Icon(Icons.Default.DeleteOutline, null, tint = C.danger); Text(" Delete this recipe", color = C.danger, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
      }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
    }

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(if (id == null) "Discard this recipe?" else "Discard your changes?") },
        text = { Text("What you've written here will be lost.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; back() }) { Text("Discard", color = C.danger, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
    )

    if (confirmDelete && id != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${draft.title}”?") },
        text = { Text("It will also leave your plan.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    try {
                        app.api.deleteRecipe(id)
                        store.forgetRecipe(id); store.recipesChanged(); store.refreshPlans()
                        deleted()
                    } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
                }
            }) { Text("Delete", color = C.danger, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
    )
}

/** A photo from the gallery, shrunk to at most 1600 px and saved as JPEG (phones take huge ones). */
private fun shrink(context: Context, uri: Uri): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: error("That file isn't a photo Cauldron can read")
    val scale = 1600f / maxOf(bitmap.width, bitmap.height)
    val sized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
    return ByteArrayOutputStream().also { sized.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
}

@Composable
private fun SectionTitle(title: String, hint: String?, padded: Boolean = true) {
    Column(Modifier.padding(start = if (padded) 16.dp else 0.dp, end = if (padded) 16.dp else 0.dp, top = 24.dp, bottom = 6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        hint?.let { Text(it, color = C.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(title.uppercase(), color = C.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.8.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { content() }
    }
}

@Composable
private fun IconButtonSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).pressable(onClick, 0.85f), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = C.faint, modifier = Modifier.size(20.dp))
    }
}

/** A plain rounded text box with a grey hint, in the app's style. */
@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier.fillMaxWidth(),
    label: String? = null, big: Boolean = false, small: Boolean = false, minLines: Int = 1, number: Boolean = false, capitalize: Boolean = false,
) {
    Column(modifier.padding(top = if (small) 4.dp else 10.dp)) {
        label?.let { Text(it.uppercase(), color = C.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 6.dp)) }
        val style = if (big) MaterialTheme.typography.headlineSmall.copy(color = C.ink) else TextStyle(fontSize = if (small) 15.sp else 16.sp, color = C.ink)
        val focus = remember { FocusRequester() }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(if (small) 12.dp else 16.dp)).background(if (small) C.bg else C.surface)
                .clickable(interactionSource = null, indication = null) { focus.requestFocus() } // the whole box, not just the text line
                .border(1.dp, C.line, RoundedCornerShape(if (small) 12.dp else 16.dp)).padding(horizontal = 14.dp, vertical = if (small) 10.dp else 14.dp)
                .heightIn(min = if (minLines > 1) (minLines * 20).dp else 0.dp),
        ) {
            if (value.isEmpty()) Text(hint, style = style.copy(color = C.faint))
            BasicTextField(
                value, onChange, textStyle = style, cursorBrush = SolidColor(C.go), minLines = minLines, singleLine = minLines == 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
                    capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}
