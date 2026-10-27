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
- `design/`：设计稿与图标源文件（`design-*.png`、`design-mockups.html`、`app-icon.*`）。只是素材，不参与构建，根目录不再放散图。
- `artifacts/`：打包产物（APK 等），已在 `.gitignore` 里，只留本机。发版脚本会把 APK 复制一份到这里。
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

## 国宝详情正文规范（`detail` 字段）

详情页正文写在 `195件永禁文物.json` 的 `detail` 字段里，是**一篇自由结构的 markdown 长文**。

### 结构：不设模板

曾经有过「前世今生 / 为什么重要 / 为什么不可替代 / 为什么不能出国」四段固定小标题的规矩，**已废弃**。
原因：绝大多数文物撑不满四段，硬凑出来的第二段就是同一句话换个说法。读者读完只觉得注水。

现在的规则只有一条：**这件文物有什么值得说的就写什么，没什么值得说的就跳过。**

- 小节数量、标题措辞由内容决定，2–5 节都行，标题里不要求出现固定字眼。
- 工艺流程强的器物，「来历」和「凭什么独一份」可以合成一节。
- 流转史惊心动魄的（抗战藏宝、近代劫难），那一段单独展开，可以比别处长得多。
- 自检方法：逐节问「删掉这一节，读者会损失什么」，答不上来就删。同一件事不写第二遍。
- 「为什么不能出国」是**按需写**的一节：195 件共享同一套法律和目录，每件复述一遍法条就是噪音。
  只在有这件自己的理由时（孤品、唯一带铭文、工艺失传、有机质易损、搬不动、历史劫难）才写。
  写了就必须出现「出境」「禁止」两个词——讲的是机制，不是感叹。

### 格式（App 端只实现这个子集，多写不会渲染）

- `##` / `###` 小标题，最多三级。不要 H1，页面标题已经有了。
- 段落之间空一行。
- `-` 无序列表、`1.` 有序列表。
- `**粗体**`、`*斜体*`。
- **不支持**：表格、引用块、链接、图片、代码块、分割线、五级及以下标题。写了会原样显示成符号。
- 正文里不写 HTML 标签，不写「点击查看更多」这类界面文案。

### 硬规则（`tools/validate_relic_detail.py` 会查）

- 200–2400 字（去标记后的中文字数）。宁短勿冗，别为凑字数注水。
- 至少一个小标题分段，整篇堆成一段算格式错误。
- 年份写全：「公元前 221 年」「1939 年」。四位数且不在 1900–2099 之间却没写「公元/公元前」的，判错。
- 有争议的说法（年代、释读、产地、真伪）必须标出来源或分歧，不要单向陈述。
- 不确定的事不写。宁可少一段，不编一个数字。
- 不要把 `story` / `reason` 字段原文贴进来——那是素材，不是定稿。

### 写完怎么验

```bash
python3 tools/validate_relic_detail.py --stats   # 看覆盖率 + 逐条报错
python3 tools/sync_assets.py                     # 同步到 Android assets（改了数据必须跑）
```

写手批量产稿时，用 `python3 .drafts/merge_details.py --dry-run <草稿.json>` 先自检再合并。

## 「博物馆怎么逛」指南（`museum_guide.json`）

这是独立于文物和博物馆名录的**通用观展知识**，对应「更多 → 看展指南」入口。

| 位置 | 作用 |
|---|---|
| `tools/build_guide.py` | **正文唯一源**，改内容改这里，不要直接改 assets 里的 JSON |
| `android/app/src/main/assets/museum_guide.json` | 生成产物，由脚本写出 |
| `model/MuseumGuide.kt` | 数据结构 |
| `data/MuseumRepository.readGuide()` | 解析，失败返回 `MuseumGuide.empty` |
| `ui/MuseumGuideScreen.kt` | 目录页 + 单节正文页 |

- `sections[].id` 一旦发布**不要改**：深链 `guide/{sectionId}` 和用户收藏都依赖它。
- `sections[].body` 用和文物详情**同一套** `RelicMarkdown` 解析器，所以支持的语法子集完全一致（见上一节）。不要在指南里引入表格、引用块、代码块。
- 解析正文走 `cleanMarkdown()` 而不是 `cleanDisplay()`：后者是为维基抄来的脏文本设计的，会把行内空白吃掉，破坏 markdown 结构。
- 正文属于 App 版本内容，**不参与探索状态的编码和备份**，避免老备份覆盖新指南。
- 规则类内容（预约、放票、闭馆日）会过期，`sources` 字段必须在界面上如实展示来源和更新时间。
- 目录页按 `MuseumGuide.GUIDE_LAYOUT` 分三组展示（心态与准备 / 各品类怎么看 / 现场与之后）。**新增一节要同时往这个常量里登记 id**，否则会落到「更多」组末尾——仍然可见，但位置不对，`MuseumGuideGroupingTest` 会盯着节不丢不重。
- 品类之外另有三节**专题**：`kilns`（五大名窑）、`painting-history`（绘画史）、`calligraphy-history`（书法史），整理自剪藏笔记 `剪藏/五大名窑.md`、`剪藏/中国绘画的发展史.md`、`剪藏/中国书法的发展脉络.md`。它们是「知识地图」，和「看什么」的品类节并排，不复述后者的内容。
- `glossary[].items[].hint` 是可选字段，写这条词落在哪一门知识里（`五大名窑 · 钧窑`、`元四家`），界面显示在释义下方。解析时走 `optionalString` 不过 `TextCleaner`——后者会把 ` · ` 两侧的空格压成零宽，`五大名窑 · 汝窑` 会粘成「五大名窑汝窑」。

跑法：

```bash
python3 tools/build_guide.py   # 重新生成 assets
```

改了 `build_guide.py` 必须重跑它；`MuseumGuideAssetTest` 会拦截 id 重复和渲染器不支持的语法。

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
