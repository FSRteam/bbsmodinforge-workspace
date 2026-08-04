# Research: BBS Mod Morph System Architecture

- **Query**: BBSMod (NeoForge 1.21.1) Morph 系统架构梳理，为新增"声音伪装目标"Morph 类型做准备
- **Scope**: internal
- **Date**: 2026-07-26

## Key Finding: Form-Based Morph System

**重要**: BBS Mod 的 Morph 系统是基于 **Form** 体系实现的，而非传统的独立 Morph 类型。

### 架构概览

```
Player (IMorphProvider)
    └─ Morph 容器 (Morph.java)
           └─ Form 实例 (MobForm / ModelForm / ParticleForm / ...)
                  ├─ 序列化：MapType (BBSMod 自有格式) + NBT 双层存储
                  ├─ 生命周期：onMorph / onDemorph / update()
                  ├─ 渲染：FormRenderer 及其子类
                  └─ UI编辑：UIForm + UIFormPanel
```

---

## 1. Morph 基础类与注册系统

### Core Classes

| 文件 | 类名 | 用途 |
|------|------|------|
| `new/src/main/java/mchorse/bbs_mod/morphing/Morph.java` | `Morph` | Morph 容器，管理当前 Form 实例；包含 update/onMorph/onDemorph 入口 |
| `new/src/main/java/mchorse/bbs_mod/morphing/IMorphProvider.java` | `IMorphProvider` | 接口，使 Entity 能提供 Morph 实例 |
| `new/src/main/java/mchorse/bbs_mod/forms/FormArchitect.java` | `FormArchitect` | 扩展 MapFactory，注册&序列化 Form 类型 |
| `new/src/main/java/mchorse/bbs_mod/forms/FormUtils.java` | `FormUtils` | Form 序列化/反序列化入口，调用 FormArchitect |

### Form 基础类

| 文件 | 类名 | 说明 |
|------|------|------|
| `new/src/main/java/mchorse/bbs_mod/forms/forms/Form.java` | `Form` | 所有 Form 的基类，extends ValueGroup；包含 transform/lighting/hitbox/hp/speed/states 字段 |

### Form 注册位置

**注册点**: `new/src/main/java/mchorse/bbs_mod/BBSMod.java:~165-177`
```
forms = new FormArchitect();
forms
    .register(Link.bbs("billboard"), BillboardForm.class, null)
    .register(Link.bbs("label"), LabelForm.class, null)
    .register(Link.bbs("model"), ModelForm.class, null)
    .register(Link.bbs("particle"), ParticleForm.class, null)
    .register(Link.bbs("extruded"), ExtrudedForm.class, null)
    .register(Link.bbs("block"), BlockForm.class, null)
    .register(Link.bbs("item"), ItemForm.class, null)
    .register(Link.bbs("anchor"), AnchorForm.class, null)
    .register(Link.bbs("mob"), MobForm.class, null)
    .register(Link.bbs("vanilla_particles"), VanillaParticleForm.class, null)
    .register(Link.bbs("trail"), TrailForm.class, null)
    .register(Link.bbs("framebuffer"), FramebufferForm.class, null);
```

**关键方法**: `FormArchitect.register(Link id, Class<? extends Form> form, Void)` (继承自 MapFactory)
- **Key/ID 命名约定**: `Link.bbs("lowercase_underscore_id")`
- **从 MapFactory 继承的方法**: `getType(Form)` → ResourceLocation，`toData(Form)` → MapType，`fromData(MapType)` → Form

---

## 2. Morph 序列化机制

### 序列化方式

**使用**: BBSMod 自有数据格式 **MapType/BaseType**（未使用原生 NBT）

| 方向 | 方法 | 签名 | 位置 |
|------|------|------|------|
| Form → 序列化 | `FormUtils.toData(Form)` | `Form → MapType` | `new/src/main/java/mchorse/bbs_mod/forms/FormUtils.java:65-73` |
| 序列化 → Form | `FormUtils.fromData(MapType)` | `MapType → Form` | `new/src/main/java/mchorse/bbs_mod/forms/FormUtils.java:38-63` |
| Morph → NBT | `Morph.toNbt()` | `Morph → CompoundTag (contains MapType→NBT)` | `new/src/main/java/mchorse/bbs_mod/morphing/Morph.java:109-119` |
| NBT → Morph | `Morph.fromNbt(CompoundTag)` | `CompoundTag (MapType) → Morph` | `new/src/main/java/mchorse/bbs_mod/morphing/Morph.java:121-129` |

### 序列化流程

```
Morph.toNbt()
    └─ FormUtils.toData(this.form)         // Form → MapType
        └─ BBSMod.getForms().toData(form)  // 调用 FormArchitect
            └─ MapFactory.toData()          // 附加 "id" 字段
    └─ DataStorageUtils.toNbt(MapType)     // MapType → CompoundTag
    └─ return CompoundTag
```

**重要**: Form 中每个字段继承自 ValueGroup（设置系统），自动序列化通过 `fromData(BaseType)` / 字段的 `fromData()` 完成。

---

## 3. 现有 Form 类型完整实现示例

### 示例 1: MobForm（生物伪装）

| 项目 | 内容 |
|------|------|
| **类定义** | `new/src/main/java/mchorse/bbs_mod/forms/forms/MobForm.java:1-42` |
| **继承链** | `MobForm` → `Form` → `ValueGroup` |
| **字段** | `mobID` (ValueString), `mobNBT` (ValueString), `texture` (ValueLink), `slim` (ValueBoolean), `pose` (ValuePose), `poseOverlay` (ValuePose) |
| **序列化** | 继承 Form 的 fromData/toData；各字段通过 add() 自动注册到 ValueGroup |
| **生命周期** | onMorph/onDemorph：修改 HP/Speed/StepHeight；update()：由 Morph 容器调用，执行部分更新 |
| **渲染** | `MobFormRenderer` (`new/src/client/java/mchorse/bbs_mod/forms/renderers/MobFormRenderer.java`) |
| **UI编辑** | `UIMobForm` → `UIMobFormPanel` (`new/src/client/java/mchorse/bbs_mod/ui/forms/editors/forms/UIMobForm.java:1-18` 和 `UIMobFormPanel.java:21-78`) |

### 示例 2: ParticleForm（粒子伪装）

| 项目 | 内容 |
|------|------|
| **类定义** | `new/src/main/java/mchorse/bbs_mod/forms/forms/ParticleForm.java:1-46` |
| **字段** | `effect` (ValueString), `paused` (ValueBoolean), `texture` (ValueLink), `user1-user6` (ValueFloat) |
| **生命周期** | update()：由 Morph 调用，可通过 ITickable renderer 驱动粒子更新 |
| **渲染** | `ParticleFormRenderer` (implements ITickable)：管理 ParticleEmitter，更新 paused 状态，驱动粒子帧更新 |
| **UI编辑** | `UIParticleForm` (`new/src/client/java/mchorse/bbs_mod/ui/forms/editors/forms/UIParticleForm.java:1-41`) |

### 示例 3: ModelForm（模型伪装）

| 项目 | 内容 |
|------|------|
| **类定义** | `new/src/main/java/mchorse/bbs_mod/forms/forms/ModelForm.java:28-98` |
| **字段** | `texture` (ValueLink), `model` (ValueString), `pose` (ValuePose), `actions` (ValueActionsConfig), `color` (ValueColor), `shapeKeys`, `ik` (ValueData), `physics` (ValueData), `constraints` (ValueData) 等 |
| **特殊** | 运行时 override 字段：materialTextureOverrides, ikTargetOverrides, physicsTargetOverrides, windControlOverride 等 (transient，不序列化) |
| **渲染** | `ModelFormRenderer`：复杂渲染流程，支持 IK/Physics/动画轨道等 |
| **UI编辑** | `UIModelForm` + 多个专用 Panel：UIModelFormPanel, UIModelIKFormPanel, UIModelPhysicsFormPanel, UIModelConstraintsFormPanel |

---

## 4. Morph 的渲染入口与生命周期

### 应用入口 (World 级别)

**实现**: 通过 Mixin 和 IMorphProvider

| 文件 | 位置 | 说明 |
|------|------|------|
| `new/src/main/java/mchorse/bbs_mod/mixin/PlayerEntityMorphMixin.java:12-34` | Player Mixin | Player 实现 IMorphProvider，包含 `Morph morph` 字段；baseTick() 调用 `morph.update()` |

**关键调用点**:
- `PlayerEntityMorphMixin.baseTick()` (每 tick)
  - 调用 `this.morph.update()` → 更新 Form 动画状态和部分渲染器

### Form 生命周期方法

| 方法 | 签名 | 调用点 | 用途 |
|------|------|--------|------|
| `onMorph` | `void onMorph(LivingEntity)` | `Morph.setForm()` (变身时) | 修改 entity 属性 (HP/Speed/StepHeight) |
| `onDemorph` | `void onDemorph(LivingEntity)` | `Morph.setForm(null)` (解除时) | 恢复 entity 默认属性 |
| `update` | `void update(IEntity)` | `Morph.update()` 每 tick | 更新 BodyPartManager、StatePlayer、Renderer tick |
| `playMain` | `void playMain()` | `Morph.setForm()` | 播放主动画状态 |
| `applyStates` | `void applyStates(float transition)` | Renderer 开始渲染前 | 应用动画状态覆盖到 Form 字段 |

### 渲染入口 (Client 级别)

**渲染方法**: `FormRenderer.render(FormRenderingContext)` (`new/src/client/java/mchorse/bbs_mod/forms/renderers/FormRenderer.java:100-150` 及以后)

**关键步骤**:
1. `applyStates(transition)` — 应用当前动画状态
2. 检查 visible，push 矩阵栈
3. 应用 transform（形变、旋转、缩放）
4. 调用子类实现的 `render*` 方法（如 renderLitModel, renderBillboard 等）
5. 处理 BodyParts 及其嵌套 Form 渲染
6. 恢复矩阵栈

---

## 5. 客户端编辑器结构 (UI Panel)

### 编辑器基类与组件

| 文件 | 类名 | 用途 |
|------|------|------|
| `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/UIFormEditor.java:80-149` | `UIFormEditor` | 主编辑器，管理 UIForm 实例切换与注册 |
| `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/forms/UIForm.java:24-80` | `UIForm<T>` | 抽象基类，管理多个 UIFormPanel；提供 gizmo/轨道等编辑工具 |
| `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/panels/UIFormPanel.java:16-66` | `UIFormPanel<T>` | 抽象基类，右侧选项面板；包含 scrollable options 和 draggable resize 控件 |

### UIFormEditor 中的 Form 编辑器注册

**注册点**: `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/UIFormEditor.java:133-144`

```java
static {
    register(BillboardForm.class, UIBillboardForm::new);
    register(ExtrudedForm.class, UIExtrudedForm::new);
    register(LabelForm.class, UILabelForm::new);
    register(ModelForm.class, UIModelForm::new);
    register(ParticleForm.class, UIParticleForm::new);
    register(BlockForm.class, UIBlockForm::new);
    register(ItemForm.class, UIItemForm::new);
    register(AnchorForm.class, UIAnchorForm::new);
    register(MobForm.class, UIMobForm::new);
    register(VanillaParticleForm.class, UIVanillaParticleForm::new);
    register(TrailForm.class, UITrailForm::new);
    register(FramebufferForm.class, UIFramebufferForm::new);
}
```

**方法**: `UIFormEditor.register(Class, Supplier<UIForm>)` → 存储到静态 `panels` Map

### 客户端编辑器实现示例

#### UIParticleForm 实现

**文件**: `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/forms/UIParticleForm.java:15-40`

**结构**:
```java
public class UIParticleForm extends UIForm<ParticleForm> {
    public UIParticleForm() {
        super();
        
        // 创建"选择纹理"按钮
        UIElement button = new UIButton(...);
        
        // 注册默认 panel（通过 registerDefaultPanels()）
        this.registerDefaultPanels();
        
        // 将按钮添加到最后一个 panel 的选项
        this.defaultPanel = this.panels.get(this.panels.size() - 1);
        this.defaultPanel.options.prepend(button);
        
        // 注册快捷键
        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, button::clickItself);
    }
}
```

**UI 控件基类** (在 UIFormPanel options 中使用):
- `UISearchList<T>` — 搜索列表（如生物选择）
- `UIButton` — 按钮
- `UIToggle` — 开关
- `UITextarea<T>` — 多行文本（如 NBT 编辑）
- `UITexturePicker` — 纹理选择器

#### UIMobFormPanel 实现

**文件**: `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/panels/UIMobFormPanel.java:21-78`

**结构**:
```java
public class UIMobFormPanel extends UIFormPanel<MobForm> {
    public UIButton pick;           // 选择纹理按钮
    public UIToggle slim;            // 细长模型切换
    public UISearchList<String> mobID;   // 生物 ID 搜索列表
    public UITextarea<TextLine> mobNBT;  // NBT 编辑区
    
    public UIMobFormPanel(UIForm editor) {
        super(editor);
        
        // 创建各控件并配置
        this.pick = new UIButton(..., (b) -> {
            Link link = this.form.texture.get();
            UITexturePicker.open(this.getContext(), link, (l) -> this.form.texture.set(l));
        });
        
        this.slim = new UIToggle(..., (b) -> {
            this.form.slim.set(b.getValue());
        });
        
        this.mobID = new UISearchList<>(new UIStringList((l) -> this.form.mobID.set(l.get(0))));
        this.mobID.list.background().add(mobIDs);  // 从 BuiltInRegistries 加载
        
        this.mobNBT = new UITextarea<>((t) -> this.form.mobNBT.set(t));
        
        // 添加到右侧 options 面板
        this.options.add(this.pick, this.slim, this.mobID, this.mobNBT);
    }
    
    @Override
    public void startEdit(MobForm form) {
        super.startEdit(form);
        // 同步 UI 与 Form 数据
        this.slim.setValue(this.form.slim.get());
        this.mobID.list.setCurrentScroll(this.form.mobID.get());
        this.mobNBT.setText(this.form.mobNBT.get());
    }
}
```

---

## 6. 客户端渲染器注册

### 渲染器注册系统

**文件**: `new/src/client/java/mchorse/bbs_mod/forms/FormUtilsClient.java:46-150`

**静态初始化块** (行 52-90):
```java
static {
    register(BillboardForm.class, BillboardFormRenderer::new);
    register(ExtrudedForm.class, ExtrudedFormRenderer::new);
    register(LabelForm.class, LabelFormRenderer::new);
    register(ModelForm.class, ModelFormRenderer::new);
    register(ParticleForm.class, ParticleFormRenderer::new);
    register(BlockForm.class, BlockFormRenderer::new);
    register(ItemForm.class, ItemFormRenderer::new);
    register(AnchorForm.class, AnchorFormRenderer::new);
    register(MobForm.class, MobFormRenderer::new);
    register(VanillaParticleForm.class, VanillaParticleFormRenderer::new);
    register(TrailForm.class, TrailFormRenderer::new);
    register(FramebufferForm.class, FramebufferFormRenderer::new);
}
```

**方法**: `FormUtilsClient.register(Class<T>, IFormRendererFactory<T>)` → 存储到静态 `map`

**获取渲染器**: `FormUtilsClient.getRenderer(Form)` — 延迟创建渲染器实例

---

## 7. 完整清单：添加新 Morph 类型的必需步骤

### 服务端 (新增"声音伪装目标"为例)

**第1步**: 创建新 Form 类
- **文件**: `new/src/main/java/mchorse/bbs_mod/forms/forms/SoundForm.java`
- **继承**: `extends Form`
- **字段**: 声音相关字段（如 `sound` ValueString, `pitch` ValueFloat, `volume` ValueFloat 等）
- **构造**: 在 constructor 中 `add()` 所有字段

**第2步**: 注册 Form 类型
- **文件修改**: `new/src/main/java/mchorse/bbs_mod/BBSMod.java` (~165-177 行)
- **添加代码**:
  ```java
  .register(Link.bbs("sound"), SoundForm.class, null)
  ```

### 客户端 (必需)

**第3步**: 创建渲染器
- **文件**: `new/src/client/java/mchorse/bbs_mod/forms/renderers/SoundFormRenderer.java`
- **继承**: `extends FormRenderer<SoundForm>`
- **实现**: `renderInUI()` (UI 预览) 和 `render*()` 方法（世界渲染）
- 可选：实现 `ITickable` 接口以支持 tick 更新

**第4步**: 注册渲染器
- **文件修改**: `new/src/client/java/mchorse/bbs_mod/forms/FormUtilsClient.java` (静态初始化块 52-90)
- **添加代码**:
  ```java
  register(SoundForm.class, SoundFormRenderer::new);
  ```

**第5步**: 创建 UI 编辑器
- **文件**: `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/forms/UISoundForm.java`
- **继承**: `extends UIForm<SoundForm>`
- **实现**: constructor 中创建 defaultPanel 并注册

**第6步**: 创建 UI Panel
- **文件**: `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/panels/UISoundFormPanel.java`
- **继承**: `extends UIFormPanel<SoundForm>`
- **实现**: constructor 中创建 UI 控件（UISlider、UIColorPicker 等），添加到 `this.options`
- **实现**: `startEdit(SoundForm)` 方法同步 UI 与 Form 数据

**第7步**: 注册 UI 编辑器
- **文件修改**: `new/src/client/java/mchorse/bbs_mod/ui/forms/editors/UIFormEditor.java` (静态初始化块 133-144)
- **添加代码**:
  ```java
  register(SoundForm.class, UISoundForm::new);
  ```

### 总结表格

| 步骤 | 文件 | 类名 | 继承/实现 | 操作 |
|------|------|------|---------|------|
| 1 | `forms/forms/SoundForm.java` | `SoundForm` | `Form` | 创建新类，add() 字段 |
| 2 | `BBSMod.java` | - | - | 添加 `.register(Link.bbs("sound"), SoundForm.class, null)` |
| 3 | `forms/renderers/SoundFormRenderer.java` | `SoundFormRenderer` | `FormRenderer<SoundForm>` | 创建新类，实现 render 方法 |
| 4 | `FormUtilsClient.java` | - | - | 添加 `register(SoundForm.class, SoundFormRenderer::new);` |
| 5 | `ui/forms/editors/forms/UISoundForm.java` | `UISoundForm` | `UIForm<SoundForm>` | 创建新类，register panel |
| 6 | `ui/forms/editors/panels/UISoundFormPanel.java` | `UISoundFormPanel` | `UIFormPanel<SoundForm>` | 创建新类，add() UI 控件 |
| 7 | `UIFormEditor.java` | - | - | 添加 `register(SoundForm.class, UISoundForm::new);` |

---

## Caveats & Notes

1. **ID 命名**: Form 的 ID 必须通过 `Link.bbs("id")` 注册，序列化时自动附加 "id" 字段，反序列化时 FormArchitect 据此查找类型
2. **NBT 存储**: Morph 包含的 Form 通过 MapType 序列化再转 NBT，最终存储在 player 数据中；支持跨版本升级（MissingForm 作为 fallback）
3. **序列化扩展**: 若要自定义字段序列化，覆盖 Form 的 `fromData(BaseType)` 方法
4. **渲染 tick**: ParticleFormRenderer 实现 ITickable 驱动粒子更新；其他 renderer 通过 Form.update() 间接驱动
5. **UI 控件**: options 面板使用 UIScrollView，宽度可通过 draggable 调整；各控件需要手动绑定 Form 字段的 getter/setter
6. **Plugin 扩展**: 客户端 API 提供 registerRenderer / registerEditor 接口允许插件添加自定义 Form 类型
