# Film home responsive layout tiers

## Goal

影片首页当前是单一定宽布局:banner 固定 150px、广告板常驻(最窄 170px)、新闻卡固定 260×64。
小窗口下广告板挤占网格、banner 中心裁切把 1920×1080 背景图上居中的 logo 切掉、新闻卡堆叠。
按用户确认的分档方案,让首页在不同窗口尺寸下都保持可用且背景图 logo 完整可见。

## Requirements

1. 按窗口宽度分档(T0–T3)调整首页布局,带 ±8px 迟滞,切档不重置任何状态:
   - T0 (W≥1000): 现状布局,banner 150。
   - T1 (700≤W<1000): banner 120,广告板列最宽 180,卡片最小宽 180。
   - T2 (W<700): banner 96,广告板折叠为工具栏入口(overlay 打开),新闻条切紧凑单行,
     工具栏隐藏滑杆/面包屑/排序,网格卡片最小宽 160。
   - T3 (W<480): banner 64,强制列表视图;离开 T3 恢复用户手动选择的视图。
2. 高度分档:H<700 → banner 96;H<480 → banner 64 + 新闻条折叠为工具栏入口。
3. banner 背景图改为高度优先 contain 适配(logo 完整、水平居中),两侧用图像边缘
   像素延展 + 向 base/deep 色渐变过渡;底部 deep 渐变遮罩与 accent 发丝线不变。
4. 紧凑档新闻单行卡保留 tag chip/标题/日期,可点开详情,横向滚动不变。
5. 折叠入口 tooltip 复用现有键 `bbs.ui.film.home.ads` / `bbs.ui.film.home.news`,不新增语言键。

## Non-goals

- 不改远程内容链路(FilmHomeContent)、详情弹窗、CRUD 行为。
- 不做用户手动锁定布局档位的设置项。

## Acceptance Criteria

- [ ] `compileClientJava` 通过,现有测试不回归。
- [ ] 宽/高阈值与迟滞(±8px)行为正确;切档时网格选中、广告轮播页码、搜索过滤不重置。
- [ ] T0/T1 布局与现状一致(仅 banner 渲染策略变化);T2 广告板让位给网格。
- [ ] overlay 面板在极小窗口下被 UIOverlay bounds 正确钳制。
- [ ] 渲染热路径零 IO、零分配(仅复用既有字段/缓存字符串)。
