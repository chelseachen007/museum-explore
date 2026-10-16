# 验证脚本

这些脚本用来确认两个页面没坏。**日常不用跑**，改了 `tools/` 里的构建脚本或页面逻辑之后跑一下。

## 前置

需要 Node（用托管的那份）：

```sh
NODE=/Users/chenjie/.workbuddy-ai/binaries/node/versions/22.22.2-6/bin/node
```

除了 `harness5.js`，其他都要先**双击 `启动博物馆探索.command`** 把服务起起来（`http://127.0.0.1:8765`）。

脚本里用到的 Playwright 是全局装的，路径写死为 `/Users/chenjie/node_modules/playwright`；Chromium 启动时带 `proxy: 127.0.0.1:7890`，否则 Wikimedia 的实物照片加载不出来。

## 脚本

| 脚本 | 需要浏览器 | 说明 |
|---|---|---|
| `verify.js` | ✅ | **最常用**。一次跑完首页 + 全国 195 页 + 杭州页：入口数、卡片数、实物照片解码数、地图状态、页面异常 |
| `smoke.js` | ✅ | 双页冒烟：卡片数 / 拍照按钮数 / 照片条数 / 有没有 JS 异常 |
| `harness5.js` | ❌ | 离线 DOM 桩：模拟「无 IndexedDB + 无腾讯地图」的最差环境，确认降级文案正确、195 张卡片照样渲染。**不需要起服务** |
| `nat_phototest.js` | ✅ | 全国页拍照打卡端到端：传 2 张 → 缩略图/加号/按钮文案/自动打卡/计数 → 足迹页照片墙 → 刷新持久化 → 灯箱 → 删除 |
| `phototest.js` | ✅ | 杭州页同上（7 件版） |
| `crosstest3.js` | ✅ | 跨页打卡同步：杭州页勾选 → 全国页应认，反之亦然 |
| `keycheck.js` | ✅ | 旧存储键迁移：写入 `museum195_visited_v1` / `hz_guobao_visited_v1`，确认合并进 `guobao_visited_v1` |
| `uncheck.js` | ✅ | 取消打卡后刷新不会被旧键迁移回来；顺带验双页照片库共享 |
| `filetest.js` | ✅ | `file://` 协议下 localStorage / IndexedDB 是否可用（判断双击 HTML 能不能存住数据） |

> 没配腾讯地图 Key 时「地图区应是干净占位、无鉴权失败浮层」这一条，已经并进 `verify.js` 的杭州页检查里（看 `地图区文本` / `鉴权失败字样` 两个字段）。

## 跑法

```sh
cd tools/tests

# 最常用：三页一次过
$NODE verify.js

# 离线降级（不用起服务）
$NODE harness5.js

# 拍照打卡端到端
$NODE nat_phototest.js
$NODE phototest.js
```

截图落在 `_shots/`。

## 注意

- 拍照打卡测试会往 IndexedDB 写真实数据。测试用的是**独立的浏览器上下文**，跑完就没了，不影响你平时用启动器打开时的那份数据
- 脚本里所有页面地址都指向 `127.0.0.1:8765`（启动器的端口）。换端口的话改 `verify.js` 里的 `BASE`
