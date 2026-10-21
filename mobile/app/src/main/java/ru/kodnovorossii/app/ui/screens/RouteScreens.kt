@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ru.kodnovorossii.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ru.kodnovorossii.app.AppViewModel
import ru.kodnovorossii.app.data.ApiException
import ru.kodnovorossii.app.data.EventDto
import ru.kodnovorossii.app.data.Logic
import ru.kodnovorossii.app.data.PlaceDto
import ru.kodnovorossii.app.data.RouteDto
import ru.kodnovorossii.app.ui.components.*
import ru.kodnovorossii.app.ui.theme.Brand
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ---------- Таймлайн маршрута: цепочка узлов ----------
@Composable
fun RouteTimeline(r: RouteDto, nav: NavController, actions: (@Composable (Int) -> Unit)? = null) {
    val car = r.transport == "car"
    Column {
        r.stops.orEmpty().forEachIndexed { i, s ->
            if (i > 0 && s.travel_min > 0) Row(Modifier.padding(start = 34.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (car) Icons.Filled.DirectionsCar else Icons.Filled.DirectionsWalk, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" ${Logic.fmtMin(s.travel_min)} · ${Logic.fmtDist(s.travel_m.toDouble())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().clickable { nav.navigate("place/${s.place.id}") }.padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                val pause = s.kind == "pause"
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(if (pause) MaterialTheme.colorScheme.surface else hex(s.place.vertical?.color))
                        .border(if (pause) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pause) Icon(if (s.pause_type == "rest") Icons.Filled.Weekend else Icons.Filled.LocalCafe, null, Modifier.size(14.dp))
                    else Text("${s.order}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("${s.arrive}–${s.leave}" + if (pause) "  · пауза" else "", fontWeight = FontWeight.Bold)
                    Text(s.place.name, style = MaterialTheme.typography.titleMedium)
                    Text("${s.place.category?.name.orEmpty()} · ${Logic.fmtMin(s.stay_min)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!s.note.isNullOrBlank()) Text(s.note, style = MaterialTheme.typography.bodySmall)
                }
                actions?.invoke(i)
            }
        }
    }
}

fun routePins(r: RouteDto) = r.stops.orEmpty().map { s ->
    MapPin(s.place.id * 100 + s.order, s.place.lat, s.place.lng, (if (s.kind == "pause") Color.Gray else hex(s.place.vertical?.color)).toArgb(), s.place.name, order = s.order)
}

@Composable
fun RoutesScreen(vm: AppViewModel, nav: NavController) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Маршруты", style = MaterialTheme.typography.headlineLarge)
            Text("Готовые тематические маршруты: точки, порядок, время и паузы. Работают офлайн.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { nav.navigate("builder") }, modifier = Modifier.padding(top = 10.dp)) { Icon(Icons.Filled.AutoAwesome, null); HGap(); Text("Собрать свой маршрут") }
        }
        items(bundle?.routes.orEmpty(), key = { it.id }) { r ->
            Card(onClick = { nav.navigate("route/${r.slug}") }) {
                Chips { r.verticals.orEmpty().forEach { Tag(it.name.orEmpty(), hex(it.color)) } }
                Text(r.title, style = MaterialTheme.typography.titleLarge)
                r.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${r.stops?.size ?: 0} точек · ${Logic.fmtMin(r.duration_min)} · ${r.distance_km} км · ${if (r.transport == "car") "на машине" else "пешком"}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun RouteScreen(vm: AppViewModel, nav: NavController, slug: String) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val loc by vm.location.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val r = bundle?.routes.orEmpty().firstOrNull { it.slug == slug }
    if (r == null) { Column { BackBar("Маршрут", { nav.popBackStack() }); Empty(Icons.Filled.Directions, "Маршрут не найден") }; return }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            BackBar(r.title, { nav.popBackStack() })
            OsmMap(routePins(r), Modifier.fillMaxWidth().height(280.dp), cluster = false, fit = true,
                polyline = r.polyline.orEmpty().map { GeoPoint(it[0], it[1]) }, polylineColor = hex(r.color, Brand.Ink).toArgb(),
                me = loc?.let { GeoPoint(it.first, it.second) }, onPin = { nav.navigate("place/${it.id / 100}") })
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.title, style = MaterialTheme.typography.headlineLarge)
                r.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${r.stops?.size ?: 0} точек · ${Logic.fmtMin(r.duration_min)} (${r.start}–${r.finish}) · ${r.distance_km} км", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val pts = r.stops.orEmpty().joinToString("~") { "${it.place.lat},${it.place.lng}" }
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?rtext=$pts&rtt=${if (r.transport == "car") "auto" else "pd"}"))) }
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Navigation, null); HGap(); Text("Начать") }
                    OutlinedButton(onClick = {
                        BuilderState.ids = r.stops.orEmpty().map { it.place.id }
                        nav.navigate("builder")
                    }, modifier = Modifier.weight(1f)) { Text("Изменить под себя") }
                }
                Gap(8.dp)
                RouteTimeline(r, nav)
            }
        }
    }
}

/** Передача набора мест в конструктор (из маршрута, бота или избранного). */
object BuilderState { var ids: List<Int>? = null }

@Composable
fun BuilderScreen(vm: AppViewModel, nav: NavController) {
    val scope = rememberCoroutineScope()
    val loc by vm.location.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    var ids by remember { mutableStateOf(BuilderState.ids ?: vm.favoritePlaces().map { it.id }) }
    var car by rememberSaveable { mutableStateOf(false) }
    var hours by remember { mutableFloatStateOf(3f) }
    var interests by remember { mutableStateOf(setOf<String>()) }
    var saveDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { BuilderState.ids = null }

    val places: List<PlaceDto> = ids.mapNotNull { vm.place(it) }
    val route = remember(places, car) { Logic.timeline(places, vm.categories(), "10:00", car) }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackBar("Конструктор маршрута", { nav.popBackStack() }) }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card {
                    Text("Подобрать за меня", style = MaterialTheme.typography.titleMedium)
                    Text("Время: ${"%.1f".format(hours)} ч", style = MaterialTheme.typography.bodySmall)
                    Slider(value = hours, onValueChange = { hours = it }, valueRange = 1f..8f, steps = 13)
                    Chips {
                        vm.verticals().filter { it.slug != "investment" }.forEach { v ->
                            FilterChip(selected = v.slug in interests, onClick = { interests = if (v.slug in interests) interests - v.slug else interests + v.slug }, label = { Text(v.name) })
                        }
                    }
                    Button(onClick = {
                        scope.launch {
                            try {
                                val r: RouteDto = vm.api.post("/api/routes/generate", mapOf("hours" to hours, "interests" to interests.toList(), "lat" to loc?.first, "lng" to loc?.second, "transport" to if (car) "car" else "walk"))
                                ids = r.stops.orEmpty().map { it.place.id }
                                vm.say("Подобрано ${ids.size} мест на ${Logic.fmtMin(r.duration_min)}")
                            } catch (e: ApiException) { vm.say(if (e.code == 0) "Подбор работает онлайн. Офлайн соберите маршрут из избранного" else e.message ?: "Ошибка") }
                        }
                    }) { Icon(Icons.Filled.AutoAwesome, null); HGap(); Text("Подобрать") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !car, onClick = { car = false }, label = { Text("Пешком") }, leadingIcon = { Icon(Icons.Filled.DirectionsWalk, null) })
                    FilterChip(selected = car, onClick = { car = true }, label = { Text("На машине") }, leadingIcon = { Icon(Icons.Filled.DirectionsCar, null) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ids = vm.favoritePlaces().map { it.id } }, modifier = Modifier.weight(1f)) { Text("Из избранного") }
                    Button(onClick = {
                        val start = loc
                        val before = route.distance_km
                        ids = Logic.optimize(places, start).map { it.id }
                        val after = Logic.timeline(ids.mapNotNull { vm.place(it) }, vm.categories(), "10:00", car).distance_km
                        if (before - after > 0.05) vm.say("Порядок оптимизирован: короче на ${"%.2f".format(before - after)} км")
                    }, modifier = Modifier.weight(1f), enabled = places.size > 2) { Icon(Icons.Filled.Shuffle, null); HGap(4.dp); Text("Оптимизировать") }
                }
            }
        }
        if (places.isEmpty()) item { Empty(Icons.Filled.Directions, "Маршрут пуст", "Добавьте места в избранное или воспользуйтесь подбором") }
        else {
            item {
                OsmMap(routePins(route), Modifier.fillMaxWidth().height(260.dp).padding(16.dp).clip(RoundedCornerShape(16.dp)), cluster = false, fit = true,
                    polyline = places.map { GeoPoint(it.lat, it.lng) }, onPin = { nav.navigate("place/${it.id / 100}") })
                Text("${Logic.fmtMin(route.duration_min)} · ${route.distance_km} км · ${route.start}–${route.finish}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    RouteTimeline(route, nav) { i ->
                        Row {
                            IconButton(onClick = { if (i > 0) ids = ids.toMutableList().also { val t = it[i]; it[i] = it[i - 1]; it[i - 1] = t } }) { Icon(Icons.Filled.ArrowUpward, "Выше") }
                            IconButton(onClick = { ids = ids.filterIndexed { k, _ -> k != i } }) { Icon(Icons.Filled.Delete, "Убрать") }
                        }
                    }
                    Button(onClick = { if (user == null) nav.navigate("profile") else saveDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Save, null); HGap(); Text("Сохранить в аккаунт") }
                }
            }
        }
    }
    if (saveDialog) {
        var title by remember { mutableStateOf("Мой маршрут") }
        AlertDialog(onDismissRequest = { saveDialog = false }, title = { Text("Название маршрута") },
            text = { OutlinedTextField(title, { title = it }) },
            confirmButton = { TextButton(onClick = {
                val stops = route.stops.orEmpty().map { mapOf("place_id" to it.place.id, "stay_min" to it.stay_min, "kind" to it.kind, "pause_type" to it.pause_type) }
                vm.sendOrQueue("POST", "/api/me/routes", mapOf("title" to title, "stops" to stops, "transport" to if (car) "car" else "walk"), "Маршрут сохранён — он доступен и на сайте")
                saveDialog = false
            }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("Отмена") } })
    }
}

// ---------- Афиша ----------
private val KINDS = mapOf("festival" to "Фестиваль", "exhibition" to "Выставка", "masterclass" to "Мастер-класс", "open_day" to "День открытых дверей",
    "concert" to "Концерт", "lecture" to "Лекция", "excursion" to "Экскурсия", "hackathon" to "Хакатон", "theatre" to "Спектакль")
private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
private val HUMAN = SimpleDateFormat("d MMMM, HH:mm", Locale("ru"))
fun parseIso(s: String?): java.util.Date? = runCatching { ISO.parse(s!!.take(16)) }.getOrNull()
fun humanDate(s: String?): String = parseIso(s)?.let { HUMAN.format(it) } ?: s.orEmpty()

@Composable
fun EventsScreen(vm: AppViewModel, nav: NavController) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf("all") }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    val now = Calendar.getInstance()
    fun dayStart(days: Int) = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.time
    val (from, to) = when (period) {
        "today" -> dayStart(0) to dayStart(1)
        "tomorrow" -> dayStart(1) to dayStart(2)
        "weekend" -> { val d = (Calendar.SATURDAY - now.get(Calendar.DAY_OF_WEEK) + 7) % 7; dayStart(d) to dayStart(d + 2) }
        "week" -> dayStart(0) to dayStart(7)
        else -> dayStart(-1) to dayStart(365)
    }
    val list = bundle?.events.orEmpty().filter { e ->
        val s = parseIso(e.start) ?: return@filter false
        val en = parseIso(e.end) ?: s
        !en.before(from) && s.before(to) && (kind == null || e.kind == kind)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackBar("Афиша", { nav.popBackStack() }) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all" to "Все", "today" to "Сегодня", "tomorrow" to "Завтра", "weekend" to "Выходные", "week" to "Неделя")) { (k, l) ->
                    FilterChip(selected = period == k, onClick = { period = k }, label = { Text(l) })
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(KINDS.entries.toList()) { (k, l) -> FilterChip(selected = kind == k, onClick = { kind = if (kind == k) null else k }, label = { Text(l) }) }
            }
        }
        if (list.isEmpty()) item { Empty(Icons.Filled.Event, "Событий нет", "Выберите другой период") }
        items(list, key = { it.id }) { e -> Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) { EventCard(e) { nav.navigate("event/${e.id}") } } }
    }
}

@Composable
fun EventCard(e: EventDto, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val d = parseIso(e.start)
                Text(d?.let { SimpleDateFormat("d", Locale("ru")).format(it) } ?: "", style = MaterialTheme.typography.headlineMedium)
                Text(d?.let { SimpleDateFormat("MMM", Locale("ru")).format(it) } ?: "", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Tag(KINDS[e.kind] ?: e.kind, hex(e.vertical?.color))
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                Text("${humanDate(e.start)}${e.place?.let { " · ${it.name}" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (e.price > 0) "${e.price} ₽" else "Бесплатно", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun EventScreen(vm: AppViewModel, nav: NavController, id: Int) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val e = bundle?.events.orEmpty().firstOrNull { it.id == id }
    if (e == null) { Column { BackBar("Событие", { nav.popBackStack() }); Empty(Icons.Filled.Event, "Событие не найдено") }; return }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            BackBar(KINDS[e.kind] ?: "Событие", { nav.popBackStack() })
            Cover(hex(e.vertical?.color, Brand.Red), "star", e.id + 500, Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)), 56.dp, photo = e.photo ?: e.place?.photo ?: e.place?.photos?.firstOrNull())
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.title, style = MaterialTheme.typography.headlineLarge)
                Text(humanDate(e.start) + (e.end?.let { " — ${humanDate(it)}" } ?: ""), fontWeight = FontWeight.SemiBold)
                e.place?.let { pl -> Row(Modifier.clickable { nav.navigate("place/${pl.id}") }) { Icon(Icons.Filled.Place, null); HGap(); Text(pl.name, color = Brand.Blue) } }
                Text(if (e.price > 0) "${e.price} ₽" else "Вход свободный")
                if (e.capacity > 0) Text("Осталось мест: ${e.capacity - e.booked} из ${e.capacity}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(e.description.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.registration) Button(onClick = {
                        if (user == null) nav.navigate("profile") else vm.sendOrQueue("POST", "/api/bookings", mapOf("event_id" to e.id, "people" to 1), "Вы записаны!")
                    }, modifier = Modifier.weight(1f)) { Text("Записаться") }
                    OutlinedButton(onClick = {
                        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, e.title)
                            .putExtra(CalendarContract.Events.EVENT_LOCATION, e.place?.name.orEmpty())
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, parseIso(e.start)?.time ?: 0L)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, parseIso(e.end ?: e.start)?.time ?: 0L)
                        runCatching { ctx.startActivity(i) }
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.EventAvailable, null); HGap(4.dp); Text("В календарь") }
                }
            }
        }
    }
}
