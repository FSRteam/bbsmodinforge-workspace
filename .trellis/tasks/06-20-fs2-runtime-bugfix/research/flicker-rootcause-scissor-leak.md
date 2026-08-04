# 闪烁根因终局：TimelineRulerRenderer 的 scissor 栈泄漏（2026-07-04 定案）

## 根因

`ui/utils/renderers/TimelineRulerRenderer.render`（带 rulerDecorator 的重载，FS2 迁移新增代码）：

```java
context.batcher.clip(area, context);        // ① GuiGraphics ScissorStack 压栈 +1
...
if (rulerDecorator != null)
{
    rulerDecorator.accept(context);          // decorator 内部平衡（净 0）
    context.batcher.clip(area, context);     // ② 又压 +1（作者误以为 clip 是覆盖式 set）
}
...
context.batcher.unclip(context);             // ③ 只弹 -1 —— 每帧净泄漏 +1 层
```

BBS 的 `Batcher2D.clip` = `GuiGraphics.enableScissor`（**压栈**语义），`unclip` = `disableScissor`（弹一层）。decorator 非 null 时每帧净压一层不弹 → GL scissor 永久钳制在时间轴标尺矩形。

## 触发链（完美吻合全部症状）

- 放置回放 actor → `UIReplaysEditor.updateChannelsList` 创建 keyframeEditor 并注入 `rulerRenderer`（:635）→ `UIKeyframeDopeSheet:743` 每帧以非 null decorator 调 `TimelineRulerRenderer.render` → 每帧泄漏。**这就是"添加回放后才闪"的条件**。`UIClips:1872` 的调用不传 decorator，不泄漏。
- **闪烁**：scissor 残留后，矩形外的所有绘制写入失败（显示陈旧缓冲/黑），下一帧 clip/unclip 配对短暂恢复→再泄漏→交替。
- **区域性**：残留矩形 = 时间轴区域（用户截图中暂停菜单只有底部条带被重画，上部为编辑器 UI 残留帧——铁证）。
- **跨 Screen 残留**：vanilla 从不主动重置 scissor → ESC 菜单/主界面同样被钳制。
- **三轮逐帧日志全"正常"**：scissor 不在任何日志监控项里（framebuffer/尺寸/toggle 都对）。
- 与 mod 无关（用户纯 BBS 复现）。

## 修复（2026-07-04，已编译构建）

1. `TimelineRulerRenderer.render`：decorator 分支改为先 `unclip` 再 `clip`（保持栈深恒 1），附注释说明压栈语义。
2. 附带修复（同日）：`UIPickableFormRenderer` 工作区退化——6/28 加固时弄丢的 `GlStateManager._enableScissorTest()` 收尾已补回。
3. 防御纵深（同日，可选保留）：`UIFilmController.renderStencil` finally 中加全局写状态复位（colorMask/depthMask/scissor/shaderColor/blend）+ 主动 `endBatch` 清共享缓冲残留顶点。

## 审计结论（全仓库 clip/unclip 平衡）

其余全部配对且无提前 return：UIClips（5 对）、UIKeyframeDopeSheet（3 对）、UIKeyframeGraph、UIReplaysEditor.renderRuler、UIPixelsEditor、UIEnvelope/UICurveClip/UIKeyframeClip/UIRemapperClip（共用平衡的静态 renderRuler）。唯一净不平衡即本根因。

## 教训

- "每帧交替、区域性、跨 Screen 残留、CPU 状态日志正常"的组合 → 首查 scissor/colorMask 类**不在常规监控里的 GL 写状态**。
- 用户的现象观察（残留截图）比日志插桩定位快一个数量级。
- diff 行号上的 clip/unclip 计数会被跨方法新增行误导，必须回到工作区文件逐方法核对。
