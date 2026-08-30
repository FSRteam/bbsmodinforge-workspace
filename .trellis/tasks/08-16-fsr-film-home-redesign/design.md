# FSR 影片首页（Film Home）重设计

> 任务：`08-16-fsr-film-home-redesign` · 状态：已实施（P0-P2 全量 + 多轮实测反馈迭代）
> 适用代码：`new/`（FSR, NeoForge 1.21.1）客户端 UI。
> 约束来源：`.trellis/spec/frontend/bbs-ui-theming.md`（语义色 token、圆角 token、渲染热路径红线、v2 动效规约）。

## 实施结果（2026-08-23，分支 `fsr-film-home-redesign`）

与原设计的偏差（均经用户确认）：

1. **左下角列 = 广告栏**（原设计的"阿雪绘制单"被广告栏替换）：4:3 广告图翻页轮播——10 秒自动向左翻、过渡时新旧两图错开滑动、分页圆点点击跳转、支持鼠标左右拖动换页；末尾固定"广告栏招租"加号位。内容走 `film_home/ads.json`。
2. **新闻条保留**在 banner 下方（横向卡片），详情弹窗与广告共用样式。
3. **封面截图**最终采用监视器右键 📷 同款管线（`UIFilmPreview.snapshotToFile`：导出分辨率隔离渲染一帧 → `ScreenshotRecorder` 写全分辨率 PNG → 后台缩放为 320px 封面），挂载点为 `fill(Film)` 数据到达时与 `forceSave`；无任何手动入口、完全静默。中途的"自动批量开片生成"方案因劫持导航被移除。
4. **Markdown 详情**：新闻/广告正文支持轻量 Markdown（标题/加粗/行内代码/链接/列表/引用/代码块/`![图片](url)` 远程图带加载动画），正文可拖选复制；链接有复制按钮 + http 可浏览器打开。
5. 工具栏控件（卡片大小滑杆/排序/视图切换/CRUD）右对齐成组；banner 无新建按钮（创建走工具栏 ＋ 或右键菜单）。

关键提交：`98d08163`(P0) `10a52491`(P1+P2) `3519a5f3` `c382677b` `26d7986a` `016f6023` `5877748d` 等。

## 1. 现状与问题

当前"影片列表"由通用选择屏实现：

- `new/src/client/java/mchorse/bbs_mod/ui/dashboard/panels/UISelectionScreen.java` — 一张 420×420 居中卡片：顶部 150px banner（`assets/bbs/textures/banners/bg.png`）、标题 + 增/复制/改名/删按钮、`UIDataPathList` 扁平文本列表 + 搜索框。
- `new/src/client/java/mchorse/bbs_mod/ui/film/UIFilmSelectionPanel.java` — 影片面板的薄封装（加了"复制当前影片"按钮）。
- `new/src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java:399` — 未打开影片时 `selectionPanel` 全幅挂载（Tab 栏之下），`pickData` 后隐藏。

问题：

1. 卡片只占屏幕中央，四周是空的 backdrop；150px banner 只是装饰，没有承载标题/动作。
2. 列表是纯文本行，没有"剪辑软件工程列表"式的视觉呈现：无首帧缩略图、无时长、无最后修改时间。
3. 没有内容分区（新闻 / 展示位），首页空间浪费。
4. `Film`（`new/src/main/java/mchorse/bbs_mod/film/Film.java`）只有 `createdAt`/`description`/`timeSpentActive`，**没有最后修改时间**；无缩略图缓存设施。

## 2. 设计目标 / 非目标

**目标**

- 把影片列表从"居中卡片"升级为**整个 Film 面板的首页**：顶部全宽 banner 背景图 → 新闻区 → 下方左右分栏（左：阿雪的 Blockbench 绘制单；右：影片网格）。
- 影片卡片采用剪辑软件（Premiere / 剪映工程列表）式呈现：**第一帧缩略图 + 标题 + 最后修改时间**。
- 保留全部现有能力：文件夹、增/复制/改名/删、搜索、多选、右键菜单、Delete/F2/Enter/Ctrl+A 键位。
- 主题合规：只用 `BBSSettings` 语义色与圆角 token；渲染热路径零 IO。

**非目标**

- 不改其它数据面板（Form/Sound 等仍用 `UISelectionScreen`）。
- 不做远程内容拉取（新闻/绘制单纯本地 JSON，远程更新另立任务）。
- 不改影片存储协议（`FilmRepository` 网络路径不动，`updated_at` 随现有 save 数据走）。

## 3. 信息架构与线框

首页在 Film 面板 Tab 栏（现有 `UIDataTabs`，不动）之下整体滚动：

```
┌────────────────────────────────────────────────────────────────────────┐
│ [Film 面板 Tab 栏（现有，不动）]                                         │
├────────────────────────────────────────────────────────────────────────┤
│ ██ BANNER 背景图（全宽 ~150px，主题可换图，底部 accent 渐隐分隔线）██    │
│                                                                        │
│  影片 Films                                              [＋ 新建影片]    │
│  共 12 部 · 最近修改 2 小时前                                            │
├────────────────────────────────────────────────────────────────────────┤
│ 新闻 ›                                                          (⋯)     │
│ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐               │
│ │▓▓│标题一行 │ │▓▓│标题一行 │ │▓▓│标题一行 │ │▓▓│标题一行 │  ← 横向滚动  │
│ │  │tag·日期 │ │  │tag·日期 │ │  │tag·日期 │ │  │tag·日期 │               │
│ └───────────┘ └───────────┘ └───────────┘ └───────────┘               │
├────────────────────┬───────────────────────────────────────────────────┤
│ 阿雪的绘制单        │ 影片 [🔍 搜索影片…] [排序:最近修改▾] [⊞|≡] 📁＋⧉✎✕ │
│ 接单中 · 剩 3 位    │ [根目录 /] [番剧 /] [第一季 /]        ← 文件夹面包屑│
│ ┌────────────────┐ │ ┌────────────┐ ┌────────────┐ ┌────────────┐     │
│ │    封面图       │ │ │  第一帧     │ │  第一帧     │ │  第一帧     │     │
│ │ ══════░░░ 60%  │ │ │        1:20│ │ │        3:05│ │ │        0:45│     │
│ ├────────────────┤ │ ├────────────┤ ├────────────┤ ├────────────┤     │
│ │ Q版立绘  ●接单中│ │ │ 我的电影 #4 │ │ |番剧·02    │ │ │ 空镜头测试  │     │
│ │ 2/5 位 · ¥120起 │ │ │ 2小时前     │ │ │ 昨天        │ │ │ 8-12       │     │
│ └────────────────┘ │ └────────────┘ └────────────┘ └────────────┘     │
│ ┌────────────────┐ │ ┌────────────┐ ┌────────────┐                     │
│ │  …更多单子      │ │ │ 📁 未整理(3)│ │  …           │                    │
│ └────────────────┘ │ └────────────┘ └────────────┘                     │
│                    │ 已选 2 项 · 双击打开 · F2 改名 · Del 删除           │
└────────────────────┴───────────────────────────────────────────────────┘
```

层级结构（新元素，均在 `mchorse.bbs_mod.ui.film.home` 包）：

```
UIFilmHomePanel（替换 UIFilmSelectionPanel 的呈现层，滚动根）
├─ UIFilmBanner          全宽 banner + 标题/统计/搜索/新建
├─ UINewsStrip           横向新闻卡片条（可整节隐藏）
└─ UIHomeContentRow      左右分栏
   ├─ UICommissionBoard  阿雪的 Blockbench 绘制单（可折叠/隐藏）
   └─ UIFilmSection
      ├─ UIFilmToolbar   排序、视图切换、面包屑、CRUD 图标
      ├─ UIFilmGrid      网格视图（默认；新通用 UICardGrid 容器）
      └─ UISearchList    列表视图（复用现有 UIDataPathList，次选视图）
```

## 4. 分区详细设计

### 4.1 Banner（顶部背景图）

- 全宽、高 ~150px（沿用 `BANNER_H` 量级），cover 裁切（复用现 `renderBanner` 的居中裁切算法）。
- 默认贴图 `assets/bbs/textures/film_home/banner.png`；缺失回退现 `textures/banners/bg.png`；主题包可经 `getBannerTexture()` 覆盖（沿用 `UISelectionScreen` 的 override 点）。
- 贴图之上自上而下压一层 `deepSurface` 渐变蒙版（顶部 0 → 底部 ~A75），保证任何图上文字可读。
- 左侧文案两行：标题「影片」（现有 `UIKeys.FILM_TITLE`）；副行统计「共 N 部 · 最近修改 X」（相对时间）。
- 右侧动作仅保留「＋ 新建影片」主按钮（primary 底、圆角 `cornerWidget()`；Shift+点击沿用现有自动编号路径）。**搜索不放在 banner**：它与影片结果之间隔着新闻区，操控与内容分离；搜索下沉到影片区工具栏（见 4.4）。
- 底部 accent 分隔线沿用现卡片上的 `gradientHBox` 中间向两侧渐隐手法。

### 4.2 新闻区

- 区头：「新闻」+ 左右滚动箭头（内容不足不显示箭头）。
- 卡片 ~260×72，横向排列、滚轮横向滚动：左 56×56 图（可选），右侧 tag 徽标（更新=primary / 公告=warning / 教程=active，语义色）+ 标题一行 + 日期一行（`mutedTextColor()`）。
- 点击 → 详情 overlay（标题、日期、全文、大图；若有 `url` 附「在浏览器打开」）。
- **无数据即整节隐藏**（不占位）。

### 4.3 左列：阿雪的 Blockbench 绘制单

- 列宽 `clamp(240, 30%, 320)`px；区头显示自定义名称（默认「阿雪的绘制单」）与接单汇总（「接单中 · 剩 N 位」由数据汇总）。
- 单子卡片（竖向堆叠，占满列宽，高 ~132px）：
  - 封面图 16:9，cover 裁切；左下角状态徽标（接单中=positive / 排队中=warning / 制作中=active / 已完结=muted）。
  - 封底下缘 2px 进度条（`progress` 字段 0~1，>0 才显示）。
  - 标题一行截断；meta 行「2/5 位 · ¥120 起」。
  - 点击 → 详情 overlay：大封面 + 说明 + 「复制联系方式」（`Window.setClipboard`）+ 可选外链。
- 无数据整列隐藏，影片区吃满宽度。

### 4.4 右区：影片网格（核心）

**卡片解剖（剪辑软件工程列表式）：**

```
┌──────────────────────┐
│ ┌──────────────────┐ │  16:9 首帧缩略图（懒加载）
│ │      第一帧       │ │   缺省回退：按 id 哈希的双色渐变 + FILM 图标
│ │              1:20 │ │   右下角时长角标（黑 A75 底 + 白字，位于贴图上层）
│ └──────────────────┘ │
│ 我的电影 #4        ⋯ │  标题单行截断；hover 显 ⋯ 快捷菜单（打开/复制/改名/删）
│ 2 小时前             │  最后修改时间（相对时间）；列表视图里再附时长/回放数
└──────────────────────┘
```

- 卡宽 `clamp(200, 网格均分, 280)`，列数按容器宽自适应 2→4；间距 8px；卡片底 `raisedSurface()`、圆角 `cornerWidget()`、1px `dividerColor()` 描边。
- 选中：`primaryColor` 外描边 1.5px + `primaryColor(Colors.A12)` 蒙版；hover：`primaryColor(Colors.A25)` 蒙版。多选沿用 Ctrl/Shift 语义。
- 文件夹卡片：FOLDER 图标 + 名称 + 「N 项」，排序时置顶；双击进入（面包屑同步）。
- 交互对齐现状：单击选中、**双击/Enter 打开**（现 `openOnSingleClick(false)`）、右键完整 CRUD 菜单（含复制/粘贴）、Delete/F2/Enter/Ctrl+A 键位全部保留，另有方向键在网格内移动选中。
- 网格首个格子固定为虚线幽灵卡「＋ 新建影片」（剪辑软件工程列表惯例）：创建入口也贴着影片本体，与 banner 的页面级 CTA 指向同一动作；空状态时它是唯一卡片，替代原空态按钮。
- 工具栏（本区所有操控贴着网格；整页滚动时工具栏在面板顶吸顶，搜索/排序始终可达）：最左搜索框 [🔍 搜索影片…]（同时过滤网格与列表视图，过滤语义同现 `UISearchList`）；排序下拉（**最近修改**（默认）/ 创建时间 / 名称 / 时长）；视图切换（网格 ⊞ / 列表 ≡，列表视图直接复用现 `UISearchList`+`UIDataPathList`）；面包屑 chips（根目录 / … / 当前）；最右保留现有 📁(新建文件夹)＋(新建)⧉(复制)✎(改名)✕(删) 图标组。
- 空状态：居中 FILM 大图标（`mutedTextColor` tint）+「还没有影片」+「＋ 新建影片」按钮。
- 底部状态行：「已选 N 项 · 双击打开 · F2 改名 · Del 删除」。

## 5. 数据与存储

### 5.1 `Film.updated_at`（新字段，最后修改时间）

- `Film` 增加 `ValueString updated_at`（ISO-8601，同 `createdAt` 风格）；`UIFilmPanel.save()` → `FilmRepository.save` 路径盖章 `Instant.now()`。
- 展示：相对时间（刚刚 / N 分钟前 / N 小时前 / 昨天 / `formatCreatedAtForDisplay` 同款 MEDIUM 日期）；缺失时回退 `createdAt`，再缺失显示「—」。
- 排序默认按 `updated_at` desc，缺失沉底。`UIFilmDetailsOverlayPanel` 增一行「最后修改」。

### 5.2 首帧缩略图管线（新）

- **采集**：影片在编辑器中打开且预览尺寸稳定后（`applyPreviewSizeToBBS("layoutSettled")` 后若干帧），从预览帧缓冲 `readPixels` → `NativeImage` 缩放到 320×180 → 后台线程写 `config/bbs_mod/cache/film_thumbnails/<sha1(id)>.png` + 同名 `.meta`（记录 `updated_at`，避免陈旧封面）。
- **刷新时机**：打开影片首帧、保存且 `updated_at` 变化、右键「刷新封面」。
- **网格加载**：LRU（~48 张）；渲染期只查内存缓存，miss 时调度后台 IO 任务、主线程上传纹理（主题红线：渲染热路径零 IO 零分配）。
- **回退**：无缩略图（未打开过的旧片/换机器）→ id 哈希渐变卡 + FILM 图标 + 时长角标，视觉完整不残缺。
- 多人注意事项：影片数据在服务端，缩略图只能是本地缓存，回退卡即跨机形态。

### 5.3 首页内容（新闻 + 绘制单），本地 JSON

目录 `config/bbs_mod/film_home/`（用户可编辑，阿雪无需重新打包即可换内容），缺失时回退 jar 内 `assets/<ns>/film_home/` 同名默认；两者皆无 → 对应分区隐藏。JSON 解析一次并缓存，坏文件 warn + 隐藏分区（不崩、不弹错）。

`news.json`：

```json
{
  "items": [
    {
      "id": "fsr-2-1",
      "tag": "update",              // update | notice | tutorial
      "date": "2026-08-16",
      "title": "FSR 2.1：影片首页上线",
      "summary": "影片列表全面改版……",
      "image": "images/news/fsr21.png",   // 相对 film_home/，可省略
      "url": ""                            // 可选；有则详情里提供"在浏览器打开"
    }
  ]
}
```

`commissions.json`：

```json
{
  "display_name": "阿雪的绘制单",
  "contact": "QQ 12345678",
  "items": [
    {
      "id": "chibi-standee",
      "title": "Q版 Blockbench 立绘",
      "cover": "images/commissions/chibi.png",
      "status": "open",             // open | queued | working | done | closed
      "slots": [5, 2],              // [总数, 已占]，可省略
      "price": "¥120 起",
      "progress": 0.6,              // 0~1，可省略
      "note": "含 2 次免费修改"
    }
  ]
}
```

## 6. 主题与视觉规约（对齐 bbs-ui-theming.md）

- 表面：banner 压字蒙版与详情 overlay 背景 `deepSurface`；卡片 `raisedSurface`；网格底 `baseSurface`；描边 `dividerColor()`。
- 强调/状态：选中描边与蒙版 `primaryColor`；状态徽标 `positiveColor/warningColor/activeColor/mutedTextColor`；图标 tint 白（走主题 atlas）。
- 圆角只走 `cornerWidget()/cornerPanel()`，绘制走 `Batcher2D.roundedBox/roundedFrame`；banner 全宽不圆角。
- 动效克制：hover 只做 alpha 蒙版/描边变化，不位移（命中判定稳定）；若做卡片入场，仅限 v2 motion tracks（translate→scale→alpha）内，settled 后短路回旧路径。
- 新 UI 代码禁止硬编码语义色；`Colors.*` 只允许出现在纯蒙版/黑阴影等非语义场合。

## 7. 响应式（Film 面板可拖拽/分屏，宽度不定）

| 容器宽 | 行为 |
|---|---|
| ≥ 900px | 完整三段：banner + 新闻 + 左右分栏（左列 ~30%） |
| 560–900px | 绘制单列收窄至 240px；新闻卡片露出 2–3 张 |
| < 560px | 绘制单列隐藏（banner 下出现「绘制单」入口按钮，点开为 overlay）；网格固定 2 列；新闻条降级为单行标题滚动条 |

高度不足时（面板被压扁）：banner 降至 96px，新闻条保持一行。

## 8. 实施切分

- **P0 骨架 + 网格**：`UIFilmHomePanel`（banner/分栏/工具栏/面包屑/空态）、`UICardGrid`、`Film.updated_at` + 相对时间展示 + 默认排序、CRUD 与键位全量迁移；其它面板仍走 `UISelectionScreen`。
- **P1 首帧缩略图**：采集、缓存、懒加载、回退卡、刷新封面。
- **P2 内容区**：新闻 + 绘制单 JSON 加载、卡片、详情 overlay、隐藏策略。
- **P3 可选**：卡片入场动效（主题 motion 约束内）、远程内容拉取（另立任务）。

验证：`compileClientJava` + `testThemeCore` + 完整 `check`；`filmEditorTest` 增补首页布局/键位/CRUD 迁移 source test。

## 9. 风险与开放问题

1. 预览小窗分辨率有限 → 封面清晰度受限；可选在视频导出时顺手存一张全分辨率 poster。
2. 多人服务器上影片键列表/保存走网络，首页统计行与排序需容忍 `updated_at` 缺失。
3. 新闻/绘制单是本地静态内容，更新要动 config；如需"改一处全局更新"需远程源（P3）。
4. `UICardGrid` 是新通用容器，需覆盖键盘导航与多选命中测试，避免回归列表视图用户习惯。
