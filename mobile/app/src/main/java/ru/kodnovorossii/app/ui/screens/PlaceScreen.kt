@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ru.kodnovorossii.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ru.kodnovorossii.app.AppViewModel
import ru.kodnovorossii.app.data.ApiException
import ru.kodnovorossii.app.data.Geo
import ru.kodnovorossii.app.data.Logic
import ru.kodnovorossii.app.data.PlaceExtra
import ru.kodnovorossii.app.data.PromoActivation
import ru.kodnovorossii.app.data.PromoDto
import ru.kodnovorossii.app.data.SlotDto
import ru.kodnovorossii.app.data.StoryDto
import ru.kodnovorossii.app.ui.components.*
import ru.kodnovorossii.app.ui.theme.Brand
import java.util.Locale

private val DAYS = listOf("mon" to "Понедельник", "tue" to "Вторник", "wed" to "Среда", "thu" to "Четверг", "fri" to "Пятница", "sat" to "Суббота", "sun" to "Воскресенье")
private val LABELS = mapOf("interns" to "Принимаем стажёров", "excursions" to "Проводим экскурсии", "orders" to "Принимаем заказы")

/** Аудиогид: синтез речи на устройстве — работает офлайн, без записи аудиофайлов. */
@Composable
fun rememberTts(): TextToSpeech? {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx) { status -> if (status == TextToSpeech.SUCCESS) { engine?.setLanguage(Locale("ru", "RU")); ready = true } }
        tts.value = engine
        onDispose { engine?.stop(); engine?.shutdown() }
    }
    return if (ready) tts.value else null
}

@Composable
fun StoryCard(story: StoryDto, tts: TextToSpeech?, vm: AppViewModel) {
    var playing by remember { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(story.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            story.period?.let { Tag(it) }
        }
        Text(story.text, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(story.author.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                if (tts == null) { vm.say("Синтез речи недоступен на устройстве"); return@OutlinedButton }
                if (playing) { tts.stop(); playing = false } else {
                    tts.speak("${story.title}. ${story.text}", TextToSpeech.QUEUE_FLUSH, null, "story${story.id}")
                    playing = true
                    vm.sendOrQueue("POST", "/api/heritage/stories/${story.id}/listen", emptyMap<String, Any>(), "")
                }
            }) { Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, null); HGap(4.dp); Text(if (playing) "Стоп" else "Аудиогид") }
        }
    }
}

@Composable
fun PlaceScreen(vm: AppViewModel, nav: NavController, id: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val favs by vm.favoriteIds.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val loc by vm.location.collectAsStateWithLifecycle()
    val p = remember(bundle, id) { vm.place(id) }
    var extra by remember { mutableStateOf<PlaceExtra?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var promo by remember { mutableStateOf<Pair<PromoDto, PromoActivation?>?>(null) }
    var slot by remember { mutableStateOf<SlotDto?>(null) }
    val tts = rememberTts()

    LaunchedEffect(id, user) { extra = vm.placeExtra(id) }
    if (p == null) {
        Column { BackBar("Место", { nav.popBackStack() }); Empty(Icons.Filled.Place, "Объект не найден", "Обновите данные в настройках") }
        return
    }
    val color = hex(p.vertical?.color)
    val webUrl = "${vm.prefs.serverUrl.trimEnd('/')}/place/${p.slug}"

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            BackBar(p.category?.name.orEmpty(), { nav.popBackStack() }) {
                IconButton(onClick = { share(ctx, p.name, webUrl); vm.sendOrQueue("POST", "/api/analytics/event", mapOf("type" to "share", "place_id" to p.id), "") }) { Icon(Icons.Filled.Share, "Поделиться") }
            }
            Cover(color, p.category?.icon, p.id, Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)), iconSize = 64.dp, photo = p.photos?.firstOrNull())
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chips {
                    Tag(p.category?.name.orEmpty(), color)
                    if (p.made_in_region) Tag("Сделано в регионе", Color.White, MaterialTheme.colorScheme.tertiary)
                    if (p.accessible) Tag("Доступная среда", Brand.Blue)
                    p.labels.orEmpty().forEach { Tag(LABELS[it] ?: it, Brand.Ok) }
                }
                Text(p.name, style = MaterialTheme.typography.headlineLarge)
                p.short?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Rating(extra?.rating ?: p.rating, extra?.reviews_count ?: p.reviews_count); OpenStatus(p)
                }
                if (p.status == "temporarily_unavailable") Tag("Объект временно недоступен — проверьте перед поездкой", Brand.Warn)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openRoute(ctx, p); vm.sendOrQueue("POST", "/api/analytics/event", mapOf("type" to "route_build", "place_id" to p.id), "") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Directions, null); HGap(6.dp); Text("Маршрут")
                    }
                    OutlinedButton(onClick = { vm.toggleFavorite(p) }, modifier = Modifier.weight(1f)) {
                        Icon(if (p.id in favs) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (p.id in favs) Brand.Red else MaterialTheme.colorScheme.onSurface)
                        HGap(6.dp); Text(if (p.id in favs) "В избранном" else "В избранное")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dialog = "report" }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Flag, null, Modifier.size(18.dp)); HGap(4.dp); Text("Неточность") }
                    OutlinedButton(onClick = {
                        if (user == null) { nav.navigate("profile"); return@OutlinedButton }
                        scope.launch {
                            val l = Geo.current(ctx)
                            try {
                                vm.api.post<Map<String, Any?>>("/api/checkins", mapOf("place_id" to p.id, "lat" to l?.latitude, "lng" to l?.longitude))
                                vm.say("Штамп получен! Он в вашем паспорте путешественника")
                            } catch (e: ApiException) { vm.say(e.message ?: "Ошибка") }
                        }
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Verified, null, Modifier.size(18.dp)); HGap(4.dp); Text("Отметиться") }
                }
                if (p.id in favs) Text("Сохранено на устройстве — доступно без сети", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        extra?.promos?.firstOrNull()?.let { pr ->
            item {
                Card(Modifier.padding(horizontal = 16.dp), onClick = {
                    scope.launch {
                        val act = try { vm.api.post<PromoActivation>("/api/promos/${pr.id}/activate") } catch (e: Exception) { null }
                        promo = pr to act
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ConfirmationNumber, null, tint = Brand.Red, modifier = Modifier.size(32.dp)); HGap(12.dp)
                        Column(Modifier.weight(1f)) { Text("Скидка ${pr.discount}% по промокоду", style = MaterialTheme.typography.titleMedium); Text(pr.description.orEmpty(), style = MaterialTheme.typography.bodySmall) }
                        Text("Показать", color = Brand.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("О месте")
                Text(p.description.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                p.fields.orEmpty().forEach { f ->
                    Row { Text(f.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)); Text(fmtValue(f.value) + (f.unit?.let { " $it" } ?: ""), fontWeight = FontWeight.SemiBold) }
                }
                if (p.vertical?.slug == "industry") (p.attrs?.get("safety") as? String)?.let {
                    Card { Row { Icon(Icons.Filled.Engineering, null, tint = Brand.Warn); HGap(); Column { Text("Требования безопасности", style = MaterialTheme.typography.titleMedium); Text(it, style = MaterialTheme.typography.bodyMedium) } } }
                }
                Chips { p.tags.orEmpty().forEach { t -> Tag("#$t") } }
            }
        }
        if (!extra?.slots.isNullOrEmpty()) item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Расписание экскурсий")
                extra!!.slots!!.forEach { s ->
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.start.replace("T", " ").take(16), style = MaterialTheme.typography.titleMedium)
                                Text("${s.title} · ${s.duration_min} мин · ${if (s.price > 0) "${s.price} ₽" else "бесплатно"}${if (s.group_only) " · только группы" else ""}", style = MaterialTheme.typography.bodySmall)
                                Text("Свободно мест: ${s.capacity - s.booked}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { if (user == null) nav.navigate("profile") else slot = s }, enabled = s.capacity > s.booked) { Text("Записаться") }
                        }
                    }
                }
            }
        }
        if (!p.services.isNullOrEmpty()) item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("Услуги")
                p.services!!.forEach { s -> Row(Modifier.padding(vertical = 4.dp)) { Text(fmtValue(s["name"]), modifier = Modifier.weight(1f)); Text(fmtValue(s["price"]), fontWeight = FontWeight.SemiBold) } }
            }
        }
        if (!p.portfolio.isNullOrEmpty()) item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Портфолио")
                p.portfolio!!.forEach { w ->
                    Card {
                        Text(fmtValue(w["title"]), style = MaterialTheme.typography.titleMedium)
                        Text("${fmtValue(w["year"])} · ${fmtValue(w["description"])}", style = MaterialTheme.typography.bodySmall)
                        if (w["rights_confirmed"] == true) Text("✓ Права на материал подтверждены", color = Brand.Ok, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { dialog = "copyright" }) { Icon(Icons.Filled.Copyright, null); HGap(4.dp); Text("Пожаловаться на нарушение авторских прав") }
            }
        }
        val stories = extra?.stories ?: bundle?.heritage.orEmpty().filter { it.place_id == p.id }
        if (stories.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Истории и аудиогид")
                stories.forEach { StoryCard(it, tts, vm) }
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Адрес и контакты")
                OsmMap(listOf(placePin(p)), Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)), cluster = false,
                    center = GeoPoint(p.lat, p.lng), zoom = 15.0, me = loc?.let { GeoPoint(it.first, it.second) })
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Place, null); HGap(); Text("${p.address}, ${p.city}" + (vm.distanceTo(p)?.let { " · ${Logic.fmtDist(it)}" } ?: "")) }
                if (p.hours?.get("always") == true) Text("Круглосуточно") else DAYS.forEach { (k, n) ->
                    val today = Logic.todayKey() == k
                    Row { Text(n, modifier = Modifier.weight(1f), fontWeight = if (today) FontWeight.Bold else FontWeight.Normal); Text((p.hours?.get(k) as? String) ?: "выходной", fontWeight = if (today) FontWeight.Bold else FontWeight.Normal) }
                }
                p.phone?.takeIf { it.isNotBlank() }?.let { ph -> Row(Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ph"))) }.padding(vertical = 4.dp)) { Icon(Icons.Filled.Phone, null); HGap(); Text(ph, color = Brand.Blue) } }
                p.email?.takeIf { it.isNotBlank() }?.let { Row { Icon(Icons.Filled.Email, null); HGap(); Text(it) } }
                Text("Информация проверена: ${p.last_checked ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Отзывы", Modifier.weight(1f))
                    if (extra?.is_owner != true && extra?.my_review == null) TextButton(onClick = { if (user == null) nav.navigate("profile") else dialog = "review" }) { Text("Написать") }
                }
                if (extra?.is_owner == true) Text("Вы владелец: можете отвечать на отзывы, но не оценивать свой объект.", style = MaterialTheme.typography.bodySmall)
                val reviews = extra?.reviews.orEmpty()
                if (extra == null) Text("Отзывы загрузятся при подключении к сети", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (reviews.isEmpty()) Text("Отзывов пока нет — будьте первым", color = MaterialTheme.colorScheme.onSurfaceVariant)
                reviews.take(20).forEach { r ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row { Text(r.user_name.orEmpty(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("★".repeat(r.rating), color = Brand.Star) }
                        if (!r.text.isNullOrBlank()) Text(r.text)
                        if (!r.owner_reply.isNullOrBlank()) Text("Ответ владельца: ${r.owner_reply}", style = MaterialTheme.typography.bodySmall, color = Brand.Blue, modifier = Modifier.padding(top = 4.dp))
                        if (user != null && user!!.id != r.user_id) TextButton(onClick = { vm.sendOrQueue("POST", "/api/reviews/${r.id}/complaint", mapOf("reason" to "Жалоба из приложения"), "Жалоба отправлена — модератор рассмотрит её в течение 24 часов") }) { Text("Пожаловаться", style = MaterialTheme.typography.bodySmall) }
                        HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }

    when (dialog) {
        "report" -> {
            var field by remember { mutableStateOf("hours") }
            var msg by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Сообщить о неточности") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chips { listOf("hours" to "Время работы", "status" to "Закрыто", "address" to "Адрес", "contacts" to "Контакты", "other" to "Другое").forEach { (k, l) -> FilterChip(selected = field == k, onClick = { field = k }, label = { Text(l) }) } }
                    OutlinedTextField(msg, { msg = it }, label = { Text("Что не так?") }, minLines = 3)
                    Text("Без сети сообщение отправится автоматически позже", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = { TextButton(onClick = { vm.sendOrQueue("POST", "/api/places/${p.id}/report", mapOf("field" to field, "message" to msg), "Спасибо! Модератор проверит информацию"); dialog = null }, enabled = msg.length > 2) { Text("Отправить") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } })
        }
        "review" -> {
            var rating by remember { mutableIntStateOf(5) }
            var text by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Ваш отзыв") }, text = {
                Column {
                    Row { (1..5).forEach { n -> IconButton(onClick = { rating = n }) { Icon(Icons.Filled.Star, "$n", tint = if (n <= rating) Brand.Star else MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp)) } } }
                    OutlinedTextField(text, { text = it }, label = { Text("Впечатления") }, minLines = 3)
                }
            }, confirmButton = { TextButton(onClick = {
                scope.launch {
                    try { vm.api.post<Map<String, Any?>>("/api/places/${p.id}/reviews", mapOf("rating" to rating, "text" to text)); vm.say("Отзыв опубликован"); extra = vm.placeExtra(p.id) }
                    catch (e: ApiException) { vm.say(e.message ?: "Ошибка") }
                }
                dialog = null
            }) { Text("Опубликовать") } }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } })
        }
        "copyright" -> {
            var name by remember { mutableStateOf("") }
            var contact by remember { mutableStateOf("") }
            var what by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Нарушение авторских прав") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(what, { what = it }, label = { Text("Какой материал") })
                    OutlinedTextField(name, { name = it }, label = { Text("Правообладатель") })
                    OutlinedTextField(contact, { contact = it }, label = { Text("Контакт") })
                }
            }, confirmButton = { TextButton(enabled = what.isNotBlank() && name.isNotBlank() && contact.isNotBlank(), onClick = {
                vm.sendOrQueue("POST", "/api/copyright", mapOf("place_id" to p.id, "material" to what, "claimant_name" to name, "claimant_contact" to contact), "Жалоба зарегистрирована. Срок рассмотрения — до 3 рабочих дней"); dialog = null
            }) { Text("Отправить") } }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } })
        }
    }
    slot?.let { s ->
        var people by remember { mutableIntStateOf(if (s.group_only) 5 else 1) }
        AlertDialog(onDismissRequest = { slot = null }, title = { Text("Запись на экскурсию") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.start.replace("T", " ").take(16))
                (p.attrs?.get("safety") as? String)?.let { Text("Безопасность: $it", style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Человек:", modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (people > 1) people-- }) { Icon(Icons.Filled.Remove, "Меньше") }
                    Text("$people", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { people++ }) { Icon(Icons.Filled.Add, "Больше") }
                }
            }
        }, confirmButton = { TextButton(onClick = {
            scope.launch {
                try { val r = vm.api.post<Map<String, Any?>>("/api/bookings", mapOf("slot_id" to s.id, "people" to people)); vm.say(r["message"]?.toString() ?: "Вы записаны"); extra = vm.placeExtra(p.id) }
                catch (e: ApiException) { vm.say(e.message ?: "Ошибка") }
            }
            slot = null
        }) { Text("Записаться") } }, dismissButton = { TextButton(onClick = { slot = null }) { Text("Отмена") } })
    }
    promo?.let { (pr, act) -> PromoDialog(pr, act) { promo = null } }
}

@Composable
fun PromoDialog(pr: PromoDto, act: PromoActivation?, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, confirmButton = { TextButton(onClick = onClose) { Text("Готово") } },
        title = { Text("Скидка ${pr.discount}%") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QrImage(act?.qr ?: "KODNR:${pr.code}")
                Text(pr.code, style = MaterialTheme.typography.headlineMedium)
                Text("Назовите промокод или покажите QR-код на кассе. ${pr.description.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            }
        })
}
