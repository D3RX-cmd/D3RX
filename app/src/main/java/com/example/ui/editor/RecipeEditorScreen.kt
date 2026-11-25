package com.example.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Recipe
import com.example.data.model.RecipeSourceType
import com.example.ui.components.StarRatingRow
import com.example.ui.cookbook.CookbookViewModel

private val suggestedTagPool = listOf(
    "Weeknight", "Quick", "Comfort Food", "Vegetarian",
    "Italian", "Baking", "Breakfast", "Dessert", "Healthy", "One Pot"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditorScreen(
    existingRecipeId: String?,
    startWithImport: Boolean,
    viewModel: CookbookViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val existing = remember(existingRecipeId) {
        existingRecipeId?.let { viewModel.findRecipeById(it) }
    }
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var sourceType by remember(existing?.id) {
        mutableStateOf(
            if (existing != null) existing.parsedSourceType
            else if (startWithImport) RecipeSourceType.ONLINE
            else RecipeSourceType.PERSONAL
        )
    }
    var sourceUrl by remember(existing?.id) { mutableStateOf(existing?.sourceUrl.orEmpty()) }
    var sourceName by remember(existing?.id) { mutableStateOf(existing?.sourceName.orEmpty()) }
    var imageUrl by remember(existing?.id) { mutableStateOf(existing?.imageUrl.orEmpty()) }
    var prepMinutesText by remember(existing?.id) {
        mutableStateOf((existing?.prepMinutes ?: 15L).toString())
    }
    var cookMinutesText by remember(existing?.id) {
        mutableStateOf((existing?.cookMinutes ?: 20L).toString())
    }
    var servingsText by remember(existing?.id) {
        mutableStateOf((existing?.servings ?: 4L).toString())
    }
    var favorite by remember(existing?.id) { mutableStateOf(existing?.favorite ?: false) }
    var offlinePinned by remember(existing?.id) { mutableStateOf(existing?.offlinePinned ?: true) }
    var rating by remember(existing?.id) { mutableLongStateOf(existing?.rating ?: 0L) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }

    val ingredients = remember(existing?.id) {
        mutableStateListOf<String>().apply {
            if (existing != null && existing.ingredients.isNotEmpty()) {
                addAll(existing.ingredients)
            } else {
                add("")
            }
        }
    }
    val steps = remember(existing?.id) {
        mutableStateListOf<String>().apply {
            if (existing != null && existing.steps.isNotEmpty()) {
                addAll(existing.steps)
            } else {
                add("")
            }
        }
    }
    val tags = remember(existing?.id) {
        mutableStateListOf<String>().apply {
            if (existing != null) addAll(existing.tags)
        }
    }

    var customTagInput by remember { mutableStateOf("") }
    var importFeedback by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var pasteTarget by remember { mutableStateOf<String?>(null) } // "ingredients" or "steps"
    var pasteBuffer by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            existing != null -> "Edit Recipe"
                            startWithImport -> "Import Online Recipe"
                            else -> "New Recipe"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val cleanTitle = title.trim()
                            val cleanIngredients = ingredients.map { it.trim() }.filter { it.isNotEmpty() }
                            val cleanSteps = steps.map { it.trim() }.filter { it.isNotEmpty() }

                            when {
                                cleanTitle.isEmpty() -> {
                                    validationError = "Please enter a recipe title."
                                    return@Button
                                }
                                cleanIngredients.isEmpty() -> {
                                    validationError = "Add at least one ingredient."
                                    return@Button
                                }
                                cleanSteps.isEmpty() -> {
                                    validationError = "Add at least one instruction step."
                                    return@Button
                                }
                            }
                            validationError = null

                            val draft = Recipe(
                                id = existing?.id.orEmpty(),
                                userId = existing?.userId.orEmpty(),
                                title = cleanTitle,
                                description = description.trim(),
                                sourceType = sourceType.name,
                                sourceUrl = sourceUrl.trim().takeIf { it.isNotEmpty() },
                                sourceName = sourceName.trim().takeIf { it.isNotEmpty() },
                                imageUrl = imageUrl.trim().takeIf { it.isNotEmpty() },
                                prepMinutes = (prepMinutesText.toLongOrNull() ?: 0L).coerceIn(0L, 1440L),
                                cookMinutes = (cookMinutesText.toLongOrNull() ?: 0L).coerceIn(0L, 1440L),
                                servings = (servingsText.toLongOrNull() ?: 4L).coerceIn(1L, 100L),
                                ingredients = cleanIngredients,
                                steps = cleanSteps,
                                tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
                                favorite = favorite,
                                offlinePinned = offlinePinned,
                                rating = rating,
                                notes = notes.trim()
                            )

                            viewModel.saveRecipe(existing?.id, draft) { savedId ->
                                onSaved(savedId)
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("editor_save_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = validationError != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = validationError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        )
                    }
                }

                // Source Type Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = sourceType == RecipeSourceType.PERSONAL,
                        onClick = { sourceType = RecipeSourceType.PERSONAL },
                        label = { Text("My Own Recipe") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = sourceType == RecipeSourceType.ONLINE,
                        onClick = { sourceType = RecipeSourceType.ONLINE },
                        label = { Text("Found Online") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Web URL Importer Card (visible when Online source selected)
                AnimatedVisibility(visible = sourceType == RecipeSourceType.ONLINE) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Import from Recipe Link",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedTextField(
                                value = sourceUrl,
                                onValueChange = { sourceUrl = it },
                                label = { Text("Recipe webpage URL") },
                                placeholder = { Text("https://example.com/recipes/pasta") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_url_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        importFeedback = null
                                        viewModel.importFromUrl(
                                            url = sourceUrl,
                                            onSuccess = { draft ->
                                                title = draft.title
                                                if (draft.description.isNotBlank()) description = draft.description
                                                sourceUrl = draft.sourceUrl
                                                sourceName = draft.sourceName
                                                if (!draft.imageUrl.isNullOrBlank()) imageUrl = draft.imageUrl
                                                prepMinutesText = draft.prepMinutes.toString()
                                                cookMinutesText = draft.cookMinutes.toString()
                                                servingsText = draft.servings.toString()
                                                if (draft.ingredients.isNotEmpty()) {
                                                    ingredients.clear()
                                                    ingredients.addAll(draft.ingredients)
                                                }
                                                if (draft.steps.isNotEmpty()) {
                                                    steps.clear()
                                                    steps.addAll(draft.steps)
                                                }
                                                if (draft.tags.isNotEmpty()) {
                                                    tags.clear()
                                                    tags.addAll(draft.tags)
                                                }
                                                importFeedback = if (draft.extractedStructuredData) {
                                                    "Imported ${draft.ingredients.size} ingredients and ${draft.steps.size} steps from ${draft.sourceName}."
                                                } else {
                                                    "Imported title & link from ${draft.sourceName}. Paste or type ingredients and steps below."
                                                }
                                            },
                                            onError = { err ->
                                                importFeedback = err
                                            }
                                        )
                                    },
                                    enabled = !isImporting && sourceUrl.isNotBlank(),
                                    modifier = Modifier.testTag("fetch_url_button")
                                ) {
                                    if (isImporting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Fetching...")
                                    } else {
                                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Fetch Recipe")
                                    }
                                }

                                OutlinedTextField(
                                    value = sourceName,
                                    onValueChange = { sourceName = it },
                                    label = { Text("Source name") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            importFeedback?.let { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Core Info
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 140) title = it },
                    label = { Text("Recipe Title *") },
                    placeholder = { Text("e.g., Roasted Garlic Tomato Rigatoni") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 1000) description = it },
                    label = { Text("Short Description") },
                    placeholder = { Text("What makes this dish special?") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_description_input")
                )

                // Cover Image URL + Preview
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Cover Photo URL (optional)") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (imageUrl.trim().startsWith("http")) {
                    AsyncImage(
                        model = imageUrl.trim(),
                        contentDescription = "Recipe cover preview",
                        placeholder = painterResource(id = R.drawable.img_recipe_default),
                        error = painterResource(id = R.drawable.img_recipe_default),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }

                // Times & Servings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = prepMinutesText,
                        onValueChange = { prepMinutesText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Prep (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cookMinutesText,
                        onValueChange = { cookMinutesText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Cook (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = servingsText,
                        onValueChange = { servingsText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Servings") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Tags Section
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.titleMedium
                )
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { tags.remove(tag) },
                                label = { Text("#$tag") },
                                trailingIcon = {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove tag", modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customTagInput,
                        onValueChange = { customTagInput = it },
                        label = { Text("Add tag") },
                        placeholder = { Text("e.g., Sunday Dinner") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val clean = customTagInput.trim()
                            if (clean.isNotEmpty() && tags.none { it.equals(clean, ignoreCase = true) } && tags.size < 20) {
                                tags.add(clean)
                                customTagInput = ""
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    suggestedTagPool.filter { pool ->
                        tags.none { it.equals(pool, ignoreCase = true) }
                    }.forEach { suggestion ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (tags.size < 20) tags.add(suggestion)
                            },
                            label = { Text("+ $suggestion") }
                        )
                    }
                }

                // Ingredients Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ingredients *",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = {
                            pasteBuffer = ""
                            pasteTarget = "ingredients"
                        }
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste list")
                    }
                }

                ingredients.forEachIndexed { index, value ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { ingredients[index] = it },
                            placeholder = { Text("e.g., 2 cloves garlic, minced") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_ingredient_$index")
                        )
                        if (ingredients.size > 1) {
                            IconButton(
                                onClick = { ingredients.removeAt(index) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Remove ingredient")
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { if (ingredients.size < 100) ingredients.add("") },
                    modifier = Modifier.testTag("editor_add_ingredient_btn")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Ingredient")
                }

                // Steps Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Instructions *",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = {
                            pasteBuffer = ""
                            pasteTarget = "steps"
                        }
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste steps")
                    }
                }

                steps.forEachIndexed { index, value ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { steps[index] = it },
                            label = { Text("Step ${index + 1}") },
                            minLines = 2,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_step_$index")
                        )
                        if (steps.size > 1) {
                            IconButton(
                                onClick = { steps.removeAt(index) },
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(48.dp)
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Remove step")
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { if (steps.size < 100) steps.add("") },
                    modifier = Modifier.testTag("editor_add_step_btn")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Step")
                }

                // Notes & Options
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 2000) notes = it },
                    label = { Text("Kitchen Notes (optional)") },
                    placeholder = { Text("Substitutions, wine pairings, or storage tips...") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pin for Offline Viewing", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Keep highlighted in your Offline Saved filter",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = offlinePinned, onCheckedChange = { offlinePinned = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mark as Favorite", style = MaterialTheme.typography.titleSmall)
                            Switch(checked = favorite, onCheckedChange = { favorite = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Rating", style = MaterialTheme.typography.titleSmall)
                            StarRatingRow(rating = rating, onRatingSelected = { rating = it })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (pasteTarget != null) {
        val isIngredients = pasteTarget == "ingredients"
        AlertDialog(
            onDismissRequest = { pasteTarget = null },
            title = { Text(if (isIngredients) "Paste Ingredients" else "Paste Instructions") },
            text = {
                OutlinedTextField(
                    value = pasteBuffer,
                    onValueChange = { pasteBuffer = it },
                    placeholder = {
                        Text(
                            if (isIngredients) {
                                "One ingredient per line...\n2 cups flour\n1 tsp salt"
                            } else {
                                "One step per line...\nPreheat oven to 190C\nMix dry ingredients"
                            }
                        )
                    },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val lines = pasteBuffer.lines()
                            .map { it.trim().replaceFirst(Regex("""^[-*•\d.)]+\s+"""), "") }
                            .filter { it.isNotEmpty() }
                        if (lines.isNotEmpty()) {
                            if (isIngredients) {
                                if (ingredients.size == 1 && ingredients[0].isBlank()) ingredients.clear()
                                ingredients.addAll(lines.take(100 - ingredients.size))
                            } else {
                                if (steps.size == 1 && steps[0].isBlank()) steps.clear()
                                steps.addAll(lines.take(100 - steps.size))
                            }
                        }
                        pasteTarget = null
                    }
                ) {
                    Text("Add Lines")
                }
            },
            dismissButton = {
                TextButton(onClick = { pasteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
