# -*- coding: utf-8 -*-
"""把文物图片下载到本地，并同步 Android assets。

最终目标：

- 根目录 ``relic_images/<id>.<ext>`` 是唯一的本地图片源。
- ``195件永禁文物.json`` 里的 ``image`` 只写相对路径，不再出现外链。
- ``tools/local_images.json`` 记录名称到本地图片的映射，供 build.py 在重建时兜底。
- ``android/app/src/main/assets`` 与根目录数据保持一致。

脚本可重复执行：本地已存在且可识别的图片不会重复下载。
"""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import re
import shutil
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
DATA_FILE = ROOT / "195件永禁文物.json"
MUSEUMS_FILE = ROOT / "museums.json"
LOCAL_IMAGES_FILE = HERE / "local_images.json"
WIKI_IMPORT_FILE = HERE / "wiki_import.json"
COMMONS_MANUAL_FILE = HERE / "commons_manual.json"
WD_CACHE_FILE = HERE / "wd_cache.json"

ROOT_IMAGE_DIR = ROOT / "relic_images"
ANDROID_ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
ANDROID_IMAGE_DIR = ANDROID_ASSETS / "relic_images"
ANDROID_DATA_FILE = ANDROID_ASSETS / "195件永禁文物.json"
ANDROID_MUSEUMS_FILE = ANDROID_ASSETS / "museums.json"

USER_AGENT = "MuseumExplorer/0.5.1 (local offline museum data; Wikimedia Commons)"
THUMB_WIDTH = 960
CONTENT_EXT = {
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/gif": ".gif",
    "image/avif": ".avif",
    "image/svg+xml": ".svg",
}


def load_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def detect_indent(path: Path) -> int:
    if path.exists():
        text = path.read_text(encoding="utf-8")
        match = re.search(r"\n( +)\S", text)
        if match:
            return len(match.group(1))
    return 1


def save_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    indent = detect_indent(path)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=indent)
        handle.write("\n")


def is_image_file(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < 1024:
        return False
    try:
        head = path.read_bytes()[:32]
    except OSError:
        return False
    if head.startswith(b"\xff\xd8") or head.startswith(b"\x89PNG") or head.startswith(b"GIF8"):
        return True
    if head.startswith(b"RIFF") and head[8:12] == b"WEBP":
        return True
    if path.suffix.lower() == ".svg" and (b"<svg" in head or b"<?xml" in head):
        return True
    return False


def sniff_ext(data: bytes) -> str:
    if data.startswith(b"\xff\xd8"):
        return ".jpg"
    if data.startswith(b"\x89PNG"):
        return ".png"
    if data.startswith(b"GIF8"):
        return ".gif"
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        return ".webp"
    if b"<svg" in data[:512] or data[:64].lstrip().startswith(b"<?xml"):
        return ".svg"
    return ".jpg"


def wikimedia_filename(url: str) -> str | None:
    parsed = urllib.parse.urlsplit(url)
    path = urllib.parse.unquote(parsed.path)
    if "/Special:FilePath/" in path:
        name = path.split("/Special:FilePath/", 1)[1]
    elif "/wikipedia/" in path:
        name = path.rsplit("/", 1)[-1]
    else:
        return None
    name = re.sub(r"^\d+px-", "", name)
    return name or None


def wikimedia_thumb_url(filename: str, width: int = THUMB_WIDTH) -> str:
    """按 Commons 官方规则拼出 upload.wikimedia.org 缩略图地址。

    Special:FilePath 会经过 commons.wikimedia.org 的限流；直接访问
    upload.wikimedia.org 的缩略图路径更稳定。
    """
    safe_name = filename.replace(" ", "_")
    digest = hashlib.md5(safe_name.encode("utf-8")).hexdigest()
    encoded = urllib.parse.quote(safe_name, safe="")
    return (
        f"https://upload.wikimedia.org/wikipedia/commons/thumb/"
        f"{digest[0]}/{digest[:2]}/{encoded}/{width}px-{encoded}"
    )


def commons_thumb_url(filename: str, width: int = THUMB_WIDTH) -> str:
    """Commons 的 thumb.php 通道；限流时比 Special:FilePath 更稳。"""
    return (
        "https://commons.wikimedia.org/w/thumb.php?"
        + urllib.parse.urlencode({"f": filename.replace(" ", "_"), "w": width})
    )


def download_url(url: str) -> tuple[Path, bytes]:
    last_error: Exception | None = None
    for attempt in range(1, 6):
        try:
            request = urllib.request.Request(
                url,
                headers={"User-Agent": USER_AGENT, "Accept": "image/*,*/*;q=0.8"},
            )
            with urllib.request.urlopen(request, timeout=90) as response:
                content_type = (response.headers.get("Content-Type") or "").split(";")[0].strip().lower()
                data = response.read()
            if not data or len(data) < 1024:
                raise ValueError("下载文件过小")
            if content_type and not content_type.startswith("image/"):
                raise ValueError(f"返回的不是图片: {content_type}")
            return Path(url), data
        except urllib.error.HTTPError as exc:
            last_error = exc
            if exc.code == 429:
                retry_after = exc.headers.get("Retry-After") if exc.headers else None
                try:
                    wait = max(15.0, min(30.0, float(retry_after)))
                except (TypeError, ValueError):
                    wait = min(30.0, 5.0 * attempt)
                print(f"    Wikimedia 限流，等待 {wait:.0f}s 后重试（{attempt}/5）", flush=True)
                time.sleep(wait)
            elif attempt < 5:
                time.sleep(3.0 * attempt)
        except Exception as exc:  # noqa: BLE001 - 网络请求需要统一重试
            last_error = exc
            if attempt < 5:
                time.sleep(1.5 * attempt)
    raise RuntimeError(f"{url}: {last_error}")


def best_existing(entry_id: int) -> Path | None:
    candidates = []
    for directory in (ROOT_IMAGE_DIR, ANDROID_IMAGE_DIR):
        if not directory.is_dir():
            continue
        for path in sorted(directory.glob(f"{entry_id}.*")):
            if is_image_file(path):
                candidates.append(path)
    if not candidates:
        return None
    # 优先根目录；同目录内优先 jpg/png，避免误选临时文件。
    candidates.sort(key=lambda p: (p.parent != ROOT_IMAGE_DIR, p.suffix.lower() not in (".jpg", ".jpeg", ".png")))
    return candidates[0]


def ensure_root_copy(source: Path) -> Path:
    """保证图片位于根目录 relic_images/，Android 目录只作为副本。"""
    ROOT_IMAGE_DIR.mkdir(parents=True, exist_ok=True)
    target = ROOT_IMAGE_DIR / source.name
    if source.resolve() != target.resolve():
        shutil.copy2(source, target)
    return target


def localize_one(item: dict, force: bool = False) -> tuple[int, str | None, str | None]:
    """返回 (id, 相对路径或 None, 错误信息或 None)。"""
    entry_id = int(item["id"])
    raw_image = (item.get("image") or "").strip()

    if not raw_image:
        existing = best_existing(entry_id)
        if existing is not None:
            return entry_id, f"relic_images/{existing.name}", None
        return entry_id, None, None

    existing = best_existing(entry_id) if not force else None
    if existing is not None:
        return entry_id, f"relic_images/{ensure_root_copy(existing).name}", None

    if raw_image.startswith("file:///android_asset/relic_images/"):
        source = ANDROID_IMAGE_DIR / raw_image.rsplit("/", 1)[-1]
        if not is_image_file(source):
            return entry_id, None, f"Android 资源不存在或不可读: {source}"
        return entry_id, f"relic_images/{ensure_root_copy(source).name}", None

    if raw_image.startswith("relic_images/"):
        source = ROOT / raw_image
        if is_image_file(source):
            return entry_id, raw_image, None
        source = ANDROID_IMAGE_DIR / raw_image.rsplit("/", 1)[-1]
        if is_image_file(source):
            return entry_id, f"relic_images/{ensure_root_copy(source).name}", None
        return entry_id, None, f"本地图片缺失: {raw_image}"

    if not raw_image.startswith(("http://", "https://")):
        return entry_id, None, f"无法识别的 image 值: {raw_image}"

    filename = wikimedia_filename(raw_image)
    if not filename:
        return entry_id, None, f"无法解析 Wikimedia 文件名: {raw_image}"

    target_url = commons_thumb_url(filename)
    fallback_url = wikimedia_thumb_url(filename)
    try:
        _, data = download_url(target_url)
    except Exception as first_error:  # noqa: BLE001
        try:
            _, data = download_url(fallback_url)
        except Exception as second_error:  # noqa: BLE001
            return entry_id, None, f"{first_error}; 备用地址失败: {second_error}"

    ext = sniff_ext(data)
    ROOT_IMAGE_DIR.mkdir(parents=True, exist_ok=True)
    target = ROOT_IMAGE_DIR / f"{entry_id}{ext}"
    target.write_bytes(data)
    if not is_image_file(target):
        return entry_id, None, f"下载内容无法识别为图片: {target}"
    return entry_id, f"relic_images/{target.name}", None


def sync_android(root_data: dict) -> None:
    ANDROID_ASSETS.mkdir(parents=True, exist_ok=True)
    ANDROID_IMAGE_DIR.mkdir(parents=True, exist_ok=True)

    for path in sorted(ROOT_IMAGE_DIR.glob("*")):
        if not path.is_file():
            continue
        target = ANDROID_IMAGE_DIR / path.name
        if not target.exists() or target.stat().st_size != path.stat().st_size or target.stat().st_mtime_ns < path.stat().st_mtime_ns:
            shutil.copy2(path, target)

    save_json(ANDROID_DATA_FILE, root_data)
    if MUSEUMS_FILE.exists():
        save_json(ANDROID_MUSEUMS_FILE, load_json(MUSEUMS_FILE))


def update_source_caches(local_by_name: dict[str, str]) -> int:
    updated = 0

    if WIKI_IMPORT_FILE.exists():
        data = load_json(WIKI_IMPORT_FILE)
        for name, record in (data.get("items") or {}).items():
            local = local_by_name.get(name)
            if local and record.get("image") != local:
                record["image"] = local
                updated += 1
        save_json(WIKI_IMPORT_FILE, data)

    if COMMONS_MANUAL_FILE.exists():
        data = load_json(COMMONS_MANUAL_FILE)
        for name, record in data.items():
            local = local_by_name.get(name)
            if local and isinstance(record, dict) and record.get("image") != local:
                record["image"] = local
                updated += 1
        save_json(COMMONS_MANUAL_FILE, data)

    if WD_CACHE_FILE.exists():
        data = load_json(WD_CACHE_FILE)
        for name, record in data.items():
            if not isinstance(record, dict):
                continue
            info = record.get("info")
            local = local_by_name.get(name)
            if local and info and info.get("thumb") != local:
                info["thumb"] = local
                updated += 1
        save_json(WD_CACHE_FILE, data)

    return updated


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="下载文物图片并同步 Android assets")
    parser.add_argument("--force", action="store_true", help="忽略已有本地图片，重新下载外链")
    parser.add_argument("--workers", type=int, default=1, help="并发下载数；默认 1，避免 Wikimedia 限流")
    args = parser.parse_args(argv)

    if not DATA_FILE.exists():
        print(f"数据文件不存在: {DATA_FILE}")
        return 1

    data = load_json(DATA_FILE)
    items = data.get("items") or []
    external = [(item, (item.get("image") or "").strip()) for item in items
                if (item.get("image") or "").startswith(("http://", "https://"))]

    print(f"文物 {len(items)} 件；待下载外链图片 {len(external)} 张；本地目标 {ROOT_IMAGE_DIR}")
    results: dict[int, str | None] = {}
    errors: list[tuple[int, str, str]] = []

    # 已在本地的先处理，避免占用网络并发。
    pending = []
    for item, raw_image in external:
        existing = best_existing(int(item["id"])) if not args.force else None
        if existing is not None:
            results[int(item["id"])] = f"relic_images/{ensure_root_copy(existing).name}"
        else:
            pending.append(item)
    for item in items:
        entry_id = int(item["id"])
        if entry_id in results:
            continue
        raw_image = (item.get("image") or "").strip()
        if raw_image.startswith("file:///android_asset/relic_images/") or raw_image.startswith("relic_images/"):
            entry_id, local, error = localize_one(item, force=args.force)
            if error:
                errors.append((entry_id, item.get("name", ""), error))
            else:
                results[entry_id] = local

    if pending:
        with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, args.workers)) as executor:
            futures = {executor.submit(localize_one, item, args.force): item for item in pending}
            done = 0
            for future in concurrent.futures.as_completed(futures):
                item = futures[future]
                entry_id, local, error = future.result()
                done += 1
                if local:
                    results[entry_id] = local
                    print(f"  [{done}/{len(pending)}] ✓ {item['name']}")
                else:
                    errors.append((entry_id, item.get("name", ""), error or "未知错误"))
                    print(f"  [{done}/{len(pending)}] ✗ {item['name']}: {error}")
                if done < len(pending):
                    time.sleep(1.0)

    local_by_name: dict[str, str] = {}
    for item in items:
        entry_id = int(item["id"])
        local = results.get(entry_id)
        raw_image = (item.get("image") or "").strip()
        if not local and raw_image.startswith("relic_images/"):
            local = raw_image
        if not local:
            existing = best_existing(entry_id)
            if existing is not None:
                local = f"relic_images/{ensure_root_copy(existing).name}"
        if local:
            item["image"] = local
            local_by_name[item["name"]] = local
        elif raw_image.startswith(("http://", "https://")):
            # 下载失败时保留原值，方便定位；校验脚本会明确报出来。
            pass
        else:
            item["image"] = None

    save_json(DATA_FILE, data)
    save_json(LOCAL_IMAGES_FILE, {"images": local_by_name})
    updated = update_source_caches(local_by_name)
    sync_android(data)

    print(f"本地图片 {len(results)} 张，名称映射 {len(local_by_name)} 条，同步源缓存 {updated} 处")
    if errors:
        print("\n失败条目：")
        for entry_id, name, error in errors:
            print(f"  {entry_id} {name}: {error}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
