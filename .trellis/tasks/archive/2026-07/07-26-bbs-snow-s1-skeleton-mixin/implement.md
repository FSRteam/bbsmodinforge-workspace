# S1 执行计划

## 前置

```bash
cd new && ./gradlew jar
echo "EXIT=$?"
ls -l build/libs/BBS_FSR_beta_v0.0.10.jar
```

`EXIT=0` 且 jar 存在才继续。**不要用管道接 gradlew**，管道会吞掉退出码。

## 步骤

### 1. 建目录与 wrapper

- [ ] 创建 `bbs-snow-addon/`
- [ ] 从 `bbs-premiere-export/` 复制 `gradlew`、`gradlew.bat`、`gradle/wrapper/`
- [ ] `settings.gradle`：`rootProject.name = "bbs-snow-addon"` + NeoForge maven pluginManagement

### 2. build.gradle

以 `bbs-premiere-export/build.gradle` 为底：

- [ ] `plugins { id 'net.neoforged.gradle.userdev' version '7.1.21' }`
- [ ] `sourceSets`：`main.java.srcDirs = ['src/main/java', 'src/client/java']`，resources 指向 `src/main/resources` 与 `src/client/resources`
- [ ] `java.toolchain.languageVersion = JavaLanguageVersion.of(21)`
- [ ] `dependencies`：`implementation "net.neoforged:neoforge:${neo_version}"` + BBS 本地 jar
- [ ] `verifyBbsCoreJar` 任务（照搬，`requiredEntries` 换成本 addon 实际依赖的 class，例如 `mchorse/bbs_mod/events/BBSAddonMod.class`、`mchorse/bbs_mod/addon/BBSAddonRegisterEvent.class`、`mchorse/bbs_mod/ui/framework/UIBaseMenu.class`）
- [ ] `processResources` expand `neoforge.mods.toml`

**Mixin 相关**：先按主 mod 的最简形态（不加任何额外 mixin 配置）试一次编译。NeoGradle userdev 会随 `net.neoforged:neoforge` 带上 mixin 依赖与 AP。若 `@Mixin` 注解无法解析或 AP 未运行，再补 `annotationProcessor` 声明——**先试最简，别预先堆配置**。

### 3. gradle.properties

```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
org.gradle.daemon=false

mod_id=bbs_snow
mod_name=bbs_snow
mod_version=1.0.0
mod_group_id=mchorse
archives_base_name=bbs-snow-addon

minecraft_version=1.21.1
minecraft_version_range=[1.21.1]
java_version=21
neo_version=21.1.222
neo_version_range=[21.1,)
loader_version_range=[1,)

bbs_version=0.0.10
```

### 4. neoforge.mods.toml

以 `bbs-premiere-export` 的为底，加四个 `[[mixins]]` 块（见父任务 `design.md` §6）。注意 `bbs` 依赖的 `side` 应为 `BOTH`（源插件 main sourceSet 有 12 个 mixin，非纯客户端）。

### 5. 四份 mixin config 空壳

```json
{
    "required": true,
    "package": "mchorse.bbs_snow.mixin",
    "compatibilityLevel": "JAVA_21",
    "mixins": [],
    "injectors": { "defaultRequire": 1 }
}
```

client 三份用 `"client": []`。**不写 `refmap`。**

### 6. 入口类

- [ ] `src/main/java/mchorse/bbs_snow/BBSSnowMod.java` — `@Mod("bbs_snow")`，构造函数收 `IEventBus modBus`，`addListener(this::onBBSAddonRegister)`
- [ ] `src/main/java/mchorse/bbs_snow/BBSSnowAddon.java` — `implements BBSAddonMod`，一个 `@Subscribe void onRegisterSettings(RegisterSettingsEvent)` 打日志
- [ ] `src/client/java/mchorse/bbs_snow/BBSSnowModClient.java` — `Dist.CLIENT` 隔离

### 7. 探针 Mixin

- [ ] `src/client/java/mchorse/bbs_snow/mixin/client/UIBaseMenuProbeMixin.java`

```java
@Mixin(UIBaseMenu.class)
public abstract class UIBaseMenuProbeMixin
{
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbs_snow$probe(CallbackInfo ci)
    {
        LoggerFactory.getLogger("bbs_snow").info("[bbs_snow] PROBE: UIBaseMenu mixin applied");
    }
}
```

先读 `new/src/client/java/mchorse/bbs_mod/ui/framework/UIBaseMenu.java` 确认构造函数签名，选一个真实存在的注入点。

- [ ] 加进 `bbs_snow.client.mixins.json`

### 8. 构建

```bash
cd bbs-snow-addon && ./gradlew build
echo "EXIT=$?"
```

exit code 非 0 就读完整日志定位，不要靠 `| tail` 猜。

### 9. 运行验证（AC3/AC4/AC5 —— 本任务的重点）

```bash
cd bbs-snow-addon && ./gradlew runClient
```

- [ ] 客户端起来
- [ ] 日志出现 mod `bbs_snow` 加载
- [ ] 日志出现 `[bbs_snow] PROBE: UIBaseMenu mixin applied`（需在游戏内打开一次 BBS 面板触发）
- [ ] 日志出现 `BBSAddonMod` 的 `@Subscribe` 输出
- [ ] 无 mixin apply 失败

**把这几行日志原样贴进本任务记录**，这是 Gate 1 的证据。

### 10. 负向验证（AC6）

- [ ] 临时把探针的 `method = "<init>"` 改成 `method = "thisMethodDoesNotExist"`
- [ ] 重新构建/启动，确认**报错**而不是静默通过
- [ ] 改回来，删除该临时改动

这一步是为了确认后续 75 个 Mixin 的失败不会被吞掉。跳过它等于后面每个 mixin 都在裸奔。

### 11. 收尾

- [ ] `bbs-snow-addon/README.md`：写明依赖关系、`bbs_snow$` 前缀约定、如何重建 BBS jar
- [ ] `.gitignore`（照 `bbs-photon-addon/.gitignore`）
- [ ] `git diff --stat new/` 为空
- [ ] `grep -rn "net.fabricmc" bbs-snow-addon/src` 无结果
- [ ] 提交（只 add `bbs-snow-addon/` 下的文件，工作树有其他会话的改动，不要 `git add -A`）

## 回滚

`bbs-snow-addon/` 是全新目录，整体删除即可，不影响仓库其余部分。

## 关卡

第 9 步的 AC4 不通过就**停下来回父任务**，按 `prd.md` 的「失败处置」把选择权交回用户。不要自行切换构建插件反复试 —— 那是需要用户拍板的方案变更。
