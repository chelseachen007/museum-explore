#!/bin/bash
# 博物馆探索 · 本地启动器（macOS）
#
# 双击运行即可。做的事：在 127.0.0.1 上起一个只监听本机的静态服务，
# 然后用默认浏览器打开。关掉这个终端窗口就停止。
#
# 为什么不直接双击 HTML：file:// 协议下腾讯地图 API 会拒绝工作，
# 杭州页的地图标记会全部不显示。走 http://127.0.0.1 就没这个问题，
# 而且浏览器会把这里的数据当成一个稳定的"站点"，打卡记录更不容易丢。

cd "$(dirname "$0")" || exit 1

PORT=8765
URL="http://127.0.0.1:$PORT/"

# 探测本机端口是否已被占用。
# 注意：不能用 curl —— 系统如果配了 http_proxy，curl 会把 127.0.0.1 也发给代理，
# 代理返回 502 但 curl 退出码仍是 0，会被误判成"服务已经在跑"。
# 用 bash 内建的 /dev/tcp，不经过任何代理。
port_open() {
  (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null && exec 3<&- 2>/dev/null && return 0
  return 1
}

# 端口已经被上一次的服务占着，就直接开浏览器，不重复启动
if port_open; then
  open "$URL"
  exit 0
fi

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
  echo "找不到 python3，无法启动本地服务。"
  echo "按任意键关闭。"
  read -n 1 -s
  exit 1
fi

"$PY" -m http.server "$PORT" --bind 127.0.0.1 >/dev/null 2>&1 &
SRV=$!

# 等服务起来（最多 5 秒）
for _ in 1 2 3 4 5 6 7 8 9 10; do
  port_open && break
  sleep 0.5
done

if ! port_open; then
  echo ""
  echo "  启动失败：端口 $PORT 起不来。"
  echo "  可能是端口被别的程序占用，把脚本里的 PORT 改一个数再试。"
  echo "  按任意键关闭。"
  read -n 1 -s
  exit 1
fi

open "$URL"

echo ""
echo "  博物馆探索已启动 → $URL"
echo ""
echo "  打卡记录和照片都存在浏览器本地，关掉不会丢。"
echo "  要停掉服务，关掉这个终端窗口，或者按 Ctrl-C。"
echo ""

trap 'kill $SRV 2>/dev/null' EXIT INT TERM
wait $SRV
