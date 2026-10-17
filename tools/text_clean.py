# -*- coding: utf-8 -*-
"""统一清洗 JSON 文本字段里的 HTML 标签、实体和引用标记。

数据来自 Wikipedia / 机构官网时，简介、署名、搜索摘要等字段偶尔会混入
``<span>``、``<a href>``、``&nbsp;``、``[1]`` 等内容。这里提供一个可复用的
清洗函数，并在 ``__main__`` 里批量处理项目中的几个数据 JSON。

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
CITATION = re.compile(r"\[\s*\d+\s*\]")
HORIZONTAL_SPACE = re.compile(r"[\t\x0b\f\r\u00a0\u2007\u202f]+")
SPACE_BEFORE_PUNCTUATION = re.compile(r"\s+([，。！？；：、,.!?;:])")
TRAILING_SPACE = re.compile(r"[ \t]+\n")
TOO_MANY_BLANK_LINES = re.compile(r"\n\s*\n+")
SUSPICIOUS = re.compile(
    r"(?i)<[a-z!/][^>]{0,200}>|&(?:lt|gt|amp|nbsp|quot|#\d+|#x[0-9a-f]+);"
)


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
    text = CITATION.sub("", text)
    text = SPACE_BEFORE_PUNCTUATION.sub(r"\1", text)
    text = HORIZONTAL_SPACE.sub(" ", text)
    text = TRAILING_SPACE.sub("\n", text)
    text = TOO_MANY_BLANK_LINES.sub("\n\n", text)
    return "\n".join(line.strip() for line in text.splitlines()).strip()


def clean_tree(value: object) -> object:
    """递归清洗 JSON 树中的所有字符串。"""
    if isinstance(value, dict):
        return {key: clean_tree(item) for key, item in value.items()}
    if isinstance(value, list):
        return [clean_tree(item) for item in value]
    return clean_text(value)


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
        if SUSPICIOUS.search(text):
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
    cleaned = clean_tree(original)
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
    os.path.join(HERE, "wd_cache.json"),
    os.path.join(HERE, "commons_search_cache.json"),
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
