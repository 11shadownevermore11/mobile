package ru.kodnovorossii.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapListener
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import ru.kodnovorossii.app.ui.theme.LocalDark

data class MapPin(
    val id: Int, val lat: Double, val lng: Double, val color: Int, val title: String,
    val subtitle: String = "", val order: Int? = null, val dim: Boolean = false,
)

data class MapShape(val points: List<GeoPoint>, val color: Int, val title: String = "", val onClick: (() -> Unit)? = null)

object Tiles {
    // Тайлы CARTO на данных OpenStreetMap — те же, что на сайте. Разрешают офлайн-кэширование области.
    val LIGHT: OnlineTileSourceBase = XYTileSource(
        "CartoVoyager", 1, 19, 256, ".png",
        arrayOf("https://a.basemaps.cartocdn.com/rastertiles/voyager/", "https://b.basemaps.cartocdn.com/rastertiles/voyager/", "https://c.basemaps.cartocdn.com/rastertiles/voyager/"),
        "© OpenStreetMap © CARTO",
    )
    val DARK: OnlineTileSourceBase = XYTileSource(
        "CartoDark", 1, 19, 256, ".png",
        arrayOf("https://a.basemaps.cartocdn.com/dark_all/", "https://b.basemaps.cartocdn.com/dark_all/", "https://c.basemaps.cartocdn.com/dark_all/"),
        "© OpenStreetMap © CARTO",
    )
}

/** Рисует метки и кластеры. Кластер показывает число объектов; нажатие — приближение, а на крупном масштабе — список. */
private class ClusterLayer(
    private val map: MapView,
    var onPin: (MapPin) -> Unit,
    var onClusterList: (List<MapPin>) -> Unit,
) {
    var pins: List<MapPin> = emptyList()
    var cluster = true
    private val markers = mutableListOf<Overlay>()
    private val handler = Handler(Looper.getMainLooper())
    private val density = map.context.resources.displayMetrics.density
    private val renderTask = Runnable { render() }

    fun schedule() { handler.removeCallbacks(renderTask); handler.postDelayed(renderTask, 60) }

    private fun bitmap(color: Int, sizeDp: Float, text: String?, ring: Boolean = false): BitmapDrawable {
        val s = (sizeDp * density).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = s / 2f
        if (ring) { p.color = color; p.alpha = 70; c.drawCircle(r, r, r, p); p.alpha = 255 }
        val inner = if (ring) r * 0.8f else r
        p.color = android.graphics.Color.WHITE; c.drawCircle(r, r, inner, p)
        p.color = color; c.drawCircle(r, r, inner - 3 * density, p)
        if (text != null) {
            p.color = android.graphics.Color.WHITE
            p.textSize = inner * 0.85f
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            c.drawText(text, r, r - (p.descent() + p.ascent()) / 2, p)
        } else {
            p.color = android.graphics.Color.WHITE; c.drawCircle(r, r, inner * 0.28f, p)
        }
        return BitmapDrawable(map.resources, b)
    }

    fun render() {
        if (map.width == 0) { schedule(); return }
        markers.forEach { map.overlays.remove(it) }
        markers.clear()
        val proj = map.projection
        val cell = 56 * density
        val groups = LinkedHashMap<Pair<Int, Int>, MutableList<MapPin>>()
        for (p in pins) {
            val pt = proj.toPixels(GeoPoint(p.lat, p.lng), null)
            val key = if (cluster) (pt.x / cell).toInt() to (pt.y / cell).toInt() else p.id to 0
            groups.getOrPut(key) { mutableListOf() }.add(p)
        }
        for (g in groups.values) {
            val m = Marker(map)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            if (g.size == 1) {
                val p = g[0]
                m.position = GeoPoint(p.lat, p.lng)
                val col = if (p.dim) android.graphics.Color.GRAY else p.color
                m.icon = bitmap(col, 30f, p.order?.toString())
                m.title = p.title
                m.setOnMarkerClickListener { _, _ -> onPin(p); true }
            } else {
                val lat = g.sumOf { it.lat } / g.size
                val lng = g.sumOf { it.lng } / g.size
                m.position = GeoPoint(lat, lng)
                val dominant = g.groupingBy { it.color }.eachCount().maxByOrNull { it.value }!!.key
                val mixed = g.map { it.color }.distinct().size > 1
                m.icon = bitmap(dominant, if (g.size < 10) 40f else 48f, g.size.toString(), ring = mixed)
                m.title = "${g.size} объектов"
                m.setOnMarkerClickListener { _, mv ->
                    val box = BoundingBox.fromGeoPoints(g.map { GeoPoint(it.lat, it.lng) })
                    val tight = mv.zoomLevelDouble >= 16.5 || box.diagonalLengthInMeters < 60
                    if (tight) onClusterList(g) else mv.zoomToBoundingBox(box.increaseByScale(1.6f), true, (40 * density).toInt())
                    true
                }
            }
            markers.add(m)
            map.overlays.add(m)
        }
        map.invalidate()
    }
}

/** Карта приложения. Есть интернет и ключ — Яндекс Карты; нет сети или Яндекс не загрузился — офлайн-карта osmdroid
 *  (её область можно заранее скачать кнопкой «Скачать офлайн-карту» на экране карты). */
@Composable
fun OsmMap(
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
    onReady: (MapView) -> Unit = {},
) {
    val context = LocalContext.current
    // "yandex" — Яндекс Карты; "offline" — нет интернета, сохранённая карта региона; иначе — текст ошибки Яндекса
    var mode by remember { mutableStateOf(if (MapConfig.YANDEX_KEY.isNotBlank() && isOnline(context)) "yandex" else "offline") }
    var attempt by remember { mutableIntStateOf(0) }
    when (mode) {
        "yandex" -> androidx.compose.runtime.key(attempt) {
            YandexWebMap(pins, modifier, cluster, center, zoom, fit, polyline, polylineColor, shapes, me, onPin, onClusterList,
                onFail = { msg -> mode = if (isOnline(context)) msg else "offline" })
        }
        "offline" -> OfflineMap(pins, modifier, cluster, center, zoom, fit, polyline, polylineColor, shapes, me, onPin, onClusterList, onReady)
        else -> Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(mode, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { attempt++; mode = "yandex" }) { Text("Повторить") }
            }
        }
    }
}

/** Офлайн-карта региона: скачивает тайлы Донецка и окрестностей (масштабы 10–15) для работы без интернета. */
fun downloadOfflineRegion(context: Context) {
    val mv = MapView(context).apply { setTileSource(Tiles.LIGHT) }
    CacheManager(mv).downloadAreaAsync(context, BoundingBox(48.12, 38.0, 47.90, 37.62), 10, 15)
}

@Composable
private fun OfflineMap(
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
    onReady: (MapView) -> Unit = {},
) {
    val context = LocalContext.current
    val dark = LocalDark.current
    val mapView = remember { createMap(context, center ?: GeoPoint(48.008, 37.804), if (center != null) zoom else 12.0) }
    val layer = remember { ClusterLayer(mapView, onPin, onClusterList) }
    layer.onPin = onPin
    layer.onClusterList = onClusterList
    val extras = remember { mutableListOf<Overlay>() }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) mapView.onResume()
            if (e == Lifecycle.Event.ON_PAUSE) mapView.onPause()
        }
        lifecycle.addObserver(obs)
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { layer.schedule(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { layer.schedule(); return false }
        })
        onReady(mapView)
        onDispose { lifecycle.removeObserver(obs); mapView.onDetach() }
    }

    LaunchedEffect(dark) { mapView.setTileSource(if (dark) Tiles.DARK else Tiles.LIGHT) }

    LaunchedEffect(pins, cluster) {
        layer.pins = pins
        layer.cluster = cluster
        layer.schedule()
        if (fit && pins.isNotEmpty()) {
            mapView.post {
                if (pins.size == 1) { mapView.controller.setZoom(15.0); mapView.controller.setCenter(GeoPoint(pins[0].lat, pins[0].lng)) }
                else runCatching {
                    val box = BoundingBox.fromGeoPoints(pins.map { GeoPoint(it.lat, it.lng) })
                    mapView.zoomToBoundingBox(box.increaseByScale(1.25f), false, 60)
                }
                layer.schedule()
            }
        }
    }

    LaunchedEffect(polyline, shapes, me) {
        extras.forEach { mapView.overlays.remove(it) }
        extras.clear()
        shapes.forEach { s ->
            val pg = Polygon(mapView)
            pg.points = s.points
            pg.fillPaint.color = (s.color and 0x00FFFFFF) or 0x40000000
            pg.outlinePaint.color = s.color
            pg.outlinePaint.strokeWidth = 4f
            pg.title = s.title
            pg.setOnClickListener { _, _, _ -> s.onClick?.invoke(); true }
            extras.add(pg)
        }
        if (polyline != null && polyline.size > 1) {
            val pl = Polyline(mapView)
            pl.setPoints(polyline)
            pl.outlinePaint.color = polylineColor
            pl.outlinePaint.strokeWidth = 9f
            pl.setOnClickListener { _, _, _ -> true }
            extras.add(pl)
        }
        if (me != null) {
            val m = Marker(mapView)
            m.position = me
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            val s = (22 * context.resources.displayMetrics.density).toInt()
            val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = android.graphics.Color.WHITE; c.drawCircle(s / 2f, s / 2f, s / 2f, p)
            p.color = android.graphics.Color.parseColor("#1B4FA3"); c.drawCircle(s / 2f, s / 2f, s / 2f - 5, p)
            m.icon = BitmapDrawable(context.resources, b)
            m.setOnMarkerClickListener { _, _ -> true }
            extras.add(m)
        }
        mapView.overlays.addAll(0, extras.filter { it !is Marker })
        mapView.overlays.addAll(extras.filter { it is Marker })
        mapView.invalidate()
    }

    LaunchedEffect(center?.latitude, center?.longitude) {
        if (center != null && !fit) mapView.controller.animateTo(center, zoom, 600L)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun createMap(context: Context, center: GeoPoint, zoom: Double): MapView =
    MapView(context).apply {
        setTileSource(Tiles.LIGHT)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        minZoomLevel = 5.0
        maxZoomLevel = 19.0
        isTilesScaledToDpi = true
        controller.setZoom(zoom)
        controller.setCenter(center)
    }
