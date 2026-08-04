# `new` 实机验收矩阵

## 判定规则

本文件只记录必须在真实 NeoForge 客户端、真实 dedicated server、真实 GPU/驱动、真实 FFmpeg 或真实远程控制链路中验证的行为。源码形态、反射测试、mock 进程和 dependency-light Java 测试只能作为前置门，不能代替这里的任何一项。

- 每项只能填写 `PASS`、`FAIL` 或 `BLOCKED`；缺少 macOS、Iris、多人客户端、外部 Addon 或真实 FFmpeg 环境时填 `BLOCKED`，不能填 `PASS`。
- 每次执行记录 Core 构建、NeoForge/Minecraft 版本、操作系统、JDK、GPU/驱动、Iris/光影版本、FFmpeg 版本、服务端类型及参与客户端数量。
- `FAIL` 至少保留 `latest.log`、截图/录屏、影片/粒子/模型 fixture 和精确复现步骤；多人问题还要记录每个玩家的 UUID、权限、维度及角色。
- 表中的“只执行一次”“只发送一次”“没有旧事件”等结论，必须通过可观察的世界状态、UI 状态或临时 debug 日志确认，不能只凭“看起来正常”。
- 不在生产世界执行破坏性、权限或路径测试；复制测试世界和 repository 后再测。

## 已知残余风险

- TARGETED Actor 仍然是加入 `ServerLevel` 的真实实体。当前最小隔离会让 logical-server private Actor 跳过 `LivingEntity.tick/baseTick/aiStep`，因此不再触发 `LivingBreatheEvent`、`LivingEquipmentChangeEvent`、装备附魔 tick/location effect、环境伤害、travel 或 AI world effect；服务端六个装备槽也保持为空。
- `EntityJoinLevelEvent` 与 `EntityTickEvent.Pre/Post` 在 Actor 自身 tick 之外由 NeoForge/`ServerLevel` 发布，无法通过 `ActorEntity` override 消除。全局 Addon 仍可观察 private Actor 的加入和每 tick 存在；要完全不可观察必须改成不加入共享实体表的 client-only projection。
- 因此 private Actor 隔离不能只凭自动 source/unit test 标记完全闭合。`AUTH-07` 与 `AUTH-11` 必须在真实 dedicated server 上确认非 audience 无 tracking/packet，且 LivingEntity hook 与 world effect 为零；外层 Join/Tick 被测试 Addon 观察到属于本项已知残余，而不是误报为完整隔离。

## 公共测试夹具

| 夹具 | 要求 |
| --- | --- |
| 玩家 | A：真实在线管理员/影片 issuer；B：普通玩家/target；C：普通旁观者。三者使用不同 UUID，并至少准备一次 B 断线后同 UUID 重连。 |
| 影片 | `VISUAL`：只含精确 `SwipeActionClip`；`EFFECTFUL`：含命令、物品/方块/实体或其他内置 effectful action；`UNKNOWN`：含 custom/subclass、nested 或 disabled action；`TARGETED`：带真实 Actor 的定向影片。 |
| 交互场景 | 箱子、门、音符盒+头颅、可成功/失败放置的方块、可用副手物品、村民/可交互生物、盔甲架命中点、矿车或船等非 `LivingEntity`、主目标和至少两个横扫目标。 |
| 渲染场景 | 普通 Form、Actor、远离原点的 ModelBlock，各自挂 Particle 与 Trail；另备透明材质、Chroma、遮挡物和普通实体以观察 depth/blend/GL 泄漏。 |
| IK/Physics 模型 | 同一资源的两个独立实例；两骨预弯链、完全直链、短链；窄 swing/twist 限制、pole、显式/缺省 softness、physics、世界碰撞；同时准备 cubic immediate、cubic VAO 和 BOBJ。 |
| repository | 远程库 `R` 与本地库 `L` 均创建同名影片，但内容明显不同；`R` 中影片产生未保存改动。 |
| 导出 | 真实 FFmpeg 安装根目录，路径中含空格；短影片包含相机运动、粒子、透明物、Actor、声音和首尾辨识帧；另备无效 FFmpeg 路径。 |

状态栏初始均为 `未测`。

## 1. NeoForge、Mixin 与 Addon 启动

| ID | 环境/前置 | 步骤 | 预期结果 | 状态/证据 |
| --- | --- | --- | --- | --- |
| BOOT-01 | 干净的 NeoForge 1.21.1 客户端实例，使用待发布 JAR | 1. 启动到主菜单。<br>2. 进入单机世界并打开 BBS 主界面、影片编辑器和模型编辑器。<br>3. 搜索日志中的 Mixin apply、target、injection、client init 和 registry 错误。 | 客户端完成启动并进入世界；没有 `InvalidInjectionException`、critical injection failure、错误 target 或缺失 BBS 注册；三个界面均能打开。 | 未测 |
| BOOT-02 | NeoForge 1.21.1 dedicated server；Core、一个旧 v1 Addon 和一个 API 2.0 Addon | 1. 在无图形环境启动 dedicated server。<br>2. 等待 common setup 完成。<br>3. 让客户端加入并使用两个 Addon 各自注册的内容。 | 服务端不解析/初始化客户端类且不崩溃；v1 与 v2 Addon 都能完成各自合法注册；公开 API 仍报告精确 `2.0`。 | 未测 |
| BOOT-03 | 已启用动作录制的客户端/服务端 | 1. 开始录制。<br>2. 左键攻击普通生物和非 `LivingEntity`。<br>3. 主/副手右键生物，并对盔甲架不同部位执行 `interactAt`。<br>4. 停止录制并回放。 | 当前 NeoForge `ServerGamePacketListenerImpl` 路径上的攻击与实体交互 Mixin 均实际命中；录制项各出现一次，回放命中原目标/hand/命中点；日志无运行时注入失败。 | 未测 |
| BOOT-04 | 可控制注册顺序的 v1/v2 测试 Addon | 1. 让 v1 与 v2 来源用同一 id 注册不同可见内容。<br>2. 再让后注册实现抛 `Exception`/`LinkageError`。<br>3. 尝试在 common/client 窗口关闭后注册 renderer。 | 第一实现始终胜出且只运行一次；后注册失败不篡改赢家状态；异常被隔离并产生一次可诊断记录；late client registration 被明确拒绝，Core/已注册 renderer 继续工作。 | 未测 |
| BOOT-05 | A/B 两客户端，相同服务端 | 1. 生成 Actor 与枪弹丸。<br>2. B 分别移动到约 255/257 blocks 和 383/385 blocks，来回跨越边界。 | Actor 的实际跟踪边界约为 256 blocks，枪约为 384 blocks；不会因把 block 数误当 chunk 数而在异常远距离仍同步，也不会在合法距离提前消失。 | 未测 |

## 2. 多人 authority、重连与 TARGETED 可见性

| ID | 环境/前置 | 步骤 | 预期结果 | 状态/证据 |
| --- | --- | --- | --- | --- |
| AUTH-01 | A 为管理员，B/C 为普通玩家；A 正在播放/编辑影片并拥有 ModelBlock | 1. B 尝试暂停/停止 A 的影片、强制 A 切面板、共享受限 Form、写 A 的 ModelBlock。<br>2. 分别在同维远距离、跨维度和近距离重试。 | 所有未授权 mutation 均被服务端拒绝；A 的播放、界面和世界数据不变；B 不会因客户端返回值误开编辑器；距离/维度变化不能绕过权限。 | 未测 |
| AUTH-02 | A 管理员启动 `EFFECTFUL`，B 为普通 target | 1. 让命令读取 `@s`、当前位置并执行一项仅管理员可做的效果。<br>2. 观察 A、B、Actor/fake execution entity 和世界状态。 | 权限来自真实 issuer A，不从 B 或固定高权限 FakePlayer借权；`@s` 与执行位置仍锚定 replay Actor/fake execution context；B 不因此获得管理员能力。 | 未测 |
| AUTH-03 | A 启动一个前后各有 effectful action 的长影片 | 1. 第一项执行后撤销 A 的管理员权限或让 A 下线。<br>2. 等待后一项触发。<br>3. 用恢复权限后的新 session 再测。 | 每个 effectful mutation 时动态复验真实在线 issuer；撤权/下线后的 effectful action fail closed，不借 target 权限；恢复权限只影响新建立且身份匹配的 session。 | 未测 |
| AUTH-04 | console/command block 或没有真实玩家 issuer 的 legacy 调用入口 | 1. 启动 `VISUAL`。<br>2. 再启动 `EFFECTFUL`、first-person 影片和 `UNKNOWN`。 | `VISUAL` 仍可播放；effectful、first-person 和无法证明纯视觉的影片 fail closed；不会给 console/command block 人造一个可动态复验的玩家权限。 | 未测 |
| AUTH-05 | 普通玩家 B；`UNKNOWN` 分别含 custom subclass、nested effectful child、disabled effectful child | 1. 由 B 启动三种影片。<br>2. 启用/展开 child 后再次触发。 | 只有精确 `SwipeActionClip` 被当作纯视觉；custom/subclass、nested、disabled/后续启用动作均默认要求真实管理员 issuer，不能通过包装或禁用状态绕过预检。 | 未测 |
| AUTH-06 | B 已建立可写 mutation/影片 session | 1. 在一次延迟操作或分片传输尚未完成时断开 B。<br>2. 用同 UUID 立即重连为 B2。<br>3. 让旧回调/旧影片结束，再由 B2 发新操作。 | 旧 session 同时绑定 UUID 和捕获的 `ServerPlayer` 实例；旧回调不能接管/修改 B2，也不能向 B2 发送旧 c5；B2 的新 session 独立工作。 | 未测 |
| AUTH-07 | A 启动 `TARGETED` 给 B，C 与 B 同维同区域；再让 C 跨维/重进范围 | 1. 播放、暂停、恢复和强制停止。<br>2. B/C 分别靠近 Actor。<br>3. B 断线重连后重新建立一轮。 | Actor 与定向播放状态只对该 TARGETED session 明确包含的 B 可见；C 不因 vanilla tracking、距离、重进范围或维度切换看到/接管 Actor；断线后旧 audience 被清理。 | 未测 |
| AUTH-08 | NORMAL、TARGETED、FILM_EDITOR、RECORDING 四种 session；可观察 c5/客户端退出 | 1. NORMAL/TARGETED 分别自然结束与强制停止。<br>2. FILM_EDITOR 分别自然结束与强制停止。<br>3. RECORDING 停止。<br>4. 对已结束/不归该玩家的 runtime 再 stop。 | NORMAL/TARGETED 两种终止都产生幂等的一次 c5；FILM_EDITOR 只在 forced stop 发一次；RECORDING 不发 c5；找不到精确 runtime/owner 时返回失败且不发 c5，客户端不被错误踢出其他 session。 | 未测 |
| AUTH-09 | A/B；备份 B 的属性与完整背包，准备合法包和被截断/非法包 | 1. B 发送 s14，确认拒绝。<br>2. A 对允许目标发送合法包。<br>3. 再发送解码中途失败或含非法 slot 的包。 | 非管理员在解码前即被拒绝；主线程 apply 时再次校验 issuer/target 身份；合法数据完整应用；非法数据先完整 stage 后整体失败/回滚，背包和属性没有半更新。 | 未测 |
| AUTH-10 | A/B 两连接；可发 PacketCrusher 分片 | 1. 两连接、两个通道用相同 transfer id 交错发送不同内容。<br>2. B 中途断线并用同 UUID 重连后复用该 id。<br>3. 让旧传输超时。 | 数据按精确 connection + channel + transfer id 隔离；不会拼成混合 payload；断线/超时释放旧状态；重连的新传输不能续接旧连接分片。 | 未测 |
| AUTH-11 | A 向 B 播放 TARGETED；C 同维在旁；测试 Addon 统计 EntityJoin/Tick、LivingBreathe、LivingEquipmentChange，Actor fixture 带 Frost Walker/Soul Speed 等位置附魔 | 1. 由普通玩家、管理员与 console 分别启动纯视觉 TARGETED。<br>2. 让 Actor 跨水、移动并挥主/副手，持续至少 40 ticks。<br>3. 致死 private Actor 并等待清理。<br>4. 比较 B/C 收包、事件计数、方块/声音/粒子与 Actor 移除。 | B 保持完整 Form/装备/挥手/受伤/死亡视觉，C 无 spawn/tracking/动画包；服务端 private Actor 六槽为空，LivingBreathe/EquipmentChange 与附魔 world effect 为 0，死亡 20 ticks 后移除。测试 Addon 仍可能看到外层 EntityJoinLevel/EntityTick Pre/Post，这是已知残余；不得据此把“完全不可观察”标为 PASS。 | 未测 |

## 3. 枪、Shift、副手、方块、攻击与实体动作

| ID | 环境/前置 | 步骤 | 预期结果 | 状态/证据 |
| --- | --- | --- | --- | --- |
| ACT-01 | 主手枪，副手为可使用物品；再交换双手 | 1. 主手开火。<br>2. 交换后从副手开火。<br>3. 在客户端预测和服务端结果中计数弹丸/物品消费。 | 成功开火只消费被选中的 hand；被消费后不继续分派另一 hand；每次仅生成一枚弹丸；客户端与服务端选择一致。 | 未测 |
| ACT-02 | 普通玩家 B；一把纯数值 GunData 和一把 command-bearing GunData | 1. B 编辑并使用合法纯数值枪。<br>2. B 尝试写入/使用带命令枪。<br>3. A 重复第 2 步。 | 普通玩家仍可编辑合法数值枪；command-bearing 数据在编辑边界、`GunItem` 和 `GunProjectile` 最终执行边界均要求管理员；A 的合法命令枪按真实 issuer 权限执行。 | 未测 |
| ACT-03 | 可快速生成弹丸的测试枪 | 1. 单个 owner 尝试维持超过 256 个活跃弹丸。<br>2. 多玩家尝试令全服超过 4096。<br>3. 等弹丸销毁后再开火。 | 单 owner 与全服预算分别受限；超额请求安全拒绝且不拖垮服务器；弹丸销毁后预算释放；入世界前已设置 owner，不自撞，伤害/击杀来源归 owner。 | 未测 |
| ACT-04 | 箱子/门；音符盒+头颅；动作录制开启 | 1. 普通右键箱子/门并回放。<br>2. Shift+右键，分别让默认方块分支被跳过和让物品分支消费。<br>3. 对音符盒使用产生 `SKIP_DEFAULT_BLOCK_INTERACTION` 的头颅并回放。 | 只记录原版最终实际执行的一个 full-dispatch 动作；普通分支等价打开/切换；Shift 与 SKIP 场景不先误触默认方块逻辑，不产生 `InteractBlock`/`UseBlockItem` 双重回放。 | 未测 |
| ACT-05 | 可成功和失败的方块放置；一个由 NeoForge hook 在 `ItemStack.use` 前消费的空中物品 | 1. 分别录制成功/PASS/失败放置。<br>2. 录制 hook 消费的 air-use。<br>3. 发空手 air-use 包或构造空手 clip 回放。 | 成功放置只录一次；失败/PASS 不伪造成功 action；air-use 走相同 game-mode hook 且只录一次；空手在 packet/game-mode 前后均保持 no-op。 | 未测 |
| ACT-06 | 主攻击目标、两个横扫受害者、一个非 `LivingEntity` | 1. 录制一次横扫攻击。<br>2. 回放并分别统计三个实体受击与 attack-target hook。<br>3. 单独攻击矿车/船等非 `LivingEntity` 并回放。 | 每个记录的伤害只命中原目标一次；多目标伤害不集中到最近实体；只有 primary path 触发 attack-target hook；非 `LivingEntity` 保留原 entity identity，不被静默丢弃或改找附近目标。 | 未测 |
| ACT-07 | 主/副手可与生物交互的物品；盔甲架；secondary-use 场景 | 1. 主/副手使用 name tag、spawn egg 或同类分支。<br>2. 点击盔甲架不同部位。<br>3. 按 Shift 交互并在随后进行普通交互。<br>4. 全部录制回放。 | 保存 pre-use stack、hand、目标和 entity-relative hit vector；依次保留 `interact`/`interactAt`/`mobInteract` 与 NeoForge hook 结果；secondary-use 只在该 action 内有效，随后恢复。 | 未测 |
| ACT-08 | 含目标 action 的影片；A/B/C/D 四个 Replay；两个并行 `ActionPlayer` 使用相同 replay id/坐标 | 1. 删除目标实体后回放。<br>2. 重建 Film Actor。<br>3. 重排/删除目标 Replay。<br>4. 并行播放。 | 删除目标时 no-op，不 raycast/重定向邻近实体；Actor 可按 replay identity + entity type 重绑；重排跟随原逻辑对象，删除留下 tombstone/no-op；并行播放只解析自身 `ActionPlayer` 拥有的 Actor。 | 未测 |
| ACT-09 | 含 absolute 与 actor-relative item drop 的影片；另备 legacy `place_block`/`use_block_item` | 1. 整体平移影片后回放 item drop。<br>2. 加载并回放 legacy 动作。 | absolute 掉落坐标随影片 shift；relative offset 不被重复平移，只随已平移 Actor 一次；legacy 动作仍能加载回放，但实时录制不会再生成 nested legacy 类型。 | 未测 |
| ACT-10 | 两个并行 Replay，使用会写 cooldown、背包、盔甲、selected slot、use/sleep 状态的动作 | 1. 交错执行动作并让其中一个异常/提前结束。<br>2. 随后再次执行相同物品动作。 | 每个 `SuperFakePlayer` 的 cooldown、背包、盔甲、selected slot、use/sleep 状态按 replay 隔离并在清理后恢复；一个 replay 的状态/异常不抑制或污染另一个。 | 未测 |
| ACT-11 | 两个 `ActionPlayer`，含持续方块破坏 action，可选择同坐标或不同坐标 | 1. 交错开始、暂停、恢复和取消破坏。<br>2. 其中一个销毁/重建 Replay 后继续另一个。 | break progress 同时绑定 `ActionPlayer` 和 Replay identity；进度、粒子、完成/取消不会跨影片借用或互相清掉。 | 未测 |
| ACT-12 | A/B 点击同一 ModelBlock | 1. B 未授权点击。<br>2. A 授权点击并保存。<br>3. A 离开距离或换维后再发保存。 | B 不打开 UI；客户端/服务端 sided result 不造成重复动作；A 只打开一次；最终 mutation 仍复验会话、距离、维度和权限，失效后不写世界。 | 未测 |
| ACT-13 | 影片编辑器 Add Clip 菜单及含历史实体动作的影片 | 1. 打开 Add Clip 菜单。<br>2. 加载含已录制/序列化实体交互的旧影片。 | `interact_entity` 不作为用户可手工添加的新 clip 暴露；历史动作仍可加载、显示面板并回放；中英文 UI 不显示原始缺失语言键。 | 未测 |
| ACT-14 | 开启动作录制；服务器开启 secure profile；测试模组可取消指定 `CommandEvent` | 1. 分别发送成功命令、未知命令、无权限命令、参数运行期失败命令。<br>2. 发送被 `CommandEvent` 取消的合法命令。<br>3. 分别触发 unsigned signable 拒绝和 signed argument decode 失败。<br>4. 成功执行一次会产生多个 leaf result 的 fork/redirect 命令。 | 只有 Brigadier 最终报告 success 的顶层玩家命令生成 `CommandActionClip`；解析、权限、运行期、签名和事件取消失败均不生成；fork/redirect 无论成功 leaf 数量都只保存原始命令一次；录像异常不改变命令本身结果。 | 未测 |

## 4. 粒子、Actor、ModelBlock、Iris 与 GL 状态

| ID | 环境/前置 | 步骤 | 预期结果 | 状态/证据 |
| --- | --- | --- | --- | --- |
| RENDER-01 | 关闭 Iris/光影；普通 Form、Actor、远坐标 ModelBlock 都挂 Particle/Trail | 1. 主体保持静止。<br>2. 环绕、俯仰、平移和快速切换第一/第三人称相机。<br>3. 播放/暂停影片。 | 三种宿主的 mesh、Particle 和 Trail 均固定在各自语义位置；相机运动不成为 emitter/Trail 位移；普通世界 Form 也不漂移或被重复应用 view matrix。 | 未测 |
| RENDER-02 | 启用受支持的 Iris 光影；使用与 RENDER-01 相同场景 | 1. 环绕场景并跨 chunk layer。<br>2. 同屏显示普通 Form、影片 replay、Actor、ModelBlock、粒子和 Trail。<br>3. 开关光影后重测。 | 所有对象保持 world-fixed；每帧 BBS world composite 只出现一次，没有重影、双亮、闪烁或相机相关偏移；Iris callback 的矩阵不被全局旧值替代。 | 未测 |
| RENDER-03 | actor-mode 与普通 stub replay | 1. 播放后暂停。<br>2. 禁用 replay。<br>3. 移动相机并切换 actor mode。 | 真实 Actor 的世界位置由服务端/vanilla interpolation 所有；客户端不持续覆盖位置；disabled replay 立即停止 update/render；暂停/禁用后 Actor 不随相机漂移。 | 未测 |
| RENDER-04 | 静止 emitter 放在远离原点位置；collision event 可计数 | 1. 首次显示后静置。<br>2. 让粒子首次跨碰撞面。<br>3. 保持接触、离开，再独立跨入两次。 | 首次定位先建立真实 baseline，不产生从原点到远坐标的 travel-distance 事件风暴；同一次接触只触发一次；离开后重新武装，每次新的 crossing 恰好触发一次。 | 未测 |
| RENDER-05 | 含 timeline key `"0"`、合法节点、未知 sibling/unsupported raw payload 的粒子 JSON | 1. 在编辑器只修改一个已知合法字段并保存。<br>2. 重载后再次保存。<br>3. 在文本框聚焦时按 Space、Ctrl+Z、Ctrl+Y。 | key `"0"` 与未编辑 unknown/raw sibling 逐项保留，触发列表形状不被无关编辑重写；聚焦文本框只接收输入/撤销重做，不触发全局播放或粒子快捷键。 | 未测 |
| RENDER-06 | `docs/test1/黑洞1.json` 与 `docs/test2/护盾5-0-stay.json`，覆盖 look-at/rotate 模式 | 1. 在粒子编辑器 GUI 查看。<br>2. 作为世界 Form、Actor 和 ModelBlock 查看。<br>3. 切换前/第三人称。 | editor GUI 与世界路径的 camera-facing 方向语义一致；GUI 非 look-at 模式没有双翻转；两个样例保持可见且不随相机错误旋转/反向。 | 未测 |
| RENDER-07 | Chroma、透明粒子、Trail、遮挡方块、水面/透明实体和普通 UI 同屏 | 1. 让 BBS 渲染发生在普通透明/实体/UI 渲染前后。<br>2. 切屏、抛出一次资源缺失或 renderer 早退。<br>3. 继续观察后续帧。 | framebuffer、model-view、depth test/write、blend、texture、scissor 等状态在所有分支精确恢复；后续世界/UI 不穿墙、不全黑、不丢透明度、不被错误裁剪。 | 未测 |

## 5. Film timeline、Anchor Gizmo 与滚动

| ID | 环境/前置 | 步骤 | 预期结果 | 状态/证据 |
| --- | --- | --- | --- | --- |
| FILM-01 | 影片编辑器，预览区与 camera/action/replay timeline 都可见 | 1. 在 timeline 区按 Ctrl+滚轮。<br>2. 在预览区和其他面板重复。<br>3. 不按 Ctrl 滚轮。 | Ctrl+滚轮只在 timeline 命中区域移动游标；预览区保留 Orbit zoom；其他面板不偷走事件；普通滚轮仍执行各区域原有滚动。 | 未测 |
| FILM-02 | Replay 含 Form anchor 轨道和非默认 offset | 1. 选择 anchor 轨道。<br>2. 依次拾取移动、旋转、缩放、trackball/view/screen/scale-all 等可见 Gizmo 句柄。<br>3. 再点击普通骨骼/Form。 | anchor 轨道可见且选择的是整 Form Gizmo；所有保留 raw ID 的句柄都可用；Gizmo 与普通 Form/骨骼 ID 不冲突，点击普通对象不会误启动句柄。 | 未测 |
| FILM-03 | Film A/B/C，各自把 camera/action/replay timeline 滚到明显不同位置 | 1. A→B→C→A 来回切换。<br>2. 保存、关闭、重新打开每部 Film。 | 每部 Film 分别恢复自己的三类滚动位置，不继承上一部；repository/remote hook 不把一个 Film 的 viewport 状态写给另一个。 | 未测 |
| FILM-04 | 多层长 Film；clip 属性面板可滚动 | 1. 首次打开 Film。<br>2. 调整窗口大小、触发 fill/布局重算。<br>3. 切换长短 clip 属性。 | 首次打开在实际层范围计算完成后居中，不以空/旧范围定位；resize/fill 后 clip 属性滚动恢复到合法位置，不跳顶、不越界。 | 未测 |
| FILM-05 | A/B/C/D Replay 互相用 anchor 和 `EntityClip` selector 引用；有/无 sheet 与 selection | 1. 分别用 Delete 快捷键和右键菜单删除任意 Replay。<br>2. 重排 Replay。<br>3. 在空白区、轨道外、无 sheet/selection 时按 `[`, `]` 及 Ctrl/Shift 组合。 | 两种删除入口走同一 identity remap；剩余引用指向原逻辑对象或明确失效，不按旧索引串人；关键帧边界快捷键不抛异常、不误改其他组件。 | 未测 |
| FILM-06 | 含相机、动作、Replay、Web Remote hook 的 Film | 1. 逐一切换六种既有相机模式。<br>2. 打开右键菜单并执行保存/退出/重进。<br>3. 远程观察/控制一次后切回本地。 | 六种模式、菜单、保存和退出无回归；timeline/Gizmo 修复不截断既有事件；Web Remote hook 仍收到一致状态且不会改变本地滚动/selection owner。 | 未测 |
| FILM-07 | Replay A anchor 到 Replay B 的移动/旋转 procedural bone，含 offset、blend、nested anchor | 1. 播放、暂停、scrub、换 replay 顺序并环绕相机。<br>2. 让 B 的骨骼由动画+IK+physics 驱动。<br>3. 观察 child、axes、shadow、Tracker、Orbit pivot 和 Gizmo。 | 所有消费者跟随同一最终 rendered procedural bone，首帧也正确；重排和不同 loop period 不改变同一帧结果；不会有一帧使用未 anchor 的实体坐标。 | 未测 |
| FILM-08 | 两个结构相同但 identity 不同的 Replay | 1. 分别拖拽、录制、选择、共享。<br>2. 删除/重建其中一个。 | UI/controller 始终操作实际被选中的 Replay 对象，不因 value equality 命中第一个；删除/重建不会把选中或 action target 转给另一个相同 Replay。 | 未测 |

## 6. IK / Physics：world、preview、first-person 与 item contexts

更细的数值边界仍需同时执行子任务的 [IK 手测矩阵](../07-17-ik-physics-selection/manual-test.md)；下表是父任务发布门，不能用子任务自动数值测试替代。

| ID | 环境/前置 | 步骤 | 预期结果 | 状态/证据 |
| --- | --- | --- | --- | --- |
| IK-01 | 世界中放置两个使用同一模型 id 的 physics 实体 | 1. 两实体静止时环绕/平移玩家相机。<br>2. 玩家快速移动、急停。<br>3. 分别移动一个宿主。 | 相机不会产生 gravity/inertia；两个 renderer/owner history 互不 kick/reset；每个模型只响应自身宿主 semantic transform。 | 未测 |
| IK-02 | 远离原点且启用碰撞的 live ModelBlock，同时打开该块的模型编辑器 | 1. 在原点和 ModelBlock 周围布置不同碰撞物。<br>2. orbit/pan preview。<br>3. 移动/旋转 live block，关闭并重开编辑器。 | preview 使用独立稳定 owner、identity-local space 且不采样 Level collision；世界实例独立使用绝对 transform/碰撞；开关编辑器不重置或踢动世界 history。 | 未测 |
| IK-03 | 带 transform 的 nested body part，内部有 IK/physics | 1. 在世界实体、Film preview、ModelBlock 中分别移动 parent 并转相机。<br>2. 同屏放两个共享模型资源的 nested 实例。 | child solver 与 mesh 同步继承 body-part semantic transform；不继承 camera view；每个 placement history 独立。 | 未测 |
| IK-04 | 同一 physics-capable form 同时用于 first-person 主/副手 | 1. 双手可见时快速 look/turn。<br>2. 移动、停止、切手、隐藏/恢复一只手。<br>3. 动态启用/禁用 first-person film 两个 runtime。 | camera rotation 不产生模拟冲击；主/副手 owner/history 独立且按 player tick；manager-level lease 防止两个 runtime 同时缓存/恢复同一背包；结束后 inventory、armor、selected slot 精确恢复。 | 未测 |
| IK-05 | 同一模型物品可在所有 `ItemDisplayContext` 显示 | 1. 依次测试 first-person L/R、third-person L/R、GUI、ground、fixed/item-frame、head、NONE。<br>2. 快速切 GUI/world、丢弃/拾取、换手、开关背包。<br>3. 在原点/玩家附近放碰撞块并配置 absolute film targets。 | 每个 display context 有稳定独立 history；不继承其他 context 的 velocity/pole/pin；全部 item-local，不采样 Level collision或 absolute world target，ground/fixed/head/NONE 也不误判为世界实体。 | 未测 |
| IK-06 | cubic 与 BOBJ；constraints only、IK only、physics only 及三种组合；窄 limits | 1. 逐项播放。<br>2. 用 tip rotation 和大 physics twist 越过限制。<br>3. 测一骨/短链和 descendants。 | 最终实际写给 renderer 的 full quaternion 均在限制内；child parent frame 使用钳制后的值；IK/physics 结果不被后置 generic constraint 清除；debug line 与 mesh 不分叉。 | 未测 |
| IK-07 | 预弯动画两骨链、直链、缺省/显式 softness、pole | 1. 在 controller bind 状态启用，切 pole false→true、disable/re-enable、weight 0→1。<br>2. 目标拉过全长并返回。<br>3. 探测预测弯曲 0°/2°/3°/4°，从两侧穿越，再移 pole 约 10° 并设 `Pole Angle=5°`。 | 零 controller delta 保持当帧 FK 且启用不跳；缺省 softness=0 可精确全伸，显式 softness 保留 soft reach；0°/2°/3° 共线，4° 恢复弯曲，20° 前 bend side 稳定；pole 使用相对角增量且重绑时不继承旧 owner。 | 未测 |
| IK-08 | 多个不同 loop period Replay，互相提供 IK/pole/physics target；另有 relative replay | 1. 重排 replay 与 target channel。<br>2. play/pause/scrub 并观察同一 frame。<br>3. 尝试把 relative replay 用作 absolute anchor、target、Tracker、Orbit source。 | 两阶段求值令重排前后 target map、local tick 和最终 pose 一致，target sampling 不推进 world physics；relative replay 保持 local owner、无碰撞/absolute override，所有跨 policy world consumer fail closed。 | 未测 |
| IK-09 | IK/physics/constraint 面板；cubic immediate/VAO/BOBJ；含绑定与未绑定骨、debug marker | 1. 在每个可见面板普通/Ctrl 点击绑定和未绑定骨。<br>2. 切 nested/root Form 后点击。<br>3. 开关 target/pole/attach marker并点击全部 Gizmo handle。 | 可见面板优先选中并滚动到真实 bone；隐藏 pose/body-part editor 不消费 stale selection；Form owner 不串；三种几何路径普通骨 ID 一致，marker 只追加，Gizmo 全 raw-ID 范围不吞骨骼。 | 未测 |
| IK-10 | 多链高负载 fixture；固定 iterations、碰撞、风、substep 和实体数 | 1. 预热后记录 frame time、1% low 和 GC。<br>2. 比较 IK only、physics only、三者组合。<br>3. 快速切两个共享资源 Form 并关闭其一。 | 预热后无持续 topology/scratch 分配尖峰；性能改善不靠降低质量；关闭一个 preview 不清另一个 owner 的 workspace/bend history。 | 未测 |

## 7. 真实 FFmpeg、GPU/PBO、macOS 与断线

| ID | 环境/前置 | 步骤 | 预期结果 | 状态/证据 |
| --- | --- | --- | --- | --- |
| EXPORT-01 | Windows 或 Linux；真实 FFmpeg 位于含空格的 `<root>/bin/ffmpeg(.exe)` | 1. 设置 encoder path 为安装根目录而非可执行文件。<br>2. 导出含音频的短片。<br>3. 用独立播放器/ffprobe 检查。 | 正确解析平台路径而非用 path separator 拼文件名；生成可播放、时长/尺寸/FPS/音频正确的文件；只有 exit=0 才播放完成音和打开输出目录。 | 未测 |
| EXPORT-02 | 支持 PBO 的真实 GPU；F4/F6 world export；自定义分辨率 | 1. 录制时持续移动和旋转相机。<br>2. 分别在无 BBS screen 与 Film/Morph UI 打开时观察窗口。<br>3. 检查导出首尾帧和 recording overlay。 | PBO 输出无黑帧、撕裂、通道错乱、首帧/尾帧丢失或一帧延迟污染；窗口保持实时；无 BBS screen 时 private framebuffer 正确呈现到窗口，有 BBS UI 时不全屏覆盖；operator overlay 不进入编码视频。 | 未测 |
| EXPORT-03 | 正常导出 session | 1. 完整导出两次。<br>2. 第一次自然完成后立即预约/启动第二次。<br>3. 尝试在同一 session 中重入 start/stop。 | 每次成功只有一个终态和一次成功副作用；session reservation/reentry fence 不产生双 FFmpeg、双提示或复用旧 PBO/generation。 | 未测 |
| EXPORT-04 | 无效 encoder path；另可在导出中杀掉真实 FFmpeg | 1. 测启动即失败。<br>2. 首帧后/中途终止 FFmpeg 或关闭 pipe。<br>3. 测非零 exit 与手动取消。 | 所有失败进入 `FAILED`，取消进入 `CANCELLED`，都不显示完成、不播放完成音、不打开目录；进程、recording、PBO、临时音频和 UI 状态只 teardown 一次。 | 未测 |
| EXPORT-05 | 带临时音频的 panel export | 1. warmup 阶段断线。<br>2. active recording 阶段断线。<br>3. world unload 后重连并再次导出。 | 断线在 dashboard/panel owner 清除前走 session cancel；两阶段都不残留 `bbs-export-*.wav`、FFmpeg、PBO、recording flag 或旧 UI；重连后新导出不被旧 generation 回调覆盖。 | 未测 |
| EXPORT-06 | 真实 GPU，连续高分辨率/高 FPS 导出 | 1. 连续开始、取消、失败、成功至少 10 轮。<br>2. 期间 resize、切 screen、切世界。<br>3. 观察显存、句柄、日志与成片序列。 | PBO/缓冲/纹理有界且终态释放；没有持续显存增长、stale frame、旧 fence 发布或 GL 状态泄漏；最新 session 的帧不会被旧 encoder 回调覆盖。 | 未测 |
| EXPORT-07 | 真实 macOS 客户端与真实 FFmpeg | 1. 使用与 EXPORT-01 相同 fixture 导出。<br>2. 快速运动相机并检查全片。<br>3. 取消后再成功导出。 | macOS 使用 direct-buffer readback 而非已知会黑屏的 PBO 路径；视频不是全黑、没有翻转/错色/丢尾帧；取消与重启清理正确。 | 未测 |

## 8. 远程 repository lease、输入释放与 HUD

| ID | 环境/前置 | 步骤 | 预期结果 | 状态/证据 |
| --- | --- | --- | --- | --- |
| REMOTE-01 | 远程握手可在 `R`/`L` 间选择；同名 Film 内容不同 | 1. 在加载 Film 前切换 repository 两次。<br>2. 从 `R` 成功加载 Film 并产生未保存修改。<br>3. 再发送选择 `L` 的握手。 | Film 加载前可随握手重选；加载成功后 session 冻结具体 `R` repository 实例，后续握手不能把已加载 Film 的保存目标改成 `L`。 | 未测 |
| REMOTE-02 | 承接 REMOTE-01 | 1. 修改尚未保存时断开 remote/network。<br>2. 等断线清理完成。<br>3. 检查 `R` 与 `L` 的同名 Film 后重连。 | 保存/恢复发生在 handshake reset 前，并使用冻结的 `R` 实例；`R` 保留修改，`L` 完全不变；不会因断线找不到 repository 而丢改动。 | 未测 |
| REMOTE-03 | 一个 remote controller lease；本地鼠标键盘同时可操作 | 1. remote 按下并拖动时，本地在另一坐标按键/点击并改变 modifier。<br>2. 交换先后顺序。<br>3. remote lease 释放后再本地操作。 | 一个手势的坐标、按钮和 modifier 全部来自单一 owner；remote lease 活动时本地不能拼成混合 gesture；释放后本地通过明确仲裁恢复，而非继承 remote held state。 | 未测 |
| REMOTE-04 | Gizmo/Trackpad/UIDraggable；可同时发 left/right/middle | 1. remote 左键按下形成 pending Gizmo pick。<br>2. 在 pick 完成前按右键并交错移动。<br>3. 先释放右键，再释放左键；交换顺序重测。 | pending pick 保留最初左键 owner，不被右键覆盖；每个 drag/trackpad 只由匹配按钮完成或取消；不同按钮 release 不结束另一按钮手势，每次 release 被正确消费一次。 | 未测 |
| REMOTE-05 | owner A 正持 lease；owner B 有 pending generation/batches | 1. 保持 A 按住。<br>2. 对 B 执行 clear/disconnect。<br>3. 再释放 A 并让 B 重连。 | `clear(B)` 即使 A 当前持 lease也取消 B 自己的 pending generation/batches；不清 A 的合法 held；B 重连不会补发旧 batch。 | 未测 |
| REMOTE-06 | remote batch 含会打开/关闭/替换 screen 的首个事件以及后续事件 | 1. 一次提交多事件 batch。<br>2. 首事件切换 screen/session。<br>3. 用首事件关闭 screen 和替换为同类新 screen 各测一次。 | 每个离散事件后重验 current screen/session generation；旧 screen 不接收剩余 move/click/release；后续事件也不能仅因新 screen 类型相同而投递。 | 未测 |
| REMOTE-07 | 旧 screen 持多个 remote keys/buttons；release callback 会打开新 screen 或安装新 lease | 1. 触发 synthetic release。<br>2. 在第一个 release callback 中切屏/重入并让新 owner 按下。<br>3. 观察旧 owner 剩余 releases。 | 每个 synthetic release 后重验 target/screen/session；旧 owner 的剩余 release 不进入新 screen/新 lease；新 owner 刚建立的 held state不被旧 cleanup 清掉。 | 未测 |
| REMOTE-08 | remote 同时 held 多个键/按钮并在拖动 | 1. 直接断开网络/关闭 remote viewer。<br>2. 观察 release 期间 held snapshot。<br>3. 重连后做普通拖动与快捷键。 | remote held snapshot 在所有 synthetic release 完成前保留，以便精确释放；每个 owned key/button 恰好释放一次；之后再清 snapshot/lease；无卡住按钮、modifier、drag、Gizmo 或 Trackpad。 | 未测 |
| REMOTE-09 | 本地与 remote 分别在旧/新 screen 使用不同按钮 | 1. local 正拖动时 remote 请求控制。<br>2. remote 正拖动时切 screen。<br>3. synthetic release 与真实 release 交错。 | cursor/gesture ownership 与 screen generation 一致；repository pin 和 session owner 在输入可到达前已经确定；旧 screen 释放不污染新 screen，本地/remote 都不会永久失去 release。 | 未测 |
| REMOTE-10 | 两种初始 HUD：`hideGui=false` 与用户先按 F1 令 `hideGui=true` | 1. 各自打开/关闭 BBS screen。<br>2. remote 断线、导出取消和异常退出各执行一次。 | UI 退出精确恢复进入前的原 `hideGui`；初始显示则显示，初始隐藏仍隐藏；任何终态都不固定写成 `false`。 | 未测 |
| REMOTE-11 | 远程控制过程中启动/停止导出 | 1. remote 持键/拖动时开始导出。<br>2. 尝试发送影响 screen/camera 的本地和 remote 输入。<br>3. 取消/失败/成功结束后再操作。 | export gate 不允许旧 held 手势穿越导出边界或操纵错误 screen；进入/退出时按 owner 成对释放；导出终态后输入恢复，无永久锁定或旧 batch 回放。 | 未测 |
| REMOTE-12 | UI/control socket 与 media socket 均连接；remote 正持 lease | 1. 仅切换 smooth/high/original/custom quality。<br>2. 关闭/重开 media viewer。<br>3. 切换包含 native form preview 的 screen。 | 只重连 media demand；UI/control session、controller identity 和 held ownership 保持；零 viewer 停止 capture；screen generation 变化后没有旧 atlas/旧 screen frame覆盖新 UI。 | 未测 |
| REMOTE-13 | Film clip timeline、Replay/通用排序列表、关键帧、模型视口、粒子曲线/渐变/颜色、音频编辑器、Textarea 和右键菜单；remote 可分别发送 left/right/middle | 1. 用左键开始拖动、选择或排序，在不打开新 overlay 的位置交错按下并先释放右键/中键，再释放左键。<br>2. 用中键开始平移/导航，交错按下并先释放左键/右键，再释放中键。<br>3. 在颜色条、文本选择和已按下但未释放的右键菜单项上重复异按钮 release。 | 只有发起按钮能完成、取消或提交对应手势；异按钮 release 不提前重排 Replay/列表、不提交 clip/keyframe/gradient/color/audio 数据、不停止模型/曲线/文本平移选择，也不执行 pending 菜单动作或清除另一按钮的 pressed 状态；发起按钮最终 release 恰好生效一次。 | 未测 |

## 9. 发布兼容与资源烟测

| ID | 环境/前置 | 步骤 | 预期结果 | 状态/证据 |
| --- | --- | --- | --- | --- |
| COMPAT-01 | 旧 v1 Addon、旧 API 2.0 Addon、当前 Web/Film collaboration Addon；客户端与 dedicated server | 1. 分别单独和组合启动。<br>2. 加载旧序列化 Film、动作、粒子和 Form。<br>3. 执行一次注册内容和网络交互。 | 旧 Addon/数据继续工作；dedicated server 不加载 client API；公开 API 保持 `2.0`；网络仍使用既定 `s1..s15/c1..c19`，没有因新增包重编号导致握手/解码错位。 | 未测 |
| COMPAT-02 | `en_us` 与 `zh_cn`，打开 Chroma、ModelBlock、动作面板和 Add Clip 菜单 | 1. 切换语言并遍历相关 UI。<br>2. 触发新增实体交互的历史动作面板。 | 默认英语和中文均显示人类可读文本；`chroma_sky_color`、`bbs:interact_entity` 等不直接显示原始 key；隐藏的新建实体动作仍不妨碍历史动作面板显示。 | 未测 |
| COMPAT-03 | 临时资源根、同名前缀兄弟目录 `assets_backup`；Windows junction 或 POSIX symlink（按平台） | 1. 从合法 provider root 加载资源。<br>2. 尝试 root 本身、兄弟目录、符号链接/联接越界和 deleted leaf。<br>3. 回到合法路径加载。 | 合法资源可用；越界/同名前缀/symlink 或 junction 逃逸均 fail closed，不读取根外文件；一次拒绝不污染后续合法 provider lookup。 | 未测 |

## 执行记录

| 字段 | 值 |
| --- | --- |
| Core 构建/commit |  |
| Minecraft / NeoForge | 1.21.1 /  |
| 服务端构建 |  |
| 操作系统 / JDK |  |
| GPU / 驱动 |  |
| Iris / 光影 |  |
| FFmpeg |  |
| Addon 清单 |  |
| 执行人 / 日期 |  |
| PASS / FAIL / BLOCKED 计数 |  |
| 证据目录或链接 |  |
