package ru.kodnovorossii.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.kodnovorossii.app.data.Api
import ru.kodnovorossii.app.data.ApiException
import ru.kodnovorossii.app.data.BundleDto
import ru.kodnovorossii.app.data.CacheEntity
import ru.kodnovorossii.app.data.CategoryDto
import ru.kodnovorossii.app.data.FavoriteEntity
import ru.kodnovorossii.app.data.Geo
import ru.kodnovorossii.app.data.KodDb
import ru.kodnovorossii.app.data.LoginResponse
import ru.kodnovorossii.app.data.PendingEntity
import ru.kodnovorossii.app.data.PlaceDto
import ru.kodnovorossii.app.data.Prefs
import ru.kodnovorossii.app.data.UserDto
import ru.kodnovorossii.app.data.VerticalDto
import ru.kodnovorossii.app.geo.GeoWorker
import java.util.concurrent.TimeUnit

/** Единое состояние приложения. Данные берутся из офлайн-пакета (Room), сеть — только для обновления и отправки. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val api = Api(prefs)
    private val db = KodDb.get(app)
    private val gson = api.gson

    val bundle = MutableStateFlow<BundleDto?>(null)
    val syncing = MutableStateFlow(false)
    val online = MutableStateFlow(true)
    val user = MutableStateFlow<UserDto?>(prefs.userJson?.let { runCatching { gson.fromJson(it, UserDto::class.java) }.getOrNull() })
    val theme = MutableStateFlow(prefs.theme)
    val proMode = MutableStateFlow(prefs.proMode)
    val location = MutableStateFlow<Pair<Double, Double>?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val favorites: StateFlow<List<FavoriteEntity>> = db.favorites().observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favoriteIds: StateFlow<Set<Int>> = favorites.map { l -> l.map { it.placeId }.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val pendingCount: StateFlow<Int> = db.pending().count().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            db.cache().get("bundle")?.let { c -> runCatching { bundle.value = gson.fromJson(c.json, BundleDto::class.java) } }
            // Первый запуск без сервера: данные и фото встроены в приложение (assets) — всё работает сразу
            if (bundle.value == null) runCatching {
                val text = getApplication<Application>().assets.open("bundle.json").bufferedReader().use { it.readText() }
                bundle.value = gson.fromJson(text, BundleDto::class.java)
            }
            sync(silent = true)
        }
        Geo.lastKnown(app)?.let { location.value = it.latitude to it.longitude }
        scheduleGeo()
    }

    // ---------- Справочники ----------
    fun verticals(): List<VerticalDto> = bundle.value?.config?.verticals.orEmpty().filter { it.enabled }
    fun vertical(slug: String?): VerticalDto? = verticals().firstOrNull { it.slug == slug }
    fun categories(): Map<Int, CategoryDto> = verticals().flatMap { it.categories.orEmpty() }.associateBy { it.id }
    fun places(): List<PlaceDto> = bundle.value?.places.orEmpty()
    fun place(id: Int): PlaceDto? = places().firstOrNull { it.id == id }
    fun say(msg: String) { messages.tryEmit(msg) }

    // ---------- Синхронизация ----------
    fun sync(silent: Boolean = false) = viewModelScope.launch {
        syncing.value = true
        try {
            val text = api.raw("GET", "/api/sync/bundle")
            val b = gson.fromJson(text, BundleDto::class.java)
            bundle.value = b
            db.cache().put(CacheEntity("bundle", text, System.currentTimeMillis()))
            prefs.lastSync = System.currentTimeMillis()
            online.value = true
            flushPending()
            syncFavorites()
            refreshUser()
            if (!silent) say("Данные обновлены: ${b.places?.size ?: 0} мест, ${b.events?.size ?: 0} событий")
        } catch (e: Exception) {
            online.value = false
            if (!silent) say(if (bundle.value != null) "Нет связи с сервером — работаем на сохранённых данных" else "Нет связи с сервером. Проверьте интернет")
        } finally {
            syncing.value = false
        }
    }

    private suspend fun flushPending() {
        for (p in db.pending().all()) {
            try { api.raw(p.method, p.path, p.body); db.pending().remove(p) } catch (e: ApiException) { if (e.code != 0) db.pending().remove(p) else return }
        }
    }

    /** Отправить сейчас или поставить в очередь до появления сети. */
    fun sendOrQueue(method: String, path: String, body: Any, okMessage: String) = viewModelScope.launch {
        val json = gson.toJson(body)
        try { api.raw(method, path, json); if (okMessage.isNotBlank()) say(okMessage) } catch (e: ApiException) {
            if (e.code == 0) { db.pending().add(PendingEntity(method = method, path = path, body = json, createdAt = System.currentTimeMillis())); if (okMessage.isNotBlank()) say("Нет сети — отправим автоматически, когда появится связь") }
            else say(e.message ?: "Ошибка")
        }
    }

    // ---------- Избранное (локально, без входа и без сети) ----------
    fun toggleFavorite(p: PlaceDto) = viewModelScope.launch {
        val existing = db.favorites().all().firstOrNull { it.placeId == p.id && !it.deleted }
        if (existing != null) {
            if (prefs.token != null) db.favorites().upsert(existing.copy(deleted = true, synced = false)) else db.favorites().hardDelete(p.id)
            say("Убрано из избранного")
        } else {
            db.favorites().upsert(FavoriteEntity(p.id, gson.toJson(p), System.currentTimeMillis()))
            say(if (online.value) "Добавлено в избранное" else "Добавлено в избранное офлайн")
        }
        if (prefs.token != null) runCatching { syncFavorites() }
    }

    private suspend fun syncFavorites() {
        if (prefs.token == null) return
        val all = db.favorites().all()
        val ids = all.filter { !it.deleted }.map { it.placeId }
        val removed = all.filter { it.deleted }.map { it.placeId }
        try {
            val r: Map<String, List<Double>> = api.post("/api/me/favorites/sync", mapOf("ids" to ids, "removed" to removed))
            removed.forEach { db.favorites().hardDelete(it) }
            val server = r["ids"].orEmpty().map { it.toInt() }
            for (id in server) if (id !in ids) place(id)?.let { db.favorites().upsert(FavoriteEntity(id, gson.toJson(it), System.currentTimeMillis(), synced = true)) }
        } catch (_: Exception) { }
    }

    fun favoritePlaces(): List<PlaceDto> = favorites.value.mapNotNull { f -> place(f.placeId) ?: runCatching { gson.fromJson(f.json, PlaceDto::class.java) }.getOrNull() }

    // ---------- Аккаунт ----------
    suspend fun login(email: String, password: String, totp: String?): LoginResponse {
        val r: LoginResponse = api.post("/api/auth/login", mapOf("email" to email, "password" to password, "totp" to totp?.ifBlank { null }))
        if (r.token != null && r.user != null) setAuth(r.token, r.user)
        return r
    }

    suspend fun register(email: String, password: String, name: String) {
        val r: LoginResponse = api.post("/api/auth/register", mapOf("email" to email, "password" to password, "name" to name))
        if (r.token != null && r.user != null) setAuth(r.token, r.user)
    }

    suspend fun setup2fa(setupToken: String): Map<String, String> = api.post("/api/auth/2fa/setup", null, setupToken)

    suspend fun enable2fa(setupToken: String, code: String) {
        val r: LoginResponse = api.post("/api/auth/2fa/enable", mapOf("code" to code), setupToken)
        if (r.token != null && r.user != null) setAuth(r.token, r.user)
    }

    private fun setAuth(token: String, u: UserDto) {
        prefs.token = token
        prefs.userJson = gson.toJson(u)
        user.value = u
        viewModelScope.launch { syncFavorites() }
    }

    private suspend fun refreshUser() {
        if (prefs.token == null) return
        try {
            val u: UserDto = api.get("/api/auth/me")
            user.value = u; prefs.userJson = gson.toJson(u)
        } catch (e: ApiException) { if (e.code == 401) logout() }
    }

    fun logout() { prefs.token = null; prefs.userJson = null; user.value = null }

    // ---------- Настройки ----------
    fun setTheme(t: String) { prefs.theme = t; theme.value = t }
    fun setPro(v: Boolean) { prefs.proMode = v; proMode.value = v }

    fun scheduleGeo() {
        val wm = WorkManager.getInstance(getApplication())
        if (prefs.geoEnabled) {
            val req = PeriodicWorkRequestBuilder<GeoWorker>(15, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork("geo", ExistingPeriodicWorkPolicy.UPDATE, req)
        } else wm.cancelUniqueWork("geo")
    }

    fun refreshLocation() = viewModelScope.launch {
        val l = Geo.current(getApplication())
        if (l != null) location.value = l.latitude to l.longitude else say("Не удалось определить местоположение")
    }

    /** Карточка: сначала сеть (свежие отзывы, слоты), при отсутствии сети — последний сохранённый ответ. */
    suspend fun placeExtra(id: Int): ru.kodnovorossii.app.data.PlaceExtra? {
        val key = "place:$id"
        return try {
            val text = api.raw("GET", "/api/places/$id")
            db.cache().put(CacheEntity(key, text, System.currentTimeMillis()))
            gson.fromJson(text, ru.kodnovorossii.app.data.PlaceExtra::class.java)
        } catch (e: Exception) {
            db.cache().get(key)?.let { runCatching { gson.fromJson(it.json, ru.kodnovorossii.app.data.PlaceExtra::class.java) }.getOrNull() }
        }
    }

    suspend fun cachedGet(key: String, path: String): String? = try {
        val text = api.raw("GET", path)
        db.cache().put(CacheEntity(key, text, System.currentTimeMillis()))
        text
    } catch (e: Exception) { db.cache().get(key)?.json }

    fun distanceTo(p: PlaceDto): Double? = location.value?.let { ru.kodnovorossii.app.data.Logic.haversine(it.first, it.second, p.lat, p.lng) }
}
