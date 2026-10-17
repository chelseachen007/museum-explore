# -*- coding: utf-8 -*-
"""统一清洗 JSON 文本字段，只保留可展示的事实文本。

数据来自 Wikipedia / 机构官网时，简介、署名、搜索摘要等字段偶尔会混入
``<span>``、``<a href>``、``&nbsp;``、``[1]`` 等内容。这里提供一个可复用的
清洗函数，并在 ``__main__`` 里批量处理项目中的几个数据 JSON。样式、坐标与
页面抓取残留属于渲染层信息，不应进入数据层。

注意：清洗只作用于字符串值。URL 字段（如 imageCredit.page）会保留原样，
其中的 ``&amp;`` 会被正确解码成 ``&``。
"""
from __future__ import annotations

import argparse
import html
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

SCRIPT_OR_STYLE = re.compile(r"(?is)<(script|style)\b[^>]*>.*?</\1\s*>")
LINE_BREAK = re.compile(r"(?i)<br\s*/?>|</p\s*>|</div\s*>|</li\s*>|</tr\s*>")
TAG = re.compile(r"(?is)<[^>]+>")
NUMERIC_ENTITY = re.compile(r"&#(x?[0-9a-fA-F]+);")
HORIZONTAL_SPACE = re.compile(r"[\t\x0b\f\r\u00a0\u2007\u202f]+")
SPACE_BEFORE_PUNCTUATION = re.compile(r"\s+([，。！？；：、,.!?;:])")
TRAILING_SPACE = re.compile(r"[ \t]+\n")
TOO_MANY_BLANK_LINES = re.compile(r"\n\s*\n+")

# 维基百科 / 机构官网抓取时混入的残留，不属于正文：
# - .mw-parser-output 开头的 CSS 规则块
# - 零宽字符（BOM / 零宽空格等）
# - 坐标（度分秒 / 小数度 / 分号小数对）
# - 脚注标记 [1] / [ 注 1 ] / [ a ] / [來源請求] 等
MW_CSS = re.compile(r"\.mw-parser-output[^{}]*\{[^{}]*\}")
ZERO_WIDTH = re.compile(r"[\u200b\u200c\u200d\ufeff]")
COORD_DMS = re.compile(r"-?\d{1,3}°\d{1,2}′\d{1,2}(?:\.\d+)?″[NSEW]")
COORD_DEC = re.compile(r"-?\d{1,3}(?:\.\d+)?°[NSEW]")
COORD_SEMI = re.compile(r"-?\d{1,3}(?:\.\d+)?\s*;\s*-?\d{1,3}(?:\.\d+)?")
FOOTNOTE = re.compile(
    r"\[\s*(?:[A-Za-z]|\d+|(?:注|註|网|網|书|書|來源請求|来源请求|需要引用|需要引证))\s*\d*\s*\]"
)
CSS_START = re.compile(r"\.mw-parser-output")
CSS_END = re.compile(r"\}\s*[^\s{}。！？；：，、,.!?;:]+")
CJK = re.compile(r"[\u3400-\u9fff]")
CSS_TOKEN = re.compile(
    r"(?:font-family|font-size|font-weight|line-height|display|margin|padding|"
    r"background|border|color|width|height|white-space|list-style)\s*:",
    re.I,
)
HTML_TAG_LIKE = re.compile(r"(?is)</?[a-z][^>\n]{0,200}>")

SUSPICIOUS = re.compile(
    r"(?i)<[a-z!/][^>]{0,200}>|&(?:lt|gt|amp|nbsp|quot|#\d+|#x[0-9a-f]+);"
)
SCRAPING_RESIDUE = re.compile(
    r"\.mw-parser-output|[\ufeff\u200b\u200c\u200d]"
    r"|\d{1,3}°\d{1,2}′\d{1,2}(?:\.\d+)?″[NSEW]"
    r"|-?\d{1,3}(?:\.\d+)?°[NSEW]"
    r"|\[\s*(?:[A-Za-z]|\d+|(?:注|註|网|網|书|書|來源請求|来源请求|需要引用|需要引证))\s*\d*\s*\]"
)
STYLE_LEFTOVER = re.compile(
    r"(?:font-family|font-size|font-weight|line-height|display|margin|padding|"
    r"background|border|white-space|list-style)\s*:|"
    r"\{[^{}]*\}|color\s*:\s*#(?:[0-9a-f]{3,8})\b",
    re.I,
)
COORD_SEMI_FULL = re.compile(r"-?\d{1,3}(?:\.\d+)?\s*;\s*-?\d{1,3}(?:\.\d+)?")
HTML_ENTITY = re.compile(r"&(?:lt|gt|amp|nbsp|quot|#\d+|#x[0-9a-f]+);", re.I)


def _is_cjk(ch: str) -> bool:
    return bool(CJK.match(ch))


def _remove_mw_css(text: str) -> str:
    """删除 MediaWiki CSS，同时保留它后面被截断的正文。

    网站抓取有时会截断在字体名中间，末尾没有 ``}``。因此不能简单地从
    ``.mw-parser-output`` 删到文末；遇到连续的 CSS 属性时继续跳过，遇到
    第一个连续中文句子就停止，并保留这句正文。
    """
    prev = None
    while prev != text:
        prev = text
        text = MW_CSS.sub(" ", text)

    while True:
        start = CSS_START.search(text)
        if not start:
            return text
        end = len(text)
        brace_end = CSS_END.search(text, start.end())
        if brace_end:
            end = brace_end.start()
        else:
            for index in range(start.end(), len(text) - 5):
                if (
                    _is_cjk(text[index])
                    and _is_cjk(text[index + 1])
                    and _is_cjk(text[index + 2])
                    and _is_cjk(text[index + 3])
                    and _is_cjk(text[index + 4])
                ):
                    end = index
                    break
        text = text[: start.start()] + " " + text[end:]


def _remove_style_fragments(text: str) -> str:
    """删除断句后仍然像 CSS 的短片段，防止字体名漏进正文。"""
    kept = []
    for part in re.split(r"(?<=[。！？；])", text):
        stripped = part.strip()
        if not stripped:
            continue
        if CSS_TOKEN.search(stripped) and len(CJK.findall(stripped)) < 8:
            continue
        kept.append(stripped)
    return "".join(kept)


def _remove_orphan_slashes(text: str) -> str:
    """删除坐标分隔符残留的孤立斜杠；保留「10元/人」这类斜杠。"""
    if re.match(r"https?://\S+\Z", text):
        return text
    return re.sub(r"(?<![\d元])\s*/\s*(?![\d元])", " ", text)


def clean_text(raw: object) -> object:
    """清理单个字符串；非字符串原样返回。"""
    if not isinstance(raw, str):
        return raw
    text = SCRIPT_OR_STYLE.sub(" ", raw)
    text = LINE_BREAK.sub("\n", text)
    text = TAG.sub("", text)
    text = html.unescape(text)
    text = LINE_BREAK.sub("\n", text)
    text = TAG.sub("", text)
    text = _remove_mw_css(text)
    text = _remove_style_fragments(text)
    text = ZERO_WIDTH.sub(" ", text)
    text = COORD_DMS.sub(" ", text)
    text = COORD_DEC.sub(" ", text)
    text = COORD_SEMI.sub(" ", text)
    text = _remove_orphan_slashes(text)
    text = FOOTNOTE.sub(" ", text)
    text = HTML_ENTITY.sub(" ", text)
    text = STYLE_LEFTOVER.sub(" ", text)
    text = HORIZONTAL_SPACE.sub(" ", text)
    text = re.sub(
        r"(?<=[\u3400-\u9fff，。！？；：、“”《》])[ \t]+(?=[\u3400-\u9fff，。！？；：、“”《》])",
        "",
        text,
    )
    text = SPACE_BEFORE_PUNCTUATION.sub(r"\1", text)
    text = re.sub(r"[ ]{2,}", " ", text)
    text = re.sub(r"([。！？；])\1+", r"\1", text)
    text = re.sub(r"(?<=[。！？；])[ \t]+(?=[^\n])", "", text)
    text = re.sub(r"^[，。！？；：、,.!?;:]+\s*", "", text)
    text = re.sub(r"\(\s*(?=\S)", "（", text)
    text = re.sub(r"\s*\)", "）", text)
    text = re.sub(r"\s*（\s*", "（", text)
    text = re.sub(r"\s*）", "）", text)
    text = TRAILING_SPACE.sub("\n", text)
    text = TOO_MANY_BLANK_LINES.sub("\n\n", text)
    return "\n".join(line.strip() for line in text.splitlines()).strip()


def clean_tree(value: object) -> object:
    """递归清洗传入的文本树。

    用于已经筛选过的单个字段；整份 JSON 请使用 ``clean_document``，避免
    把 URL、图片文件名和字段说明当作正文。
    """
    if isinstance(value, dict):
        return {key: clean_tree(item) for key, item in value.items()}
    if isinstance(value, list):
        return [clean_tree(item) for item in value]
    return clean_text(value)


def clean_document(value: object) -> object:
    if isinstance(value, dict):
        return {key: clean_document_field(item, key) for key, item in value.items()}
    if isinstance(value, list):
        return [clean_document(item) for item in value]
    return value


def clean_document_field(value: object, key: str) -> object:
    if isinstance(value, (dict, list)):
        return clean_document(value)
    if key in {
        "name", "era", "cat", "museum", "city", "province", "reason", "story",
        "address", "intro", "openingHours", "closedDays", "reservation", "wechat",
    }:
        return clean_text(value)
    return value


def iter_strings(value: object, path: str = ""):
    if isinstance(value, dict):
        for key, item in value.items():
            yield from iter_strings(item, f"{path}/{key}")
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from iter_strings(item, f"{path}[{index}]")
    elif isinstance(value, str):
        yield path, value


def load_json(path: str) -> object:
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def detect_indent(path: str) -> int:
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    match = re.search(r"\n( +)\S", text)
    return len(match.group(1)) if match else 2


def save_json(path: str, value: object, indent: int | None = None) -> None:
    if indent is None:
        indent = detect_indent(path)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=indent)
        handle.write("\n")


def check_file(path: str) -> int:
    data = load_json(path)
    hits = 0
    for json_path, text in iter_strings(data):
        if "/image" in json_path or json_path.endswith(("Url", "url", "page", "qid")):
            continue
        if (
            SUSPICIOUS.search(text)
            or SCRAPING_RESIDUE.search(text)
            or STYLE_LEFTOVER.search(text)
            or COORD_SEMI_FULL.search(text)
            or HTML_TAG_LIKE.search(text)
        ):
            hits += 1
            if hits <= 10:
                print(f"  {json_path}: {text[:140]!r}")
    return hits


def clean_file(path: str, check_only: bool = False) -> int:
    if check_only:
        hits = check_file(path)
        if hits:
            print(f"{path}: 仍有 {hits} 处 HTML/实体残留")
        return hits

    original = load_json(path)
    cleaned = clean_document(original)
    if cleaned == original:
        print(f"{path}: 无需修改")
        return 0
    save_json(path, cleaned)
    print(f"{path}: 已清洗")
    return 1


DEFAULT_FILES = [
    os.path.join(ROOT, "195件永禁文物.json"),
    os.path.join(ROOT, "museums.json"),
    os.path.join(HERE, "wiki_import.json"),
    os.path.join(HERE, "commons_manual.json"),
    os.path.join(HERE, "museum_overrides.json"),
]


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="清洗 JSON 文本字段里的 HTML 残留")
    parser.add_argument("files", nargs="*", help="要处理的 JSON 文件；默认处理项目数据 JSON")
    parser.add_argument("--check", action="store_true", help="只检查，不修改；有残留时返回 1")
    args = parser.parse_args(argv)

    files = args.files or [path for path in DEFAULT_FILES if os.path.exists(path)]
    if not files:
        print("没有找到可处理的 JSON 文件")
        return 1

    total = 0
    for path in files:
        if not os.path.exists(path):
            print(f"{path}: 文件不存在，跳过")
            continue
        total += clean_file(path, check_only=args.check)
    if args.check and total:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
