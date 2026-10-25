# 博物馆探索项目约定

## 项目目标

逐步建设一个安卓端博物馆旅游打卡应用：用户可以点亮地图、对准确的博物馆进行拍照打卡，并通过每个博物馆的官方小程序链接访问服务。用户数据默认本地存储。

**核心原则：名单是参考，不是门槛。** 博物馆清单只是帮用户发现去哪，不能限制用户能记什么——名单外的馆、临时路过的馆，都必须能加进来并记录。

## 目录约定

- `index.html`：项目入口页。
- `195件永禁文物·博物馆探索.html`：全国文物清单与打卡原型。
- `杭州国宝地图.html`：杭州地图与路线原型。
- `195件永禁文物.json`：结构化文物数据。
- `tools/`：数据构建、图片处理和校验脚本。
- 启动器脚本：仅用于本地预览和构建，不在其中保存密钥。

### Android 资产（`android/app/src/main/assets/`）

应用启动时由 `MuseumRepository.readMuseums()` 合并读取 `museums.json` / `museums_cn.json` / `museums_overseas.json` 三个名录文件，同名馆名以先出现者为准（按 `MuseumRepository.ASSET_FILES` 顺序）；`museum_branches.json` 由 `readBranches()` 单独读取。

| 文件 | 内容 | 现状 |
|---|---|---|
| `museums.json` | 195 件文物涉及的原始馆清单 | 59 条 |
| `museums_cn.json` | 国内全量名录（独立于文物名单） | 302 条 / 133 城市 |
| `museums_overseas.json` | 海外名录 | 182 条 / 46 国家 |
| `museum_branches.json` | 多馆区拆分配置 | 5 组 |

新增博物馆数据一律追加到 `museums_cn.json` / `museums_overseas.json`，不往代码里写死名单。

#### 名录字段约定

- `name`（必填）：官方馆名。分馆用 `父馆名 · 馆区名` 格式，`·` 两侧各一个半角空格，例如 `浙江省博物馆 · 之江馆区`。
- `city`（必填）：城市名。海外填中文城市名（如 `伦敦`）。
- `intro`：一句话介绍，25–50 字，讲清楚「这家馆以什么见长」。
- `officialUrl` / `wikiUrl`：只在核实过可访问时填写，否则 `null`。
- `address` / `openingHours` / `closedDays` / `reservation`：**不确定就留 `null`，绝不猜测**。开放时间、闭馆日、预约方式每年都在变，错误信息比没有信息更糟。
- `openToPublic`：`false` 表示非游览类机构（高校、考古所、内部研究所），可以打卡但不推荐当景点。
- `type`：`museum` / `collection_institution` 等。
- `verifiedAt` / `status`：最后一次核实的日期和状态。

#### `museum_branches.json` 结构

```json
{"museums": [{"parent": "浙江省博物馆", "city": "杭州", "address": null,
  "branches": [{"name": "浙江省博物馆", "address": null},
               {"name": "浙江省博物馆 · 之江馆区", "address": null}]}]}
```

- `branches[].name` 必须与名录里的 `name` 完全一致，否则关联不上。
- 父馆自己那一行要保留：它代表「整馆」这个可选目标，界面上会被跳过，但缺了它无法按父馆聚合。
- 只有**真实独立馆区**（不同地址、不同藏品体系）才拆条。同一座馆内部的展厅（如「南博历史馆/艺术馆」）不算。

## 数据与隐私

- 打卡状态和照片默认只存本地浏览器/设备，不上传。
- 每个馆的官方入口优先复用数据字段 `museumUrl` / `museumWechat`。
- 腾讯地图 Key 等敏感配置只放在本地配置文件，不提交到版本库。

## 开发约定

- 默认中文说明，代码、命令和变量名使用英文。
- 修改后必须运行适用的构建或校验命令。
- 不为了通过构建而注释掉错误；发现死代码只记录，不擅自删除。
- 后续迁移 Android 时，先复用现有 JSON 数据模型，再明确 Android 本地存储和权限边界。

## 参观记录的归属规则

- 参观记录用 `museumId` 指向**具体馆区**（`父馆名 · 馆区名`）；没拆馆区的馆直接用馆名。
- 父馆本身不写 journal，它只是「整馆」这个虚拟目标。
- 博物馆详情页是**整馆视角**：父馆和各馆区的记录都会列出来，查询用 `visitsForMuseumFamily()`，不要退回成 `visitsForMuseum()`，否则用户会觉得记录丢了。
- 城市列表（`museumNames(city)`）只列「文物涉及的馆 + 自己加的馆 + 我记过的馆」，不铺全量名录——几百家塞进一个城市会让人找不到去哪。

## 构建与校验

```bash
JAVA_HOME=$PWD/.toolchain/jdk-17.0.20.1+1/Contents/Home ./.toolchain/gradle-8.10.2/bin/gradle -p android assembleDebug --offline
JAVA_HOME=$PWD/.toolchain/jdk-17.0.20.1+1/Contents/Home ./.toolchain/gradle-8.10.2/bin/gradle -p android test --offline
```

改完名录 JSON 至少跑一次 `assembleDebug`，确认 assets 能被正确打包解析。
