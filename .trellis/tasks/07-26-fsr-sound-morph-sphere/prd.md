# 圆形声音伪装 Morph

父任务:`07-26-fsr-sound-morph-parent` · 依赖:`07-26-fsr-sound-acoustic-core`(必须先完成)

## Goal

交付第一个可用的声音伪装目标:**圆形(球形)声音 Form** —— 以伪装目标位置为球心向各方向均匀发声,半径可编辑,能用关键帧控制何时开始/停止播放。

本任务是「把声学核心接到 BBS Form 体系上」的**首次贯通**:走完注册 → 序列化 → 编辑面板 → 实际发声的完整链路。世界内可视化不在本任务(归子任务 3,与锥体共用绘制原语)。

## Requirements

### R1 Form 类型

- R1.1 `SoundSphereForm extends AbstractSoundForm`(基类来自子任务 1),追加 `ValueFloat radius`。
- R1.2 实现基类的形状增益抽象方法:球形各向同性,方向增益恒为 `1`。
- R1.3 覆写 `getDefaultDisplayName()`,给出可读名称。
- R1.4 以 `Link.bbs("sound_sphere")` 注册进 `FormArchitect`(`BBSMod.java:553-566` 的注册块)。

### R2 客户端接入

- R2.1 注册 `FormRenderer` 到 `FormUtilsClient.java:78-89` 的静态块;UI 预览给出可辨识的图标/占位表现。
- R2.2 注册 UI 编辑器到 `UIFormEditor.java:133-144` 的静态块。

### R3 编辑面板

- R3.1 `UISoundSphereFormPanel extends UIFormPanel<SoundSphereForm>`,分组呈现:音源 / 发射形状 / 衰减 / 反射。
- R3.2 音频选择复用 `UISoundOverlayPanel`(`ui/framework/elements/overlay/UISoundOverlayPanel.java`,构造签名 `(Consumer<Link>, UIContext)`),用法照 `UIAudioClip` 既有模式。
- R3.3 数值参数用 `UITrackpad` 并限幅;布尔用 `UIToggle`;衰减模型用循环选择控件。
- R3.4 提供试听按钮,在编辑器中即时预览当前参数(PRD R7.3)。
- R3.5 `startEdit(form)` 中把 Form 数据同步回 UI 控件(照 `UIMobFormPanel` 既有模式)。

### R4 播放贯通

- R4.1 该 Form 在世界中存在且 `playing` 为真时,按声学核心算出的增益与位置实际发声。
- R4.2 关键帧控制 `playing` 时,正播 / 倒播 / scrub / 循环四种情形播放行为正确(无重复播放、无漏播、无残留音源)。
- R4.3 关闭反射时,表现与一个普通 3D 定位音源等价。

## Acceptance Criteria

- [ ] 在伪装选择器中能创建 `sound_sphere`,保存后重新载入参数不丢(序列化往返一致)。
- [ ] 面板可选音频、调半径/音量/衰减模型/反射参数,试听按钮可用。
- [ ] 时间轴上 K 帧 `playing`,正播/倒播/scrub/循环四种情形播放行为正确。
- [ ] 关闭反射时听感与普通 3D 音源等价;开启反射后能听出回声。
- [ ] 旧存档(不含本 Form)读取无异常;新 Form 的存档在缺少新字段时走默认值。
- [ ] `cd new && ./gradlew check` 真实 exit code 为 0,日志确认 BUILD SUCCESSFUL 且 0 FAILED。
- [ ] 用户游戏内冒烟确认。

## Notes

- 面板控件用法与分组参考 `research/irlights-addon-reference.md` §3(`UISpotlightFormPanel` 的真实代码)。
- 本任务打通的注册与面板模式,子任务 3 直接复用,届时只补形状差异。
- 若在贯通过程中发现声学核心的接口不够用,回到子任务 1 修改,不要在本任务里绕过。
