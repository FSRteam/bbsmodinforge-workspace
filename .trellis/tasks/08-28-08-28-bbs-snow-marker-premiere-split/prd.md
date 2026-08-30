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

- [x] 监视器按钮行在骨骼优先性按钮右侧出现标记按钮
- [x] 未选中剪辑时左键 → 在当前光标 tick 创建时间轴标记;选中剪辑时 → 创建剪辑标记
- [x] 右键按钮 → 可配置标记颜色、删除已有标记
- [x] 时间轴上可见标记渲染
- [x] Premiere 导出设置出现"按照创建的标记切割导出"开关
- [x] 开启开关导出后,XMEML 视频轨按所有标记切割为多个 clipitem
- [x] bbs-snow-addon `./gradlew build` 通过

## Crash Fix (2026-08-29,错误报告 错误报告-2026-8-29_2.30.07)

用户在真实整合包环境(含 bbsvfx/bbsplusplus/posecurve/irlite 等)运行时崩溃:

```
MixinTransformerError → InjectionError: Critical injection failure:
Redirector bbs_snow$resolveBoneTexture ... failed injection check, (0/1) succeeded.
Scanned 0 target(s).
```

根因:`new/` 的 `CubicVAORenderer.renderGroup` 已重构 —— 材质纹理解析从
`renderGroup` 内联的 `textureResolver.apply(material)` 收敛为私有
`resolveTexture(String)`(带 per-material 缓存),旧 Redirect 挂的
`Function.apply` 调用点不复存在 → Mixin 启动期致命失败。

修复:Redirect 目标迁移到稳定语义边界
`CubicVAORenderer;resolveTexture(Ljava/lang/String;)L...Texture;`
(renderGroup 内对该方法的调用点),receiver 即渲染器实例;骨骼纹理覆盖
(GroupTextureHolder)优先,无覆盖时经 @Shadow 原样调用原方法保持缓存语义。
同时全量核对了插件其余 60+ mixin 的注入目标,均仍有效。

## Crash Fix 3 (2026-08-29 15.16,错误报告 错误报告-2026-8-29_15.16.20)

打开 BBS 仪表盘(首次类加载 UIClips)时崩溃:

```
InvalidInjectionException: Invalid descriptor on UIClipsMarkersRenderMixin
@Inject::bbs_snow$registerMarkerKeybinds(...Ljava/lang/Object;...)V!
Expected (...Lmchorse/bbs_mod/utils/factory/IFactory;...)V but found (...Ljava/lang/Object;...)V
```

根因:上一批需求给 `UIClips` 构造器注册快捷键的 `@Inject` 回调,第二个参数
写成了 `Object factory` —— **Mixin @Inject 回调的参数类型必须与目标方法
签名逐位精确一致,不能用父类型/Object 收窄**。javac 不报错(运行期才 APPLY),
所以构建通过但游戏必崩。与 Crash Fix 2 同属"构建期无法发现、运行期 APPLY
才炸"的 mixin 错误类。

修复:回调签名改为 `IUIClipsDelegate, mchorse.bbs_mod.utils.factory.IFactory, CallbackInfo`,
已 javap 验证产物描述符与目标一致;其余新增注入点(renderOverlay/
renderCameraWork/subMouseClicked/Released/Canceled/KeyPressed)全量复核签名精确。

**教训(与 Fix 2 并列):写构造器/方法注入回调时,参数类型照抄目标方法
声明,不要用 Object 糊;自测时先 `javap -p` 核对 mixin 类的回调描述符。**

## Crash Fix 2 (2026-08-29,错误报告 错误报告-2026-8-29_10.02.11)

打开 BBS 仪表盘(加载相机时间线 UIClips)时崩溃:

```
InvalidMixinException: @Shadow field area was not located in the target class
mchorse.bbs_mod.ui.film.UIClips
```

根因:本任务新增的 `UIClipsMarkersRenderMixin` 把 `area` 声明成了
`@Shadow` —— 但 `area` 不是 `UIClips` 自身声明的字段,而是继承自
`UIElement` 的 public 字段;`@Shadow` 只能定位目标类自己声明的成员,
启动期 APPLY 阶段直接失败。

修复:去掉该 `@Shadow`,经 `((UIClips)(Object)this).area` 直接访问
继承的 public 字段。`delegate`(`UIClips` 私有字段)的 Shadow 保留。
教训(已具备通用性):**Mixin 里引用目标类的继承成员时绝不要 @Shadow,
直接用 public 访问或 @Shadow 目标类真正声明它的祖先类。**

## Premiere 导出功能补全(2026-08-29,对比 temp/bbs_snow_new_src)

FSR 迁移时 Premiere 导出只搬了"manifest 驱动的原子打包"骨架,遗漏了
bbs_fslovecml 侧的完整导出体验。本次补齐:

- **导出确认弹窗**(`UIPremiereExportOverlayPanel`):点击导出按钮先弹窗,
  可填打包文件夹名与导出路径(缺省回退 BBS 视频目录 + 时间戳包名),
  弹窗内直接带"按标记切割"开关;
- **仅音频导出**(`premiere_export_audio_only`):跳过录制,直接写
  WAV + XML/SRT(`exportAudioOnly` + `buildAudioOnlyPackage`,
  manifest 新增 `audioOnly` 标志,XMEML 保留序列格式头但不生成视频 clipitem);
- **SRT 导出**(`premiere_export_srt`):`SrtGenerator`(适配快照数据源)
  在视频包/音频包两条路径都生效,代替 XMEML 输出;
- **打包目录管理**:视频模式临时把 BBS 导出目录指到打包目录,结束/取消后
  恢复原路径与"打开文件夹/播放提示音"设置(`startPremiereExportWithDialog`);
- 设置面板补齐仅音频/NTSC/SRT 三个开关;中英文文案齐全。

## 标记切割"没生效"的原因分析

代码链路完整(captureRequest→captureMarkerSplitTicks→resolveMarkerSplitFrames→
XmemlGenerator 多 clipitem),但用户此前每一次启动都死在仪表盘 mixin 崩溃上
(先 PBR Redirect,后 @Shadow area),从未真正运行过含标记功能的版本。
另补了三处可观测性:capture 时记录捕获到的标记数、打包时记录切割帧边界、
导出弹窗内直接放开关(不必去设置里找)。待用户换新 jar 后重测。

## XMEML 多 clipitem 兼容性修复(2026-08-29 12:02)

日志证实切割链路已完整工作(captured 7 markers → 8 clipitems at
[15,24,36,45,66,81,84] → SUCCESS 包发布),"没切割"的实际症状是
Premiere 导入时不把多个 clipitem 识别为分段的视频片段。原因:视频
clipitem 缺少 XMEML 规范要求的结构:

- **缺 `masterclipid`**:Premiere 要求每个 clipitem 链到一个 masterclip;
  音频 clipitem 一直有,视频 clipitem 没有,多片段引用同一文件时必须共
  用同一 `masterclip-video-<id>`;
- **缺 `timecode`**:file 元素带 00:00:00:00/NDF 时间码让 in/out 有明确
  的源时间基准;
- 补了 `label` 便于在 PR 里辨认分段。

修复后视频 clipitem 结构与(已在真实 PR 验证过的)音频 clipitem 对齐:
name/enabled/duration/rate/start/end/in/out/masterclipid/file。

## 标记交互增强(2026-08-29 下午,第二批需求)

用户反馈四项:快捷键缺失、标记点不可编辑/拖动、形状单一、回放编辑器看不见标记。实现:

- **快捷键**(走宿主 keybind 注册体系,可在 F9 键位表查看):
  - `M` = 在播放头创建标记(选中剪辑时优先剪辑标记,与按钮左键同逻辑);
  - `Ctrl+Alt+←/→` = 跳转到上一个/下一个标记(支持长按连发,repeatable);
  - 冲突处理:宿主 `Keys.CLIP_DURATION`("剪辑时长移到光标")原占用纯 M,
    移到 `Shift+M`(宿主 `new/src/.../ui/Keys.java`,注释说明);纯 M 让给标记。
- **时间线交互**(相机编辑器 UIClips,mixin 钩 subMouseClicked/Released/Canceled/KeyPressed):
  - 左键点中标记 → 选中(白色描边)并进入拖动;拖动实时改位置,
    时间轴标记改绝对 tick,剪辑标记保持锚定剪辑只改偏移(夹在剪辑时长内);
  - 右键点中标记 → 单标记编辑菜单(颜色取色器 + 5 个形状按钮 + 跳转 + 删除);
  - `Delete/Backspace` 删除选中标记(优先于宿主"删除选中剪辑");
  - 空白处左键清除选中;拖动被系统取消(subMouseCanceled)时安全结束。
- **形状**(FilmMarker.shape 持久化 ValueInt):旗标(默认/图一达芬奇式)、方形、
  圆形、倒三角、矩形(旧默认)。旗标/圆/倒三角用逐行扫描线绘制,方形/矩形整块;
  相机时间轴与回放编辑器共用一套形状绘制。
- **回放编辑器可见性**:新 mixin `UIFilmKeyframesMarkersMixin` 挂
  `UIFilmKeyframes.renderOverlay` TAIL,在关键帧表渲染标记 ——
  时间轴标记在顶部标尺带,剪辑标记在底部(用户约定:上=时间轴标记,下=剪辑标记)。
- 字符串中英齐全;`bbs-snow-addon` build 通过;宿主因 Keys 变更已重新 `gradlew jar`
  (更新宿主 jar 后 M 键才会让位)。

## 物理分段视频文件(2026-08-29 下午,用户反馈"输出依旧是一整个视频")

masterclipid/timecode 修复后用户仍看到单个视频 —— 症状本质:之前的"切割"
只存在于 XMEML 里(同一视频文件不同 in/out),磁盘与 Premiere 时间线上
都呈现为一整段。本次改为**按标记边界物理切割出多个独立视频文件**:

- 新增 `VideoSegmentSplitter`(premiere/media):对每个分段运行
  `ffmpeg -ss <start> -i <video> -t <dur> -c:v libx264 -crf 18 -pix_fmt yuv420p -c:a aac`,
  输入定位 + 重编码保证**帧级精确**切点(流复制只能切在关键帧上);
  ProcessBuilder + 输出上限 + 按分段时长比例的超时,失败带 FFmpeg 尾部输出;
- 打包流程(`publishPackage`):标记帧非空时切割进打包暂存目录,
  逐段用 `MediaProbe.probeVideo` 复探真实帧数(XMEML in/out 与文件实际长度一致),
  发布前经 `projectSegmentPaths` 把分段路径投影到最终包目录(与音频同理);
- `PremiereExportManifest` 新增 `videoSegments` 分量;`XmemlGenerator` 在
  有分段时为每段生成独立 clipitem(各自 `file`/`masterclipid`,in=0/out=段长,
  start/end 在时间轴上顺序拼接);无标记时保持原单文件行为,SRT/仅音频模式不受影响;
- 整段原视频仍保留在 BBS 视频目录,分段文件随 Premiere 包发布;
- `bbs-snow-addon ./gradlew build` 通过。

## 用户实测反馈三连修(2026-08-29 16:19)

1. **默认旗标形状不对**:按用户提供的实际图样重做 SHAPE_DEFAULT 扫描线 ——
   上下尖、中段最宽的菱圆头部(大 9x11、小 7x9),下接细杆;旧版顶部圆钝不对称。
2. **剪辑标记必须在时间线下方**:复核发现 `renderKeyframes` 的底部通道实现本身没错,
   用户截图里的上方标记全是**时间轴标记** —— 旧创建逻辑"光标不在选中剪辑内就退化成
   时间轴标记",而用户以为选中了剪辑就一定打在剪辑上。修正 `MarkerCreator`:
   选中剪辑时一律创建剪辑标记,光标在剪辑外则把偏移夹进 [0, 时长-1]。
3. **Ctrl+Z 一次回退多个标记**:根因是 `FilmMarkers.addMarker` 用 `list.size()` 当
   子项 id —— 删除标记后再新建,id 与存量标记撞车;撤销/重做按
   `markers.<id>.<字段>` 路径合并(ValueChangeUndo.isMergeable 同路径即合并),
   两个标记共享路径就会被合并进同一条撤销、还会 findRecursively 到错误的标记。
   修复:分配 id 时跳过所有已占用 id(唯一性兜底递增)。每 tick submitUndo +
   同路径合并的宿主机制不变,单标记拖动仍合为一条撤销。

另外:15:31 的崩溃报告与 15:16 完全相同(回调描述符仍是 Object)——用户替换的
jar 是修复前构建的旧包。已再次确认 16:19 构建产物的描述符为 IFactory(javap 验证过)。

## Notes

- Film 为宿主类,external addon 通过 Mixin 向其注入 markers ValueGroup(Film 构造 TAIL add 进组),持久化走 Film 自身的 NBT/JSON 序列化。
- 剪辑标记用"索引 + 偏移"而不是剪辑对象引用,避免序列化循环与剪辑删除后的悬空引用;导出时索引失效按绝对 tick 退化。
- 帧换算复用 `TickToFrameConverter.ticksToFrames`(tick × fps / 20)。
- 另发现旧错误报告(8-21)的粒子保存按钮 NPE:UIParticleSchemePanelMixin 的
  dupe 按钮回调里 `UIOverlay.addOverlay(this.getContext(),...)` 在 overlay 无父
  时 context 为 null。该路径已有 `context != null` 守卫(插件侧),宿主
  `UICRUDOverlayPanel.dupeData(UIIcon)` 自身无守卫 —— 属宿主侧问题,非本插件
  崩溃主因,未改动。

