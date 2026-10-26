# -*- coding: utf-8 -*-
"""国宝详情正文（`detail` 字段）的写作规范校验。

只校验「机器能明确判定的规则」，不评价文笔。规范见 `AGENTS.md` 的
「国宝详情正文规范」一节——改规则先改那里，再改这里，两边必须对得上。

用法：
    python3 tools/validate_relic_detail.py            # 只校验已写的
    python3 tools/validate_relic_detail.py --stats    # 额外打印覆盖率
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
DATA_FILE = ROOT / "195件永禁文物.json"
AGENT_DATA_FILE = ROOT / "android" / "app" / "src" / "main" / "assets" / "195件永禁文物.json"

# 正文是自由结构的 markdown 长文：写哪几节、节标题叫什么，由文物自己的内容决定。
# 这里不强制小节名称，只兜住「格式能被 App 正确渲染」和「内容不是空壳」。
#
# 曾经有过「四段式固定小标题」的硬规则，后来废掉了：很多文物的内容撑不满四段，
# 硬凑出来的第二段就是同一句话说两遍。现在只校验格式、长度和事实表述。
MIN_SECTIONS = 1
MIN_CHARS = 200
MAX_CHARS = 2400
MAX_HEADING_LEVEL = 3

HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
BULLET = re.compile(r"^([-*+]|\d+[.)])\s+\S")
# 允许的轻量标记：**粗体** / *斜体*。其余符号一律当错，
# 因为 App 端只实现了这个子集，多写的语法会被当普通字符原样吐出来。
ALLOWED_INLINE = re.compile(r"\*\*(.+?)\*\*|\*(.+?)\*")
FORBIDDEN_MARKUP = re.compile(
    r"(?<!`)`[^`]+`"                      # 行内代码
    r"|^#{5,}\s"                          # 五级及以上标题
    r"|^\s*>\s"                           # 引用
    r"|!\[[^\]]*\]\("                     # 图片
    r"|\[[^\]]+\]\([^)]+\)"               # 链接
    r"|^\s*\|.*\|\s*$"                    # 表格行
    r"|^\s*(?:---+|\*\*\*+)\s*$"          # 分割线
    r"|~~.+?~~",                          # 删除线
    re.MULTILINE,
)
HTML_LIKE = re.compile(r"(?is)</?[a-z][^>\n]{0,200}>")
# 事实性内容容易写错的地方：绝对年份必须带公元。写「公元前 221 年」比「221 年」清楚得多。
# 只查四位数且不在 1900–2099 之间的（那是现代年份，如 1939 年、2002 年，不会被误读为古代纪年）。
BARE_YEAR = re.compile(r"(?<![\d前公元])(?<!\d)([1-9]\d{3})(?!\d)\s*年")
# 只要正文提到了「不能出国」这件事，就得把机制讲清楚，而不是只感叹「极其珍贵」。
# 没提这件事的文物不检查——不是每件都值得单独写这段。
EXIT_TOPIC = re.compile(r"不能出国|禁止出境|禁止出国|不得出境")
EXIT_MUST_MENTION = ("出境", "禁止")


def load_items(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle).get("items") or []


def strip_inline(text: str) -> str:
    """去掉行内标记后计字数，避免靠 `**` 刷长度。"""
    stripped = ALLOWED_INLINE.sub(lambda m: m.group(1) or m.group(2), text)
    return re.sub(r"\s+", "", stripped)


def check_detail(item: dict) -> list[str]:
    errors: list[str] = []
    label = f"{item.get('id')} {item.get('name')}"
    raw = item.get("detail")

    if raw is None:
        return errors                      # 没写不算错，覆盖率另算
    if not isinstance(raw, str):
        return [f"{label}: detail 必须是字符串"]

    text = raw.strip()
    if not text:
        return []                          # 空串等价于「没写」，读取端当没写处理

    if HTML_LIKE.search(text):
        errors.append(f"{label}: 混入了 HTML 标签")
    bad_markup = FORBIDDEN_MARKUP.search(text)
    if bad_markup:
        errors.append(f"{label}: 用了 App 端不支持的标记 {bad_markup.group(0)[:40]!r}")
    if "\r" in text:
        errors.append(f"{label}: 含 CR 换行，统一用 \\n")

    headings = []
    for line in text.split("\n"):
        match = HEADING.match(line)
        if not match:
            continue
        level = len(match.group(1))
        if level > MAX_HEADING_LEVEL:
            errors.append(f"{label}: 标题层级 {level} 过深，最多 {MAX_HEADING_LEVEL} 级")
        headings.append(match.group(2))

    if len(headings) < MIN_SECTIONS:
        errors.append(f"{label}: 至少要有一个小标题分段，整篇堆在一段里难读")
    if len(headings) != len(set(headings)):
        errors.append(f"{label}: 有小标题重复了，同一件事只写一次")

    plain = strip_inline(text)
    if len(plain) < MIN_CHARS:
        errors.append(f"{label}: 正文只有 {len(plain)} 字，下限 {MIN_CHARS} 字")
    if len(plain) > MAX_CHARS:
        errors.append(f"{label}: 正文 {len(plain)} 字，超出上限 {MAX_CHARS} 字")

    if EXIT_TOPIC.search(text):
        for word in EXIT_MUST_MENTION:
            if word not in text:
                errors.append(f"{label}: 讲到了不能出国，但没提到「{word}」，没把机制讲清楚")

    if BARE_YEAR.search(plain):
        for match in BARE_YEAR.finditer(plain):
            year = int(match.group(1))
            if not 1900 <= year <= 2099:
                errors.append(f"{label}: 年份 {year} 年 没写公元/公元前，容易误读")
                break

    return errors


def main(argv: list[str]) -> int:
    if not DATA_FILE.exists():
        print(f"缺少数据文件: {DATA_FILE}")
        return 1

    items = load_items(DATA_FILE)
    errors: list[str] = []
    written = 0
    for item in items:
        detail = item.get("detail")
        if isinstance(detail, str) and detail.strip():
            written += 1
        errors.extend(check_detail(item))

    # 根目录和 Android assets 两份数据必须同步，否则「本地看着对、装到手机上没变」。
    if AGENT_DATA_FILE.exists():
        agent_items = {i.get("id"): i for i in load_items(AGENT_DATA_FILE)}
        for item in items:
            twin = agent_items.get(item.get("id")) or {}
            if (twin.get("detail") or "") != (item.get("detail") or ""):
                errors.append(f"{item.get('id')} {item.get('name')}: Android assets 的 detail 与根数据不一致")

    if "--stats" in argv or errors:
        print(f"已写深度正文 {written}/{len(items)} 件（{written * 100 // max(len(items), 1)}%）")

    if errors:
        print(f"\n校验失败，共 {len(errors)} 项：")
        for error in errors[:60]:
            print(f"  - {error}")
        if len(errors) > 60:
            print(f"  ... 还有 {len(errors) - 60} 项")
        return 1

    print("详情正文校验通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
