# S1 实施记录

## 结论

**Gate 1 通过。** Mixin 从独立 addon 注入 BBS FSR 的类这条路走得通，"独立 addon 子项目"方案成立，S2–S10 可以按原计划推进。

## 证据

### Mixin 注入生效

```
[23:02:27] [mixin/]: Selecting config bbs_snow.mixins.json
[23:02:27] [mixin/]: Selecting config bbs_snow.client.mixins.json
[23:02:27] [mixin/]: Selecting config bbs_snow.client.api.mixins.json
[23:03:09] [mixin/]: Mixing UIBaseMenuProbeMixin from bbs_snow.client.mixins.json
                     into mchorse.bbs_mod.ui.framework.UIBaseMenu
```

`runs/client/.mixin.out/class/mchorse/bbs_mod/ui/framework/UIBaseMenu.class` 被导出，即 apply
后的字节码落盘。

更强的证据来自第一次的崩溃堆栈 —— 注入方法出现在真实调用路径上，不只是 apply 成功：

```
at mchorse.bbs_mod.ui.framework.UIBaseMenu.handler$zbn000$bbs_snow$probe(UIBaseMenu.java:532)
at mchorse.bbs_mod.ui.framework.UIBaseMenu.<init>(UIBaseMenu.java:85)
at mchorse.bbs_mod.ui.dashboard.UIDashboard.<init>(UIDashboard.java:81)
```

`iris` config 因未安装 Iris 被 FML 整份跳过（只 Selecting 了三份），`requiredMods` 的隔离按预期工作。

### addon 注册与事件桥接

```
[23:03:05] [bbs-addon] accepted external-supplier registration for 'bbs_snow'
           (mchorse.bbs_snow.BBSSnowAddon) while eventWindowOpen=false externalWindowOpen=true
[23:03:10] [bbs-addon] common setup sees collected addon ids: [bbs-core-demo-addon, bbs_snow]
[23:03:10] [bbs-addon] bridging 'bbs_snow' into internal BBS event bus
[23:03:10] [bbs-eventbus] subscribed BBSSnowAddon#onRegisterSettings to RegisterSettingsEvent
[23:03:10] [bbs_snow] PROBE: internal EventBus bridge active (RegisterSettingsEvent received)
```

### 无需 refmap

mixin config 不含 `refmap` 字段，`mixin.env.disableRefMap=false` 下注入正常 —— 印证了
"目标全是不混淆的 BBS 类" 这一前提。NeoGradle userdev 无需任何额外的 mixin AP 配置。

## 两个只有真跑起来才会暴露的问题

### 1. mixin 包下放了非 mixin 类 → 打开仪表盘时崩溃

探针的 logger 伴生类 `BbsSnowProbeLogger` 和 mixin 放在同一个包：

```
IllegalClassLoadError: mchorse.bbs_snow.mixin.client.BbsSnowProbeLogger is in a defined
mixin package mchorse.bbs_snow.mixin.client.* owned by bbs_snow.client.mixins.json
and cannot be referenced directly
```

Mixin 把 config 声明的 package 下所有类都当作 mixin。**构建期不报、启动期不报**，直到玩家第一次
打开 BBS 仪表盘触发 `UIBaseMenu.<init>` 才崩。

修复：辅助状态移到 `mchorse.bbs_snow.BBSSnowMod`（非 mixin 包）。

对 S2–S10 的影响：移植源插件时必须检查其 `mixin/` 目录下是否混有非 mixin 的辅助类，有则移出。
已写入 `bbs-snow-addon/README.md` 与父任务 `design.md`。

### 2. 监听 BBSAddonRegisterEvent 永远收不到 → addon 静默不注册

第一版照 BBS 自带的 `BBSAddonDemoBootstrap` 写事件监听，结果**构建绿、启动绿、mod 列表里也有它，
但监听器永不触发，且没有任何报错**。时序：

```
[bbs-addon] posting BBSAddonRegisterEvent; currently collected 0 addon(s)
[bbs-addon] BBSAddonRegisterEvent finished; collected 1 addon(s)
[bbs-addon] registration window closed after collecting 1 addon(s)
[bbs_snow] mod constructed, waiting for BBSAddonRegisterEvent          ← 晚了
```

两个原因叠加：

1. BBS 用 `ModLoadingContext.get().getActiveContainer().getEventBus()` 拿的是**自己的** mod bus，
   在自己的 `FMLConstructModEvent` 里 post。外部 mod 监听自己的 bus 收不到。demo 能收到只因为
   它就在 BBS 的 jar 里。
2. 硬依赖必然 `ordering = "AFTER"`，等 bbs_snow 构造时事件早已发完、窗口早已关闭。

根因在 `BBSAddonCollector.validateWindowAndId`：内部/外部两条注册路径查**不同的窗口**。

| 窗口 | 谁用 | 关闭时机 |
|---|---|---|
| `registrationOpen` | `BBSAddonRegisterEvent` | BBS `onConstructMod` 结束，极早 |
| `externalRegistrationOpen` | `BBSMod.registerAddon(...)` | BBS `onCommonSetup` 桥接时，晚得多 |

修复：改用 `BBSMod.registerAddon("bbs_snow", BBSSnowAddon::new)` —— public 静态方法，带
`enqueueAddonRegistration` 排队（BBS 未初始化就排队等），校验外部窗口，对加载顺序免疫。

日志 `eventWindowOpen=false externalWindowOpen=true` 精确印证了这个分析。

## 对模板的一处偏离

`verifyBbsCoreJar` 照搬自 `bbs-premiere-export`，但**把"源码比 jar 新"从失败降级为警告**。

原判据在这个工作树里必然常真 —— `new/` 同时被多个会话编辑，首次构建就被 22:21–22:30 的 sound
morph 改动拦下，而那与本 addon 毫无关系。失败意味着被别人未完成的编辑阻塞，重建又会把别人的
半成品打进 jar。真正有意义的保护（jar 存在 + 仍提供本 addon 绑定的 API 入口）保留为强校验。

## AC6 负向验证的结果，直接改变后续验证策略

把注入点改成不存在的方法 `bbsSnowThisMethodDoesNotExist`：

**构建期：完全静默。** `./gradlew build` exit code 0，mixin 注解处理器一个字都没说。

**运行期：FATAL。**

```
[FATAL] [MixinProcessor/]: Critical injection failure: @Inject annotation on bbs_snow$probe
        could not find any targets matching 'bbsSnowThisMethodDoesNotExist'
        in mchorse/bbs_mod/ui/framework/UIBaseMenu. No refMap loaded.
[FATAL] [mixin/]: Mixin apply for mod bbs_snow failed
        bbs_snow.client.mixins.json:UIBaseMenuProbeMixin -> mchorse.bbs_mod.ui.framework.UIBaseMenu
```

（顺带第三次确认 `No refMap loaded` —— 无 refmap 是正常工作状态，不是缺失。）

### 对 S2–S10 的硬性约束

76 个 Mixin 的注入点是否写对，**编译期无法发现**。因此：

- 每个子任务的验收都必须实际 `runClient`，"编译通过"不能作为任何形式的完成证据。
- `defaultRequire: 1` 是唯一的安全网，四份 config 都不得放宽。
- 注入点漂移会在启动时以 FATAL 暴露，不会静默变成空操作 —— 这一点是好消息，意味着不需要
  额外发明校验手段，但前提是每次都真的启动。

## 验收状态

| | 项 | 状态 |
|---|---|---|
| AC1 | `./gradlew build` exit code 0 | 通过（独立回显 `$?`，未用管道） |
| AC2 | jar 含 mods.toml 与四份 mixin config | 通过 |
| AC3 | runClient 启动，BBS 与 bbs_snow 同时加载 | 通过 |
| AC4 | 探针 Mixin 日志出现在游戏日志 | 通过 —— `[23:04:31] [bbs_snow] PROBE: UIBaseMenu mixin applied and executing`，打开仪表盘触发，无崩溃 |
| AC5 | `BBSAddonMod` 的 `@Subscribe` 收到事件 | 通过 |
| AC6 | 负向验证：注入点写错会报错 | 通过（运行期 FATAL；构建期静默，见上） |
| AC7 | `git diff --stat new/` 为空 | 通过 |
| AC8 | 无 `net.fabricmc` 引用 | 通过 |

八项全部通过。负向验证后已恢复探针为 `method = "<init>"` 并重新构建，exit code 0。

## 交接给 S2

- 探针 `UIBaseMenuProbeMixin` 与 `bbs_snow.client.mixins.json` 里对它的声明，在 S2 落第一个
  真实 mixin 时删除。
- 四份 mixin config 的 `package` 已定：`mchorse.bbs_snow.mixin` / `.mixin.client` /
  `.mixin.client.accessor` / `.mixin.client.iris`。源插件的 `client.api.mixins.json` 声明
  package 为 `.accessor` 但文件实际放在 `mixin/client/` 下，这个不一致在 S2 一并理顺。
- 功能注册全部走 `BBSSnowAddon` 的 `@Subscribe` 回调，不要写在 `@Mod` 构造函数里。
