package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.PreferencesRepository
import com.example.data.local.SimmerDatabase
import com.example.data.model.ThemeMode
import com.example.data.remote.FirestoreProvider
import com.example.data.remote.NetworkMonitor
import com.example.data.remote.RecipeRepository
import com.example.data.remote.RecipeWebImporter
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.cookbook.CookbookScreen
import com.example.ui.cookbook.CookbookViewModel
import com.example.ui.detail.RecipeDetailScreen
import com.example.ui.editor.RecipeEditorScreen
import com.example.ui.theme.SimmerTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

object SimmerRoutes {
    const val COOKBOOK = "cookbook"
    const val DETAIL = "detail/{recipeId}"
    const val EDITOR = "editor?recipeId={recipeId}&importMode={importMode}"

    fun detail(recipeId: String) = "detail/$recipeId"
    fun editor(recipeId: String? = null, importMode: Boolean = false): String {
        return "editor?recipeId=${recipeId.orEmpty()}&importMode=$importMode"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appContext = LocalContext.current.applicationContext
            val prefsRepo = remember(appContext) { PreferencesRepository(appContext) }
            val themeMode by prefsRepo.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()

            val toggleTheme: () -> Unit = {
                val next = if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                scope.launch { prefsRepo.setThemeMode(next) }
            }
            val selectTheme: (ThemeMode) -> Unit = { mode ->
                scope.launch { prefsRepo.setThemeMode(mode) }
            }

            SimmerTheme(themeMode = themeMode) {
                SimmerAppContent(
                    themeMode = themeMode,
                    onSelectThemeMode = selectTheme,
                    onQuickToggleTheme = toggleTheme
                )
            }
        }
    }
}

@Composable
fun SimmerAppContent(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onQuickToggleTheme: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            themeMode = themeMode,
            onToggleTheme = onQuickToggleTheme,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedSimmerNav(
                currentUserId = user.uid,
                userEmail = user.email,
                userDisplayName = user.displayName,
                themeMode = themeMode,
                onSelectThemeMode = onSelectThemeMode,
                onQuickToggleTheme = onQuickToggleTheme
            )
        }
    }
}

@Composable
private fun AuthenticatedSimmerNav(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?,
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onQuickToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val cookbookViewModel: CookbookViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val db = FirestoreProvider.getInstance(appContext)
                val recipeDao = SimmerDatabase.getInstance(appContext).recipeDao()
                CookbookViewModel(
                    repository = RecipeRepository(db = db, recipeDao = recipeDao),
                    webImporter = RecipeWebImporter(),
                    networkMonitor = NetworkMonitor(appContext),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = SimmerRoutes.COOKBOOK
    ) {
        composable(SimmerRoutes.COOKBOOK) {
            CookbookScreen(
                viewModel = cookbookViewModel,
                userEmail = userEmail,
                userDisplayName = userDisplayName,
                themeMode = themeMode,
                onSelectThemeMode = onSelectThemeMode,
                onQuickToggleTheme = onQuickToggleTheme,
                onOpenRecipe = { recipeId ->
                    navController.navigate(SimmerRoutes.detail(recipeId))
                },
                onCreatePersonalRecipe = {
                    navController.navigate(SimmerRoutes.editor(importMode = false))
                },
                onImportOnlineRecipe = {
                    navController.navigate(SimmerRoutes.editor(importMode = true))
                },
                onSignOut = {
                    signOut(credentialManager, scope)
                }
            )
        }

        composable(
            route = SimmerRoutes.DETAIL,
            arguments = listOf(navArgument("recipeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId").orEmpty()
            RecipeDetailScreen(
                recipeId = recipeId,
                viewModel = cookbookViewModel,
                onBack = { navController.popBackStack() },
                onEdit = { id ->
                    navController.navigate(SimmerRoutes.editor(recipeId = id, importMode = false))
                }
            )
        }

        composable(
            route = SimmerRoutes.EDITOR,
            arguments = listOf(
                navArgument("recipeId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("importMode") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val recipeIdArg = backStackEntry.arguments?.getString("recipeId")?.takeIf { it.isNotBlank() }
            val importMode = backStackEntry.arguments?.getBoolean("importMode") ?: false
            RecipeEditorScreen(
                existingRecipeId = recipeIdArg,
                startWithImport = importMode,
                viewModel = cookbookViewModel,
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    if (recipeIdArg == null) {
                        navController.navigate(SimmerRoutes.detail(savedId)) {
                            popUpTo(SimmerRoutes.COOKBOOK)
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
