# Design: theme-core 模块细化

总架构见父任务 `07-23-fsr-theme-system/design.md`(§2 数据结构、§3 接线策略);
本文只写实现级细节。

## 类骨架

```java
// main: mchorse/bbs_mod/ui/themes/UITheme.java
public class UITheme
{
    public final String id, name, author, description;
    public final boolean light;                    // variant == "light"

    /* colors —— 全部 0xAARRGGBB */
    public final int surfaceChrome, surfaceBase, surfaceRaised, surfaceDeep, surfaceDivider;
    public final int accentPrimary;
    public final int textPrimary, textMuted;
    public final int statePositive, stateNegative, stateWarning, stateActive, stateHighlight, stateCursor;

    /* style */
    public final boolean textShadow, bevel, panelShadow;

    /* textures —— null = 不覆盖 */
    public final Link iconsAtlas, background;

    /* motion */
    public final boolean motionEnabled;
    public final float motionSpeed;
    public final UIThemeMotion overlay, panelSwitch, hover, notification, contextMenu, scrollbar;
}

// main: mchorse/bbs_mod/ui/themes/UIThemeMotion.java
public class UIThemeMotion { public final boolean enabled; public final int duration; public final IInterp easing; }

// main: mchorse/bbs_mod/ui/themes/ThemeManager.java
public class ThemeManager
{
    public static UITheme current();               // 惰性初始化;永不 null
    public static void setTheme(String id);        // 写 BBSSettings.themeId + 重解析
    public static void reload();                   // 重枚举 + 重解析当前
    public static Map<String, ThemeEntry> getThemes(); // id → {link/file, 解析出的元信息}
}
```

- `ThemeParser` 内部持有"代码级 dark 默认值"(一份与内置 dark JSON 相同的 UITheme 构造),
  作为继承链尾兜底——即使 jar 里的 dark JSON 也损坏,系统仍有完整主题。
- `current()` 缓存已解析实例;`themeId.callback`(BaseValue 监听)与 `reload()` 使缓存失效。
  渲染每帧读 `current()` 只是一次静态字段读。

## 关键实现点

1. **JSON 读取**:内置/外部统一走 `BBSMod.provider.getAsset(Link.assets("themes/<id>/theme.json"))`
   读流,`DataToString.mapFromString`(实现时核实确切方法名,BBS data 体系已有字符串→MapType 入口)。
   外部覆盖内置由 provider 的 SourcePack 顺序天然完成。
2. **枚举**:优先用 provider 的链接列举(`ExternalAssetsSourcePack.getLinksFromPathRecursively`
   已存在,Internal 侧也有 dev/jar 两种列举,见 `InternalAssetsSourcePack.java:185-271`);
   核实不可行则:内置 id 清单硬编码(dark/light/演示)∪ `BBSMod.getAssetsFolder()/themes` 列目录。
3. **迁移细节**(`BBSSettings.migrateLegacySettings`,已有先例结构):
   ```
   personalization.theme (int) 存在 → themeId = (0 ? "light" : "dark"),删旧键
   personalization.primary_color 存在且 != 0xff3242 → accent_follows_theme = false
   ```
4. **图标 atlas 重映射**:找到图标纹理绑定单点(Batcher2D 的 icon→`getTexture(icon.atlas)` 路径,
   实现时定位具体行),包一层
   `Link resolveIconAtlas(Link atlas)` → `atlas == Icons.ATLAS && theme.iconsAtlas != null ? theme.iconsAtlas : atlas`。
   若主题贴图文件缺失(getAsset 抛 IO),打一次 warn 后回退原 atlas(用 flag 防日志刷屏)。
5. **背景图**:现取 `BBSSettings.backgroundImage` 的消费点,改为
   `backgroundImage.get() != null ? backgroundImage.get() : theme.background`。
6. **`tooltipStyle`**:核实其消费点语义(invisible 值,默认跟 DEFAULT_THEME);若表示明暗,
   改读 `current().light`,不保留独立 int。

## 单元测试(新增,放现有 `test` source set 惯例位置)

- `ThemeParserTest`:完整 JSON 全字段解析;缺 key 回退 base;base 链(2 层)合并;循环 base 检测;
  坏颜色/坏缓动/坏 JSON 回退;`_comment` 键忽略;format 不认识 → 拒载回退。
- `ThemeMigrationTest`:`migrateLegacySettings` 三种旧配置(theme:0、theme:1、自定义 primary_color)。
- 内置 JSON 对拍测试:解析 jar 内 dark/light JSON,断言逐字段 == 旧 LIGHT_*/DARK_* 常量值
  (常量值在测试里写死,防"顺手调色")。

## 明确不做

- 不碰 `Colors.*` 的任何调用点(widget-tokens 任务)。
- 不做动画消费(motion-layer 任务)、不做设置 UI(skin-settings-ui 任务)。
- 不做文件监听热重载(手动 reload 已覆盖需求)。
