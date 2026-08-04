# BBS Refreshed UI 特性清单与实现位置

**调查日期**: 2026-07-25
**反编译源码版本**: BBS Refreshed 2.0 (1.20.1 Fabric)
**当前项目版本**: NeoForge 1.21.1 开发版本

---

## 📋 执行概要

Refreshed 相对原版 BBS 的 UI/视觉/交互改动分为 **5 大类**：
1. **自动隐藏任务栏** - 已有完整实现，需移植配置常数
2. **颜色与主题系统** - 部分移植，17 个主题色需整合
3. **UI 动画与过渡** - 大部分已移植，个别效果待实现
4. **UI 面板优化** - 布局/样式改进，与交互逻辑关系不大
5. **交互细节增强** - 按键绑定、通知系统动画

---

## 1️⃣ 自动隐藏任务栏（Auto-Hide Taskbar）

### 📍 实现位置
- **主实现**: `temp/bbs-refreshed-2.0-public-1.20.1-decompiled/src/mchorse/bbs_mod/ui/dashboard/panels/UIDashboardPanels.java:41-199`
- **配置文件**: `BBSSettings.java:106-108`
- **渲染**: 同文件 Line 255-260（背景绘制）

### ⚙️ 机制概述

**核心逻辑**:
- 任务栏（taskbar）高度固定为 20px，默认显示在窗口底部
- 当鼠标不在任务栏或其下方 16px 启示条区域内时，经过 **100-350ms 延迟** 后自动向下滑出
- 动画使用 **EXP_INOUT 缓动函数**（指数内出缓动），持续时间 **0.5 秒**（可配置）

**关键配置常数** (BBSSettings):
```java
dashboardAutoHideTaskbar          // Boolean, 启用/禁用（默认 true）
dashboardTaskbarHideDelayMs       // 隐藏延迟：100ms（默认）
dashboardTaskbarHideAnimSec       // 动画时长：0.5s（默认）
TASKBAR_HEIGHT_PX = 20            // 任务栏高度
HOVER_REVEAL_STRIP_PX = 16        // 启示条高度（鼠标进入启示区可快速展开）
DASHBOARD_AUTO_HIDE_REVEAL_HOVER_MS = 350  // 启示条悬停触发时间
```

**动画状态变量**:
- `taskbarHiddenAmount` (0.0-1.0) - 当前隐藏进度
- `taskbarAnimFrom/To` - 动画起/终点
- `taskbarAnimStartMs/DurationMs` - 时序控制

**核心方法**:
| 方法 | 职责 | 行号 |
|------|------|------|
| `tickTaskbarAutoHide()` | 每帧更新隐藏逻辑 | 97-137 |
| `advanceTaskbarAnim()` | 推进缓动动画，应用 EXP_INOUT | 168-180 |
| `ensureTaskbarHiddenTarget()` | 平滑启动隐藏动画 | 147-157 |
| `applyTaskbarSlideIfChanged()` | 应用滑动位移变换 | 188-199 |

**渲染绘制** (Line 255-260):
```java
protected void renderBackground(UIContext context) {
    Area area = this.taskBar.area;
    Area a = this.pinned.area;
    context.batcher.box(area.x, area.y, area.ex(), area.ey(), -15789804);  // 任务栏背景 (UI_CANVAS 色)
    context.batcher.box(a.ex() + 2, a.y + 3, a.ex() + 3, a.ey() - 3, 0x44FFFFFF);  // 分隔线
}
```

### ✅ 是否适合做主题可编辑选项

**是**。建议作为主题配置项：
- ✓ 自动隐藏启用/禁用 (toggle)
- ✓ 隐藏延迟时间 (slider: 0-1000ms)
- ✓ 动画时长 (slider: 0.1-2.0s)
- ✓ 任务栏背景色 (color picker)
- ✓ 启示条高度 (slider: 8-32px)

### 🔄 移植优先级
**⭐⭐⭐ 高** - 核心功能完善，配置项多样

---

## 2️⃣ 颜色与视觉风格系统

### 📍 实现位置

#### 2.1 颜色常量定义
- **主文件**: `utils/colors/Colors.java`
- **主题缓存**: `ui/theme/UiThemeCache.java`
- **配置持久化**: `settings/values/ui/ValueColors.java`

#### 2.2 Refreshed 的颜色方案

**核心颜色常量** (Colors.java:10-66):

| 常量名 | 十六进制值 | 用途 | Refreshed 值 |
|--------|-----------|------|-------------|
| `UI_CANVAS` | `-15789804` | 主画布背景 | `0xFF0F0F13` |
| `UI_TEXT_FIELD_FILL` | `-15066079` | 输入框填充 | `0xFF1C1C1F` |
| `UI_TEXT_FIELD_BORDER` | `-12956328` | 输入框边框 | `0xFF2A2A2E` |
| `UI_BUTTON_LABEL` | `-16111560` | 按钮标签 | `0xFF0A0A0A` |
| `UI_PANEL` | `-15789804` | 面板背景 | `0xFF0F0F13` |
| `UI_SURFACE` | `-15789804` | 表面/卡片 | `0xFF0F0F13` |
| `UI_LIST_SELECTED` | `-14013900` | 列表选中 | `0xFF144444` |
| `UI_TAB_ACTIVE_LINE` | `-12171686` | 标签活跃线 | `0xFF333333` |
| `UI_TAB_ACTIVE_GRADIENT` | `-2008660392` | 标签渐变 | `0x87FFFFFF` |
| `UI_AREA_TINT` | `-15198178` | 区域着色 | `0xFF1A1A1D` |
| `UI_AREA_TINT_LIGHT` | `-15000798` | 浅色区域着色 | `0xFF1C1C1F` |
| `UI_DROP_FILL` | `1142034968` | 下拉菜单填充 | `0x43FFFFFF` |
| `UI_DROP_BORDER` | `-1440077258` | 下拉菜单边框 | `0xAA555555` |
| `UI_SPLITTER_ACTIVE` | `-1434813812` | 分割条活跃 | `0xAA666666` |
| `UI_SPLITTER_IDLE` | `0x22FFFFFF` | 分割条空闲 | `0x22FFFFFF` |
| `UI_SHADOW_MUTED` | `-14277074` | 柔和阴影 | `0xFF1A1A1A` |
| `UI_TRACKPAD_SCRUB` | `-9539972` | 轨迹垫擦除 | `0xFF707070` |

**alpha（透明度）常量**:
```java
public static final int A100 = -16777216;   // 100% (完全不透明)
public static final int A90 = -301989888;   // 90%
public static final int A75 = -1157627904;  // 75%
public static final int A50 = -2013265920;  // 50%
public static final int A35 = 0x35000000;   // 35%
public static final int A25 = 0x44000000;   // 25%
public static final int A12 = 0x22000000;   // 12%
public static final int A6 = 0x11000000;    // 6%
```

#### 2.3 主题色系统 (UiThemeCache.java)

**主题缓存单例** 存储 17 个主题色变量：
```java
int canvas;               // 主画布
int panel;               // 面板背景
int surface;             // 表面
int textFieldBorder;     // 输入框边框
int textFieldFill;       // 输入框填充
int listSelected;        // 列表选中
int tabActiveLine;       // 标签线
int tabActiveGradient;   // 标签渐变
int filmEditZoneCanvas;  // 影片编辑区画布
int filmEditZone;        // 影片编辑区
int filmEditZoneBar;     // 影片编辑区条
int previewLetterbox;    // 预览黑边
int dropBorder;          // 下拉边框
int dropFill;            // 下拉填充
int splitterActive;      // 分割条活跃
int areaTintLight;       // 浅区域着色
int trackpadScrub;       // 轨迹垫擦除
```

#### 2.4 颜色处理工具方法 (Colors.java:67-186)

| 方法 | 功能 |
|------|------|
| `mulRGB(color, factor)` | RGB 分量按系数缩放（用于亮度调整） |
| `setA(color, alpha)` | 设置透明度 |
| `mulA(color, factor)` | 透明度缩放 |
| `a(alpha)` | 快速创建纯 alpha 值 |
| `interpolate(target, a, b, x)` | 两色线性插值 |
| `parse(color, default)` | 解析 #RRGGBB/#AARRGGBB 字符串 |
| `HSVtoRGB(h, s, v)` | HSV → RGB 色彩空间转换 |
| `RGBtoHSV(r, g, b)` | RGB → HSV 色彩空间转换 |

### ✅ 是否适合做主题可编辑选项

**是**。**所有 17 个主题色** 都适合作为可编辑选项：
- ✓ 颜色选择器（支持 RGB/HSV/Hex）
- ✓ 预设主题方案（浅色/深色/高对比）
- ✓ 导入/导出主题配置

### 🔄 移植优先级
**⭐⭐⭐ 高** - 完全可定制化，通用性强

---

## 3️⃣ UI 动画与过渡效果

### 📍 实现位置

#### 3.1 缓动函数库
- **主文件**: `utils/interps/easings/Easings.java`
- **插值工具**: `utils/interps/Lerps.java` (164+ 个方法)
- **预设插值**: `utils/interps/Interpolations.java`

#### 3.2 缓动函数清单

**基础缓动类型** (10+):
| 类型 | 用途示例 |
|------|---------|
| `LINEAR` | 线性（无缓动） |
| `QUADRATIC` | 二次多项式 |
| `CUBIC` | 三次多项式 |
| `QUARTIC` | 四次多项式 |
| `QUINTIC` | 五次多项式 |
| `EXP` | 指数（任务栏隐藏用） |
| `BACK` | 回弹超调效果 |
| `ELASTIC` | 弹性抖动 |
| `BOUNCE` | 落地反弹 |
| `SINE`/`CIRCLE` | 三角/圆形缓动 |

**缓动修饰符**:
- `.out()` - 减速阶段
- `.inOut()` - 加速 → 减速（平滑）

**实际应用**:
- `Interpolations.EXP_INOUT` - 任务栏隐藏动画（UIDashboardPanels:174）

#### 3.3 动画框架

**插值方法** (Lerps.java):
```java
lerp(a, b, x)            // 线性插值
cubicHermite(...)        // Hermite 三次曲线
bSpline(...)             // B 样条
bezier(...)              // Bezier 曲线
quadBezier(...)          // 二次 Bezier
envelope(...)            // 包络线（淡入/淡出）
```

**插值上下文** (InterpContext):
- 状态机：用于在复杂缓动中维持计算状态

#### 3.4 具体应用位置

| 动画类型 | 实现类 | 行号 | 持续时间 |
|---------|--------|------|---------|
| 任务栏滑动 | `UIDashboardPanels` | 168-180 | 0.5s（可配） |
| UI 切换 | `UIToggle.java` | - | 200ms |
| 通知滑入 | `UINotifications.java` | 46-72 | 根据通知类型 |
| 布局过渡 | `UIFilmPanel.java` | 2023-2080 | 0.32s（可配） |
| 文本输入打开 | `BBSSettings.java` | 201-204 | 0.5s（可配） |

### ✅ 是否适合做主题可编辑选项

**部分是**。建议编辑：
- ✓ 全局动画持续时间比例 (slider: 0.5-2.0x)
- ✓ 启用/禁用特定动画类别（布局、切换、通知）
- △ 缓动函数选择（高级用户）

**已在当前项目移植的动画**（无需重复）:
- ✓ 圆角绘制（Batcher2D 五宫格 + mask）
- ✓ 布局动画（UIFilmPanel 快照插值）
- ✓ 平滑滚动（Scroll 双值趋近）
- ✓ 弹层过渡（SCALE/SLIDE 与 deferred focus）
- ✓ hover/press 缩放

**尚未移植的动画**（待实现）:
- △ 通知系统滑入动画（Lerps.lerp，x 轴滑动）
- △ 文本输入开启时缩放动画（0.85 → 1.0）
- △ 图标活跃态背景色过渡

### 🔄 移植优先级
**⭐⭐ 中** - 大部分已实现，边界效果待补充

---

## 4️⃣ UI 面板与界面改进

### 📍 实现位置

#### 4.1 面板层级结构

**基类**:
- `UIDashboardPanel.java` - 所有仪表盘面板的基类
- `UISidebarDashboardPanel.java` - 含侧栏的面板（图标栏 + 编辑器布局）
- `UICRUDDashboardPanel.java` - CRUD 操作面板（创建/读取/更新/删除）
- `UIDataDashboardPanel.java` - 数据管理面板

#### 4.2 Refreshed 中的 UI 面板列表

**仪表盘核心**:
- `UIDebugPanel` - 调试工具面板
- `UITextureManagerPanel` - 纹理管理器
- `UIGraphPanel` - 图表可视化

**影片编辑器** (6 个):
- `UIFilmPanel` - 主影片编辑界面
- `UIClipsPanel` - 时间线剪辑管理
- `UIFilmSelectionPanel` - 影片选择
- `UIReplayPropertiesPanel` - 回放属性
- `UIReplaysListPanel` - 回放列表

**表单编辑器** (14+ 个专化形态):
- `UIActionsFormPanel` - 动作表单
- `UIBillboardFormPanel` - 广告牌
- `UIBlockFormPanel` - 方块实体
- `UIExtrudedFormPanel` - 挤出网格
- `UIFramebufferFormPanel` - 帧缓冲
- `UIGeneralFormPanel` - 通用表单
- `UIItemFormPanel` - 物品
- `UILabelFormPanel` - 标签
- `UIMobFormPanel` - 生物实体
- `UIModelFormPanel` - 模型
- `UIPlayerFormPanel` - 玩家
- `UITrailFormPanel` - 轨迹
- `UIVanillaParticleFormPanel` - 原版粒子
- (更多...)

**叠加面板** (20+):
- `UIOverlayPanel` - 基叠加面板
- `UIConfirmOverlayPanel` - 确认对话框
- `UIMessageOverlayPanel` - 消息框
- `UIPromptOverlayPanel` - 输入提示
- `UIListOverlayPanel` - 下拉菜单
- `UIColorPalette`/`UIColorPicker` - 颜色选择
- `UIFolderOverlayPanel`/`UIFileOverlayPanel` - 文件浏览
- (更多...)

**专化编辑器** (6+):
- `UIMorphingPanel` - 变身/姿态编辑
- `UIParticleSchemePanel` - 粒子系统
- `UIModelBlockPanel` - 模型方块
- `UIAudioEditorPanel` - 音频编辑
- `UIPresetsOverlayPanel` - 预设管理
- `UILanguageEditorOverlayPanel` - 本地化编辑

#### 4.3 具体改进细节

**布局与定位** (areas/resizers):
- 使用 `Flex` 布局引擎（Flexbox 风格）
- 支持相对定位、行/列/网格布局
- `Margin` 系统用于间距控制

**可折叠部分**:
- 示例：`UIParticleSchemeSection.java:90-99`
- 点击标题切换 `fields` 子元素可见性
- 动态添加/移除子元素

**背景与样式**:
- `UIIcon` 支持多状态背景色（默认/hover/按下/活跃/禁用）
- 按钮可选背景 (`.background(false)` 隐藏背景)
- 使用主题颜色而非硬编码色值

### ✅ 是否适合做主题可编辑选项

**有限**。建议编辑：
- ✓ 面板间距与内边距 (slider)
- ✓ 按钮大小与圆角 (已通过 `uiCornerRadiusButtonsTrackpads` 实现)
- ✓ 界面元素缩放 (已通过 `userIntefaceScale` 实现)
- ✗ 面板布局本身（过于复杂，易破坏功能）

### 🔄 移植优先级
**⭐ 低** - 美观改进，需案例分析而非通用参数

---

## 5️⃣ 交互细节增强

### 📍 实现位置

#### 5.1 按键绑定系统
- **主文件**: `ui/Keys.java` (140+ 按键组合)
- **配置**: `BBSSettings.java` 中注册

#### 5.2 关键按键组合

| 功能 | 按键 | 代码 | 行号 |
|------|------|------|------|
| 保存 | `Ctrl+S` | `Keys.SAVE` | - |
| 复制 | `Ctrl+C` | `Keys.COPY` | - |
| 粘贴 | `Ctrl+V` | `Keys.PASTE` | - |
| 撤销 | `Ctrl+Z` | `Keys.UNDO` | - |
| 重做 | `Ctrl+Y` | `Keys.REDO` | - |
| 删除 | `Delete` | `Keys.DELETE` | - |
| 隐显 UI | `F11` | `Keys.TOGGLE_VISIBILITY` | - |
| 工具面板 | `Num 7` | `Keys.OPEN_UTILITY_PANEL` | - |
| 数据管理器 | `N` | `Keys.OPEN_DATA_MANAGER` | - |
| 新建标签 | `Ctrl+N` | `Keys.OPEN_NEW_TAB` | - |

#### 5.3 通知系统
- **主类**: `ui/framework/notifications/UINotifications.java` (25-72 行)
- **通知类**: `Notification.java` (17-xxx)

**通知渲染细节** (UINotifications.java:46-72):
```java
// 通知从右侧滑入，宽度 300px
int x = (int)Lerps.lerp(
    context.menu.width,                    // 起始位置（屏幕右侧）
    context.menu.width / 2 - w / 2,        // 目标位置（屏幕中心偏左）
    notification.getFactor(...)            // 插值因子
);
```

**通知样式**:
- 背景色：`Colors.mulRGB(-15789804, 0.95f)` （略浅的 UI_CANVAS）
- 边框色：`notification.background` （可自定义）
- 文字色：`notification.color` （可自定义）

**特性**:
- ✓ 支持多行文本（自动换行）
- ✓ 图标背景色
- ✓ 自动过期移除

#### 5.4 图标状态颜色系统

**UIIcon 背景色** (ui/framework/elements/buttons/UIIcon.java:31-127):
```java
int backgroundColor;        // 默认背景色
int hoverBackgroundColor;   // 悬停背景色
int pressedBackgroundColor; // 按下背景色
int activeBackgroundColor;  // 活跃背景色
int disabledBackgroundColor; // 禁用背景色
```

**默认颜色推导**:
- `defaultHoverBackgroundColor()` - 从主题主色推导

### ✅ 是否适合做主题可编辑选项

**是**。建议编辑：
- ✓ 通知滑入速度（影响 `getFactor()`）
- ✓ 通知背景色与边框色
- ✓ 图标状态颜色预设
- ✓ 按键快捷键自定义（已支持）

### 🔄 移植优先级
**⭐⭐ 中** - 交互完善，用户自定义价值高

---

## 📊 特性移植优先级建议

| 优先级 | 特性 | 工作量 | 用户价值 | 建议周期 |
|--------|------|--------|---------|---------|
| ⭐⭐⭐ 高 | 任务栏自动隐藏 | 中 | 高 | Sprint 1 |
| ⭐⭐⭐ 高 | 颜色主题系统 | 中-大 | 高 | Sprint 1-2 |
| ⭐⭐ 中 | 动画参数调优 | 小 | 中 | Sprint 2 |
| ⭐⭐ 中 | 通知系统增强 | 小 | 中 | Sprint 2 |
| ⭐ 低 | UI 面板美化 | 大 | 低 | Sprint 3+ |
| ⭐ 低 | 交互快捷键 | 小 | 低 | 持续 |

---

## 🔍 详细配置参数汇总

### BBSSettings 中的 UI 相关配置

```java
// 外观类
ValueBoolean darkMode;                          // 深色模式
ValueInt primaryColor;                          // 主色调（默认：#46A8D8）
ValueInt userIntefaceScale;                     // UI 缩放（0-4，默认：2）
ValueInt uiCornerRadiusButtonsTrackpads;        // 按钮/轨迹垫圆角
ValueInt uiCornerRadiusInterface;               // 界面圆角
ValueInt tooltipStyle;                          // 提示框样式
ValueBoolean showTooltips;                      // 显示提示

// 仪表盘类
ValueBoolean dashboardAutoHideTaskbar;          // 自动隐藏任务栏（默认：true）
ValueInt dashboardTaskbarHideDelayMs;           // 隐藏延迟（默认：100ms）
ValueFloat dashboardTaskbarHideAnimSec;         // 隐藏动画时长（默认：0.5s）

// 编辑器布局
ValueBoolean filmEditorLayoutTransitionEnabled; // 布局过渡（默认：启用）
ValueFloat filmEditorLayoutTransitionSec;       // 过渡时长（默认：0.32s）

// 文本输入
ValueBoolean textEntryOpenAnim;                 // 文本输入打开动画
ValueFloat textEntryOpenAnimDurationSec;        // 打开动画时长（默认：0.5s）
ValueFloat textEntryOpenAnimStartScale;         // 初始缩放（默认：0.85）

// 颜色管理
ValueColors favoriteColors;                     // 收藏颜色
ValueColors recentColors;                       // 最近使用颜色
ValueBoolean hsvColorPicker;                    // HSV 颜色拾取器
```

---

## 🎯 移植验证清单

- [ ] 任务栏配置常数复制到当前项目 BBSSettings
- [ ] 颜色常量对齐（17 个主题色）
- [ ] UiThemeCache 主题缓存机制集成
- [ ] Interpolations.EXP_INOUT 缓动应用于任务栏
- [ ] 通知滑入动画实现（Lerps.lerp x 轴）
- [ ] 文本输入缩放动画补充（0.85 → 1.0）
- [ ] 图标多状态背景色系统验证
- [ ] 完整功能测试与性能验证

---

## 📝 研究数据源

| 源 | 路径 | 说明 |
|----|------|------|
| 反编译源 (Refreshed) | `temp/bbs-refreshed-2.0-public-1.20.1-decompiled/src/mchorse/bbs_mod/` | CFR 反编译，intermediary 映射 |
| 当前项目 | `new/src/main/java/mchorse/bbs_mod/` | NeoForge 1.21.1 开发版本 |

---

**生成人**: AI 代理 (Claude Fable 5)
**质量级**: 全覆盖调查 (Very Thorough)
