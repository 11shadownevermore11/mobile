package ru.kodnovorossii.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import ru.kodnovorossii.app.ui.theme.LocalDark

/** Ключ Яндекс Карт (JavaScript API). Тот же, что у сайта. Пустая строка — всегда карта OpenStreetMap. */
object MapConfig {
    const val YANDEX_KEY = "16cd06bb-bd28-4d38-9389-4d02acec42a2"
}

fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun hexColor(c: Int) = String.format("#%06X", 0xFFFFFF and c)

/** Мост из JavaScript карты в Kotlin. Вызовы приходят не в главном потоке — переносим их туда. */
private class MapBridge(
    val onReady: () -> Unit,
    val onFail: () -> Unit,
    val onPin: (Int) -> Unit,
    val onCluster: (List<Int>) -> Unit,
    val onShape: (Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun ready() { main.post { onReady() } }
    @JavascriptInterface fun fail() { main.post { onFail() } }
    @JavascriptInterface fun pin(id: Int) { main.post { onPin(id) } }
    @JavascriptInterface fun shape(i: Int) { main.post { onShape(i) } }
    @JavascriptInterface fun cluster(json: String) {
        val arr = JSONArray(json)
        val ids = (0 until arr.length()).map { arr.getInt(it) }
        main.post { onCluster(ids) }
    }
}

/** Яндекс Карты (JavaScript API 2.1) внутри WebView: метки, кластеры с числом объектов, маршрут, контуры, «я здесь».
 *  Если сеть пропала или API не загрузилось — вызывается onFail, и показывается офлайн-карта (osmdroid). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YandexWebMap(
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
    onFail: () -> Unit,
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
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(MapBridge(
                onReady = { ready = true },
                onFail = { curFail() },
                onPin = { id -> curPins.firstOrNull { it.id == id }?.let { curOnPin(it) } },
                onCluster = { ids -> curOnCluster(curPins.filter { it.id in ids }) },
                onShape = { i -> curShapes.getOrNull(i)?.onClick?.invoke() },
            ), "Android")
            loadDataWithBaseURL("https://localhost/", yandexHtml(MapConfig.YANDEX_KEY), "text/html", "utf-8", null)
        }
    }
    DisposableEffect(Unit) { onDispose { web.destroy() } }

    LaunchedEffect(ready, pins, cluster, polyline, shapes, me, dark, center?.latitude, center?.longitude) {
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

private fun yandexHtml(key: String) = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
html,body,#m{margin:0;padding:0;width:100%;height:100%;background:#e8eef4}
.pin{width:30px;height:30px;border-radius:50%;border:3px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center;color:#fff;font:700 13px sans-serif;box-sizing:border-box}
.dot{width:8px;height:8px;border-radius:50%;background:#fff}
.cl{border-radius:50%;border:3px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,.3);display:flex;align-items:center;justify-content:center;color:#fff;font:700 15px sans-serif;box-sizing:border-box}
.me{width:20px;height:20px;border-radius:50%;background:#1B4FA3;border:3px solid #fff;box-shadow:0 0 0 6px rgba(27,79,163,.3);box-sizing:border-box}
body.dark [class*="ground-pane"]{filter:invert(.92) hue-rotate(180deg) saturate(.7) brightness(.95)}
</style>
<script src="https://api-maps.yandex.ru/2.1/?apikey=$key&lang=ru_RU" onerror="Android.fail()"></script>
</head><body><div id="m"></div>
<script>
var map, pinsLayer, extra, Pin, Cl, Me, last = {};
setTimeout(function(){ if (!map) Android.fail(); }, 10000);
if (window.ymaps) ymaps.ready(function () {
  map = new ymaps.Map('m', {center: [48.008, 37.804], zoom: 12, controls: ['zoomControl']}, {suppressMapOpenBlock: true, yandexMapDisablePoiInteractivity: true});
  Pin = ymaps.templateLayoutFactory.createClass('<div style="position:absolute;left:-15px;top:-15px"><div class="pin" style="background:{{ properties.color }}">{{ properties.inner|raw }}</div></div>');
  Me = ymaps.templateLayoutFactory.createClass('<div style="position:absolute;left:-10px;top:-10px"><div class="me"></div></div>');
  Cl = ymaps.templateLayoutFactory.createClass('<div></div>', {
    build: function () {
      Cl.superclass.build.call(this);
      var objs = this.getData().properties.get('geoObjects') || [], counts = {}, keys;
      objs.forEach(function (o) { var c = o.properties.get('color'); counts[c] = (counts[c] || 0) + 1; });
      keys = Object.keys(counts).sort(function (a, b) { return counts[b] - counts[a]; });
      var n = objs.length, s = n < 10 ? 40 : n < 30 ? 48 : 56;
      var ring = keys.length > 1 ? 'box-shadow:0 0 0 4px ' + keys[1] + '55,0 2px 8px rgba(0,0,0,.3);' : '';
      this.getParentElement().innerHTML = '<div style="position:absolute;left:' + (-s / 2) + 'px;top:' + (-s / 2) + 'px"><div class="cl" style="width:' + s + 'px;height:' + s + 'px;background:' + keys[0] + ';' + ring + '">' + n + '</div></div>';
    }
  });
  extra = new ymaps.GeoObjectCollection();
  map.geoObjects.add(extra);
  Android.ready();
});
else Android.fail();

function fitTo(coords) {
  if (!coords.length) return;
  if (coords.length === 1) { map.setCenter(coords[0], 15); return; }
  map.setBounds(ymaps.util.bounds.fromPoints(coords), {checkZoomRange: true, zoomMargin: 50}).then(function () { if (map.getZoom() > 15) map.setZoom(15); });
}

function drawPins(d) {
  if (pinsLayer) map.geoObjects.remove(pinsLayer);
  var marks = d.pins.map(function (p) {
    var pm = new ymaps.Placemark([p.lat, p.lng], {color: p.color, id: p.id, hintContent: p.title,
      inner: p.order != null ? String(p.order) : '<div class="dot"></div>'},
      {iconLayout: Pin, iconShape: {type: 'Circle', coordinates: [0, 0], radius: 18}, hasBalloon: false});
    pm.events.add('click', function () { Android.pin(p.id); });
    return pm;
  });
  if (d.cluster) {
    pinsLayer = new ymaps.Clusterer({clusterIconLayout: Cl, clusterIconShape: {type: 'Circle', coordinates: [0, 0], radius: 28},
      clusterDisableClickZoom: true, clusterOpenBalloonOnClick: false, hasBalloon: false, gridSize: 80});
    pinsLayer.add(marks);
    pinsLayer.events.add('click', function (e) {
      var t = e.get('target');
      if (!t || !t.getGeoObjects) return;
      var objs = t.getGeoObjects(), coords = objs.map(function (o) { return o.geometry.getCoordinates(); });
      var b = ymaps.util.bounds.fromPoints(coords);
      if (map.getZoom() >= 16 || ymaps.coordSystem.geo.getDistance(b[0], b[1]) < 60)
        Android.cluster(JSON.stringify(objs.map(function (o) { return o.properties.get('id'); })));
      else map.setBounds(b, {checkZoomRange: true, zoomMargin: 40});
    });
  } else {
    pinsLayer = new ymaps.GeoObjectCollection();
    marks.forEach(function (m) { pinsLayer.add(m); });
  }
  map.geoObjects.add(pinsLayer);
  if (d.fit && d.pins.length) fitTo(d.pins.map(function (p) { return [p.lat, p.lng]; }));
}

function drawExtra(d) {
  extra.removeAll();
  if (d.line) extra.add(new ymaps.Polyline(d.line, {}, {strokeColor: d.lineColor, strokeWidth: 5, strokeOpacity: .85}));
  (d.shapes || []).forEach(function (s, i) {
    var pg = new ymaps.Polygon([s.coords], {hintContent: s.title}, {fillColor: s.color + '40', strokeColor: s.color, strokeWidth: 2});
    pg.events.add('click', function () { Android.shape(i); });
    extra.add(pg);
  });
  if (d.me) extra.add(new ymaps.Placemark(d.me, {}, {iconLayout: Me, hasBalloon: false}));
}

function render(d) {
  if (!map) return;
  document.body.classList.toggle('dark', !!d.dark);
  var pk = JSON.stringify([d.pins, d.cluster]);
  if (pk !== last.pins) { last.pins = pk; drawPins(d); }
  var ek = JSON.stringify([d.line, d.shapes, d.me]);
  if (ek !== last.ex) { last.ex = ek; drawExtra(d); }
  var ck = JSON.stringify(d.center || null);
  if (d.center && ck !== last.center) {
    var first = last.center === undefined;
    last.center = ck;
    if (!d.fit) map.setCenter(d.center, d.zoom, first ? {} : {duration: 400});
  }
}
</script></body></html>
"""
