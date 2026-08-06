# 实施计划：伪装列表 Form 动画正常播放与冻结开关

## 实施顺序

按依赖关系和验证边界分 4 个阶段：

1. **新增冻结开关设置项** → 提供配置基础设施
2. **修复列表动画播放** → 核心修复，可独立验证
3. **接入冻结开关与选中豁免** → 整合阶段 1 + 2
4. **测试与回归检查** → 验证所有验收标准

---

## Phase 1: 新增冻结开关设置项

### 1.1 `BBSSettings` 新增字段

**文件**：`new/src/main/java/mchorse/bbs_mod/BBSSettings.java`

**改动点**：
1. 在字段声明区（约 63 行 `freezeModels` 后）添加：
   ```java
   public static ValueBoolean freezeFormAnimations;
   ```

2. 在初始化区（约 694 行 `freezeModels` 注册后）添加：
   ```java
   freezeFormAnimations = builder.getBoolean("freeze_form_animations", false);
   ```

**验证**：
```bash
./gradlew compileJava
```

**检查点**：
- [ ] 编译通过
- [ ] 启动游戏 → 设置界面 → Appearance 类别 → 找到新开关
- [ ] 切换开关，退出重进，设置保持

---

## Phase 2: 修复列表动画播放

### 2.1 `MobFormRenderer` 新增 UI 动画时钟

**文件**：`new/src/client/java/mchorse/bbs_mod/forms/renderers/MobFormRenderer.java`

**改动点 A**：在字段声明区（约 58-102 行）添加：
```java
// UI animation clock (independent from entity.tickCount in world/editor contexts)
private float uiAnimationAge = 0F;
private int entityInitialTickCount = 0;
```

**改动点 B**：在 `ensureEntity()` 方法末尾（约 230 行，`this.entity != null` 分支）添加：
```java
if (this.entity != null)
{
    // ... 现有实体初始化 ...
    
    // 记录初始 tickCount，用于 UI 动画时钟叠加
    if (this.entityInitialTickCount == 0)
    {
        this.entityInitialTickCount = this.entity.tickCount;
    }
}
```

**改动点 C**：在 `ensureEntity()` 重置分支（约 172-197 行，`!this.lastId.equals(id) ...` 内）添加：
```java
this.entityInitialTickCount = 0;
this.uiAnimationAge = 0F;
```

### 2.2 `MobFormRenderer.renderInUI` 推进时钟并临时覆盖

**文件**：同上

**改动点 D**：在 `renderInUI` 方法开头（约 234 行，`this.ensureEntity()` 后）添加：
```java
@Override
protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
{
    this.ensureEntity();
    
    if (this.entity != null)
    {
        // 推进 UI 动画时钟（阶段 3 会加冻结检查）
        this.uiAnimationAge += 1.0F;
        
        // 防止溢出（约 9 天连续渲染）
        if (this.uiAnimationAge > 1_000_000F)
        {
            this.uiAnimationAge = 0F;
        }
        
        this.ensureAnimationInitialized(null);
        
        // ... 现有代码（PoseStack stack = ...）...
```

**改动点 E**：在调用 vanilla renderer 前后包裹（约 287-289 行）：
```java
// 现有代码：
// try (MobRenderContext ignored = MobRenderContext.push(...))
// {
//     Minecraft.getInstance().getEntityRenderDispatcher().render(...);
// }

// 修改为：
int originalTickCount = this.entity.tickCount;
try
{
    // 临时覆盖 tickCount 为 UI 动画时钟
    this.entity.tickCount = this.entityInitialTickCount + (int) this.uiAnimationAge;
    
    try (MobRenderContext ignored = MobRenderContext.push(renderer, this.form.pose.get(), this.mergeOverlays(), this.getColor(0xffffffff)))
    {
        Minecraft.getInstance().getEntityRenderDispatcher().render(this.entity, 0D, 0D, 0D, 0F, transition, stack, consumers, LightTexture.FULL_BLOCK);
    }
}
finally
{
    // 恢复原始 tickCount
    this.entity.tickCount = originalTickCount;
}
```

**验证命令**：
```bash
./gradlew compileJava compileClientJava
```

**游戏内验证**：
1. 启动游戏
2. 打开伪装菜单 → 列表里找一个四肢动画明显的实体（如僵尸、玩家）
3. **验收点**：列表缩略图动画平滑播放，无抽搐
4. 找末影龙 → **验收点**：翅膀有摆动动画，不静止
5. 进入编辑 → 3D 预览 → **验收点**：动画仍正常

**检查点**：
- [ ] AC1: 4 个入口列表动画平滑播放
- [ ] AC2: 末影龙列表有翅膀动画
- [ ] AC4: 编辑 3D 预览无回归

---

## Phase 3: 接入冻结开关与选中豁免

### 3.1 扩展 `FormUtilsClient.renderUI` 签名

**文件**：`new/src/client/java/mchorse/bbs_mod/forms/FormUtilsClient.java`

**改动点 A**：修改 `renderUI` 方法签名（约 178 行）：
```java
// 现有：
// public static void renderUI(Form form, UIContext context, int x1, int y1, int x2, int y2)

// 修改为：
public static void renderUI(Form form, UIContext context, int x1, int y1, int x2, int y2, boolean isSelected)
{
    FormRenderer renderer = getRenderer(form);

    if (renderer != null)
    {
        renderer.renderUI(context, x1, y1, x2, y2, isSelected);
    }
}

// 保留向后兼容的重载（非列表场景调用）
public static void renderUI(Form form, UIContext context, int x1, int y1, int x2, int y2)
{
    renderUI(form, context, x1, y1, x2, y2, false);
}
```

### 3.2 扩展 `FormRenderer.renderUI` 签名

**文件**：`new/src/client/java/mchorse/bbs_mod/forms/renderers/FormRenderer.java`

**改动点 B**：修改 `renderUI` 方法（约 68 行）：
```java
// 现有：
// public final void renderUI(UIContext context, int x1, int y1, int x2, int y2)

// 修改为：
public final void renderUI(UIContext context, int x1, int y1, int x2, int y2, boolean isSelected)
{
    this.beginRenderUI();
    this.renderInUI(context, x1, y1, x2, y2, isSelected);
    this.finishRenderUI(context, x1, y1, x2, y2);
}

// 向后兼容
public final void renderUI(UIContext context, int x1, int y1, int x2, int y2)
{
    this.renderUI(context, x1, y1, x2, y2, false);
}
```

**改动点 C**：修改抽象方法签名（约 111 行）：
```java
// 现有：
// protected abstract void renderInUI(UIContext context, int x1, int y1, int x2, int y2);

// 修改为：
protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2, boolean isSelected)
{
    // 默认实现：忽略 isSelected，调用旧签名（向后兼容）
    this.renderInUI(context, x1, y1, x2, y2);
}

// 保留旧签名作为 protected 方法，供子类选择性覆盖
protected abstract void renderInUI(UIContext context, int x1, int y1, int x2, int y2);
```

### 3.3 `MobFormRenderer` 实现选中豁免逻辑

**文件**：`new/src/client/java/mchorse/bbs_mod/forms/renderers/MobFormRenderer.java`

**改动点 F**：覆盖新签名（在类中添加新方法）：
```java
@Override
protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2, boolean isSelected)
{
    this.ensureEntity();
    
    if (this.entity != null)
    {
        // 决定是否推进动画时钟
        if (this.shouldAdvanceUIAnimation(isSelected))
        {
            this.uiAnimationAge += 1.0F;
            
            if (this.uiAnimationAge > 1_000_000F)
            {
                this.uiAnimationAge = 0F;
            }
        }
        
        // ... 其余现有 renderInUI 逻辑（阶段 2 已改动）...
    }
}

private boolean shouldAdvanceUIAnimation(boolean isSelected)
{
    // 优先级 1：Form 自身 paused = true → 不推进
    if (this.form.paused.get())
    {
        return false;
    }
    
    // 优先级 2：冻结开关关闭 → 推进
    if (!BBSSettings.freezeFormAnimations.get())
    {
        return true;
    }
    
    // 优先级 3：冻结启用 → 仅选中的推进
    return isSelected;
}
```

**改动点 G**：移除旧签名的 `@Override`（如果阶段 2 加了），改为私有辅助方法或删除（因为新签名已覆盖）。

### 3.4 `UIFormCategory` 传递选中状态

**文件**：`new/src/client/java/mchorse/bbs_mod/ui/forms/categories/UIFormCategory.java`

**改动点 H**：修改 `render` 方法中的调用点（约 348 行）：
```java
// 现有：
// FormUtilsClient.renderUI(form, context, cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT);

// 修改为：
boolean isSelected = this.selected == form;
FormUtilsClient.renderUI(form, context, cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT, isSelected);
```

### 3.5 其他 `renderUI` 调用点传 `false`

**文件列表**（从阶段 2 的 grep 结果）：
- `new/src/client/java/mchorse/bbs_mod/ui/film/controller/ReplayContextAction.java:55`
- `new/src/client/java/mchorse/bbs_mod/ui/film/controller/UIFilmController.java:1564`
- `new/src/client/java/mchorse/bbs_mod/ui/film/replays/UIReplayList.java:2362`
- `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/UIForms.java:86`
- `new/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframePreviewTooltip.java:266`
- `new/src/client/java/mchorse/bbs_mod/ui/model_editor/UIModelEditorPanel.java:231`
- `new/src/client/java/mchorse/bbs_mod/ui/selectors/UISelectorList.java:60`

**策略**：这些调用点都不在列表场景，保持调用旧签名 `renderUI(form, context, x, y, x2, y2)` 即可（向后兼容重载自动传 `false`）。

**验证**：全局搜索确认没有遗漏：
```bash
cd new && grep -rn "FormUtilsClient.renderUI" --include=*.java src/client/java | grep -v "public static void renderUI"
```

**检查点**：
- [ ] 所有调用点编译通过
- [ ] 非列表场景（如影片缩略图、模型编辑器）仍正常显示

---

## Phase 4: 测试与回归检查

### 4.1 新增源码级回归测试

**文件**：`new/src/test/java/mchorse/bbs_mod/forms/renderers/MobFormUIAnimationTest.java`（新建）

**内容**：
```java
package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MobFormUIAnimationTest
{
    private MobFormRenderer renderer;
    private MobForm form;
    
    @Mock
    private UIContext context;
    
    @BeforeEach
    public void setup()
    {
        MockitoAnnotations.openMocks(this);
        
        form = new MobForm();
        form.mobID.set("minecraft:zombie");
        form.paused.set(false);
        
        renderer = new MobFormRenderer(form);
        
        // 模拟 UIContext.getTransition() 返回固定值
        when(context.getTransition()).thenReturn(0.5F);
    }
    
    @Test
    public void testUIAnimationClockAdvancesWhenFreezeOff()
    {
        BBSSettings.freezeFormAnimations.set(false);
        
        // 模拟多次渲染
        float initialAge = getUIAnimationAge(renderer);
        renderer.renderUI(context, 0, 0, 60, 80, false);
        float afterFirstRender = getUIAnimationAge(renderer);
        
        renderer.renderUI(context, 0, 0, 60, 80, false);
        float afterSecondRender = getUIAnimationAge(renderer);
        
        assertTrue(afterFirstRender > initialAge, "UI animation clock should advance");
        assertTrue(afterSecondRender > afterFirstRender, "Clock should keep advancing");
    }
    
    @Test
    public void testUIAnimationClockStaticWhenFreezeOnAndNotSelected()
    {
        BBSSettings.freezeFormAnimations.set(true);
        
        float initialAge = getUIAnimationAge(renderer);
        renderer.renderUI(context, 0, 0, 60, 80, false); // not selected
        float afterRender = getUIAnimationAge(renderer);
        
        assertEquals(initialAge, afterRender, "Clock should not advance when frozen and not selected");
    }
    
    @Test
    public void testUIAnimationClockAdvancesWhenFreezeOnButSelected()
    {
        BBSSettings.freezeFormAnimations.set(true);
        
        float initialAge = getUIAnimationAge(renderer);
        renderer.renderUI(context, 0, 0, 60, 80, true); // selected
        float afterRender = getUIAnimationAge(renderer);
        
        assertTrue(afterRender > initialAge, "Clock should advance when selected even if frozen");
    }
    
    @Test
    public void testFormPausedOverridesFreezeSetting()
    {
        form.paused.set(true);
        BBSSettings.freezeFormAnimations.set(false);
        
        float initialAge = getUIAnimationAge(renderer);
        renderer.renderUI(context, 0, 0, 60, 80, true); // selected, freeze off
        float afterRender = getUIAnimationAge(renderer);
        
        assertEquals(initialAge, afterRender, "Form.paused should override freeze setting");
    }
    
    private float getUIAnimationAge(MobFormRenderer renderer)
    {
        // 反射访问 private 字段（测试专用）
        try
        {
            var field = MobFormRenderer.class.getDeclaredField("uiAnimationAge");
            field.setAccessible(true);
            return (float) field.get(renderer);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
```

**验证**：
```bash
./gradlew test --tests MobFormUIAnimationTest
```

### 4.2 手动验收测试清单

启动游戏，按顺序验证所有 AC：

#### AC1: 列表动画平滑播放（冻结关闭）
- [ ] 设置 → Appearance → `freeze_form_animations` = OFF
- [ ] 实体选择器 → 列表缩略图动画平滑
- [ ] 伪装菜单 → 列表缩略图动画平滑
- [ ] 影片菜单 → 列表缩略图动画平滑
- [ ] 模型方块菜单 → 列表缩略图动画平滑

#### AC2: 末影龙动画
- [ ] 伪装菜单 → 找末影龙 → 翅膀有摆动

#### AC3: 未被模型方块选中的 Form
- [ ] 影片菜单 → 随机选一个实体 → 列表正常播放（不依赖之前被模型方块选中）

#### AC4: 编辑 3D 预览无回归
- [ ] 伪装菜单 → 随机选一个 → 编辑 → 3D 预览正常播放
- [ ] 影片菜单 → 编辑 → 3D 预览正常播放

#### AC5: 冻结时未选中静止
- [ ] 设置 → `freeze_form_animations` = ON
- [ ] 伪装菜单 → 列表所有 Form 静止且无抖动

#### AC6: 冻结时选中的播放
- [ ] `freeze_form_animations` = ON
- [ ] 伪装菜单 → 点击选中一个 Form → **该 Form** 恢复播放
- [ ] 点击另一个 → 前者恢复静止，新选中的播放

#### AC7: Form.paused 优先级
- [ ] 创建一个 `paused = true` 的 MobForm（需要通过 NBT 或编辑器）
- [ ] `freeze_form_animations` = OFF → 列表该 Form 静止
- [ ] `freeze_form_animations` = ON 且选中 → 仍静止

#### AC8: 世界渲染无回归
- [ ] 伪装成任意 Form → 世界内动画正常
- [ ] 放置模型方块 → 动画正常
- [ ] 影片回放 → 动画正常

#### AC9: 编译与测试
```bash
./gradlew compileJava compileClientJava
./gradlew test
```

#### AC10: 回归测试覆盖
- [ ] `MobFormUIAnimationTest` 包含 4 个测试用例
- [ ] 全部通过

### 4.3 回滚检查点

如果 AC4 失败（编辑预览回归），回滚策略：
1. 确认 `UIPickableFormRenderer.update` 仍调用 `Form.update`
2. 确认 `shouldAdvanceUIAnimation` 只在 `renderInUI` 路径生效，不影响 `render3D`
3. 检查 `entity.tickCount` 恢复逻辑的 finally 块是否正确执行

如果 AC7 失败（Form.paused 被忽略），检查：
1. `shouldAdvanceUIAnimation` 优先级顺序
2. `form.paused.get()` 返回值是否正确

---

## 实施后续

### 提交信息
```
feat(ui): smooth MobForm list animation + freeze toggle

- Add independent UI animation clock to MobFormRenderer
- Advance clock per render frame, temporarily override entity.tickCount
- New BBSSettings.freezeFormAnimations toggle (default: off)
- When enabled, only selected form in list plays animation
- Form.paused takes priority over freeze toggle
- Covers entity selector, disguise, film, model block menus
- Add MobFormUIAnimationTest regression suite

Fixes #08-01-08-01-form-list-preview-animation-twitch
Closes #08-05-form-list-animation-playback-freeze
```

### 文档更新

如需更新项目 wiki / CHANGELOG，记录：
- 新增设置项 `freeze_form_animations` 的用途与位置
- 修复列表动画抽搐问题的技术原理（UI 独立时钟）
- 冻结开关的选中豁免机制

---

## 风险与缓解

**实测风险登记**已整理至 `risks.md`。包括：
- 已发生并已修复的回归（renderer 构造触碰原版注册表）
- 剩余风险（冻结判定容差、外部 tick 记账纪律、复用姿态路径）
- 排查噪音的判别依据（`:testUiMotions` 的良性 LinkageError）
- 尚未验证的场景（影片/模型方块菜单、暂停优先级叠加）

详见：`./risks.md`

---

## 完成标志

- [ ] 所有 10 个 AC 通过
- [ ] 提交到分支 `work/mcaxolotl-form-list-animation-freeze`
- [ ] 更新 `08-01-08-01-form-list-preview-animation-twitch` 任务状态为"已被本任务修复"
