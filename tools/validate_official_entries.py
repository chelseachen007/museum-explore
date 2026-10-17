#!/usr/bin/env python3
"""Validate the official museum entry ledger and museum location data."""

from __future__ import annotations

import json
import re
import sys
from datetime import date
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parent.parent
MUSEUMS_FILE = ROOT / "museums.json"
ASSETS_MUSEUMS_FILE = ROOT / "android" / "app" / "src" / "main" / "assets" / "museums.json"
ENTRIES_FILE = ROOT / "android" / "app" / "src" / "main" / "assets" / "official_entries.json"

CHECK_DATE = date(2026, 10, 17)
VALID_STATUSES = {"verified", "stale", "unavailable"}
DATE_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2}$")
URL_FIELDS = ("landingUrl", "sourceUrl", "officialUrl", "reservationUrl", "guideUrl", "exhibitionUrl")
NON_PUBLIC_INSTITUTIONS = {
    "中国社会科学院考古研究所",
    "清华大学",
    "南京大学",
    "湖南大学",
    "安徽省文物考古研究所",
    "山东省文物考古研究院",
    "辽宁省文物考古研究院",
    "新疆维吾尔自治区文物考古研究所",
}
SHORT_OR_SEARCH_PATTERNS = (
    "mp.weixin.qq.com/s?",
    "t.cn",
    "dwz",
    "sourl",
    "bit.ly",
    "google.com/search",
    "baidu.com/s",
)


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def is_https_url(value: str) -> bool:
    parsed = urlparse(value)
    return parsed.scheme == "https" and bool(parsed.netloc)


def main() -> int:
    errors: list[str] = []
    warnings: list[str] = []

    for path in (MUSEUMS_FILE, ASSETS_MUSEUMS_FILE, ENTRIES_FILE):
        if not path.is_file():
            errors.append(f"缺少文件：{path.relative_to(ROOT)}")
    if errors:
        print_report(errors, warnings)
        return 1

    museums = load_json(MUSEUMS_FILE).get("museums", [])
    assets_museums = load_json(ASSETS_MUSEUMS_FILE).get("museums", [])
    entries = load_json(ENTRIES_FILE).get("museums", [])

    museum_names = [item.get("name") for item in museums]
    if len(museum_names) != 59:
        errors.append(f"museums.json 机构数量为 {len(museum_names)}，预期 59")
    if len(set(museum_names)) != len(museum_names):
        errors.append("museums.json 存在重复机构名称")

    # Compare the source data itself, not only the parsed object, so formatting changes
    # cannot hide a sync mistake between the root and Android assets.
    if MUSEUMS_FILE.read_bytes() != ASSETS_MUSEUMS_FILE.read_bytes():
        errors.append("根目录与 Android assets 的 museums.json 字节不一致")

    for item in museums:
        name = item.get("name") or "（未命名）"
        address = item.get("address")
        if address is None and item.get("openToPublic") is True:
            errors.append(f"museums.json {name}：address 为空但 openToPublic=true")
        if address is not None:
            if not isinstance(address, str) or not address.strip():
                errors.append(f"museums.json {name}：address 不是有效文本")
            if not item.get("verifiedAt"):
                errors.append(f"museums.json {name}：有 address 但缺 verifiedAt")
            sources = item.get("sources")
            if not isinstance(sources, list) or not sources:
                errors.append(f"museums.json {name}：有 address 但 sources 为空")
            else:
                for source in sources:
                    if not isinstance(source, str) or not is_https_url(source):
                        errors.append(f"museums.json {name}：来源不是 https 绝对地址 {source!r}")

    entry_names: list[str] = []
    museum_name_set = set(museum_names)
    for item in entries:
        name = item.get("museum")
        if not name:
            errors.append("official_entries.json：存在缺少 museum 的记录")
            continue
        entry_names.append(name)
        if name not in museum_name_set:
            errors.append(f"official_entries.json：{name} 不在 museums.json 中")

        status = item.get("status")
        if status not in VALID_STATUSES:
            errors.append(f"official_entries.json {name}：status 非法 {status!r}")
        if name in NON_PUBLIC_INSTITUTIONS:
            if status != "unavailable":
                errors.append(f"official_entries.json {name}：非博物馆机构 status 必须为 unavailable")
            for field in ("appId", "landingUrl", "reservationUrl", "guideUrl", "exhibitionUrl"):
                if item.get(field):
                    errors.append(f"official_entries.json {name}：非博物馆机构不应填写 {field}")

        verified_at = item.get("verifiedAt")
        if not isinstance(verified_at, str) or not DATE_PATTERN.fullmatch(verified_at):
            errors.append(f"official_entries.json {name}：verifiedAt 不是 YYYY-MM-DD")
        else:
            try:
                verified_date = date.fromisoformat(verified_at)
                age_days = (CHECK_DATE - verified_date).days
                if age_days > 180:
                    warnings.append(f"official_entries.json {name}：核验已过期 {age_days} 天")
                if age_days < 0:
                    errors.append(f"official_entries.json {name}：verifiedAt 晚于校验日期")
            except ValueError:
                errors.append(f"official_entries.json {name}：verifiedAt 不是有效日期")

        if item.get("landingUrl") and not item.get("appId"):
            errors.append(f"official_entries.json {name}：有 landingUrl 但缺 appId")
        if item.get("appId") and not item.get("landingUrl"):
            warnings.append(f"official_entries.json {name}：有 appId 但无 landingUrl")

        for field in URL_FIELDS:
            value = item.get(field)
            if not value:
                continue
            if not isinstance(value, str) or not is_https_url(value):
                errors.append(f"official_entries.json {name}：{field} 不是 https 绝对地址")
                continue
            lowered = value.lower()
            for pattern in SHORT_OR_SEARCH_PATTERNS:
                if pattern in lowered:
                    errors.append(f"official_entries.json {name}：{field} 含短链或搜索页 {value}")
                    break

        scheme = item.get("schemeUrl")
        if scheme and not scheme.startswith("weixin://"):
            errors.append(f"official_entries.json {name}：schemeUrl 不是已有微信协议")

    duplicates = sorted({name for name in entry_names if entry_names.count(name) > 1})
    if duplicates:
        errors.append(f"official_entries.json：重复 museum {', '.join(duplicates)}")

    missing = sorted(museum_name_set - set(entry_names))
    extra = sorted(set(entry_names) - museum_name_set)
    if missing:
        errors.append(f"official_entries.json：缺少 {len(missing)} 家：{', '.join(missing)}")
    if extra:
        errors.append(f"official_entries.json：多出 {len(extra)} 家：{', '.join(extra)}")
    if len(entries) != 59:
        errors.append(f"official_entries.json：记录数为 {len(entries)}，预期 59")

    # Integrity checks should not fail merely because Android assets use JSON formatting
    # different from the root; this catches semantic drift in addition to the byte check.
    if load_json(MUSEUMS_FILE) != {"meta": load_json(ASSETS_MUSEUMS_FILE).get("meta"), "museums": assets_museums}:
        errors.append("根目录与 Android assets 的 museums.json 内容不一致")

    entry_verified = load_json(ENTRIES_FILE).get("meta", {}).get("verifiedAt")
    if entry_verified != CHECK_DATE.isoformat():
        warnings.append(f"official_entries.json meta.verifiedAt 为 {entry_verified!r}")

    print_report(errors, warnings)
    return 1 if errors else 0


def print_report(errors: list[str], warnings: list[str]) -> None:
    print("官方入口数据校验报告")
    print(f"ERROR：{len(errors)}")
    for message in errors:
        print(f"  [ERROR] {message}")
    print(f"WARN：{len(warnings)}")
    for message in warnings:
        print(f"  [WARN] {message}")
    if not errors:
        print("结论：通过（无 ERROR）")
    else:
        print("结论：失败，请修复上述 ERROR")


if __name__ == "__main__":
    sys.exit(main())
