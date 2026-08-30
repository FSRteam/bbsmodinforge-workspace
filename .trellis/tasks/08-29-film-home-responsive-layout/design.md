# Design: Film home responsive layout tiers

## 分档模型

两根独立轴,各带 ±8px 迟滞:

- **宽度档 `widthTier`**(T0>T1>T2>T3,越往后越紧凑):驱动广告板折叠、新闻紧凑、
  工具栏精简、网格最小卡宽、T3 强制列表。
- **高度档 `heightStep`**(0=full, 1=compact@h<700, 2=tiny@h<480):只压 banner 高度,
  step 2 时把新闻条折叠为入口。宽屏矮窗不触发广告板折叠(列宽足够)。

```
widthTier:  W≥1000 → T0 · W≥700 → T1 · W≥480 → T2 · else T3
heightStep: H≥700 → 0 · H≥480 → 1 · else 2
bannerH = {T0:150, T1:120, T2:96, T3:64} 再被 heightStep 覆盖(min(档位值, 高度值))
```

迟滞实现:`below(value, threshold, alreadyBelow)`:已在阈值下 → `value < threshold+8`
才离开;在阈值上 → `value < threshold-8` 才进入。宽度/高度各自按当前档位逐界判断。

## UIFilmHomePanel 改动

- 常量:`BANNER_H` 拆为 `BANNER_H_FULL/COMPACT/TINY`(150/120/96/64 归档),
  `CONTENT_Y` 常量删除(relayout 内推导)。
- 新字段:`banner`(原局部变量提为字段)、`title`、`widthTier`、`heightStep`、
  `userGridMode`、`boardEntry`/`newsEntry`(UIIcon 入口)。
- `relayout()`:先算档位 → `applyTier()`(可见性/紧凑/强制列表,只在档位变化时执行)→
  再按 bannerH、新闻条高度、广告板列宽定位全部区块。banner 高度变化时同步重定位
  title/stats(`xy(12, max(4, bannerH-58))` 等)。
- `applyTier()`(档位变化才跑,不重置状态):
  - collapsed = widthTier ≥ T2:`board.setVisible(!collapsed)`、`boardEntry.setVisible(collapsed)`、
    滑杆/面包屑/排序隐藏、`newsStrip.setCompact(collapsed)`。
  - heightStep==2:`newsEntry` 显示、`newsStrip` 隐藏;否则按内容空/拉取中决定。
  - 进入 T3:记 `userGridMode=gridMode`,`setView(false)`;离开 T3:`setView(userGridMode)`。
- `FilmCardRenderer.preferredCardWidth()`:`max(CARD_W*scale, 档位最小卡宽)`(T1=180, T2=160)。
  `UICardGrid` 的 `cardWidth = usable/columns ≥ preferred`,天然保证最小卡宽。
- `renderBanner()`:高度优先 contain——`scale=area.h/texH`,图水平居中;两侧用纹理
  左右 1px 边缘列拉伸铺满(texturedBox 子像素 UV),再叠 `deepSurface` 横向渐变
  (外缘 A85 → 图缘 A0)过渡;contain 宽度 ≥ 面板宽时直接走整幅绘制(不裁切,优于 cover);
  仅面板比图还窄的极端情况才退回原 cover 裁切。底部渐变+发丝线不变。

## UINewsStrip 改动

- `setCompact(boolean)` + `private int cardHeight()`(紧凑 22 / 常规 64),
  `getPreferredHeight()`、`pick()`、`renderStrip()`、spinner 全部走 `cardHeight()`。
- 紧凑渲染:单行圆角/方卡,tag chip + 限宽标题 + 右对齐日期,无缩略图/图标;
  命中测试与点击开详情复用现有逻辑。

## 折叠入口 overlay(UIFilmHomePanel)

- 广告入口 `Icons.IMAGE` tooltip=`…home.ads`:开 `UIOverlayPanel` 放一个新的
  `UIAdBoard`(fill 自 `FilmHomeContent.INSTANCE.ads`),宽 `min(320, 0.9*guiW)`,
  高 = 幻灯 4:3 + 圆点 + 招租位(~110)。UIAdBoard 本身就是纯 area 自绘元素,
  直接可嵌入。
- 新闻入口 `Icons.FILE` tooltip=`…home.news`:overlay 放新 `UINewsStrip`(fill 自
  INSTANCE.news),宽 `min(360, 0.9*guiW)`,高 = `getPreferredHeight()+padding`。
  两者都被 `UIOverlay.addOverlay` bounds 钳制。

## 状态保持

- 切档只跑 relayout/applyTier,不调 `fill()`;广告 `page/pageShownAt`、网格选中、
  搜索过滤全部保留(`paintedNews/paintedAds` 引用比较照旧)。
- `newsStrip.setCompact` 只改渲染分支与高度,不清 cards。

## 验证

- `gradlew compileClientJava`;若存在 UI/主题测试一并跑。
- 逻辑自查:阈值表(边界值 ±7/±9)+ 状态保持路径逐条过一遍(见 implement.md 勾选)。
