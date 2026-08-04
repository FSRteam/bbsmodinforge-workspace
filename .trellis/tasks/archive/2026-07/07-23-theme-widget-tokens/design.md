# Design: theme-widget-tokens —— 映射表与扫描策略

总架构见父任务 design.md §3.1。本文的核心是**映射表**:每个 `Colors` 语义常量/惯用法
→ 替换目标。执行 = 映射表驱动的机械替换 + 逐文件人审。

## 映射表 v1

### 直接替换(常量 → BBSSettings 语义函数)

| 原写法 | 替换为 | 说明 |
|---|---|---|
| `Colors.ACTIVE` | `BBSSettings.activeColor()` | 选中/激活蓝 0x0088ff |
| `Colors.POSITIVE` / `Colors.GREEN`(语义=成功/确认) | `BBSSettings.positiveColor()` | 绿 |
| `Colors.NEGATIVE` / `Colors.RED`(语义=错误/删除/危险) | `BBSSettings.negativeColor()` | 红 |
| `Colors.INACTIVE` | `BBSSettings.warningColor()` | 黄 0xffbb00 |
| `Colors.HIGHLIGHT` | `BBSSettings.highlightColor()` | 高亮 0xddddff |
| `Colors.CURSOR` | `BBSSettings.cursorColor()` | 文本光标绿 |
| `Colors.CONTROL_BAR` | `BBSSettings.chromeSurface()` 系(核实每处语义) | 深条底色 0xff141417 |
| `Colors.WHITE`(语义=正文/按钮/列表文字色) | `BBSSettings.textColor()` | **最大数量、最需人审**:仅当作为 `batcher.text/textShadow` 的 color 参数或 textColor 字段默认值 |
| `Colors.LIGHTER_GRAY`/`GRAY`(语义=次要文字/占位符) | `BBSSettings.mutedTextColor()` | 同上,人审 |
| 文字绘制的 shadow 布尔(普通 UI 文字) | `BBSSettings.textShadow()` | 见 prd 需求 4 |

### 保留常量(禁止替换)

- `Colors.WHITE` 作为**图标 tint**(`batcher.icon(icon, Colors.WHITE, ...)`)——图标换色由
  贴图/未来 v2 token 管,v1 保持白 tint。
- `Colors.WHITE/GRAY 系` 参与颜色数学(`mulRGB/lerp/setA` 的运算中间量)且结果语义仍是
  "原色变暗/变透明"的 hover 惯用法——保留(hover 渐变动画在 motion-layer 处理,不改颜色来源)。
- `PLANE_XZ/PLANE_XY/PLANE_ZY`、`RED/GREEN/BLUE` 作为 3D gizmo 轴色与向量分量色。
- 剪辑/工厂注册色(`ClipFactoryData` 的 0x159e64 等)——内容语义色,非 UI 皮肤。
- `Colors.A100/A75/...` 纯 alpha 面罩、黑色遮罩、阴影黑——结构性明暗,不随主题。
- 关键帧/曲线编辑器里的通道色(x=红 y=绿 z=蓝 惯例)。
- 调色板类(favoriteColors/recentColors、拾色器 UI 内部色阶)。

### 灰区裁决规则

一处用色同时像"文字色"又像"装饰":看它在 light 主题下的现状——如果现状 light 下它
仍然是白色且贴着浅底(可读性 bug),按文字色替换并把该处记入"修复清单";如果 light 下
它有配套的深色底(结构性),保留。仍拿不准 → 保留 + 登记到"待裁决"清单,宁少勿滥。

## 扫描策略

1. 逐目录推进(顺序见 implement.md),每目录:
   `grep -n "Colors\." <dir> -r` 全列 → 逐条按映射表分类(替换/保留/灰区)→ 改 → 编译 →
   截图对比 → 下一目录。
2. 禁止正则盲替:`Colors.WHITE` 必须看上下文(text 参数位 vs icon tint 位)。
3. 每目录一个 commit(用户确认后),message 注明目录与替换计数,便于二分回滚。

## 完成态核查

```bash
# framework 内语义常量应清零(输出应只剩映射表"保留"节允许的模式)
grep -rn "Colors.ACTIVE\|Colors.POSITIVE\|Colors.NEGATIVE\|Colors.INACTIVE\|Colors.HIGHLIGHT\|Colors.CURSOR\|Colors.CONTROL_BAR" new/src/client/java/mchorse/bbs_mod/ui/framework
```

夸张测试主题(prd 验收用)放 `config/bbs/assets/themes/qa-loud/`,theme.json 见
`docs/theme-spec/README.md` 附录(builtin-packs 任务会把它正式化为演示主题的基础)。
