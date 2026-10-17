# -*- coding: utf-8 -*-
"""离线数据校验：图片必须本地、文本不能残留 HTML。"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
DATA_FILE = ROOT / "195件永禁文物.json"
MUSEUMS_FILE = ROOT / "museums.json"
LOCAL_IMAGES_FILE = HERE / "local_images.json"
ANDROID_ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
ANDROID_DATA_FILE = ANDROID_ASSETS / "195件永禁文物.json"
ANDROID_IMAGE_DIR = ANDROID_ASSETS / "relic_images"

SUSPICIOUS = re.compile(
    r"(?i)<[a-z!/][^>]{0,200}>|&(?:lt|gt|nbsp|quot|#\d+|#x[0-9a-f]+);"
)


def load_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def iter_strings(value, path: str = ""):
    if isinstance(value, dict):
        for key, item in value.items():
            yield from iter_strings(item, f"{path}/{key}")
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from iter_strings(item, f"{path}[{index}]")
    elif isinstance(value, str):
        yield path, value


def image_ok(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < 1024:
        return False
    head = path.read_bytes()[:32]
    return (
        head.startswith(b"\xff\xd8")
        or head.startswith(b"\x89PNG")
        or head.startswith(b"GIF8")
        or (head.startswith(b"RIFF") and head[8:12] == b"WEBP")
    )


def check_text(label: str, value) -> list[str]:
    errors: list[str] = []
    for json_path, text in iter_strings(value):
        if SUSPICIOUS.search(text):
            errors.append(f"{label}{json_path}: HTML 残留 {text[:120]!r}")
    return errors


def main() -> int:
    errors: list[str] = []

    if not DATA_FILE.exists():
        print(f"缺少数据文件: {DATA_FILE}")
        return 1

    data = load_json(DATA_FILE)
    items = data.get("items") or []
    if len(items) != 195:
        errors.append(f"文物数量不是 195：{len(items)}")

    local_count = 0
    missing: list[str] = []
    for item in items:
        image = item.get("image")
        if not image:
            continue
        if image.startswith(("http://", "https://")):
            errors.append(f"{item['id']} {item['name']}: image 仍是外链 {image}")
            continue
        if image.startswith("file:///android_asset/"):
            errors.append(f"{item['id']} {item['name']}: image 仍是 Android 专用路径 {image}")
            continue
        local_path = ROOT / image
        if not image_ok(local_path):
            missing.append(f"{item['id']} {item['name']}: 本地图片缺失或损坏 {image}")
            continue
        local_count += 1
    errors.extend(missing)

    errors.extend(check_text("195件永禁文物.json", data))
    if MUSEUMS_FILE.exists():
        errors.extend(check_text("museums.json", load_json(MUSEUMS_FILE)))

    if LOCAL_IMAGES_FILE.exists():
        local_map = load_json(LOCAL_IMAGES_FILE).get("images") or {}
        for name, image in local_map.items():
            if image.startswith(("http://", "https://")) or not image_ok(ROOT / image):
                errors.append(f"local_images.json 中 {name} 的图片不可用: {image}")
    else:
        errors.append("缺少 tools/local_images.json")

    if ANDROID_DATA_FILE.exists():
        android_data = load_json(ANDROID_DATA_FILE)
        android_items = android_data.get("items") or []
        if len(android_items) != len(items):
            errors.append(f"Android assets 文物数量不一致: {len(android_items)} != {len(items)}")
        for root_item, asset_item in zip(items, android_items):
            if root_item.get("image") != asset_item.get("image"):
                errors.append(f"{root_item['id']}: Android image 与根数据不一致")
                continue
            image = asset_item.get("image")
            if image and not image_ok(ANDROID_IMAGE_DIR / Path(image).name):
                errors.append(f"{root_item['id']} {root_item['name']}: Android assets 图片缺失 {image}")
    else:
        errors.append(f"缺少 Android assets 数据: {ANDROID_DATA_FILE}")

    for html_name in ("195件永禁文物·博物馆探索.html", "杭州国宝地图.html"):
        html_path = ROOT / html_name
        if not html_path.exists():
            continue
        text = html_path.read_text(encoding="utf-8")
        if '"image":"http' in text or '"image":"file:///android_asset' in text:
            errors.append(f"{html_name}: 内嵌数据里仍有外链或 Android 专用图片路径")

    wiki_import = HERE / "wiki_import.json"
    if wiki_import.exists():
        for name, record in (load_json(wiki_import).get("items") or {}).items():
            image = (record or {}).get("image")
            if image and image.startswith(("http://", "https://")):
                errors.append(f"wiki_import.json {name}: 仍有外链图片 {image}")

    commons_manual = HERE / "commons_manual.json"
    if commons_manual.exists():
        for name, record in load_json(commons_manual).items():
            image = (record or {}).get("image")
            if image and image.startswith(("http://", "https://")):
                errors.append(f"commons_manual.json {name}: 仍有外链图片 {image}")

    print(f"本地图片 {local_count} 件；无图 {len(items) - local_count} 件")
    if errors:
        print(f"\n校验失败，共 {len(errors)} 项：")
        for error in errors[:40]:
            print(f"  - {error}")
        if len(errors) > 40:
            print(f"  ... 还有 {len(errors) - 40} 项")
        return 1
    print("校验通过：图片全部本地化，文本无 HTML 残留。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
