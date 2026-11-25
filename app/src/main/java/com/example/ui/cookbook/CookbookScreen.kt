package com.example.ui.cookbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.RecipeSortOption
import com.example.data.model.SourceFilter
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.ui.components.EmptyCookbookView
import com.example.ui.components.RecipeCard
import com.example.ui.components.SyncStatusPill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CookbookScreen(
    viewModel: CookbookViewModel,
    userEmail: String?,
    userDisplayName: String?,
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onQuickToggleTheme: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onCreatePersonalRecipe: () -> Unit,
    onImportOnlineRecipe: () -> Unit,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncInfo by viewModel.syncInfo.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddSheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showIngredientFilterPanel by remember { mutableStateOf(true) }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add Recipe") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_recipe_fab")
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
                    .widthIn(max = 1100.dp)
                    .fillMaxSize()
            ) {
                // Editorial Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Simmer",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SyncStatusPill(syncInfo = syncInfo)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onQuickToggleTheme,
                            modifier = Modifier.testTag("cookbook_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (themeMode == ThemeMode.DARK) {
                                    Icons.Outlined.LightMode
                                } else {
                                    Icons.Outlined.DarkMode
                                },
                                contentDescription = "Toggle dark mode"
                            )
                        }
                        IconButton(
                            onClick = { showAccountSheet = true },
                            modifier = Modifier.testTag("account_sheet_button")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = "Account and appearance",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                when (val state = uiState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Opening your recipe book...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Couldn't sync recipes",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    is UiState.Success -> {
                        val model = state.data
                        val filters = model.filterState
                        val hasActiveFilters = filters.searchQuery.isNotBlank() ||
                            filters.ingredientFilters.isNotEmpty() ||
                            filters.selectedTag != null ||
                            filters.sourceFilter != SourceFilter.ALL

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 300.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 96.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Search & Ingredient Filter Input
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = filters.searchQuery,
                                            onValueChange = viewModel::updateSearchQuery,
                                            placeholder = { Text("Search recipes or ingredients...") },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.Search, contentDescription = null)
                                            },
                                            trailingIcon = {
                                                if (filters.searchQuery.isNotBlank()) {
                                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                                        Icon(
                                                            Icons.Outlined.Close,
                                                            contentDescription = "Clear search"
                                                        )
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(
                                                onSearch = {
                                                    if (filters.searchQuery.isNotBlank()) {
                                                        viewModel.addIngredientFilter(filters.searchQuery)
                                                        viewModel.updateSearchQuery("")
                                                    }
                                                }
                                            ),
                                            shape = MaterialTheme.shapes.large,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("recipe_search_input")
                                        )

                                        IconButton(
                                            onClick = { showIngredientFilterPanel = !showIngredientFilterPanel },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("toggle_ingredient_panel")
                                        ) {
                                            Surface(
                                                shape = MaterialTheme.shapes.medium,
                                                color = if (filters.ingredientFilters.isNotEmpty() || showIngredientFilterPanel) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainer
                                                },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.FilterList,
                                                        contentDescription = "Ingredient filter",
                                                        tint = if (filters.ingredientFilters.isNotEmpty() || showIngredientFilterPanel) {
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Quick "+ Add as ingredient filter" prompt when typing
                                    AnimatedVisibility(visible = filters.searchQuery.trim().length >= 2) {
                                        val candidate = filters.searchQuery.trim()
                                        AssistChip(
                                            onClick = {
                                                viewModel.addIngredientFilter(candidate)
                                                viewModel.updateSearchQuery("")
                                            },
                                            label = { Text("Filter by ingredient: \"$candidate\"") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Outlined.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.testTag("add_query_as_ingredient_chip")
                                        )
                                    }

                                    // Ingredient Filter Panel
                                    AnimatedVisibility(
                                        visible = showIngredientFilterPanel &&
                                            (filters.ingredientFilters.isNotEmpty() || model.suggestedIngredients.isNotEmpty())
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            tonalElevation = 1.dp
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.SoupKitchen,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = "Filter by Ingredients",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }

                                                    if (filters.ingredientFilters.size >= 2) {
                                                        TextButton(
                                                            onClick = {
                                                                viewModel.setMatchAllIngredients(!filters.matchAllIngredients)
                                                            }
                                                        ) {
                                                            Text(
                                                                text = if (filters.matchAllIngredients) "Match: ALL" else "Match: ANY",
                                                                style = MaterialTheme.typography.labelMedium
                                                            )
                                                        }
                                                    }
                                                }

                                                if (filters.ingredientFilters.isNotEmpty()) {
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        filters.ingredientFilters.forEach { ing ->
                                                            InputChip(
                                                                selected = true,
                                                                onClick = { viewModel.toggleIngredientFilter(ing) },
                                                                label = { Text(ing) },
                                                                trailingIcon = {
                                                                    Icon(
                                                                        Icons.Outlined.Close,
                                                                        contentDescription = "Remove $ing",
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                val unselectedSuggestions = model.suggestedIngredients.filter { suggestion ->
                                                    filters.ingredientFilters.none {
                                                        it.equals(suggestion, ignoreCase = true)
                                                    }
                                                }
                                                if (unselectedSuggestions.isNotEmpty()) {
                                                    LazyRow(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        items(unselectedSuggestions) { ingredient ->
                                                            FilterChip(
                                                                selected = false,
                                                                onClick = { viewModel.toggleIngredientFilter(ingredient) },
                                                                label = { Text(ingredient) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Source filters + Sort dropdown
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LazyRow(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            items(SourceFilter.entries) { filter ->
                                                FilterChip(
                                                    selected = filters.sourceFilter == filter,
                                                    onClick = { viewModel.selectSourceFilter(filter) },
                                                    label = { Text(filter.label) },
                                                    modifier = Modifier.testTag("source_filter_${filter.name.lowercase()}")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Box {
                                            AssistChip(
                                                onClick = { showSortMenu = true },
                                                label = { Text("Sort") },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Outlined.Sort,
                                                        contentDescription = "Sort recipes",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                modifier = Modifier.testTag("sort_menu_button")
                                            )
                                            DropdownMenu(
                                                expanded = showSortMenu,
                                                onDismissRequest = { showSortMenu = false }
                                            ) {
                                                RecipeSortOption.entries.forEach { option ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = option.label,
                                                                color = if (filters.sortOption == option) {
                                                                    MaterialTheme.colorScheme.primary
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurface
                                                                }
                                                            )
                                                        },
                                                        onClick = {
                                                            viewModel.selectSortOption(option)
                                                            showSortMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Tag filter strip
                                    if (model.allTags.isNotEmpty()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            items(model.allTags) { tag ->
                                                val selected = filters.selectedTag.equals(tag, ignoreCase = true)
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = { viewModel.selectTag(tag) },
                                                    label = { Text("#$tag") }
                                                )
                                            }
                                        }
                                    }

                                    // Result count & active sort indicator
                                    if (model.allRecipes.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${model.filteredRecipes.size} of ${model.allRecipes.size} recipes • ${filters.sortOption.label}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (hasActiveFilters) {
                                                TextButton(onClick = viewModel::clearAllFilters) {
                                                    Text("Reset")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (model.filteredRecipes.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    EmptyCookbookView(
                                        isFiltered = hasActiveFilters && model.allRecipes.isNotEmpty(),
                                        onCreatePersonal = onCreatePersonalRecipe,
                                        onImportOnline = onImportOnlineRecipe,
                                        onClearFilters = viewModel::clearAllFilters
                                    )
                                }
                            } else {
                                items(
                                    items = model.filteredRecipes,
                                    key = { it.id }
                                ) { recipe ->
                                    RecipeCard(
                                        recipe = recipe,
                                        activeIngredientFilters = filters.ingredientFilters,
                                        onClick = { onOpenRecipe(recipe.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(recipe) },
                                        onToggleOffline = { viewModel.toggleOfflinePinned(recipe) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add to Simmer",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Card(
                    onClick = {
                        showAddSheet = false
                        onCreatePersonalRecipe()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_write_recipe")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Write My Own Recipe",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Record your own ingredients, steps, tags, and kitchen notes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Card(
                    onClick = {
                        showAddSheet = false
                        onImportOnlineRecipe()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_import_recipe")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Import Recipe from Online",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Paste a recipe link to extract ingredients, steps, and cover photo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAccountSheet) {
        val currentRecipes = (uiState as? UiState.Success)?.data?.allRecipes.orEmpty()
        AccountAndAppearanceSheet(
            userEmail = userEmail,
            userDisplayName = userDisplayName,
            totalRecipes = currentRecipes.size,
            offlinePinnedCount = currentRecipes.count { it.offlinePinned },
            syncInfo = syncInfo,
            themeMode = themeMode,
            onSelectThemeMode = onSelectThemeMode,
            onSignOut = {
                showAccountSheet = false
                onSignOut()
            },
            onDismiss = { showAccountSheet = false }
        )
    }
}
