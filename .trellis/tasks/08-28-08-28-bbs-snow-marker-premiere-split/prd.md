# BBSsnow 标记功能 + Premiere 按标记切割导出

## Goal

在 bbs-snow-addon 中添加达芬奇式标记(Marker)功能:标记可打在剪辑上或时间轴位置上;监视器按钮行骨骼优先性右边添加标记按钮,左键创建标记(选中剪辑优先创建在剪辑上),右键配置标记颜色;时间轴渲染标记;Premiere 导出设置新增『按创建的标记切割导出』选项,导出时将剪辑标记与时间轴标记一并计算,在 XMEML 中切割视频片段。

## Requirements

### 1. 标记(Marker)数据与交互
- 标记分两类:
  - **剪辑标记 (clip marker)**:绑定一个相机剪辑(记录剪辑在 Clips 列表中的索引 + 剪辑内偏移 tick),剪辑整体移动时标记跟随;
  - **时间轴标记 (timeline marker)**:绑定绝对 tick。
- 监视器(UIFilmPreview)下方按钮行中,在"骨骼优先性"按钮右侧添加标记按钮。
- 左键按钮:在当前光标 tick 处创建标记;若当前选中了剪辑(UIClips delegate.getClip() != null)则优先创建剪辑标记。
- 右键按钮:打开配置菜单 —— 设置默认标记颜色、列出当前 Film 已有标记(可删除单个 / 清空全部)。
- 标记数据随 Film 持久化(ValueGroup 数据模型)。

### 2. 时间轴渲染
- UIClips 时间轴上渲染标记(按颜色画竖线/标记图形),时间轴标记在标尺区显示,剪辑标记画在对应剪辑上。

### 3. Premiere 导出联动
- 在 Premiere 导出按钮右键设置面板(UIPremiereExportSettingsOverlayPanel)新增开关:"按照创建的标记切割导出"。
- 开启后导出时把时间轴标记与剪辑标记都计算在内(剪辑标记换算为绝对 tick = clip.tick + offset,索引越界时按记录的绝对 tick 退化),按标记位置将导出视频在 XMEML 中切割为多个 clipitem(同一视频文件、不同 in/out)。

## Acceptance Criteria

- [ ] 监视器按钮行在骨骼优先性按钮右侧出现标记按钮
- [ ] 未选中剪辑时左键 → 在当前光标 tick 创建时间轴标记;选中剪辑时 → 创建剪辑标记
- [ ] 右键按钮 → 可配置标记颜色、删除已有标记
- [ ] 时间轴上可见标记渲染
- [ ] Premiere 导出设置出现"按照创建的标记切割导出"开关
- [ ] 开启开关导出后,XMEML 视频轨按所有标记切割为多个 clipitem
- [ ] bbs-snow-addon `./gradlew build` 通过

## Notes

- Film 为宿主类,external addon 通过 Mixin 向其注入 markers ValueGroup(Film 构造 TAIL add 进组),持久化走 Film 自身的 NBT/JSON 序列化。
- 剪辑标记用"索引 + 偏移"而不是剪辑对象引用,避免序列化循环与剪辑删除后的悬空引用;导出时索引失效按绝对 tick 退化。
- 帧换算复用 `TickToFrameConverter.ticksToFrames`(tick × fps / 20)。
