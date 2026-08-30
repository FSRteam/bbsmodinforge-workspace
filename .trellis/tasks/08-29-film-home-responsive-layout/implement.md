# Implement: Film home responsive layout tiers

Worktree: `.worktrees/fsr-film-home`

## Steps

- [x] 1. `UINewsStrip`:compact 单行模式(setCompact/cardHeight/getPreferredHeight/renderCard 紧凑分支)
- [x] 2. `UIFilmHomePanel`:档位枚举 + 迟滞计算 + applyLayoutState + relayout 重写(banner 高度、title/stats 重定位、板列宽、最小卡宽)
- [x] 3. `renderBanner` contain + 边缘延展 + 渐变
- [x] 4. 广告/新闻折叠入口图标 + overlay 面板
- [x] 5. `compileClientJava` + 既有测试(themeCore/uiInputMailbox/uiMirrorDemand/filmEditor 全过)
- [x] 6. 阈值/状态保持自查清单(独立 JVM 状态机模拟 ALL PASS)
- [x] 7. spec 沉淀 + journal

## 阈值自查表(已按独立 JVM 模拟验证,严格 `<` 语义:不切换带 [T-8, T+7])

| 场景 | 期望 |
| --- | --- |
| W=1008(从 T0 起) | 保持 T0(加带) |
| W=992 | 保持 T0(迟滞带内) |
| W=991(从 T0 缩) | 切 T1 |
| W=1007(从 T1) | 保持 T1 |
| W=1009 | 切 T0 |
| W=708(从 T2 升) | 切 T1 |
| W=692 | 保持 T1(带内) |
| W=691(从 T1 缩) | 切 T2 |
| W=488(从 T3 升) | 切 T2 |
| W=471(从 T2 缩) | 切 T3 |
| H=708→700 | 保持 full;H=691 切 compact(96) |
| H=489(从 tiny 升) | compact;H=471 切 tiny(64 + 新闻折叠) |
| T2 进入/退出 | 广告轮播 page 不重置(applyLayoutState 不碰 fill) |
| T3 进入/退出 | 进入记 userGridMode 强制列表,离开恢复 |
| H<480 且 W≥1000 | banner 64 + 新闻折叠,但广告板仍展开 |
