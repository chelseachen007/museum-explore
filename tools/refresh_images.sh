#!/bin/sh
# 补全实物照片：跑流水线 → 补抓署名 → 重建数据与两个页面
# 可反复执行；已查过的条目走缓存，不会重复请求
set -e
PY=/Users/chenjie/.workbuddy-ai/binaries/python/envs/default/bin/python3
# 切到脚本自身所在目录，整个工作区搬到哪里都能跑
cd "$(dirname "$0")"
echo "== 1/4 检索图片（受 Wikimedia 限流，可中断后重跑） =="
$PY wdall.py || true
echo "== 2/4 补抓缺失署名 =="
$PY repair.py || true
echo "== 3/4 重建 JSON =="
$PY build.py
echo "== 4/4 重建页面 =="
$PY build_html.py
$PY build_hz.py
echo "完成。"
