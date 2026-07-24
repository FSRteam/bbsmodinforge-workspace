# Implement: theme-widget-tokens 执行清单

前置:theme-core 已完成并过基线。
实际执行方式与原计划不同:未按目录推进,而是**按常量类别分批**(判断集中做一次,
执行派发/并行),覆盖同一集合。V0 截图基线无法在本环境拍摄(无头),恒等性由
"位级恒等规则 + 编译 + check + 对拍单测"保证,游戏内截图对比待用户冒烟。

## 实际执行记录

- [x] P1 纯语义常量批(47 处:ACTIVE 19 / HIGHLIGHT 12 / NEGATIVE 7 / POSITIVE 5 /
      CURSOR 3 / INACTIVE 2;CONTROL_BAR 全仓库 0 处):
      逐处人工裁决后按**位级恒等三规则**替换:
      - `Colors.A100 | 常量` → `BBSSettings.xxxColor()`(函数返回完整 ARGB,恒等)
      - `Colors.Axx | 常量`(xx≠100)→ `(fn() & Colors.RGB) | Colors.Axx`
      - 裸常量(传入后续会自补 alpha 的 API,如 `menu.action`/`setChannel`/keyframe `c|A100`)
        → `fn() & Colors.RGB`(核实过 `ColorfulContextAction` 裸用 color 做渐变终点,
        全 alpha 输入会破坏渐变,必须 &RGB)
      - `Colors.setA(常量, a)` → `Colors.setA(fn(), a)`(setA 重写 alpha,恒等)
- [x] P2 文字色直接调用批(36 行裁决 → 33 处替换):`batcher.text*/textCard/wallText`
      color 参数位与 `UILabel.color(...)` 的 `Colors.WHITE→textColor()`、
      `Colors.LIGHTER_GRAY→mutedTextColor()`(值恒等 0xffaaaaaa)。
- [x] P3 控件默认色字段批(哨兵模式,render 期
      `字段 == Colors.WHITE ? BBSSettings.textColor() : 字段`):
      UIButton.textColor、UIToggle.color、UIText.color/hoverColor、UILabel.color、
      Textbox.color(顺带 placeholder 灰 0xaaaaaa → mutedTextColor(),文字位 opaque 恒等)。
- [x] P4 汇聚点单点批(高杠杆):
      - `Batcher2D.text(label,x,y)` / `textShadow(label,x,y)` / `textCard(text,x,y)`
        三个无 color 重载的默认白 → `BBSSettings.textColor()`(覆盖全部默认色调用)。
      - `Batcher2D.text(...,color,shadow)` 汇聚点接入 `BBSSettings.textShadow()` 拦截
        (shadow 请求 && token 关 ⇒ 无阴影);light 分支原硬编码 `shadow=false` 移入内置
        light 主题 JSON(`style.text_shadow=false`),darkenWhite 保留——内置行为零变化,
        第三方 light 主题获得开阴影自由。对应单测断言已加(light textShadow=false)。
- [x] S6 完成态核查:client 全域七常量残留 7 处,全部属于映射表"保留"名单(见下);
      `ui/framework` 内残留 **0**。
- [x] S7 `.trellis/spec/frontend/bbs-ui-theming.md` 规约已登记(index.md 已挂)。

执行分工:P1/P2 的机械执行派发 3 个 haiku 子代理(精确 old/new 清单驱动),
判断与灰区裁决、P3/P4、终验由主会话完成;并行竞态逐文件 grep 复核无丢失
(补了派发遗漏的 UIAudioPlayer:209 CURSOR 一处)。

## 验证

- [x] `compileClientJava` + `testThemeCore` 通过。
- [x] `./gradlew check` 全量(见会话记录)。
- [ ] 游戏内:dark 逐像素对比、qa-loud 夸张主题走查、light 走查(**待用户冒烟**)。

## 合法保留残留(七常量,7 处)

| 位置 | 保留理由 |
|---|---|
| UICameraUtils.java:50/83/128、UIActionClip.java:69、UIBlockHitResult.java:80 | camera copy 菜单的 position红/point绿/angle黄 是**类型编码**,非状态语义 |
| UIReplaysEditor.java:191 | replay 属性通道色注册表("color"→黄),通道色惯例 |
| CDNAssetSyncService.java:381 | CDN 同步状态色板(与 GREEN/BLUE/ORANGE 并列的枚举一组) |

## light 修复清单

(P2/P4 的 textColor() 化在 light 主题下仍经 `darkenWhite` 兜底,纯白→黑的现状行为
保留,无新增视觉变化;待游戏内走查后补充逐条记录。)

## 待裁决清单(灰区,v2 议题)

1. `Colors.GRAY`(0xff888888)大量用作"更暗文字/禁用态":**无对应 token**
   (mutedTextColor=0xffaaaaaa 值不等,替换即视觉变化)→ 全部保留;v2 考虑
   `text.disabled` token。
2. `UITextarea.textColor` 字段 + `setColor()` 是遗留死代码(渲染走 renderTextLine 硬编码,
   现已 token 化);字段未删,v2 清理或接通。
3. `Colors.CONTROL_BAR`(0xff141417)常量已无引用,可在 v2 删除常量本体。
4. UIReplayList:2343 与 UIBlockStateEditor:122 等处 `Colors.GRAY` 同灰区 1。

## 回滚

改动均为纯取色表达式改写 + 5 个控件的哨兵行 + Batcher2D 三个默认重载,单 commit
revert 即净;无布局/事件/绘制顺序改动。
