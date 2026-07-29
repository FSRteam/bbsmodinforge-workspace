# S9 执行计划

## 1. 固化数据与注册边界

- [ ] 迁移 `FluidForm` 的 19 个 value、三种 mode、默认值和安全 mode clamp；添加非默认值 clone/serialization verifier。
- [ ] 在 `BBSSnowAddon` 的 `RegisterFormsEvent` 窗口冲突感知地注册 `bbs:fluid`，验证 common/server 类加载不触及 client 类。
- [ ] 在 `BBSSnowModClient` 构造期幂等注册 renderer/editor factory，在 client-started 阶段幂等加入 Extra category exemplar。

## 2. 建立确定性模拟与预算 owner

- [ ] 迁移/整理双 buffer `FluidSimulation`，实现固定 step、force、边界安全查询、resize/reset/release；稳定尺寸零大数组分配。
- [ ] 增加单一 grid/sample budget owner，按比例限制 axis、cells、quads 和 samples；用极端 size/subdivisions verifier 证明不突破上限。
- [ ] 用固定 seed + simulation tick 替换 `Math.random()`，移除所有 wall-clock 可见时间；验证相同输入序列逐 float 一致。

## 3. 迁移交互控制器

- [ ] 将 Yarn world/entity/vector 类型改为 Mojmap，处理 null world、奇异 surface matrix、owner 缺失和被销毁 entity。
- [ ] 复用现有 `FilmsControllerAccessor`，identity 去重播放/editor controller，找到 owner 并计算 anchor 后的语义 surface matrix。
- [ ] 实现 bounded world/replay/root sampling；接入 `fluidRealisticModelInteraction` 的标准 Cubic detailed 分支和 BOBJ/root fallback。
- [ ] 复用 scratch vectors/collections并清理 debug snapshot；验证 setting on/off、sample budget 和 debug true→false。

## 4. 迁移三种 renderer

- [ ] 先完成 Mojmap/PoseStack/Shader/Buffer API 骨架、纹理 fallback 和平衡 GL state transaction，建立 UI/world 空网格 smoke。
- [ ] 实现 FULL_OCEAN 的 bounded surface mesh、simulation height、turbulence、smooth/flat normals。
- [ ] 实现 `fillBlock/sizeY` 四侧面+底面，验证开关前后 vertex/topology/bounds 差异。
- [ ] 实现 PROCEDURAL 的 timeline/logical-time 多频波、waveFrequency 与 simulation overlay。
- [ ] 实现 WATER_DROP 的 bounded sphere、tension/viscosity wobble、smooth/flat normals和极点退化保护。
- [ ] 保证 render pass 不推进 simulation；验证暂停、重复 render、Iris/motion blur 多 pass 下状态不漂移。

## 5. 迁移 UI 与本地化

- [ ] 迁移 `FluidUIKeys`、`UIFluidForm`、`UIFluidFormPanel`，对齐当前 UI API和主题控件约定。
- [ ] 接入 19 个 value、三种 mode visibility、纹理/alpha 颜色和四个 preset；验证预设一次性刷新且 Custom 无副作用。
- [ ] 向 addon 的 `en_us.json`/`zh_cn.json` 增加旧版 30 个 fluid 键，验证键和占位符完全对称。

## 6. 自动验证、构建和严格客户端

- [ ] 扩展 `BBSSnowRuntimeVerification` 覆盖 registry/data/simulation/controller/UI/resources/mesh consumer；S9 日志输出 mode、预算和 sample 摘要。
- [ ] 在没有运行客户端时执行 core freshness/install 检查，运行 `verifyBbsCoreJar build`，保存真实 exit code，不使用会吞退出码的管道。
- [ ] 审计 JAR：S9 类/资源存在，embedded core、Fabric、Yarn 名、wall-clock/random 引用为 0，英语/简中键对称。
- [ ] 运行自动退出严格客户端，确认真实 renderer/editor/category/mesh branch；只有 `bbs_snow.exitAfterVerification=true` 时才由 verifier 主动关闭客户端。
- [ ] 将 world/Film/UI、三 mode、fillBlock、真实实体/Cubic interaction、暂停/逐帧/导出和 Iris 视觉项记录到 S10 的 `s9-manual-acceptance.md`。

## Review Gates

- Gate A：FluidForm + common registration + roundtrip 通过后，再写 client simulation。
- Gate B：simulation/controller 的 determinism/budget 通过后，再接 renderer，避免用渲染卡顿掩盖状态错误。
- Gate C：每个 mode 独立通过有限 geometry verifier 后再合并 GL draw path。
- Gate D：自动 verifier/build/JAR audit 全绿后才安装运行时 addon，并且先确认没有正在使用对应 JAR 的客户端。
- Gate E：严格客户端通过后进入 Trellis check；人工视觉项可按父任务约定留给 S10，但必须有清单。

## 风险与回滚点

- `FluidFormRenderer` 旧实现 784 行，按 API 骨架→单 mode→共享 budget 的顺序迁移；任何阶段都保留可编译 checkpoint，不一次性粘贴整文件。
- controller 会触发其他 Form 的 matrix collection，必须使用当前 context-aware API与 sample budget，避免重复推进 IK/physics 或产生每 tick 大量对象。
- `fillBlock` 是目标修复而非旧 renderer 复制；若视觉 winding/culling 有问题，只回滚侧/底 mesh 分支，不回滚整个 fluid surface。
- 不使用 `git add -A`；不修改/提交 `new/`、`bbs_FSloveCML_1.21.1/` 或用户现有四个无关 spec 修改。
- 不在客户端运行时替换 core/addon JAR；任何自动关闭都必须在日志和交付说明中标注是 verifier 的预期 `client.stop()`。

## 验证命令

```powershell
Set-Location bbs-snow-addon
.\gradlew.bat --no-daemon verifyBbsCoreJar build --max-workers=1 --console=plain

# 仅在确认 S9 run 目录没有客户端进程后执行
.\gradlew.bat --offline --no-daemon installBbsCoreForRun --rerun-tasks --max-workers=1 --console=plain

# 使用项目既有 strict runtimeVerification / exitAfterVerification 属性运行客户端
.\gradlew.bat --offline --no-daemon runClient --max-workers=1 --console=plain
```

附加审计包括 `git diff --check`、root/addon 双仓库 status、JAR entry/bytecode 禁止项和两种 JSON 翻译键集合比较；命令不得通过 `Select-Object`/管道来判定 Gradle 成败。
