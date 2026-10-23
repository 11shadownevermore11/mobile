@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ru.kodnovorossii.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import ru.kodnovorossii.app.AppViewModel
import ru.kodnovorossii.app.data.ApiException
import ru.kodnovorossii.app.data.ChatReply
import ru.kodnovorossii.app.data.Logic
import ru.kodnovorossii.app.ui.components.*
import ru.kodnovorossii.app.ui.theme.Brand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Чат-бот «Гид» ----------
private data class Msg(val me: Boolean, val text: String, val reply: ChatReply? = null)

@Composable
fun AssistantScreen(vm: AppViewModel, nav: NavController) {
    val scope = rememberCoroutineScope()
    val loc by vm.location.collectAsStateWithLifecycle()
    val msgs = remember { mutableStateListOf(Msg(false, "Здравствуйте! Я Гид «Кода Новороссии». Найду место, соберу маршрут, расскажу об афише и помогу разобраться с платформой.", ChatReply(suggestions = listOf("Где поесть рядом?", "Маршрут на 3 часа", "Что на выходных?", "Как добавить свой бизнес?")))) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) { listState.animateScrollToItem(maxOf(0, msgs.size - 1)) }

    fun send(t: String) {
        if (t.isBlank() || busy) return
        // последние реплики — чтобы GigaChat на сервере помнил контекст диалога
        val history = msgs.takeLast(6).map { mapOf("role" to (if (it.me) "user" else "assistant"), "content" to it.text) }
        msgs.add(Msg(true, t)); text = ""; busy = true
        scope.launch {
            try {
                val r: ChatReply = vm.api.post("/api/assistant/chat", mapOf("message" to t, "lat" to loc?.first, "lng" to loc?.second, "history" to history))
                msgs.add(Msg(false, r.reply, r))
            } catch (e: ApiException) {
                // Офлайн: отвечаем локальным поиском по сохранённой базе
                val (hits, _) = Logic.search(t, vm.places())
                val found = hits.take(4).map { it.place }
                msgs.add(Msg(false, if (found.isNotEmpty()) "Нет сети, но вот что нашлось в сохранённой базе:" else "Нет сети. Попробуйте поиск в каталоге — он работает офлайн.", ChatReply(places = found)))
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar("Помощник «Гид»", { nav.popBackStack() })
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.me) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = if (m.me) Brand.Blue else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(m.text, color = if (m.me) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp).widthIn(max = 300.dp))
                    }
                    m.reply?.places?.take(4)?.forEach { p ->
                        Card(Modifier.widthIn(max = 320.dp), onClick = { nav.navigate("place/${p.id}") }) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Rating(p.rating); Text(p.category?.name.orEmpty(), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    m.reply?.events?.forEach { e -> Box(Modifier.widthIn(max = 320.dp)) { EventCard(e) { nav.navigate("event/${e.id}") } } }
                    m.reply?.route?.let { r ->
                        Card(Modifier.widthIn(max = 320.dp)) {
                            r.stops.orEmpty().forEach { s -> Text("${s["arrive"]}  ${if (s["kind"] == "pause") "☕ " else ""}${s["name"]}", style = MaterialTheme.typography.bodySmall) }
                            Button(onClick = {
                                BuilderState.ids = r.stops_raw.orEmpty().mapNotNull { (it["place_id"] as? Double)?.toInt() }
                                nav.navigate("builder")
                            }) { Text("Открыть маршрут") }
                        }
                    }
                    m.reply?.suggestions?.let { sug -> Chips { sug.forEach { s -> FilterChip(selected = false, onClick = { send(s) }, label = { Text(s) }) } } }
                }
            }
            if (busy) item { Text("Ищу…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Например: музеи рядом") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            IconButton(onClick = { send(text) }, enabled = text.isNotBlank() && !busy) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = Brand.Red) }
        }
    }
}

// ---------- Профиль: вход, регистрация, 2FA ----------
@Composable
fun ProfileScreen(vm: AppViewModel, nav: NavController) {
    val user by vm.user.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var step by remember { mutableStateOf("form") } // form | totp | setup
    var setupToken by remember { mutableStateOf<String?>(null) }
    var setupUri by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun doLogin() = scope.launch {
        busy = true
        try {
            val r = vm.login(email.trim(), password, code.ifBlank { null })
            when {
                r.need_totp == true -> step = "totp"
                r.need_totp_setup == true -> { setupToken = r.setup_token; val s = vm.setup2fa(r.setup_token!!); setupUri = s["otpauth_uri"]; step = "setup" }
                else -> { vm.say("Здравствуйте, ${vm.user.value?.name}!"); step = "form" }
            }
        } catch (e: ApiException) { vm.say(e.message ?: "Ошибка") } finally { busy = false }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackBar("Профиль", { nav.popBackStack() }) }
        val u = user
        if (u != null) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(u.name, style = MaterialTheme.typography.headlineLarge)
                    Text(u.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag(u.role_title ?: u.role, Brand.Blue); if (u.totp_enabled) Tag("2FA включена", Brand.Ok) }
                    Text("Отзывы, синхронизация избранного, личные маршруты, запись на экскурсии и заявки инвестора доступны в аккаунте.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { nav.navigate("passport") }) { Icon(Icons.Filled.EmojiEvents, null); HGap(); Text("Паспорт путешественника") }
                    if (u.is_service) Text("Панель управления для вашей роли открывается на сайте: ${ru.kodnovorossii.app.data.ApiConfig.url("/admin")}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.logout() }) { Icon(Icons.AutoMirrored.Filled.Logout, null); HGap(); Text("Выйти") }
                }
            }
        } else item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Logo(40.dp); HGap(12.dp); Text(if (step == "form") (if (mode == "login") "Вход" else "Регистрация") else "Двухфакторная защита", style = MaterialTheme.typography.headlineMedium) }
                when (step) {
                    "form" -> {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(selected = mode == "login", onClick = { mode = "login" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Вход") }
                            SegmentedButton(selected = mode == "register", onClick = { mode = "register" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Регистрация") }
                        }
                        if (mode == "register") OutlinedTextField(name, { name = it }, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
                        OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                        Button(onClick = {
                            if (mode == "login") doLogin() else scope.launch {
                                try { vm.register(email.trim(), password, name); vm.say("Аккаунт создан") } catch (e: ApiException) { vm.say(e.message ?: "Ошибка") }
                            }
                        }, enabled = !busy && email.isNotBlank() && password.length >= 6, modifier = Modifier.fillMaxWidth()) { Text(if (mode == "login") "Войти" else "Создать аккаунт") }
                        Text("Без аккаунта доступны просмотр, поиск, карта, офлайн-режим и избранное на устройстве.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Card {
                            Text("Демо-доступы (пароль demo123)", style = MaterialTheme.typography.titleMedium)
                            listOf("user@kod.ru" to "Пользователь", "owner@kod.ru" to "Владелец бизнеса", "expert@kod.ru" to "Эксперт наследия").forEach { (e, r) ->
                                TextButton(onClick = { email = e; password = "demo123"; mode = "login" }) { Text("$r — $e") }
                            }
                        }
                    }
                    "totp" -> {
                        Text("Для служебных ролей обязательна двухфакторная защита. Введите код из приложения-аутентификатора.")
                        OutlinedTextField(code, { code = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Код") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                        Button(onClick = { doLogin() }, enabled = code.length == 6 && !busy, modifier = Modifier.fillMaxWidth()) { Text("Подтвердить") }
                    }
                    "setup" -> {
                        Text("Отсканируйте QR-код в приложении-аутентификаторе и введите код.")
                        setupUri?.let { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrImage(it) } }
                        OutlinedTextField(code, { code = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Код") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { scope.launch { try { vm.enable2fa(setupToken!!, code); step = "form" } catch (e: ApiException) { vm.say(e.message ?: "Ошибка") } } }, enabled = code.length == 6, modifier = Modifier.fillMaxWidth()) { Text("Включить 2FA и войти") }
                    }
                }
            }
        }
    }
}

// ---------- Настройки ----------
@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle()
    val bundle by vm.bundle.collectAsStateWithLifecycle()
    val p = vm.prefs
    var geo by remember { mutableStateOf(p.geoEnabled) }
    var radius by remember { mutableFloatStateOf(p.geoRadius.toFloat()) }
    var quietFrom by remember { mutableFloatStateOf(p.quietFrom.toFloat()) }
    var quietTo by remember { mutableFloatStateOf(p.quietTo.toFloat()) }
    var byInterests by remember { mutableStateOf(p.geoByInterests) }
    var interests by remember { mutableStateOf(p.interests) }
    val fmt = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackBar("Настройки", { nav.popBackStack() }) }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card {
                    Text("Тема оформления", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная").forEachIndexed { i, (k, l) ->
                            SegmentedButton(selected = theme == k, onClick = { vm.setTheme(k) }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                        }
                    }
                }
                Card {
                    Text("Офлайн-режим", style = MaterialTheme.typography.titleMedium)
                    Text("Карточки, поиск, маршруты, афиша и «Важно знать» хранятся на устройстве: ${bundle?.places?.size ?: 0} мест, ${bundle?.events?.size ?: 0} событий.", style = MaterialTheme.typography.bodySmall)
                    Text("Последнее обновление: ${if (p.lastSync > 0) fmt.format(Date(p.lastSync)) else "ещё не было"}", style = MaterialTheme.typography.bodySmall)
                    if (pending > 0) Text("В очереди на отправку: $pending", color = Brand.Warn, style = MaterialTheme.typography.bodySmall)
                    Text("Карта ДНР встроена в приложение и работает без интернета", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.sync() }, enabled = !syncing) { Icon(Icons.Filled.Sync, null); HGap(); Text(if (syncing) "Обновляем…" else "Обновить данные") }
                }
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Гео-уведомления", style = MaterialTheme.typography.titleMedium); Text("Подскажем, когда вы рядом с местом из избранного или по интересам. Не более 3 в сутки.", style = MaterialTheme.typography.bodySmall) }
                        Switch(geo, { geo = it; p.geoEnabled = it; vm.scheduleGeo() })
                    }
                    if (geo) {
                        Text("Радиус: ${radius.toInt()} м")
                        Slider(radius, { radius = it }, valueRange = 100f..1000f, steps = 8, onValueChangeFinished = { p.geoRadius = radius.toInt() })
                        Text("Тихие часы: с ${quietFrom.toInt()}:00 до ${quietTo.toInt()}:00")
                        Slider(quietFrom, { quietFrom = it }, valueRange = 0f..23f, steps = 22, onValueChangeFinished = { p.quietFrom = quietFrom.toInt() })
                        Slider(quietTo, { quietTo = it }, valueRange = 0f..23f, steps = 22, onValueChangeFinished = { p.quietTo = quietTo.toInt() })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Также по интересам", modifier = Modifier.weight(1f)); Switch(byInterests, { byInterests = it; p.geoByInterests = it })
                        }
                        Chips {
                            vm.verticals().forEach { v ->
                                FilterChip(selected = v.slug in interests, onClick = { interests = if (v.slug in interests) interests - v.slug else interests + v.slug; p.interests = interests }, label = { Text(v.name) })
                            }
                        }
                    }
                }
                Card {
                    val online by vm.online.collectAsStateWithLifecycle()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Связь с сервером", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Tag(if (online) "на связи" else "офлайн", Color.White, if (online) Brand.Ok else MaterialTheme.colorScheme.outline)
                    }
                    Text("Сервер: ${ru.kodnovorossii.app.data.ApiConfig.BASE}", style = MaterialTheme.typography.bodySmall)
                    Text("Без интернета приложение работает на сохранённых данных. Сеть нужна для входа, отзывов, записи и свежей афиши.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.sync() }, enabled = !syncing, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Wifi, null); HGap(); Text(if (syncing) "Проверяем…" else "Проверить связь") }
                }
                Text("«Код Новороссии» 1.0 · Хакатон ФСП ДНР, 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }
        }
    }
}
