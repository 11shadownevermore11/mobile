# Встроенная карта ДНР

Карта приложения работает без интернета и без API Яндекс Карт: всё лежит в `app/src/main/assets/map`.

| Файл | Что это |
|---|---|
| `index.html` | страница карты: стиль (светлый/тёмный), метки, кластеры, маршрут, контуры, «я здесь», мост в Kotlin |
| `maplibre-gl.js/.css` | движок MapLibre GL JS 4.7.1 (BSD-3, см. `maplibre-LICENSE.txt`) |
| `tiles/{z}/{x}/{y}.pbf` | векторные тайлы региона (схема OpenMapTiles), масштабы 0–14, область 46.80–48.40 с.ш., 37.15–39.05 в.д. |
| `fonts/` | шрифты подписей Noto Sans (латиница, кириллица, знаки) |

Данные — © участники OpenStreetMap, лицензия ODbL (подпись на карте обязательна, она уже есть).
В Kotlin карта — `ui/components/AppMap.kt`: WebView открывает `https://appassets.androidplatform.net/assets/map/index.html`,
запросы к этому адресу отдаются из assets, любые другие адреса блокируются.

## Как обновить карту

1. Скачать данные OSM по области (Overpass API, запрос в `overpass_query.txt`; удобно 4 частями по 0.8° × 0.95°),
   сохранить как `part0.osm.gz … part3.osm.gz`.
2. Склеить: `python merge.py dnr.osm.pbf part*.osm.gz` (нужен `pip install osmium`).
3. Море из линии берега: `python make_sea.py dnr.osm.pbf coastline/water_polygons 37.15,46.80,39.05,48.40` (`pip install shapely pyshp`).
4. Тайлы: `tilemaker --input dnr.osm.pbf --output dnr.mbtiles --config config.json --process process.lua --bbox 37.15,46.80,39.05,48.40`
   (tilemaker 3.0, https://github.com/systemed/tilemaker).
5. В приложение: `python export_tiles.py dnr.mbtiles ../app/src/main/assets/map/tiles` и пересобрать APK.
