# -*- coding: utf-8 -*-
"""把写手产出的 `.drafts/details/*.json` 合并进主数据，并跑同一套校验。

写手只碰草稿文件，主数据和 assets 由主 agent 统一合并，
这样多个 agent 并行时不会互相覆盖同一个 JSON。

用法：
    python3 .drafts/merge_details.py --dry-run FILE...   # 只校验，不落盘
    python3 .drafts/merge_details.py FILE...             # 校验通过后合并 + 同步 assets
    python3 .drafts/merge_details.py --all               # 合并 .drafts/details/ 下全部
"""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

from validate_relic_detail import check_detail  # noqa: E402

DRAFT_DIR = ROOT / ".drafts" / "details"
DATA_FILE = ROOT / "195件永禁文物.json"


def load_drafts(paths: list[Path]) -> dict[int, str]:
    merged: dict[int, str] = {}
    for path in paths:
        if not path.exists():
            print(f"缺少草稿文件: {path}")
            continue
        payload = json.loads(path.read_text(encoding="utf-8"))
        for key, value in payload.items():
            try:
                merged[int(key)] = value
            except (TypeError, ValueError):
                print(f"{path.name}: 非法 id {key!r}，跳过")
    return merged


def main(argv: list[str]) -> int:
    dry = "--dry-run" in argv
    args = [a for a in argv if not a.startswith("--")]
    paths = sorted(DRAFT_DIR.glob("*.json")) if "--all" in argv or not args else [Path(a) for a in args]

    data = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    items = data.get("items") or []
    index = {item["id"]: item for item in items}
    drafts = load_drafts(paths)
    if not drafts:
        print("没有可合并的草稿。")
        return 1

    errors: list[str] = []
    for item_id, detail in sorted(drafts.items()):
        item = index.get(item_id)
        if item is None:
            errors.append(f"{item_id}: 数据里没有这件文物")
            continue
        errors.extend(check_detail({"id": item_id, "name": item.get("name"), "detail": detail}))

    print(f"检查 {len(drafts)} 件，问题 {len(errors)} 项。")
    for error in errors:
        print(f"  - {error}")
    if errors:
        return 1
    if dry:
        print("--dry-run：全部通过，未落盘。")
        return 0

    for item_id, detail in drafts.items():
        index[item_id]["detail"] = detail.strip()
    with DATA_FILE.open("w", encoding="utf-8") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=1)
        handle.write("\n")

    sync = subprocess.run([sys.executable, str(ROOT / "tools" / "sync_assets.py")], cwd=ROOT)
    if sync.returncode != 0:
        return sync.returncode
    print(f"已合并 {len(drafts)} 件。")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
