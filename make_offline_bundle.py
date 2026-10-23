"""Встраивает данные платформы и фотографии в приложение (app/src/main/assets), чтобы APK работал сразу —
даже без сервера и без интернета: каталог, карточки с фото, поиск, маршруты, афиша, «Важно знать».

Запуск (из папки mobile, после добавления новых фото или мест):   python make_offline_bundle.py
Затем пересоберите APK. Когда сервер доступен, приложение само обновит данные с него.
"""
import json
import os
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = Path(__file__).resolve().parent / "app/src/main/assets"
sys.path.insert(0, str(ROOT / "backend"))
os.environ.setdefault("DATABASE_URL", "sqlite:///" + os.path.join(tempfile.mkdtemp(), "bundle.db"))
os.environ.setdefault("GEOCODE_OFF", "1")

from fastapi.testclient import TestClient  # noqa: E402
from PIL import Image, ImageOps  # noqa: E402

from app.main import app  # noqa: E402


def src_for(url: str) -> Path | None:
    path = url.split("?")[0]
    if path.startswith("/photos/"):
        return ROOT / path.lstrip("/")
    if path.startswith("/img/"):
        return ROOT / "web/public" / path.lstrip("/")
    return None


def main():
    with TestClient(app) as c:
        bundle = c.get("/api/sync/bundle").json()
    urls = set()

    def walk(x):
        if isinstance(x, dict):
            for v in x.values():
                walk(v)
        elif isinstance(x, list):
            for v in x:
                walk(v)
        elif isinstance(x, str) and (x.startswith("/photos/") or x.startswith("/img/")):
            urls.add(x)
    walk(bundle)
    urls |= {"/img/hero-main.jpg", "/img/night-fountain.jpg", "/img/hero-panorama.jpg", "/img/arches.jpg", "/img/circus.jpg"}

    site = ASSETS / "site"
    shutil.rmtree(site, ignore_errors=True)
    index, total = [], 0
    for u in sorted(urls):
        src = src_for(u)
        if not src or not src.exists():
            continue
        rel = u.split("?")[0]
        dst = site / rel.lstrip("/")
        dst.parent.mkdir(parents=True, exist_ok=True)
        im = ImageOps.exif_transpose(Image.open(src)).convert("RGB")
        if im.width > 1280:
            im = im.resize((1280, round(im.height * 1280 / im.width)), Image.LANCZOS)
        im.save(dst, "JPEG", quality=80, optimize=True, progressive=True)
        total += dst.stat().st_size
        index.append(rel)
    (site / "index.txt").write_text("\n".join(index), encoding="utf-8")
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "bundle.json").write_text(json.dumps(bundle, ensure_ascii=False), encoding="utf-8")
    print(f"Встроено: {len(bundle.get('places', []))} мест, {len(bundle.get('events', []))} событий, "
          f"{len(index)} фото ({total / 1e6:.1f} МБ) → {ASSETS}")


if __name__ == "__main__":
    main()
