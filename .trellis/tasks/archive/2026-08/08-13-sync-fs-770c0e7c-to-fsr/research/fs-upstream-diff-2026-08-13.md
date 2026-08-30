# fs 上游差异调研:5848363c → origin/master(2026-08-13)

- **调研对象**:`temp/bbs-fs-2.3`(BBS FS mod 的 git 克隆,远端 `https://gh-proxy.com/github.com/Wemppy4/bbs-fs`)
- **基线**:本地分支 `codex/fsr-sync-20260729` @ `5848363c`("фича(доки): панель можно докать к краю всего редактора")
- **目的**:盘点上游新增提交,为"哪些改动值得移植到 NeoForge 1.21.1 项目(`new/`,BBSFSR v0.0.11)"提供依据
- **日期**:2026-08-13(只读分析,唯一写操作为 `git fetch`)

---

## 1. 结论速览

- `git fetch origin` 成功,均为 fast-forward,**无 force-push / 历史改写迹象**。
- `origin/master` 最新提交:**`770c0e7c6b69cacc6dabf70eb63bf349f24bbaa6`**,日期 **2026-08-08 23:53:22 +0200**。
- 基线落后 **19 个提交**(此前 fetch 数据是落后 3,本次 fetch 又拉到 16 个新的)。历史线性,范围内无 merge 提交,全部作者为 Wemppy4。
- 本地**没有**领先 `origin/master` 的提交(`git log origin/master..HEAD` 为空)。
- **版本号无变化**:两端 `gradle.properties` 均为 `mod_version=2.4`、`minecraft_version=1.20.4`,`fabric.mod.json` 完全一致(详见第 3 节)。
- 重要发现:上游 `origin/1.20.1` 与 `origin/1.21.1` 分支已于 2026-08-09 把 master 全量合并(merge-base = master tip),**上游自己的 Fabric 1.21.1 分支已包含这 19 个提交的 1.21.1 适配**,可作为 NeoForge 移植的直接参考(见第 7 节)。

按内容分类:功能更新 9 个、bug 修复 10 个;其中影响最大的是「热键栏九轨道 + 电影 inventory 移除」(数据格式变更)和「Dock 面板增强」两组。

---

## 2. fetch 结果与远端状态

```
From https://gh-proxy.com/github.com/Wemppy4/bbs-fs
   4220b453..770c0e7c  master     -> origin/master
   27a6866c..17183d15  1.20.1     -> origin/1.20.1
   218bcd31..a982f1dc  1.21.1     -> origin/1.21.1
```

三条更新均为 fast-forward(无 `+`/`forced update` 标记)。注:上次 fetch 时 origin/master 停在 `4220b453`(即"落后 3 个提交"的依据),本次前进到 `770c0e7c`。

---

## 3. 版本号核查(5848363c vs origin/master)

| 文件 | 字段 | 5848363c | origin/master | 变化 |
|---|---|---|---|---|
| `gradle.properties` | `mod_version` | 2.4 | 2.4 | **无** |
| `gradle.properties` | `minecraft_version` | 1.20.4 | 1.20.4 | 无 |
| `gradle.properties` | `yarn_mappings` | 1.20.4+build.1 | 1.20.4+build.1 | 无 |
| `gradle.properties` | `loader_version` / `fabric_version` | 0.16.14 / 0.91.1+1.20.4 | 同左 | 无 |
| `fabric.mod.json` | 全文件 | — | — | **逐字节一致**(version 用 `${version}` 占位,depends `minecraft ~1.20.4`) |

**结论:上游这 19 个提交没有触碰任何版本号。** 同步这批改动不会引入版本号变更,与"版本号不能随意变更"的要求不冲突。(注意:master 分支构建目标是 MC 1.20.4;真正的 1.20.1 构建在 `origin/1.20.1` 分支上,同样是 `mod_version=2.4`。)

---

## 4. 新提交总览(5848363c..origin/master,时间正序)

| # | 哈希 | 日期 | 中文主题 | 类型 |
|---|---|---|---|---|
| 1 | `6f4f9ec9` | 07-30 | 停靠面板:最大化、布局撤销、隐藏面板、拖标签页 | 功能更新 |
| 2 | `0cecc8c9` | 07-30 | 界面:clip 裁剪把"角坐标"当"宽高"用,越界不裁 | bug 修复 |
| 3 | `4220b453` | 07-30 | 停靠:属性面板比所有者活得久、预览尺寸跟不上 | bug 修复 |
| 4 | `5b8d714f` | 08-02 | 界面:builder 返回类型变更导致 addon 崩溃(ABI 兼容) | bug 修复 |
| 5 | `378bac69` | 08-02 | 界面:IK 与物理拿到专属图标,替换占位图标 | 功能更新 |
| 6 | `95a110e2` | 08-04 | 编辑器 resize 的一行修复(原文英文) | bug 修复 |
| 7 | `8a293161` | 08-04 | 电影:热键栏改九条轨道,电影 inventory 移除 | 功能更新 |
| 8 | `8c48a185` | 08-04 | 界面:物品轨道按"家族"配色并恢复图标 | 功能更新 |
| 9 | `c02aa6e6` | 08-04 | 电影:旧格式迁移后"捡起的物品"消失 | bug 修复 |
| 10 | `7f99e757` | 08-05 | 界面:小数 GUI 缩放不再撕裂文本和图标 | 功能更新 |
| 11 | `459e9db7` | 08-05 | 界面:背景色调阶梯重构(中性、均匀、柔和) | 功能更新 |
| 12 | `52c5db8f` | 08-05 | 界面:阴影/高光拆开关,浮动面板压暗/光晕可调 | 功能更新 |
| 13 | `d0f3754d` | 08-05 | 界面:"选择"与"编辑"按钮不再粘连 | bug 修复 |
| 14 | `bb1ac00f` | 08-05 | 界面:开关控件轻量化重绘 | 功能更新 |
| 15 | `fe552a58` | 08-05 | 界面:滑条有了步进,不再产生 0.123 | 功能更新 |
| 16 | `0773f9df` | 08-05 | 设置:FOV 改回 trackpad 而非滑条 | bug 修复 |
| 17 | `861257a7` | 08-07 | gizmo:"视图"空间下正对相机,不再偏斜 | bug 修复 |
| 18 | `baafc5a4` | 08-08 | 透明:全半透明纹理的模型不再自透 | bug 修复 |
| 19 | `770c0e7c` | 08-08 | 渲染:物品 form 不再渲染成一团乱面 | bug 修复 |

所有提交信息为俄语(1 条英文),多数带 `Co-Authored-By: Claude Opus 5`,正文含详细设计说明,移植时值得回读原文。

---

## 5. 逐提交分析

### 5.1 `6f4f9ec9` 功能(停靠面板):最大化、布局撤销、隐藏面板、拖标签页

- **行为变化**(dock 系统大增强,约 +1070/-133 行):
  - 堆叠(stack)的**标签页本身成为拖拽手柄**:非活动面板也能直接拖走;按下 4px 内仍算点击,超过进入拖拽;Esc 中途取消;拖拽时光标下跟随"图标+面板名"。
  - 悬停在别的标签上 0.5 秒自动切换过去(便于把面板投进被遮住的目标);插入位置画插入符;**Shift+中心投放 = 交换两个面板位置**(恢复了此前被删的 `copyWithSwappedPanels`)。
  - 投放高亮从平涂改为**方向渐变**(贴紧目标边、向内消散)+ 边缘实线。
  - **Ctrl+Space 把光标下面板最大化到整个编辑器**(不写入保存的布局树);**Ctrl+Alt+Z 撤销布局改动**(不可变树,快照零成本)。
  - 面板可**隐藏/恢复**(拖拽条菜单),隐藏状态持久化,若面板经预设/旧存档重新入树则自动摘除隐藏标记;面板有了显示名称(菜单项与标签 tooltip);布局解锁状态跨重启保留;splitter 最小尺寸改按像素而非比例(深层树里不再被压成 0);双击 splitter 对齐;重置布局同时清隐藏列表且可撤销。
  - 顺带修复:undo overlay 改为 strict 匹配,否则 Ctrl+Alt+Z 会被吞成 Ctrl+Z 误撤电影编辑(电影/表单/模型编辑器同改)。
- **关键文件**:`ui/framework/elements/layout/UIDockLayout.java`(+864)、`ILayoutSource.java`、`settings/values/ui/ValueEditorLayout.java`(新增 `hidden`/`unlocked_docks` 持久化键)、`EditorLayoutNode.java`(`copyWithSwappedPanels`)、`ui/Keys.java`(新键位 `DOCK_MAXIMIZE`、`DOCK_UNDO_LAYOUT`)、`UIFilmPanel`、`UIParticleSchemePanel`、三个 `*UndoKeys`、`en_us.json`/`ru_ru.json`(+17 条)。
- **移植注意**:BBS 自研 UI 框架纯 Java,理论可平移;前提是 `new/` 已同步基线附近的 dock 重构(`8adefc9c` 把两份 UIDockLayout 合一、`5848363c` 允许停靠到编辑器边缘)。布局持久化格式新增两个键(读取向后兼容)。键位用 GLFW 常量,与 loader 无关。语言文件需同步(注意 `new/` 若已汉化要补译)。

### 5.2 `0cecc8c9` 修复(界面):裁剪矩形算错(角坐标 vs 宽高)

- **行为变化**:`Batcher2D.clip(x,y,w,h)` 在全项目 7 处被按 `box(x1,y1,x2,y2)` 的角坐标语义调用,导致裁剪区向右/下扩大,右、下边缘完全不裁。最直观的表现:时间轴上超出右边界的 clip 会画到相邻面板上(旧版被属性面板恰好盖住,面板搬进编辑区后暴露)。同样影响 dope sheet、回放编辑器、时间轴标尺。新增语义明确的 `clipBox(x1,y1,x2,y2)`,7 处调用改用之。
- **关键文件**:`Batcher2D.java`(新增 `clipBox`)、`UIClips.java`、`UIReplaysEditor.java`、`UIKeyframeDopeSheet.java`、`TimelineRulerRenderer.java`。
- **移植注意**:如果 `new/` 继承了同样的调用写法,这是**潜伏的真 bug**,建议优先移植;改动小、无平台 API。

### 5.3 `4220b453` 修复(停靠):属性面板残留 + 预览尺寸滞后

- **行为变化**:关键帧编辑器/clip 编辑器的属性面板寄宿在编辑区(而非编辑器自身)下,编辑器被移除时面板残留(可点击、层层堆叠);现在 `removeFromParent()` 级联带走。预览自动尺寸原来只挂在"窗口 resize + splitter 拖拽结束"上,面板最大化/投放/撤销/切标签都不触发;`onSplitterDragEnd` 回调改为 `onLayoutSettled`(布局落定后触发、拖拽过程中静默)。
- **关键文件**:`UIClipsPanel.java`、`UIKeyframeEditor.java`、`UIDockLayout.java`、`UIFilmPanel.java`。
- **移植注意**:语义上依赖 5.1 的布局改动,应与 `6f4f9ec9` 成组移植。

### 5.4 `5b8d714f` 修复(界面):UITrackpad 的 addon 二进制兼容

- **行为变化**:此前把 builder 方法上移到共同基类 `UINumericInput<T>` 后,self 类型擦除为基类,针对旧签名(如 `limit(DD)LUITrackpad;`)编译的 addon 抛 `NoSuchMethodError`。本提交在 `UITrackpad` 里加回 139 行**协变覆写**(方法体只调 `super`),新旧二进制都能解析,行为不变。
- **关键文件**:`ui/framework/elements/input/UITrackpad.java`(+139)。
- **移植注意**:这是针对 fs(Fabric 1.20.x)插件生态的 ABI 修复。`new/` 是否需要取决于:① 是否做了同样的 `UINumericInput` 基类重构;② 是否承诺对自身 addon 的二进制兼容。若 `new/` 生态尚小或 API 尚未冻结,可以不移植,但建议记录该坑(基类化 builder 会破坏 ABI)。

### 5.5 `378bac69` 功能(界面):IK 与物理的专属图标

- **行为变化**:新增 `Icons.IK`(图集 224,160)与 `Icons.PHYSICS`(240,160)。表单编辑器的 IK/物理标签页(原借用 LIMB/DROP)、时间轴类别图标、物理目标轨道(原 TIME 占位)、IK 目标/极向目标轨道(原无图标)全部换用。`icons.png` 图集更新。
- **关键文件**:`Icons.java`、`icons.png`(二进制)、`UIReplaysEditor.java`、`UIReplaysEditorUtils.java`、`UIModelForm.java`。
- **移植注意**:必须同步 `icons.png` 二进制(注意与 5.8 的更新叠加,直接取 `8c48a185` 之后的版本);核对 `new/` 图集上 (224,160)/(240,160)/(0,176) 是否空闲。

### 5.6 `95a110e2` 修复:编辑器 resize(一行)

- **行为变化**:`UIKeyframeEditor.resize()` 时同时 resize 其参数面板(`this.editor`),修复窗口/布局尺寸变化后参数面板错位。唯一一条英文提交信息("bug fix with editor resizing")。
- **关键文件**:`UIKeyframeEditor.java`(+1)。
- **移植注意**:随 5.3 一并处理即可。

### 5.7 `8a293161` 功能(电影):热键栏九轨道,电影 inventory 移除 ⚠ 数据格式变更

- **行为变化**(本批**影响最大**的提交,+676/-169):
  - 旧模型:主手是独立通道,热键栏是"整个电影一份 inventory 快照"——一个格子两个主人。播放时 `equipStack(MAINHAND)` 对玩家等于写 `inventory.main[selectedSlot]`,换槽瞬间会把物品写进"刚离开的格子";inventory 按引用共享,播放中消耗的物品栈会**改坏电影数据本身**。
  - 新模型:热键栏 = 九个通道 `item_slot_0..8`;**主手不再单独存**,主手 ≡ `hotbar[selected_slot]`(非玩家实体 selectedSlot 恒 0)。`Film.inventory` 字段整个删除(`replays/Inventory.java` 删除);播放期只"借用"九格+盔甲+副手,停止时归还。UI 上"Replace inventory"按钮改为"**Record hotbar**"(把当前热键栏一次性写为第一人称 replay 光标处的 key)。
  - 旧数据迁移:新文件 `FilmLegacy`,**按 tick 重放旧运行时逻辑**生成九通道 key(不是简单搬运,否则单 key 通道的外推语义会导致偏差);inventory 背景只给第一人称 replay。
  - 网络包 `SERVER_APPLY_FILM_PLAYER_SETTINGS` 载荷从 inventory 字节流改为 `selectedSlot + packEquipment` 打包装备。
  - 顺带:玩家退出世界 / 服务器停止时正确停止 ActionPlayer,不再出现"穿着电影道具退出、自己东西找不回"的情况。
- **关键文件**:`Film.java`、`FilmLegacy.java`(新)、`replays/Inventory.java`(删)、`ReplayKeyframes.java`(+161)、`ActionPlayer.java`、`ActionManager.java`、`IEntity`/`MCEntity`/`StubEntity`(新增 hotbar API 与 `isMainHandInHotbar()`)、`ClientNetwork.java`/`ServerNetwork.java`、`UIFilmPanel`、`UIFilmPlayerSettingsOverlayPanel`、`UIReplaysEditor`、`KeyframeChannel.java`、`Recorder.java`。
- **移植注意**:
  - **电影存档格式变更**(删 `inventory` 组、增 9 个 `item_slot_*` 通道):必须连 `FilmLegacy` 迁移一起移植,并与 5.9(`c02aa6e6`)捆绑,否则旧电影行为回归。
  - 网络包结构变化:`new/` 用 NeoForge 网络 API(payload/StreamCodec),需要按其协议层重写,不能照抄 Fabric `PacketByteBuf` 代码。
  - 触及 `PlayerInventory`/`EquipmentSlot` 等 MC 类,1.21.1 mojmap 命名不同(如 `Inventory`, `EquipmentSlot`),且 1.21.x 物品栈 NBT→DataComponent 变化会影响 ItemStack 序列化,迁移代码要按 `new/` 现状适配。
  - 若 `new/` 的电影数据已有用户存档,还要考虑"fs 旧格式 → fs 新格式"与"new/ 自身旧版"两条迁移路径的兼容。

### 5.8 `8c48a185` 功能(界面):物品轨道分家族配色 + 图标

- **行为变化**:14 条物品轨道原先同为橙色、难以区分。现在:热键栏九条从橙色渐变到莓红(`Colors.ORANGE`→`0xe0245e`),副手(紧跟其后)接续更亮一号(`0xff3a74`);盔甲走冷色,自头盔向靴子渐深(`0x8fd0ff`→`0x407cc0`);`selected_slot` 是"指针"不属于两族,改为白色。图标:新 `Icons.HOTBAR`(0,176)只标第一条热键栏轨道,盔甲四件用回各自的头盔/胸甲/护腿/靴子图标,副手用 LIMB。
- **关键文件**:`UIReplaysEditor.java`、`Icons.java`、`icons.png`。
- **移植注意**:依赖 5.7 的 `hotbarChannelId`/九轨道结构,应随其后移植。

### 5.9 `c02aa6e6` 修复(电影):旧格式迁移后"捡起的物品"消失

- **行为变化**:5.7 的后续修复。旧格式电影只在开播时替换一次 inventory,播放中捡到空格子的物品会一直显示;新九轨道每 tick 重写所有格子,把它抹掉了。修复:**空通道不发言**——`applyEquipment` 对无 key 的通道不动对应格子(开播时统一清空一次,防玩家私物入镜);录制的电影不受影响(record 写满九槽)。另外旧 inventory 里的盔甲/副手在 replay 自身通道为空时也迁移为起始 key(`migrateWorn`)。
- **关键文件**:`ActionPlayer.java`、`FilmLegacy.java`、`ReplayKeyframes.java`(新增 `drivesHotbarSlot`)。
- **移植注意**:与 5.7 不可分割,一起移植。

### 5.10 `7f99e757` 功能(界面):小数 GUI 缩放平滑(pixel art shader)⚠ 渲染栈

- **行为变化**:`ui_scale` 为 1.25/1.5/1.75 等小数时,8px 字形被不均匀放大(部分像素行加倍),文字和图标呈"撕裂"状。新增 pixel art 平滑:自定义 core shader(`pixelart`、`pixelart_text`、`pixelart_text_intensity` + `include/bbs_pixelart.glsl`),`texelFetch` 手动采样、premultiplied alpha 加权,只把纹素间的接缝模糊一个屏幕像素;**整数倍缩放数学上退化为 nearest**(与旧行为逐像素一致)。vanilla 文本 program 与世界文字共享,故仅在 BBS UI 绘制期间通过 `GameRendererMixin` 替换(`UIScreen.render` 用 `PixelArt.setDrawingUI(true/false)` 括起);shader 编译失败自动回退 vanilla 路径。新设置 `pixel_art_smoothing`(默认开)。用户显式开启线性过滤/mipmap 的纹理、以及 vanilla ItemRenderer 画的物品图标不受影响。
- **关键文件**:`BBSShaders.java`(+61)、`PixelArt.java`(新)、`mixin/client/GameRendererMixin.java`、`UIScreen.java`、`Batcher2D.java`、`BBSSettings.java`、9 个 shader 资源文件(`assets/bbs/shaders/core/...`)。
- **移植注意**:**本批移植成本最高的一条**。① mixin 目标 `GameRenderer.getRenderTypeTextProgram` 是 yarn 名,mojmap/NeoForge 下为 `getRendertypeTextShader` 等,注入点要重找;② 1.21.1 的 core shader JSON/GLSL 版本与 1.20.4 有差异(1.21.x 起 shader 定义和 `moj_import` 机制有调整),9 个 shader 文件要按 1.21.1 格式校验;③ 若 `new/` 的 UI 缩放实现已不同(比如用 guiScale 原生机制),该特性可能需要重设计。建议单独立项评估,不阻塞其它移植。

### 5.11 `459e9db7` 功能(界面):背景色调阶梯重构

- **行为变化**:UI 四级表面色(deep/chrome/base/raised)与分隔线全部换值:去除色相(纯中性灰),亮度在 OKLab 感知空间均匀步进 0.022,明/暗主题互为镜像;取值参考 Essential 客户端的灰阶(暗色主底 `#181818`、卡片 `#1d1d1d`、边框 `#222222`)。此前四级中有两级仅差 0.005 亮度,层次不可读。整体观感:暗色主题更暗、层次更清楚。
- **关键文件**:`BBSSettings.java`(常量 +28/-10)。
- **移植注意**:纯常量替换,零平台风险;但会**改变全 mod 观感**,与 5.12/5.14 属同一波"主题刷新",建议三个一组决策(要么都要,要么都不要),避免混搭出四不像。若 `new/` 有自己的主题系统(`docs/theme-spec` 存在于 new 的 worktree),需按其主题层对应。

### 5.12 `52c5db8f` 功能(界面):阴影/高光独立开关,浮动面板可调

- **行为变化**:原"Shadows"设置同时控制底部阴影和左上高光边;现在拆为 `interface_shadows`(默认开)与 `interface_highlights`(默认**关**,强度 0.35→0.15);新增 `overlay_background_opacity` 滑条(浮动面板背后的压暗度,0=完全可见,默认 0.5)与 `overlay_gradient_border` 开关(浮动面板/右键菜单的主题色渐变光晕)。顺带完成 `bevelBox`→`surfaceBox` 更名(此前更名不彻底导致 client 编译失败)。
- **关键文件**:`Batcher2D.java`、`UIButton`/`UICirculate`/`UIToggle`/`UIContextMenu`/`UIOverlay`/`UIOverlayPanel`/`Scroll`、`BBSSettings.java`、语言文件(+4 设置项)。
- **移植注意**:`bevelBox`→`surfaceBox` 更名会波及 `new/` 中所有调用点;4 个新设置项需同步语言文件。

### 5.13 `d0f3754d` 修复(界面):"选择/编辑"按钮粘连(一行)

- **行为变化**:`UINestedEdit` 的两颗按钮间距从 0 改为 `UIConstants.MARGIN`,replay 属性、replay overlay、模型块编辑器等处两键不再连成一体。
- **关键文件**:`UINestedEdit.java`(1 行)。
- **移植注意**:一行,顺带移植。

### 5.14 `bb1ac00f` 功能(界面):开关控件(UIToggle)轻量化

- **行为变化**:去掉黑描边;关闭态轨道 = `deepSurface()`(与输入框同的"凹槽"),开启态 = 主题色;把手改中性 `0xffc4c4c4` 并与轨道底缘对齐(从轨道里"升起来");尺寸 22x10/12x12 → 20x8/10x10。顺带修复浅色主题下它仍画深色轨道的问题。
- **关键文件**:`UIToggle.java`。
- **移植注意**:依赖 5.11 的色调阶梯与 5.12 的 `surfaceBox` 签名(第四参 shadow/border 语义),三者一组。

### 5.15 `fe552a58` 功能(界面):滑条步进

- **行为变化**:`UISliderTrackpad` 拖动值落到**绝对网格**(以 0 为原点的步进倍数),不再出现 0.123:默认步进从范围自动切出("范围的 1% 向上取整到 1/2/5×10ⁿ",如占比→0.01、角度→5、轴缩放→0.1);可 `.snap(x)` 显式指定(`ui_scale` 声明 0.25);Shift÷5、Alt÷25(整除,粗细网格嵌套);Ctrl 仍按 `increment` 大刻度;**文本输入、方向键、滚轮不受步进限制**(精确值仍可手输);浮点尘埃清理(0.1×3 = 0.30000000000000004 → 0.3);整数滑条从 (int) 截断改为取整,修复"落后光标、负数不对称"。设置值体系新增 `slider(double step)`(`BaseValueNumber` + 三个子类协变覆写)。
- **关键文件**:`UISliderTrackpad.java`(+151)、`UIValueFactory.java`、`BaseValueNumber.java`、`ValueDouble/Float/Int`、`BBSSettings.java`。
- **移植注意**:纯 UI/设置框架;前提是 `new/` 已有 `UISliderTrackpad`(基线中 `9496cbc3` 一波引入的新控件,若 new/ 未同步到那批,先补前置)。

### 5.16 `0773f9df` 修复(设置):FOV 回到 trackpad(一行)

- **行为变化**:0..180 的滑条自动步进为 2 度,对 FOV 太粗;去掉 `.slider()`,FOV 回到 trackpad(0.25°/px,Shift 1°,Alt 0.05°,可直接输入数字)。
- **关键文件**:`BBSSettings.java`(1 行)。
- **移植注意**:一行;逻辑上是对 5.15 的回调整,随其后移植。

### 5.17 `861257a7` 修复(gizmo):"视图"空间下正对相机

- **行为变化**:VIEW 变换空间下,gizmo 的轴只是与屏幕**平行**而非**面向**眼睛,透视投影下偏离屏幕中心时看起来歪(实测 70° FOV 画面角落:Z 轴投影长出 28px,视图环变椭圆 26%、圆心偏 1px)。修复:绘制矩阵第三列替换为"gizmo→眼睛"的单位射线(`applyViewShear`),仅作用于绘制与拾取 stencil 两个 pass(保持像素级一致);`lastRenderMatrix` 在 shear 前捕获,拖拽数学的正交基不动;约束辅助线特意画在 shear 之外(它必须沿真实拖拽轴)。屏幕中心处射线即相机 Z,基回归单位阵,过中心无跳变。
- **关键文件**:`Gizmo.java`(+87)、`GizmoDrag.java`、`TransformSpace.java`。
- **移植注意**:JOML 矩阵运算,与 MC 版本无关;前提是 `new/` 的 Gizmo 与 fs 同源且已同步基线的 gizmo 大重构。

### 5.18 `baafc5a4` 修复(透明):全半透明纹理模型不再自透

- **行为变化**:颜色 alpha=1 但纹理几乎全半透明的模型走"两 pass 拆分"路径时,立即不透明 pass 画不出任何像素→深度全无,整个网格进了**关深度写**的延迟 pass,背面混到正面之上(边缘亮条、细节丢失;社区流传的"alpha 调到 99% 能修"就是切到了整体延迟分支)。修复:延迟 pass 中**实体几何恢复深度写**,form 之间的可见性靠 far→near 排序保证;平面 form(billboard、framebuffer 屏、label 部件)保持不写深度(无自遮挡需求),构造器显式拆分并注释。
- **关键文件**:`cubic/ModelInstance.java`、`forms/FormTranslucentQueue.java`。
- **移植注意**:`FormTranslucentQueue` 是 BBS 自研延迟透明队列,语义与 MC 版本弱耦合,但 `depthMask` 等 `RenderSystem` 调用点在 1.21.1 中名称/用法一致性需核对;与 5.19 同主题,务必一起移植(5.19 在其之上继续改)。

### 5.19 `770c0e7c` 修复(渲染):物品 form 不再渲染成一团乱面

- **行为变化**:vanilla `ItemRenderer` 把普通物品放进 `entity_translucent_cull` 层,而延迟队列按层名含 "translucent" 的**子串匹配**来拦截——所有物品/方块都被当成半透明延迟且不写深度,物品挤出模型的十几层面片相互堆叠成"渣"。修复:`RenderLayerCommand` **恒写深度**(参数从构造器移除,规则只有一条),与 vanilla 在这些层上写深度的行为一致;后方 form 的可见性同样靠 far→near 排序。
- **关键文件**:`forms/CustomVertexConsumerProvider.java`、`forms/FormTranslucentQueue.java`。
- **移植注意**:与 5.18 捆绑;1.21.1 里 `RenderLayer`(mojmap `RenderType`)的名称与常量组织不同,"按名匹配 translucent"的过滤逻辑在 `new/` 中的对应实现需按语义核对。

---

## 6. 移植分组建议(按依赖关系)

| 组 | 提交 | 主题 | 风险/成本 |
|---|---|---|---|
| A | `6f4f9ec9` → `0cecc8c9` → `4220b453` → `95a110e2` | Dock 面板增强与修复 | 中(纯 UI 框架,量大;0cecc8c9 是独立真 bug 修复可单独提前) |
| B | `8a293161` → `8c48a185` → `c02aa6e6` | 热键栏九轨道 + inventory 移除 | **高**(存档格式 + 网络包 + MC 物品 API;三个必须捆绑) |
| C | `459e9db7` → `52c5db8f` → `bb1ac00f` → `d0f3754d` | UI 主题刷新(色阶/阴影高光/开关/间距) | 低(常量与小控件;注意 bevelBox 更名波及面) |
| D | `fe552a58` → `0773f9df` | 滑条步进 | 低-中(依赖 UISliderTrackpad 前置) |
| E | `baafc5a4` → `770c0e7c` | 延迟透明队列深度写修复 | 中(渲染语义清晰,验证成本在跑通场景) |
| F | `861257a7` | gizmo VIEW 空间修复 | 低-中(JOML 数学,独立) |
| G | `7f99e757` | 小数缩放 pixel art 平滑 | **高**(shader + mixin 注入点,1.21.1 渲染差异大,建议单独评估) |
| H | `378bac69`(+`8c48a185` 的图集部分) | 图标资源 | 低(二进制资源同步) |
| I | `5b8d714f` | UITrackpad ABI 兼容 | 视 `new/` 的 addon ABI 策略,可不移但应记录 |

---

## 7. 上游分支状态(对移植的参考价值)

`git branch -a`(fetch 后):

```
* codex/fsr-sync-20260729        (HEAD, 5848363c)
  master                         (本地旧位置:9b28a8a4,远落后于 origin/master,勿用)
  remotes/origin/HEAD -> origin/master
  remotes/origin/master          770c0e7c | 2026-08-08 | MC 1.20.4 | mod 2.4
  remotes/origin/1.20.1          17183d15 | 2026-08-09 | MC 1.20.1, fabric 0.92.1  | mod 2.4 | 领先 master 293 提交
  remotes/origin/1.21.1          a982f1dc | 2026-08-09 | MC 1.21.1, yarn 1.21.1+build.3, fabric 0.116.12 | mod 2.4 | 领先 master 40 提交
  remotes/origin/1.21.11         c22d89aa | 2026-08-02 | MC 1.21.11, loader 0.19.3, fabric 0.141.4 | mod 2.4 | 领先 master 160 提交
  remotes/origin/2.5             58c07747 | 2026-07-28 | master 历史中的旧快照(基线前 4 条),无独有提交
```

- **`origin/1.21.1`(Fabric 1.21.1)与 `origin/1.20.1` 的 merge-base 都是 `770c0e7c` = master tip**:两分支已于 2026-08-09 全量合并这 19 个提交。也就是说,**上游作者已经把这批改动适配到了 MC 1.21.1(Fabric)**——移植组 B/E/G 这类涉及 MC API 的改动时,`origin/1.21.1` 分支上对应文件的形态是最省事的对照答案(yarn→mojmap 仍需换名,但语义适配已做)。
- `origin/1.21.11` 的 merge-base 是 `4220b453`,即它只含前 3 个新提交,后 16 个尚未合并(截至本次 fetch)。
- 本地 `master` 分支停在 `9b28a8a4`,是很旧的位置,基线核对请以 `codex/fsr-sync-20260729`(5848363c)为准。

---

## 8. 基线核对数据(供另一 agent 使用)

`git log --oneline -30 5848363c`(基线及其前 29 条):

```
5848363c фича(доки): панель можно докать к краю всего редактора
8adefc9c рефактор(доки): один UIDockLayout вместо двух копий
d6a8c847 фича(воспроизведение): ванильные шаги и партиклы спринта
58c07747 фича(редактор фильмов): кадр остаётся в мире после выхода из BBS
2774a26a фикс(фильмы): reset контекста не сбрасывал превью-оси
b26114dd фича(интерполяции): лесенка по тикам
e9b8d160 ui(кости): дерево костей рисуется аутлайнер-ветками
21737add ui(кости): бон-пикер для крепёжной кости бодипарта + сайдбар по контенту
f0478d46 ui(кости): бон-пикер — попап-дерево с поиском и пипетка со стенсил-пиком
c4b7e6f2 фикс(редактор форм): выбранная кость и развёрнутые секции переживают пересборку
952e4da7 ui(ик): блендер-имена, голые пикеры костей, плейсхолдер цепи, шире панель
eed2b45e ui(ик): оси суставов — цветом цифр и тултипами вместо букв
eb503a7f фикс(ик): буква оси в строке сустава наконец видима
6530acbb ui(ик): замок — обычная квадратная иконка-кнопка, без пиксельных сдвигов
0e40c8e1 ui(ик): замок вплотную к букве оси — сдвиг по замеру глифа в атласе
afef412c ui(ик): замок с подсветкой при активном локе + глиф прижат к букве оси
bf2c0239 ui(ик): замок оси — иконка замочка вместо тумблера, ужат отступ слева
836a801f ui(ик): панель проще — база/дополнительно, превью цепи, сустав строкой
f3e11331 фича(ик): классик-солвер на 2 кости — опция цепи поверх ядра
a7b7e672 фича(камера): трекер авторится полётом и орбитой, а не только трекпадами
b4ecd8de Revert "фикс(кривые): парсер опций шейдеров ловит отрицательные/научные дефолты и табы"
e13dd08a фича(интерфейс): превью кейфреймов при наведении
875b4b96 ui(слайдер): ручной ввод по средней кнопке открывается с выделенным числом
86cb04cb настройки: явный признак слайдера у числовой настройки
7a4c5a04 интерфейс: ограниченные углы и остальные панели на слайдер
a85ce7a2 интерфейс: доли и ограниченные параметры переведены на слайдер
9496cbc3 фича(интерфейс): слайдер-трекпад на общей базе с трекпадом
cc7d423a настройки(орбита): тугл авто-орты при снапе на ось
0e7de511 фича(орбита): клик по оси навигационной сферы включает орту
07db2337 фикс(орта): ближняя плоскость на камере, тугой бокс отсечения
```

本地领先提交:`git log --oneline origin/master..HEAD` 输出为空,`git status -sb` 显示 `behind 19` —— **本地无任何领先提交,纯落后状态**。

---

## 9. 异常与备注

1. **无异常**:fetch 一次成功(gh-proxy 正常,约 12 秒);三个分支全部 fast-forward,无 force-push/历史改写;范围内无 merge 提交。
2. 名义澄清:master 分支的构建目标是 **MC 1.20.4**(gradle.properties),不是 1.20.1;1.20.1 是独立分支 `origin/1.21.1`/`origin/1.20.1` 家族中的一支。对"fs = 1.20.1 分支"的习惯称呼不影响本报告结论,但写文档/沟通时建议注明。
3. `temp/bbs-fs-5848363c-reference`(只读参考克隆)未做任何操作。
4. 上游提交信息(俄语)包含大量设计动机与实测数据(如 gizmo 修复中的像素级测量、透明修复中的"99% 修 bug"传闻解释),移植遇到不确定语义时,优先回读 `git log -1 --format=%B <hash>` 原文。
5. 本地克隆的 `master` 分支指向很旧的 `9b28a8a4`,不要误用它做 diff 基线。
