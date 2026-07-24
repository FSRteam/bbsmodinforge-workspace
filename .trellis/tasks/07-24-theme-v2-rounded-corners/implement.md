# rounded-corners 执行清单

前置阅读顺序:本任务 `prd.md` → `design.md`(含映射对照表,**编码前必读**)→ 父任务 `design.md` §3.1 → 参考源 `temp/bbs-refreshed-2.0-public-1.20.1-decompiled/src/mchorse/bbs_mod/ui/framework/elements/utils/Batcher2D.java:129-299`。

## 阶段 1:资源与原语

- [ ] 拷贝两张 mask png(design.md §3 路径)到 `new/src/client/resources/assets/bbs/assets/textures/gui/`。
- [ ] 新建 `new/src/client/java/mchorse/bbs_mod/ui/utils/UIArt.java`(mask Link 常量 + 带校验的纹理获取)。
- [ ] `Batcher2D` 增加 `roundedBox / roundedFrame / filledCircle`(design.md §1 对照表 + §2 规格;几何照参考源,API 照本仓库惯例,**裸 Tesselator 前 flushBeforeTesselator()**)。
- [ ] 验证:`cd new && ./gradlew compileClientJava > /tmp/c1.log 2>&1; echo REAL_EXIT=$?`(REAL_EXIT=0;**禁止管道结尾判断成败**)。

## 阶段 2:主题参数

- [ ] `UITheme` + Builder:`cornerChrome/cornerPanel/cornerWidget`(缺省 0)。
- [ ] `ThemeParser`:`style.corner_radius.{chrome,panel,widget}` 解析(坏值回退 0、钳 [0,16]、继承参与);`defaultDark` 全 0。
- [ ] `BBSSettings`:三个取用函数(v1 语义函数模式)。
- [ ] `new/src/themeCoreTest/.../ThemeCoreTest.java`:解析用例(缺省 0 / 合法 / 坏值 / 钳制 / base 继承)。
- [ ] 验证:`cd new && ./gradlew testThemeCore > /tmp/c2.log 2>&1; echo REAL_EXIT=$?`。

## 阶段 3:控件接入(每文件一个独立批次,依次进行)

按 design.md §5 表格执行,统一模式(优先直调 roundedBox,内部半径 0 自动走 box):

- [ ] UIOverlayPanel(chrome)
- [ ] UIContextMenu(chrome)
- [ ] UIButton(widget)
- [ ] UIIcon(widget)
- [ ] UITextbox / UIBaseTextbox(widget)
- [ ] UIToggle(widget;`r>0` 才切 macOS 样式,`r==0` 保持 v1 方块渲染逐位不变)
- [ ] UIList 选中态(panel)
- [ ] 每批后:`compileClientJava` 真实 exit code 验证。

## 阶段 4:演示与文档

- [ ] 内置主题演示:在 `amber/theme.json` 或新增 strawberry(与父任务验收协调,首选给 amber 加 `corner_radius` 展示)——dark/light/example **不加**(保持 v1 逐位等价基准)。
- [ ] `new/docs/theme-spec/README.md`:`style.corner_radius` 逐 key 说明;example-theme 同步(example 显式写 0 全集,维持"全 key 显式"惯例)。
- [ ] `.trellis/spec/frontend/bbs-ui-theming.md`:圆角规约(三级半径语义、接入模式、mask 覆盖)。

## 阶段 5:收尾验证

- [ ] 全量:`cd new && ./gradlew check > /tmp/check.log 2>&1; echo REAL_EXIT=$?` + 日志 BUILD SUCCESSFUL、0 FAILED。
- [ ] 等价性走查:dark 主题(半径 0)下首批控件渲染路径全部经 `r<0.5 → box()` 分支,记录进执行记录。
- [ ] 用户游戏内冒烟清单:圆角弹层/按钮/输入框/开关/右键菜单/列表选中 + mask 缺失退化(改名 mask 文件验证不崩)。

## 回滚点

阶段 1-2 纯增量(无消费者即无影响);阶段 3 每控件独立回滚;主题不写 corner_radius 即全局回 v1 外观。
