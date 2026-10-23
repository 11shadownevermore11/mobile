package ru.kodnovorossii.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import ru.kodnovorossii.app.ui.theme.LocalDark
import java.io.ByteArrayInputStream
import java.io.IOException

/** Точка на карте (широта, долгота). */
data class GeoPoint(val latitude: Double, val longitude: Double)

data class MapPin(
    val id: Int, val lat: Double, val lng: Double, val color: Int, val title: String,
    val subtitle: String = "", val order: Int? = null, val dim: Boolean = false,
)

data class MapShape(val points: List<GeoPoint>, val color: Int, val title: String = "", val onClick: (() -> Unit)? = null)

/**
 * Встроенная карта ДНР. Всё лежит в APK (app/src/main/assets/map):
 *  - движок MapLibre GL JS (maplibre-gl.js),
 *  - векторные тайлы региона на данных OpenStreetMap, масштабы 0–14 (tiles/{z}/{x}/{y}.pbf, дальше — перемасштабирование до 19),
 *  - шрифты подписей (fonts) и страница со стилем (index.html).
 * Страница открывается с внутреннего адреса https://appassets.androidplatform.net/ — запросы к нему отдаются из assets,
 * а любые другие адреса блокируются: карта не ходит в интернет и не использует API Яндекс Карт.
 */
private object MapAssets {
    const val HOST = "appassets.androidplatform.net"
    const val PREFIX = "/assets/"
    const val PAGE = "https://$HOST${PREFIX}map/index.html"

    private val MIME = mapOf(
        "html" to "text/html", "js" to "application/javascript", "css" to "text/css", "json" to "application/json",
        "pbf" to "application/x-protobuf", "png" to "image/png", "txt" to "text/plain",
    )

    private fun empty(code: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", code, reason, mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))

    fun serve(context: Context, request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        val path = url.path.orEmpty()
        if (url.host != HOST || !path.startsWith(PREFIX)) return empty(403, "Offline")
        val asset = path.removePrefix(PREFIX)
        if (asset.contains("..")) return empty(403, "Forbidden")
        val mime = MIME[asset.substringAfterLast('.', "")] ?: "application/octet-stream"
        return try {
            val text = mime.startsWith("text/") || mime == "application/javascript" || mime == "application/json"
            WebResourceResponse(mime, if (text) "utf-8" else null, 200, "OK", mapOf("Cache-Control" to "max-age=86400"), context.assets.open(asset))
        } catch (_: IOException) {
            // Нет тайла (за пределами региона или пустая клетка) — карта просто ничего не рисует в этом месте
            empty(404, "Not Found")
        }
    }
}

private fun hexColor(c: Int) = String.format("#%06X", 0xFFFFFF and c)

/** Мост из JavaScript карты в Kotlin. Вызовы приходят не в главном потоке — переносим их туда. */
private class MapBridge(
    val onReady: () -> Unit,
    val onFail: (String) -> Unit,
    val onPin: (Int) -> Unit,
    val onCluster: (List<Int>) -> Unit,
    val onShape: (Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun ready() { main.post { onReady() } }
    @JavascriptInterface fun fail(msg: String) { main.post { onFail(msg) } }
    @JavascriptInterface fun pin(id: Int) { main.post { onPin(id) } }
    @JavascriptInterface fun shape(i: Int) { main.post { onShape(i) } }
    @JavascriptInterface fun cluster(json: String) {
        val arr = JSONArray(json)
        val ids = (0 until arr.length()).map { arr.getInt(it) }
        main.post { onCluster(ids) }
    }
}

/** Карта приложения: метки, кластеры с числом объектов, маршрут, контуры площадок, «я здесь», светлая и тёмная тема.
 *  Работает полностью офлайн — данные карты встроены в приложение. */
@Composable
fun AppMap(
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    cluster: Boolean = true,
    center: GeoPoint? = null,
    zoom: Double = 12.0,
    fit: Boolean = false,
    polyline: List<GeoPoint>? = null,
    polylineColor: Int = android.graphics.Color.parseColor("#17191F"),
    shapes: List<MapShape> = emptyList(),
    me: GeoPoint? = null,
    onPin: (MapPin) -> Unit = {},
    onClusterList: (List<MapPin>) -> Unit = {},
) {
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val err = error
    if (err != null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(err, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { attempt++; error = null }) { Text("Повторить") }
            }
        }
    } else key(attempt) {
        BundledWebMap(pins, modifier, cluster, center, zoom, fit, polyline, polylineColor, shapes, me, onPin, onClusterList, onFail = { error = it })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BundledWebMap(
    pins: List<MapPin>,
    modifier: Modifier,
    cluster: Boolean,
    center: GeoPoint?,
    zoom: Double,
    fit: Boolean,
    polyline: List<GeoPoint>?,
    polylineColor: Int,
    shapes: List<MapShape>,
    me: GeoPoint?,
    onPin: (MapPin) -> Unit,
    onClusterList: (List<MapPin>) -> Unit,
    onFail: (String) -> Unit,
) {
    val context = LocalContext.current
    val dark = LocalDark.current
    var ready by remember { mutableStateOf(false) }
    val curPins by rememberUpdatedState(pins)
    val curShapes by rememberUpdatedState(shapes)
    val curOnPin by rememberUpdatedState(onPin)
    val curOnCluster by rememberUpdatedState(onClusterList)
    val curFail by rememberUpdatedState(onFail)

    val web = remember {
        WebView(context).apply {
            setBackgroundColor(if (dark) 0xFF1C1F24.toInt() else 0xFFF3F1ED.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setGeolocationEnabled(false)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                    MapAssets.serve(view.context, request)

                // Ссылка «участники OpenStreetMap» в подписи карты — открываем во внешнем браузере
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.host != MapAssets.HOST) runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }
            }
            addJavascriptInterface(MapBridge(
                onReady = { ready = true },
                onFail = { msg -> curFail(msg) },
                onPin = { id -> curPins.firstOrNull { it.id == id }?.let { curOnPin(it) } },
                onCluster = { ids -> curOnCluster(curPins.filter { it.id in ids }) },
                onShape = { i -> curShapes.getOrNull(i)?.onClick?.invoke() },
            ), "Android")
            loadUrl(MapAssets.PAGE)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) web.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) web.onPause()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); web.destroy() }
    }

    LaunchedEffect(ready, pins, cluster, fit, zoom, polyline, polylineColor, shapes, me, dark, center?.latitude, center?.longitude) {
        if (!ready) return@LaunchedEffect
        val d = JSONObject()
        d.put("dark", dark)
        d.put("cluster", cluster)
        d.put("fit", fit)
        d.put("zoom", zoom)
        center?.let { d.put("center", JSONArray(listOf(it.latitude, it.longitude))) }
        me?.let { d.put("me", JSONArray(listOf(it.latitude, it.longitude))) }
        d.put("pins", JSONArray(pins.map { p ->
            JSONObject().put("id", p.id).put("lat", p.lat).put("lng", p.lng).put("title", p.title)
                .put("color", if (p.dim) "#9AA0A6" else hexColor(p.color)).put("order", p.order ?: JSONObject.NULL)
        }))
        if (polyline != null && polyline.size > 1) {
            d.put("line", JSONArray(polyline.map { JSONArray(listOf(it.latitude, it.longitude)) }))
            d.put("lineColor", hexColor(polylineColor))
        }
        d.put("shapes", JSONArray(shapes.map { s ->
            JSONObject().put("color", hexColor(s.color)).put("title", s.title)
                .put("coords", JSONArray(s.points.map { JSONArray(listOf(it.latitude, it.longitude)) }))
        }))
        web.evaluateJavascript("render($d)", null)
    }

    AndroidView(factory = { web }, modifier = modifier)
}
