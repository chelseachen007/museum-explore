# -*- coding: utf-8 -*-
"""把根目录的 `195件永禁文物.json` 同步到 Android assets。

两侧必须逐字一致，`validate_data.py` 和 `validate_relic_detail.py` 都会比对。
手改别处再复制过来容易漏字段，所以固定走这个脚本：

    python3 tools/sync_assets.py
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
SOURCE = ROOT / "195件永禁文物.json"
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
DATA_TARGET = ASSETS / "195件永禁文物.json"
IMAGE_SOURCE = ROOT / "relic_images"
IMAGE_TARGET = ASSETS / "relic_images"


def main() -> int:
    if not SOURCE.exists():
        print(f"缺少源数据: {SOURCE}")
        return 1

    with SOURCE.open(encoding="utf-8") as handle:
        data = json.load(handle)

    ASSETS.mkdir(parents=True, exist_ok=True)
    with DATA_TARGET.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=1)
        handle.write("\n")

    copied = 0
    if IMAGE_SOURCE.is_dir():
        IMAGE_TARGET.mkdir(parents=True, exist_ok=True)
        for image in sorted(IMAGE_SOURCE.iterdir()):
            if image.is_file():
                shutil.copy2(image, IMAGE_TARGET / image.name)
                copied += 1

    written = sum(1 for item in data.get("items") or [] if (item.get("detail") or "").strip())
    print(f"已同步 195件永禁文物.json（深度正文 {written} 件）与 {copied} 张图片到 assets")
    return 0


if __name__ == "__main__":
    sys.exit(main())
