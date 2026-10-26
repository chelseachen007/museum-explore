# -*- coding: utf-8 -*-
"""按文物名从 Obsidian 里的原始材料抽取对应段落，给写手当取材用。

同一件文物在各份材料里叫法不同（「后母戊鼎」/「第九件 后母戊鼎」/「司母戊鼎」），
所以匹配规则是：章节标题里包含文物的任一叫法（去掉书名号、括号说明、朝代前缀后再比）。

用法：
    python3 tools/relic_sources.py 后母戊鼎
    python3 tools/relic_sources.py --list        # 列出能匹配到的文物
"""
from __future__ import annotations

import difflib
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
DATA_FILE = ROOT / "195件永禁文物.json"

OBSIDIAN = Path("/Users/chenjie/Documents/study/github/Obsidian")
TRANSCRIPT = OBSIDIAN / "剪藏" / "195件永久禁止出境文物-整理稿.md"
QYUE_DIR = OBSIDIAN / "视频学习" / "器曰 — 每天认识一件文物"

CN_NUM = "零一二三四五六七八九十百"


def normalize(text: str) -> str:
    """去掉书名号、书名号里的书名、括号注释、数字序号，只留名字主体。"""
    text = re.sub(r"《[^》]*》", "", text)
    text = re.sub(r"[（(][^）)]*[）)]", "", text)
    text = re.sub(rf"第[{CN_NUM}\d]+件[、至到]", "", text)
    text = re.sub(rf"第[{CN_NUM}\d]+件", "", text)
    return re.sub(r"[\s·、，,。：:；;\-—「」『』\"']", "", text)


# 文物名和视频标题对不上是常态：叫法不同（玉琮王 / 良渚出土玉琮王）、
# 多了定语（淅川出土铜禁 / 云纹铜禁）、纪录片里有自己的编号（第95件 莲鹤方壶）。
# 所以匹配靠「去噪后的最长公共子串」，而不是相等。
NOISE = re.compile(
    r"出土|墓葬|墓|遗址|文化|时期|朝代|王朝|的|之|一件|一组|一组|第[一二三四五六七八九十百\d]+件[、至到]?|[·、，,。：:；;\-—「」『』\"']"
)
SHORT_STOP = {"文物", "国宝", "青铜", "遗址", "文化", "彩绘", "青铜器"}


def aliases(name: str) -> list[str]:
    """一件文物可能有的几种叫法。"""
    out = {name}
    for inner in re.findall(r"[（(]([^）)]*)[）)]", name):
        out.add(inner.strip())
    out.add(re.sub(r"[（(][^）)]*[）)]", "", name).strip())
    # 异体字/常见误写，双向补齐
    for wrong, right in {"禅": "襌", "𠀋": "㝬", "鎏": "镏"}.items():
        for candidate in list(out):
            if wrong in candidate:
                out.add(candidate.replace(wrong, right))
            if right in candidate:
                out.add(candidate.replace(right, wrong))
    return [a for a in out if a]


def core(text: str) -> str:
    return NOISE.sub("", normalize(text))


def match_score(name: str, title: str) -> int:
    """名字和标题的重合度，越大越像同一件文物；0 表示不匹配。"""
    best = 0
    for alias in aliases(name):
        left, right = core(alias), core(title)
        if not left or not right:
            continue
        if left in right:
            best = max(best, len(left))
            continue
        block = difflib.SequenceMatcher(None, left, right).find_longest_match(0, len(left), 0, len(right))
        if block.size >= 2 and left[block.a:block.a + block.size] not in SHORT_STOP:
            best = max(best, block.size)
    return best


def sections(path: Path) -> list[tuple[str, int, str]]:
    """把 markdown 切成 (标题, 行号, 正文) 的章节列表。"""
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").split("\n")
    marks = [i for i, line in enumerate(lines) if line.startswith("#")]
    result = []
    for position, index in enumerate(marks):
        end = marks[position + 1] if position + 1 < len(marks) else len(lines)
        body = "\n".join(lines[index + 1:end]).strip()
        if body:
            result.append((lines[index].lstrip("# ").strip(), index + 1, body))
    return result


def collect(name: str) -> str:
    keys = aliases(name)
    chunks: list[str] = []
    threshold = max(2, min(len(core(name)), 4) - 1)
    for title, line, body in sections(TRANSCRIPT):
        if match_score(name, title) >= threshold:
            chunks.append(f"### [整理稿 L{line}] {title}\n\n{body}")
    if QYUE_DIR.is_dir():
        for note in sorted(QYUE_DIR.glob("*.md")):
            if match_score(name, note.stem.split("—")[-1]) >= threshold:
                text = note.read_text(encoding="utf-8")
                text = text.split("---", 2)[-1].strip() if text.startswith("---") else text
                chunks.append(f"### [器曰] {note.stem}\n\n{text}")
    return "\n\n".join(chunks) if chunks else "（原始材料里没有直接对应的章节，请依靠可靠知识另查）"


def main(argv: list[str]) -> int:
    items = json.loads(DATA_FILE.read_text(encoding="utf-8")).get("items") or []
    if not argv or argv[0] == "--list":
        for item in items:
            hit = "有" if collect(item["name"]).startswith("### [") else "无"
            print(f"{item['id']:3d} {hit} {item['name']}")
        return 0
    target = argv[0]
    item = next((i for i in items if i["name"] == target), None)
    print(collect(item["name"] if item else target))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
