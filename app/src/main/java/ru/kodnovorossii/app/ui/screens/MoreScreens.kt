@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ru.kodnovorossii.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import ru.kodnovorossii.app.ui.components.GeoPoint
import ru.kodnovorossii.app.AppViewModel
import ru.kodnovorossii.app.data.ApiException
import ru.kodnovorossii.app.data.MatchResult
import ru.kodnovorossii.app.data.PassportDto
import ru.kodnovorossii.app.data.PromoActivation
import ru.kodnovorossii.app.data.PromoDto
import ru.kodnovorossii.app.data.SiteDto
import ru.kodnovorossii.app.ui.components.*
import ru.kodnovorossii.app.ui.theme.Brand

@Composable
private fun MenuRow(icon: ImageVector, title: String, sub: String, color: Color? = null, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color ?: MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = when (color) { null -> MaterialTheme.colorScheme.onSurface; MaterialTheme.colorScheme.primary -> MaterialTheme.colorScheme.onPrimary; else -> Color.White })
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
fun MoreScreen(vm: AppViewModel, nav: NavController) {
    val pro by vm.proMode.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).clip(RoundedCornerShape(24.dp))) {
                PhotoHeader("Код Новороссии", "ГИД ПО ДНР", photo = "/img/night-fountain.jpg", height = 180.dp)
                Box(Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x55000000)).padding(6.dp)) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(ru.kodnovorossii.app.R.drawable.mark_white), "ФСП ДНР", Modifier.size(34.dp))
                }
            }
        }
        item { MenuRow(Icons.Filled.SmartToy, "Помощник «Гид»", "Спросите, куда пойти, — ответит нейросеть по данным платформы", MaterialTheme.colorScheme.primary) { nav.navigate("assistant") } }
        item { MenuRow(Icons.Filled.Event, "Афиша", "${bundle?.events?.size ?: 0} событий: фестивали, выставки, мастер-классы") { nav.navigate("events") } }
        item { SectionTitle("Направления") }
        items(vm.verticals().filter { !it.is_professional }, key = { it.slug }) { v ->
            // Плитка направления: фото самого популярного места направления с фото, цвет и иконка вертикали
            val cover = bundle?.places.orEmpty().filter { it.vertical?.slug == v.slug && !it.photos.isNullOrEmpty() }.maxByOrNull { it.views }
            Box(Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(20.dp)).clickable { nav.navigate("vertical/${v.slug}") }) {
                Cover(hex(v.color), v.icon, v.id, Modifier.fillMaxSize(), photo = cover?.photos?.firstOrNull())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE6040A16), Color(0x66040A16), Color.Transparent))))
                Row(Modifier.align(Alignment.CenterStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    VNode(hex(v.color), v.icon, 44.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(v.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("${v.count} мест", style = MaterialTheme.typography.bodyMedium, color = Color(0xDDFFFFFF))
                    }
                }
            }
        }
        item { MenuRow(Icons.Filled.DirectionsBoat, "Истории и аудиогиды", "Морское наследие Приазовья") { nav.navigate("heritage") } }
        item { MenuRow(Icons.Filled.ConfirmationNumber, "Промокоды партнёров", "Скидка 3–5% в заведениях") { nav.navigate("promo") } }
        item { MenuRow(Icons.Filled.EmojiEvents, "Паспорт путешественника", "Штампы и достижения") { nav.navigate("passport") } }
        item { MenuRow(Icons.Filled.Shield, "Важно знать", "Безопасность, транспорт, связь") { nav.navigate("info") } }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Профессиональный режим", style = MaterialTheme.typography.titleMedium); Text("Разделы для инвесторов, бизнеса и кадров. По умолчанию — туристический режим.", style = MaterialTheme.typography.bodySmall) }
                    Switch(pro, { vm.setPro(it) })
                }
            }
        }
        if (pro) {
            item { MenuRow(Icons.Filled.BusinessCenter, "Инвесторам — одно окно", "Площадки, подбор, единая заявка", Brand.Blue) { nav.navigate("investors") } }
            item { MenuRow(Icons.Filled.School, "Кадры и образование", "Стажировки, курсы, вакансии, наставники", Color(0xFF7B3FE4)) { nav.navigate("careers") } }
            item { MenuRow(Icons.Filled.Storefront, "Для бизнеса", "Кабинет владельца на сайте", Brand.Red) {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ru.kodnovorossii.app.data.ApiConfig.url("/cabinet")))) }
            } }
        }
        item { SectionTitle("Аккаунт и настройки") }
        item { MenuRow(Icons.Filled.Person, user?.name ?: "Войти или зарегистрироваться", user?.role_title ?: "Отзывы, синхронизация, запись, заявки") { nav.navigate("profile") } }
        item { MenuRow(Icons.Filled.Settings, "Настройки", "Тема, гео-уведомления, офлайн-режим, сервер") { nav.navigate("settings") } }
    }
}

@Composable
fun VerticalScreen(vm: AppViewModel, nav: NavController, slug: String) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val favs by vm.favoriteIds.collectAsStateWithLifecycle()
    val v = vm.vertical(slug)
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var label by rememberSaveable { mutableStateOf<String?>(null) }
    if (v == null) { Column { BackBar("Направление", { nav.popBackStack() }); Empty(Icons.Filled.Place, "Направление отключено") }; return }
    val places = bundle?.places.orEmpty().filter { it.vertical?.slug == slug && (cat == null || it.category?.slug == cat) && (label == null || label in it.labels.orEmpty()) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            BackBar(v.name, { nav.popBackStack() })
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { VNode(hex(v.color), v.icon, 56.dp); HGap(12.dp); Text(v.name, style = MaterialTheme.typography.headlineMedium) }
                Text(v.description.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text("Все") }) }
                items(v.categories.orEmpty(), key = { it.slug }) { c -> FilterChip(selected = cat == c.slug, onClick = { cat = c.slug }, label = { Text(c.name) }, leadingIcon = { Icon(iconFor(c.icon), null, Modifier.size(16.dp)) }) }
            }
            if (slug == "creative") LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("interns" to "Принимаем стажёров", "excursions" to "Экскурсии", "orders" to "Заказы")) { (k, l) ->
                    FilterChip(selected = label == k, onClick = { label = if (label == k) null else k }, label = { Text(l) })
                }
            }
        }
        if (places.isEmpty()) item { Empty(iconFor(v.icon), "Пока нет объектов", "Владельцы могут добавить предприятие через кабинет бизнеса") }
        items(places, key = { it.id }) { p -> Box(Modifier.padding(horizontal = 16.dp)) { PlaceRow(p, p.id in favs, vm.distanceTo(p), { vm.toggleFavorite(p) }) { nav.navigate("place/${p.id}") } } }
    }
}

// ---------- Инвесторам ----------
private val SITE_STATUS = mapOf("free" to "Свободна", "reserved" to "Зарезервирована", "occupied" to "Занята")

@Composable
fun InvestorsScreen(vm: AppViewModel, nav: NavController) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sites = bundle?.sites.orEmpty()
    var area by remember { mutableFloatStateOf(5f) }
    var power by remember { mutableFloatStateOf(2f) }
    var needs by remember { mutableStateOf(setOf<String>()) }
    var results by remember { mutableStateOf<List<MatchResult>?>(null) }
    var applyFor by remember { mutableStateOf<SiteDto?>(null) }
    var applyOpen by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            BackBar("Инвесторам", { nav.popBackStack() })
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Одно окно для инвестора", style = MaterialTheme.typography.headlineMedium)
                Text("Подбор площадки, единая заявка и куратор. Инвестор — это профиль обычного пользователя.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { applyFor = null; applyOpen = true }) { Text("Подать единую заявку") }
            }
            AppMap(sites.map { MapPin(it.id, it.lat, it.lng, (if (it.status == "free") Brand.Blue else Brand.Warn).toArgb(), it.name) },
                Modifier.fillMaxWidth().height(260.dp).padding(16.dp).clip(RoundedCornerShape(16.dp)), cluster = false, fit = true,
                shapes = sites.map { s -> MapShape(s.polygon.orEmpty().map { GeoPoint(it[0], it[1]) }, (if (s.status == "free") Brand.Blue else Brand.Warn).toArgb(), s.name) { nav.navigate("site/${s.id}") } },
                onPin = { nav.navigate("site/${it.id}") })
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Card {
                    Text("Мастер подбора площадки", style = MaterialTheme.typography.titleLarge)
                    Text("Площадь: ${"%.1f".format(area)} га"); Slider(area, { area = it }, valueRange = 0f..60f)
                    Text("Мощность: ${"%.1f".format(power)} МВт"); Slider(power, { power = it }, valueRange = 0f..30f)
                    Chips { listOf("gas" to "Газ", "water" to "Вода", "sewage" to "Канализация", "railway" to "Ж/д").forEach { (k, l) -> FilterChip(selected = k in needs, onClick = { needs = if (k in needs) needs - k else needs + k }, label = { Text(l) }) } }
                    Button(onClick = {
                        scope.launch {
                            results = try { vm.api.post<List<MatchResult>>("/api/invest/match", mapOf("area_ha" to area, "power_mw" to power, "needs" to needs.toList())) }
                            catch (e: ApiException) { vm.say(if (e.code == 0) "Подбор доступен онлайн" else e.message ?: ""); null }
                        }
                    }) { Text("Подобрать") }
                }
            }
        }
        results?.let { res -> items(res, key = { "m" + it.site.id }) { r ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                Card(onClick = { nav.navigate("site/${r.site.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.score}%", style = MaterialTheme.typography.headlineMedium, color = if (r.score >= 70) Brand.Ok else Brand.Warn)
                        HGap(12.dp); Text(r.site.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    Chips { r.reasons.orEmpty().forEach { Tag("✓ $it", Brand.Ok) }; r.misses.orEmpty().forEach { Tag(it, Brand.Warn) } }
                }
            }
        } }
        item { SectionTitle("Площадки", Modifier.padding(horizontal = 16.dp)) }
        items(sites, key = { it.id }) { s ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                Card(onClick = { nav.navigate("site/${s.id}") }) {
                    Row { Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Tag(SITE_STATUS[s.status] ?: s.status, if (s.status == "free") Brand.Ok else Brand.Warn) }
                    Text("${s.city} · ${fmtValue(s.area_ha)} га · ${fmtValue(s.power_mw)} МВт${if (s.gas) " · газ" else ""}${if (s.railway) " · ж/д" else ""}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (applyOpen) ApplicationDialog(vm, applyFor) { applyOpen = false }
}

@Composable
fun ApplicationDialog(vm: AppViewModel, site: SiteDto?, onClose: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    var project by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var inv by remember { mutableStateOf("") }
    var jobs by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Единая заявка") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (user == null) Text("Войдите в профиль, чтобы подать заявку", color = Brand.Red)
            site?.let { Text("Площадка: ${it.name}", fontWeight = FontWeight.SemiBold) }
            OutlinedTextField(project, { project = it }, label = { Text("Проект") })
            OutlinedTextField(company, { company = it }, label = { Text("Компания") })
            OutlinedTextField(inv, { inv = it }, label = { Text("Инвестиции, млн ₽") })
            OutlinedTextField(jobs, { jobs = it }, label = { Text("Рабочих мест") })
        }
    }, confirmButton = { TextButton(enabled = user != null && project.isNotBlank(), onClick = {
        vm.sendOrQueue("POST", "/api/invest/applications", mapOf("project" to project, "company" to company, "site_id" to site?.id,
            "investment_mln" to (inv.toDoubleOrNull() ?: 0.0), "jobs" to (jobs.toIntOrNull() ?: 0)), "Заявка принята. Куратор свяжется с вами, статус — в профиле на сайте")
        onClose()
    }) { Text("Отправить") } }, dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } })
}

@Composable
fun SiteScreen(vm: AppViewModel, nav: NavController, id: Int) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val s = bundle?.sites.orEmpty().firstOrNull { it.id == id }
    var apply by remember { mutableStateOf(false) }
    if (s == null) { Column { BackBar("Площадка", { nav.popBackStack() }); Empty(Icons.Filled.BusinessCenter, "Площадка не найдена") }; return }
    val yes = { b: Boolean -> if (b) "есть" else "нет" }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            BackBar(s.name, { nav.popBackStack() })
            AppMap(emptyList(), Modifier.fillMaxWidth().height(260.dp), cluster = false, center = GeoPoint(s.lat, s.lng), zoom = 14.0,
                shapes = listOf(MapShape(s.polygon.orEmpty().map { GeoPoint(it[0], it[1]) }, Brand.Blue.toArgb(), s.name)))
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(SITE_STATUS[s.status] ?: s.status, if (s.status == "free") Brand.Ok else Brand.Warn)
                Text(s.name, style = MaterialTheme.typography.headlineMedium)
                Text(s.description.orEmpty())
                SectionTitle("Паспорт площадки")
                listOf("Площадь" to "${fmtValue(s.area_ha)} га", "Здания" to (if (s.building_m2 > 0) "${s.building_m2} м²" else "нет"), "Электроэнергия" to "${fmtValue(s.power_mw)} МВт",
                    "Газ" to yes(s.gas), "Вода" to yes(s.water), "Канализация" to yes(s.sewage), "Ж/д ветка" to yes(s.railway), "До трассы" to "${fmtValue(s.highway_km)} км",
                    "Собственность" to s.ownership.orEmpty(), "Сделка" to s.deal.orEmpty(), "Стоимость" to s.price.orEmpty()).forEach { (k, v) ->
                    Row { Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)); Text(v, fontWeight = FontWeight.SemiBold) }
                }
                SectionTitle("Отрасли и льготы")
                Chips { s.industries.orEmpty().forEach { Tag(it) } }
                s.benefits.orEmpty().forEach { Text("• $it") }
                s.before?.get("text")?.let { before ->
                    SectionTitle("Новая жизнь площадки")
                    Card { Text("Было (${fmtValue(s.before?.get("year"))})", fontWeight = FontWeight.Bold); Text(before.toString()) }
                    Card { Text("Стало (${fmtValue(s.after?.get("year"))})", fontWeight = FontWeight.Bold, color = Brand.Ok); Text(s.after?.get("text")?.toString().orEmpty()) }
                }
                Button(onClick = { apply = true }, enabled = s.status != "occupied", modifier = Modifier.fillMaxWidth()) { Text("Подать заявку на площадку") }
            }
        }
    }
    if (apply) ApplicationDialog(vm, s) { apply = false }
}

@Composable
fun CareersScreen(vm: AppViewModel, nav: NavController) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    val kinds = listOf("internship" to "Стажировки", "course" to "Курсы", "vacancy" to "Вакансии", "mentor" to "Наставники")
    val list = bundle?.opportunities.orEmpty().filter { kind == null || it.kind == kind }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            BackBar("Кадры и образование", { nav.popBackStack() })
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("Все") }) }
                items(kinds) { (k, l) -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(l) }) }
            }
        }
        items(list, key = { it.id }) { o ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                Card {
                    Tag(kinds.firstOrNull { it.first == o.kind }?.second ?: o.kind, Brand.Blue)
                    Text(o.title, style = MaterialTheme.typography.titleMedium)
                    Text(o.org.orEmpty(), color = Brand.Blue)
                    Text(o.description.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    Text(listOfNotNull(o.format, o.salary?.takeIf { it.isNotBlank() }, o.deadline?.let { "до $it" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Откликнуться: ${o.contact.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun HeritageScreen(vm: AppViewModel, nav: NavController) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val tts = rememberTts()
    val places = bundle?.places.orEmpty().filter { it.vertical?.slug == "maritime" }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            BackBar("Морское наследие", { nav.popBackStack() })
            AppMap(places.map { placePin(it) }, Modifier.fillMaxWidth().height(220.dp), cluster = false, fit = true, onPin = { nav.navigate("place/${it.id}") })
            Text("Истории жителей и экспертов — нажмите «Аудиогид», чтобы прослушать. Работает без интернета.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(bundle?.heritage.orEmpty(), key = { it.id }) { s -> Box(Modifier.padding(horizontal = 16.dp)) { StoryCard(s, tts, vm) } }
    }
}

@Composable
fun PromoScreen(vm: AppViewModel, nav: NavController) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf<Pair<PromoDto, PromoActivation?>?>(null) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            BackBar("Промокоды партнёров", { nav.popBackStack() })
            Text("Назовите промокод в заведении-партнёре и получите скидку 3–5% на ассортимент.", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(bundle?.promos.orEmpty(), key = { it.id }) { pr ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                Card(onClick = { scope.launch { shown = pr to runCatching { vm.api.post<PromoActivation>("/api/promos/${pr.id}/activate") }.getOrNull() } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag("−${pr.discount}%", Color.White, Brand.Red); HGap(10.dp)
                        Column(Modifier.weight(1f)) { Text(pr.place?.name.orEmpty(), style = MaterialTheme.typography.titleMedium); Text(pr.description.orEmpty(), style = MaterialTheme.typography.bodySmall) }
                        Icon(Icons.Filled.QrCode, null)
                    }
                }
            }
        }
    }
    shown?.let { (p, a) -> PromoDialog(p, a) { shown = null } }
}

@Composable
fun PassportScreen(vm: AppViewModel, nav: NavController) {
    val user by vm.user.collectAsStateWithLifecycle()
    var data by remember { mutableStateOf<PassportDto?>(null) }
    LaunchedEffect(user) { if (user != null) vm.cachedGet("passport", "/api/me/passport")?.let { data = runCatching { vm.api.gson.fromJson(it, PassportDto::class.java) }.getOrNull() } }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackBar("Паспорт путешественника", { nav.popBackStack() }) }
        if (user == null) item { Empty(Icons.Filled.EmojiEvents, "Войдите, чтобы собирать штампы", "Отмечайтесь в местах (до 300 м) и получайте достижения") }
        data?.let { d ->
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Уровень ${d.level}", style = MaterialTheme.typography.headlineLarge)
                    LinearProgressIndicator(progress = { (3 - d.next_level_in) / 3f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Text("До следующего уровня: ${d.next_level_in}", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(d.achievements.orEmpty(), key = { it.id }) { a ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (a.done) Icons.Filled.EmojiEvents else Icons.Filled.Lock, null, tint = if (a.done) Brand.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                            HGap(12.dp)
                            Column { Text(a.title, style = MaterialTheme.typography.titleMedium); Text(a.description, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            item { SectionTitle("Штампы: ${d.stamps?.size ?: 0}", Modifier.padding(horizontal = 16.dp)) }
            items(d.stamps.orEmpty(), key = { "s" + it.place.id }) { s ->
                Box(Modifier.padding(horizontal = 16.dp)) { Card(onClick = { nav.navigate("place/${s.place.id}") }) { Text(s.place.name, style = MaterialTheme.typography.titleMedium, color = hex(s.place.vertical?.color)); Text(s.at.take(10), style = MaterialTheme.typography.bodySmall) } }
            }
        }
    }
}

@Composable
fun InfoScreen(vm: AppViewModel, nav: NavController) {
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackBar("Важно знать", { nav.popBackStack() }) }
        items(bundle?.info.orEmpty(), key = { it.id }) { s ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(iconFor(s.icon), null); HGap(); Text(s.title, style = MaterialTheme.typography.titleLarge) }
                    s.items.orEmpty().forEach { Column(Modifier.padding(top = 4.dp)) { Text(it.title, fontWeight = FontWeight.SemiBold); Text(it.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
        }
    }
}
