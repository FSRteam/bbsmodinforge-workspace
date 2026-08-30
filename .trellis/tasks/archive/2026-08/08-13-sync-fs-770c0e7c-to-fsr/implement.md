# Implementation plan: sync FS 770c0e7c delta to FSR

## 1. Freeze boundaries(worktree 与初始标记)

- [x] 上游 fetch + ancestry 校验通过:区间 16 个提交,`4220b453` 是 `770c0e7c`
      祖先,`770c0e7c` 被 `origin/1.21.1@a982f1dc`、`origin/1.20.1@17183d15` 包含。
- [x] `new/` fetch:`origin/master@eaa43066` 已含于 `637d6f76`(本地领先 7 个
      未推送提交),基点取 `637d6f76`。
- [x] worktree `.worktrees/new-fs-770c0e7c` 建于 `637d6f76`,分支
      `codex/sync-fs-770c0e7c-to-fsr`;原 `new/` 工作区零改动。
- [x] prd.md 回填 `fsr_initial_commit` 与上游参考完整 SHA;
      `research/disposition-matrix.md` 建立 16 项处置矩阵骨架。

## 2. Group E:延迟透明深度写修复(P0)

- [x] 对照上游 `baafc5a4`/`770c0e7c` 与 `origin/1.21.1` 同文件形态,在 FSR 的
      `FormTranslucentQueue`/`ModelInstance`/`CustomVertexConsumerProvider`
      对应点恢复实体几何深度写、`RenderLayerCommand` 恒写深度。
- [x] 平面 form(billboard/framebuffer/label)显式保持不写深度并注释。
- [x] 聚焦验证:物品 form、方块 form、半透明纹理模型场景的渲染断言/烟测清单;
      确认粒子与 `FormTranslucentQueue` 所有权无 diff。

## 3. 独立小修复(F/A4/C4)

- [x] `861257a7`:移植 `applyViewShear`(仅绘制与拾取 stencil),核对与 FSR
      `514b2fe2` 互不覆盖;屏幕中心无跳变断言。
- [x] `95a110e2`:核对 FSR `46613ff5` 之后 resize 链路,缺则补 1 行,已等价则记录。
- [x] `d0f3754d`:`UINestedEdit` 间距 1 行。

## 4. Group D:滑条步进

- [x] 验证 FSR 已含 `UISliderTrackpad`(基线 `9496cbc3` 波次);缺则先补前置。
- [x] 移植 `fe552a58`:绝对网格、Shift/Alt 细分、`slider(step)` 设置体系、
      整数滑条取整;保留 FSR 主题色/圆角/远程输入保护。
- [x] 移植 `0773f9df`:FOV 去 `.slider()`。

## 5. Group C:主题刷新

- [x] `459e9db7`:色阶常量经 FSR 主题层落地,明/暗镜像保留。
- [x] `52c5db8f`:4 个新设置项 + `bevelBox`→`surfaceBox` 全库更名清扫
      (含 FSR 独有 UI 调用点),编译级确认无残留。
- [x] `bb1ac00f`:UIToggle 重绘,浅色主题核对。
- [x] 语言文件补 4 个设置项词条(en/ru/zh)。

## 6. Group B:热键栏九轨道(最高风险,单独提交)

- [x] 移植 `8a293161` 数据模型:九通道、主手=选中槽、删 `Film.inventory`、
      播放期借用/归还、退出/停服停止 ActionPlayer。
- [x] `FilmLegacy` 按 tick 重放迁移 + `c02aa6e6` 空通道不发言/`migrateWorn`。
- [x] 网络包按 NeoForge payload/StreamCodec 重写 `selectedSlot+packEquipment`;
      ItemStack 序列化按 DataComponent 适配。
- [x] `8c48a185` 轨道配色 + HOTBAR/盔甲/副手图标。
- [x] 回归:`testFilmEditorMigration` 扩展"fs 旧格式电影(含捡起物品)"与
      "FSR 自身旧存档"两条路径;录制写满九槽断言。

## 7. Group H:图标资源

- [x] 取 `8c48a185` 之后的 `icons.png` 合并状态;核对 FSR 图集
      (224,160)/(240,160)/(0,176) 空闲,冲突则重排坐标并同步 `Icons.java`。
- [x] `378bac69` 的 IK/物理图标接线(表单编辑器标签、时间轴类别、IK/物理目标轨道)。

## 8. Group G/I:评估与处置记录

- [x] `7f99e757` 评估:mojmap/NeoForge 下文本 program 注入点、1.21.1 core shader
      格式校验、FSR UI 缩放现状;结论(落地或拒绝/延期+理由)写入处置矩阵。
      若落地,shader 编译失败自动回退路径必须保留。
- [x] `5b8d714f` 处置:核对 FSR 是否做过同样的 `UINumericInput` 基类化;
      默认 rejected 并记录 ABI 坑说明。

## 9. 收尾:本地化、验证、提交与标记

- [x] 全量新增用户可见文本三语核对(en/ru/zh),不删既有词条。
- [x] 共享文件逐个对 `git -C new diff <基点>` 复核,确认 FSR 独有行为
      (主题/远程输入/音频/粒子/翻译)无丢失。
- [x] 串行跑:聚焦测试 → `compileJava compileClientJava` → `check` → `build`
      → `git diff --check`;版本锁与产物名断言
      (`mod_version=0.0.11`、`BBS_FSR_beta_v0.0.11.jar`)。
- [x] 在 worktree 内按组提交(仅迁移所有的文件);记录终止 SHA。
- [x] 回填 prd.md `fsr_terminal_commit`;更新
      `.trellis/spec/new/backend/fs-migration-baseline.md` 签名区
      (`fs_master_migrated_through=770c0e7c`、两个参考标记、
      `fsr_previous_target_commit`/`fsr_target_commit`/`fsr_target_branch`、
      16 项 `fs_delta_*` 处置行);Trellis 记录与代码提交分开。
- [x] 未跑的游戏内烟测(渲染、九轨道录制回放、主题观感、pixel art)逐项记为
      人工验收待办。

## Risky files

- `FormTranslucentQueue`、`ModelInstance`、`CustomVertexConsumerProvider`
  (FSR 半透明架构,语义移植不整替)。
- `Film`/`ReplayKeyframes`/`ActionPlayer`/`ActionManager`/`IEntity` 系
  (存档格式与播放语义)。
- `BBSSettings`、`Batcher2D`、`UIToggle`、trackpad/slider 系
  (FSR 主题与远程输入保护,见基线规格共享文件契约)。
- `icons.png`(二进制,坐标冲突需重排)。
- 网络注册与 payload 类(NeoForge 协议层,勿抄 Fabric 实现)。

## Rollback

- 每组独立提交;任一组失败 revert 该组提交即可,不影响他组。
- 步骤 1 完成前不产生任何代码变更;评估类(G/I)拒绝时零代码变更。
