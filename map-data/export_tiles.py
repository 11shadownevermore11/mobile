"""Выгружает тайлы из .mbtiles в папку tiles/{z}/{x}/{y}.pbf (схема XYZ, без gzip) для assets приложения."""
import gzip
import os
import shutil
import sqlite3
import sys

src, dst = sys.argv[1], sys.argv[2]
shutil.rmtree(dst, ignore_errors=True)
db = sqlite3.connect(src)
n = total = 0
for z, x, y_tms, data in db.execute('select zoom_level, tile_column, tile_row, tile_data from tiles'):
    if data[:2] == b'\x1f\x8b':
        data = gzip.decompress(data)
    y = (1 << z) - 1 - y_tms
    d = os.path.join(dst, str(z), str(x))
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, f'{y}.pbf'), 'wb') as f:
        f.write(data)
    n += 1
    total += len(data)
print(f'{n} тайлов, {total / 1e6:.1f} МБ → {dst}')
