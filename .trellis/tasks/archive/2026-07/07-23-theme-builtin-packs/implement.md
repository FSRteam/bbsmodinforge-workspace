# Implement: theme-builtin-packs 执行清单

## 清单

- [x] P1 `assets/bbs/assets/themes/example/theme.json` ← 从 `docs/theme-spec/example-theme/`
      逐字节拷贝(docs 为源,diff 校验空;对拍测试里 `Files.mismatch == -1` 持续把关);
      skin-settings-ui 导出模板读取顺序 example → dark(兜底保留,零成本)。
- [x] P2 演示主题 `themes/amber/`("Retro Amber",base=dark):
      颜色全套(暖黑底 #1A150A 系 + 琥珀 accent #FFB000 + 琥珀文字 #FFCC66)、
      style 三开关全关(纯平复古)、motion 差异(speed 0.8 放慢 + elastic_out 弹出
      overlay / bounce_out 通知 / back_out 菜单)。三层差异对拍断言已加
      (`testBuiltinPacks`:accent≠dark、三开关全 false、overlay 缓动/时长≠dark)。
- [x] P3 规范对齐校验(逐 key 表,见下)。
- [x] P4 贴图覆盖示例:`example-theme/textures/README.md` 与实现核实一致
      (icons.png 256×256/16 网格来自内置 atlas 实测;`assets:themes/<id>/...` 链接语义与
      ThemeParser `Link.create` + ThemeManager 贴图存在性校验一致)。示例 icons.png 色相
      偏移变体**未随包**(可选项,无图像处理管线;规范已写清自制方法)。
- [x] P5 主题资源清单:jar 打包走 `processResources`(client resources 全量进 jar,
      与 icons.png 同机制);`ThemeManager.getThemes` 内置硬清单更新为
      dark/light/example/amber(枚举失败仍可见)。
- [x] P6 集成验收记录回父任务 implement.md。

## 规范对齐校验表(README ↔ ThemeParser,2026-07-24)

| 规范 key | README 行为 | ThemeParser 行为 | 一致? |
|---|---|---|---|
| format | 必填,当前 1;不认识拒载回退 | `getInt("format",-1) != 1` → 拒载该层;链头拒载 = 主题不可用回退 dark | ✓ |
| name/author/description | 默认 id/""/"";不继承 | 固定默认,不走 fallback | ✓ |
| base | 默认 "dark";链≤4;禁循环;链尾隐式接 dark 默认 | 无 base = 直接落到代码级 dark 默认(与"隐式接 dark"等价);visited 防环;深度 4 截断 warn | ✓ |
| variant | "dark"/"light",默认 dark | `"light".equals(...)`;缺失沿链继承 | ✓(继承语义为通则 §3,表中"默认 dark"即链尾默认)|
| colors.* 全部 | `#RRGGBB`/`#AARRGGBB`,6 位=不透明 | `parseWithException` + 6 位补 `A100`;坏值 warn + 继承 | ✓ |
| style.* 三开关 | bool,默认 true | `getBool(key, fallback)` | ✓(light 内置显式 text_shadow=false,规范 §4.4 默认表述针对 dark 链)|
| textures.icons/background | `assets:` 链接或 null;null=默认 | 非字符串(JSON null)→ 显式清除;缺键→继承;文件缺失 → 解析期校验回退 + warn | ✓ |
| motion.enabled/speed | bool/float,用户层与主题层相乘 | `UIMotions`:用户开关 && 主题开关;`duration/(themeSpeed×userSpeed)`;speed clamp 0.1–10 | ✓ |
| motion 六条目 | enabled/duration(ms)/easing | 逐条目继承;duration clamp 0–10000;未知缓动 → sine_out + warn | ✓ |
| §4.7 缓动名表 | Interpolations.MAP 键 | 直查 MAP(33 键 + 关键帧专用,后者规范已注明不建议) | ✓ |
| `_` 前缀注释键 | 忽略 | 未知键天然忽略(逐 key 读取) | ✓ |
| 主题位置/覆盖 | jar `themes/<id>/` vs `config/bbs/assets/themes/<id>/` 同 id 覆盖 | provider SourcePack 顺序(External 先)天然覆盖;枚举三源并集 | ✓ |
| 坏文件安全 | 不崩溃,回退 + warn | 全链 try/catch;`current()` 永不 null;渲染无感 | ✓ |

**遗留不一致:零。**(规范 §6"完整默认值参考"指向 example-theme 样例——样例即 jar 资源,
对拍测试保证三方一致:docs == jar example,dark JSON == 代码级默认。)

## 验证

- [x] `./gradlew check` 全量(含 testThemeCore 的 builtin packs 断言)。
- [ ] 游戏内:选择器可见四个内置主题、amber 三层差异肉眼可辨、导出产物==docs 样例
      ——**待用户冒烟**。
