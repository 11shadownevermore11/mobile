"""Строит полигоны моря (Азовского) внутри области карты из линий natural=coastline и пишет coastline/water_polygons.shp.
Правило OSM: суша слева от линии берега, вода справа."""
import sys
import osmium
import shapefile
from shapely.geometry import LineString, Polygon, box, Point
from shapely.ops import linemerge, unary_union, polygonize

src, out = sys.argv[1], sys.argv[2]
W, S, E, N = map(float, sys.argv[3].split(','))
bbox = box(W, S, E, N)


class H(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.lines = []

    def way(self, w):
        if w.tags.get('natural') == 'coastline':
            try:
                pts = [(n.lon, n.lat) for n in w.nodes if n.location.valid()]
            except osmium.InvalidLocationError:
                return
            if len(pts) > 1:
                self.lines.append(LineString(pts))


h = H()
h.apply_file(src, locations=True)
print('coastline ways:', len(h.lines))
merged = linemerge(h.lines)
segs = list(merged.geoms) if hasattr(merged, 'geoms') else [merged]
clipped = unary_union([s.intersection(bbox) for s in segs])
pieces = list(polygonize(unary_union([clipped, bbox.boundary])))
print('pieces:', len(pieces))

# Голосование: точка чуть справа от каждого сегмента берега — это вода
votes = [0] * len(pieces)
for s in segs:
    c = list(s.coords)
    for i in range(0, len(c) - 1, max(1, (len(c) - 1) // 50)):
        (x1, y1), (x2, y2) = c[i], c[i + 1]
        dx, dy = x2 - x1, y2 - y1
        L = (dx * dx + dy * dy) ** 0.5
        if L == 0:
            continue
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2
        for sign, v in ((1, 1), (-1, -1)):  # справа (+1) — вода, слева — суша
            px, py = mx + sign * dy / L * 1e-5, my - sign * dx / L * 1e-5
            p = Point(px, py)
            for k, pc in enumerate(pieces):
                if pc.contains(p):
                    votes[k] += v
                    break
sea = [p for p, v in zip(pieces, votes) if v > 0]
print('sea pieces:', len(sea), 'area', sum(p.area for p in sea))

w = shapefile.Writer(out, shapeType=shapefile.POLYGON)
w.field('FID', 'N')
for i, p in enumerate(sea):
    rings = [list(p.exterior.coords)[::-1] if not p.exterior.is_ccw else list(p.exterior.coords)]
    # shapefile: внешнее кольцо по часовой стрелке
    ext = list(p.exterior.coords)
    if p.exterior.is_ccw:
        ext = ext[::-1]
    parts = [ext] + [list(r.coords)[::-1] if not r.is_ccw else list(r.coords) for r in p.interiors]
    w.poly(parts)
    w.record(i)
w.close()
open(out + '.prj', 'w').write('GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]]')
