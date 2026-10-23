@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ru.kodnovorossii.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ru.kodnovorossii.app.ui.components.GeoPoint
import ru.kodnovorossii.app.AppViewModel
import ru.kodnovorossii.app.data.Logic
import ru.kodnovorossii.app.data.PlaceDto
import ru.kodnovorossii.app.ui.components.*
import ru.kodnovorossii.app.ui.theme.Brand

// ---------- Общие действия ----------
fun openRoute(ctx: Context, p: PlaceDto) {
    // Яндекс Карты, если установлены; иначе — любое приложение карт через geo:
    val yandex = Intent(Intent.ACTION_VIEW, Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~${p.lat},${p.lng}&rtt=pd"))
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(${Uri.encode(p.name)})"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?rtext=~${p.lat},${p.lng}&rtt=pd"))
    for (i in listOf(yandex, geo, web)) {
        try { ctx.startActivity(i); return } catch (_: Exception) { }
    }
}

fun share(ctx: Context, title: String, url: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "$title — $url") }
    ctx.startActivity(Intent.createChooser(i, "Поделиться"))
}

fun placePin(p: PlaceDto, order: Int? = null) = MapPin(p.id, p.lat, p.lng, hex(p.vertical?.color).toArgb(), p.name, p.category?.name.orEmpty(), order, dim = p.status != "open")

// ---------- Карта ----------
@Composable
fun MapScreen(vm: AppViewModel, nav: NavController) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val loc by vm.location.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val verts = vm.verticals().filter { it.map_layer }
    var active by rememberSaveable { mutableStateOf<Set<String>?>(null) }
    val on = active ?: verts.map { it.slug }.toSet()
    var listMode by rememberSaveable { mutableStateOf(false) }
    var clusterList by remember { mutableStateOf<List<MapPin>?>(null) }
    var showSites by rememberSaveable { mutableStateOf(false) }
    var centerOn by remember { mutableStateOf<GeoPoint?>(null) }

    val places = (bundle?.places.orEmpty()).filter { it.vertical?.slug in on }
    val pins = remember(bundle, on) { places.map { placePin(it) } }
    val shapes = if (showSites) bundle?.sites.orEmpty().map { s ->
        MapShape(s.polygon.orEmpty().map { GeoPoint(it[0], it[1]) }, (if (s.status == "free") Brand.Blue else Brand.Warn).toArgb(), s.name) { nav.navigate("site/${s.id}") }
    } else emptyList()

    Box(Modifier.fillMaxSize()) {
        if (listMode) {
            val sorted = if (loc != null) places.sortedBy { vm.distanceTo(it) } else places.sortedByDescending { it.views }
            val favs by vm.favoriteIds.collectAsStateWithLifecycle()
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sorted, key = { it.id }) { p -> PlaceRow(p, p.id in favs, vm.distanceTo(p), { vm.toggleFavorite(p) }) { nav.navigate("place/${p.id}") } }
            }
        } else {
            AppMap(
                pins = pins, modifier = Modifier.fillMaxSize(), cluster = true, shapes = shapes,
                me = loc?.let { GeoPoint(it.first, it.second) }, center = centerOn, zoom = 15.0,
                onPin = { nav.navigate("place/${it.id}") }, onClusterList = { clusterList = it },
            )
        }
        LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(verts, key = { it.slug }) { v ->
                FilterChip(
                    selected = v.slug in on,
                    onClick = { active = if (v.slug in on) on - v.slug else on + v.slug },
                    label = { Text(v.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(hex(v.color))) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallFloatingActionButton(onClick = { vm.refreshLocation(); loc?.let { centerOn = GeoPoint(it.first, it.second) } }, containerColor = MaterialTheme.colorScheme.surface) { Icon(Icons.Filled.MyLocation, "Моё местоположение") }
            SmallFloatingActionButton(onClick = { showSites = !showSites }, containerColor = if (showSites) Brand.Blue else MaterialTheme.colorScheme.surface) { Icon(Icons.Filled.BusinessCenter, "Инвестплощадки", tint = if (showSites) Color.White else MaterialTheme.colorScheme.onSurface) }
        }
        Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(4.dp)) {
                listOf(false to "Карта", true to "Список").forEach { (mode, label) ->
                    TextButton(onClick = { listMode = mode }, shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            containerColor = if (listMode == mode) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            contentColor = if (listMode == mode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)) {
                        Icon(if (mode) Icons.AutoMirrored.Filled.List else Icons.Filled.Map, null, Modifier.size(18.dp)); HGap(6.dp); Text(label)
                    }
                }
            }
        }
    }
    clusterList?.let { list ->
        ModalBottomSheet(onDismissRequest = { clusterList = null }) {
            Text("${list.size} объектов в этой точке", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { pin ->
                    vm.place(pin.id)?.let { p ->
                        PlaceRow(p, false, vm.distanceTo(p), { vm.toggleFavorite(p) }) { clusterList = null; nav.navigate("place/${p.id}") }
                    }
                }
            }
        }
    }
}


// ---------- Каталог: поиск и фильтры работают офлайн ----------
@Composable
fun CatalogScreen(vm: AppViewModel, nav: NavController, initialQ: String, initialV: String) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val favs by vm.favoriteIds.collectAsStateWithLifecycle()
    val loc by vm.location.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf(initialQ) }
    var verts by rememberSaveable { mutableStateOf(if (initialV.isBlank()) emptySet() else setOf(initialV)) }
    var openNow by rememberSaveable { mutableStateOf(false) }
    var region by rememberSaveable { mutableStateOf(false) }
    var free by rememberSaveable { mutableStateOf(false) }
    var top by rememberSaveable { mutableStateOf(false) }
    var kids by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("relevance") }
    var sortMenu by remember { mutableStateOf(false) }
    var mapView by rememberSaveable { mutableStateOf(false) }

    val all = bundle?.places.orEmpty()
    val (hits, hint) = remember(q, all) { Logic.search(q, all) }
    var list = hits.map { it.place }
        .filter { verts.isEmpty() || it.vertical?.slug in verts }
        .filter { !openNow || Logic.isOpenNow(it) }
        .filter { !region || it.made_in_region }
        .filter { !free || it.price_level == 0 }
        .filter { !top || it.rating >= 4.5 }
        .filter { !kids || "с детьми" in it.formats.orEmpty() }
    list = when (sort) {
        "distance" -> list.sortedBy { vm.distanceTo(it) ?: Double.MAX_VALUE }
        "rating" -> list.sortedByDescending { it.rating }
        "popularity" -> list.sortedByDescending { it.views + it.favorites_count * 5 + it.reviews_count * 8 }
        else -> if (q.isBlank()) list.sortedByDescending { it.views } else list
    }

    Column(Modifier.fillMaxSize()) {
        Box {
            PhotoHeader("Каталог", "КУДА ПОЙТИ В ДНР", height = 168.dp)
            IconButton(onClick = { mapView = !mapView }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(Color(0x66000000))) {
                Icon(if (mapView) Icons.AutoMirrored.Filled.List else Icons.Filled.Map, "Карта / Список", tint = Color.White)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Музей, кофе, «поесть», маяк…") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, "Очистить") } },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
            )
            if (hint != null) TextButton(onClick = { q = hint }) { Text("Возможно, вы искали: $hint") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.verticals(), key = { it.slug }) { v ->
                FilterChip(selected = v.slug in verts, onClick = { verts = if (v.slug in verts) verts - v.slug else verts + v.slug }, label = { Text(v.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(hex(v.color))) })
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = openNow, onClick = { openNow = !openNow }, label = { Text("Открыто сейчас") }) }
            item { FilterChip(selected = top, onClick = { top = !top }, label = { Text("Рейтинг 4.5+") }) }
            item { FilterChip(selected = free, onClick = { free = !free }, label = { Text("Бесплатно") }) }
            item { FilterChip(selected = kids, onClick = { kids = !kids }, label = { Text("С детьми") }) }
            item { FilterChip(selected = region, onClick = { region = !region }, label = { Text("Сделано в регионе") }) }
            item {
                Box {
                    FilterChip(selected = sort != "relevance", onClick = { sortMenu = true }, label = { Text(mapOf("relevance" to "Сортировка", "distance" to "Ближе", "rating" to "Рейтинг", "popularity" to "Популярные")[sort]!!) },
                        leadingIcon = { Icon(Icons.Filled.Sort, null, Modifier.size(16.dp)) })
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf("relevance" to "По релевантности", "distance" to "По расстоянию", "rating" to "По рейтингу", "popularity" to "По популярности").forEach { (k, l) ->
                            DropdownMenuItem(text = { Text(l) }, onClick = { sort = k; sortMenu = false; if (k == "distance" && loc == null) vm.refreshLocation() })
                        }
                    }
                }
            }
        }
        Text("${list.size} ${if (list.size % 10 == 1 && list.size % 100 != 11) "место" else if (list.size % 10 in 2..4 && list.size % 100 !in 12..14) "места" else "мест"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (mapView) {
            AppMap(list.map { placePin(it) }, Modifier.fillMaxSize(), fit = true, me = loc?.let { GeoPoint(it.first, it.second) }, onPin = { nav.navigate("place/${it.id}") })
        } else if (list.isEmpty()) {
            Empty(Icons.Filled.Search, "Ничего не нашлось", "Уберите часть фильтров или измените запрос")
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(list, key = { it.id }) { p -> PlaceCard(p, p.id in favs, vm.distanceTo(p), { vm.toggleFavorite(p) }) { nav.navigate("place/${p.id}") } }
            }
        }
    }
}

// ---------- Избранное ----------
@Composable
fun FavoritesScreen(vm: AppViewModel, nav: NavController) {
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val places = remember(favs) { vm.favoritePlaces() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Избранное", style = MaterialTheme.typography.headlineLarge)
            Text(if (user != null) "Синхронизируется с сайтом" else "Хранится на устройстве и работает без интернета. Войдите, чтобы синхронизировать.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (places.size > 1) {
                androidx.compose.material3.Button(onClick = { nav.navigate("builder") }, modifier = Modifier.padding(top = 10.dp)) {
                    Icon(Icons.Filled.Directions, null); HGap(); Text("Собрать маршрут из избранного")
                }
            }
        }
        if (places.isEmpty()) item { Empty(Icons.Filled.FavoriteBorder, "Здесь будут сохранённые места", "Нажмите ♥ на карточке места") }
        items(places, key = { it.id }) { p -> PlaceCard(p, true, vm.distanceTo(p), { vm.toggleFavorite(p) }) { nav.navigate("place/${p.id}") } }
    }
}
