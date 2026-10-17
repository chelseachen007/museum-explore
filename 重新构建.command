#!/bin/bash
# 博物馆探索 · 重新构建
#
# 双击运行。做的事：用 tools/ 里的脚本重新生成 195件永禁文物.json 和两个页面。
#
# 什么时候需要跑它：
#   - 往 tmap-key.txt 填了腾讯地图 Key（启用杭州页地图）
#   - 用 tools/refresh_images.sh 补了新的文物照片
#   - 改了 tools/ 里的构建脚本
#
# 不会动你的打卡记录和照片——那些存在浏览器里，跟这些文件无关。

cd "$(dirname "$0")" || exit 1
ROOT="$(pwd)"

# 优先用托管环境的 python，找不到就用系统的
PY=""
for cand in \
  "/Users/chenjie/.workbuddy-ai/binaries/python/versions/3.13.12/bin/python3" \
  "/opt/homebrew/bin/python3" \
  "/usr/bin/python3"
do
  [ -x "$cand" ] && PY="$cand" && break
done
[ -z "$PY" ] && PY="$(command -v python3)"
if [ -z "$PY" ]; then
  echo "找不到 python3，无法构建。"
  echo "按任意键关闭。"
  read -n 1 -s
  exit 1
fi

cd "$ROOT/tools" || exit 1

echo ""
echo "  博物馆探索 · 重新构建"
echo "  ────────────────────────────────────"
echo "  1/5  数据（195 件 + 照片回填）"
"$PY" build.py || { echo ""; echo "  构建失败，已停止。"; read -n 1 -s; exit 1; }
echo ""
echo "  2/5  馆级资料"
"$PY" build_museums.py || { echo ""; echo "  构建失败，已停止。"; read -n 1 -s; exit 1; }
echo ""
echo "  3/5  清洗 JSON 文本"
"$PY" text_clean.py || { echo ""; echo "  构建失败，已停止。"; read -n 1 -s; exit 1; }
echo ""
echo "  4/5  下载外链图片并同步 Android 资源"
"$PY" localize_assets.py || { echo ""; echo "  图片下载失败，可重跑本脚本继续。"; read -n 1 -s; exit 1; }
echo ""
echo "  5/5  全国 195 件页与杭州页"
"$PY" build_html.py || { echo ""; echo "  构建失败，已停止。"; read -n 1 -s; exit 1; }
"$PY" build_hz.py || { echo ""; echo "  构建失败，已停止。"; read -n 1 -s; exit 1; }
echo ""
echo "  校验中……"
"$PY" validate_data.py || { echo ""; echo "  数据校验失败，已停止。"; read -n 1 -s; exit 1; }
echo ""
echo "  ────────────────────────────────────"
echo "  完成。双击「启动博物馆探索.command」查看。"
echo ""
echo "  按任意键关闭这个窗口。"
read -n 1 -s
