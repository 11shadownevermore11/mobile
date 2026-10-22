@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ru.kodnovorossii.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.kodnovorossii.app.ui.screens.*
import ru.kodnovorossii.app.ui.theme.Brand
import ru.kodnovorossii.app.ui.theme.KodTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val deepLinkPlace = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            KodTheme(theme) {
                androidx.compose.runtime.CompositionLocalProvider(ru.kodnovorossii.app.ui.components.LocalServer provides vm.prefs.serverUrl) {
                    AppRoot(vm, deepLinkPlace.value) { deepLinkPlace.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getIntExtra("place_id", -1)?.takeIf { it > 0 }
            ?: intent?.data?.lastPathSegment?.toIntOrNull()
        if (id != null) deepLinkPlace.value = id
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("map", "Карта", Icons.Filled.Map),
    Tab("catalog", "Каталог", Icons.AutoMirrored.Filled.List),
    Tab("favorites", "Избранное", Icons.Filled.Favorite),
    Tab("routes", "Маршруты", Icons.Filled.Directions),
    Tab("more", "Ещё", Icons.Filled.Menu),
)

@Composable
fun AppRoot(vm: AppViewModel, deepLink: Int?, consumeDeepLink: () -> Unit) {
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    val online by vm.online.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    LaunchedEffect(deepLink) { if (deepLink != null) { nav.navigate("place/$deepLink"); consumeDeepLink() } }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) vm.refreshLocation()
    }
    LaunchedEffect(Unit) {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        permissions.launch(list.toTypedArray())
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = current?.substringBefore("?") == t.route,
                        onClick = { nav.navigate(t.route) { popUpTo("map") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary, indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            if (!online) {
                Text(
                    "Нет связи с сервером — данные из памяти телефона",
                    color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().background(Brand.Warn).padding(4.dp),
                )
            }
            AppNav(nav, vm)
        }
    }
}

@Composable
fun AppNav(nav: NavHostController, vm: AppViewModel) {
    NavHost(nav, startDestination = "map") {
        composable("map") { MapScreen(vm, nav) }
        composable("catalog?q={q}&v={v}", arguments = listOf(
            navArgument("q") { type = NavType.StringType; defaultValue = "" },
            navArgument("v") { type = NavType.StringType; defaultValue = "" },
        )) { CatalogScreen(vm, nav, it.arguments?.getString("q").orEmpty(), it.arguments?.getString("v").orEmpty()) }
        composable("favorites") { FavoritesScreen(vm, nav) }
        composable("routes") { RoutesScreen(vm, nav) }
        composable("more") { MoreScreen(vm, nav) }
        composable("place/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { PlaceScreen(vm, nav, it.arguments?.getInt("id") ?: 0) }
        composable("route/{slug}") { RouteScreen(vm, nav, it.arguments?.getString("slug").orEmpty()) }
        composable("builder") { BuilderScreen(vm, nav) }
        composable("events") { EventsScreen(vm, nav) }
        composable("event/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { EventScreen(vm, nav, it.arguments?.getInt("id") ?: 0) }
        composable("vertical/{slug}") { VerticalScreen(vm, nav, it.arguments?.getString("slug").orEmpty()) }
        composable("investors") { InvestorsScreen(vm, nav) }
        composable("site/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { SiteScreen(vm, nav, it.arguments?.getInt("id") ?: 0) }
        composable("careers") { CareersScreen(vm, nav) }
        composable("heritage") { HeritageScreen(vm, nav) }
        composable("promo") { PromoScreen(vm, nav) }
        composable("passport") { PassportScreen(vm, nav) }
        composable("info") { InfoScreen(vm, nav) }
        composable("assistant") { AssistantScreen(vm, nav) }
        composable("profile") { ProfileScreen(vm, nav) }
        composable("settings") { SettingsScreen(vm, nav) }
    }
}
