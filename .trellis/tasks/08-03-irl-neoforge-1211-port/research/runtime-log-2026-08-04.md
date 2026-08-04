# 2026-08-04 客户端运行日志核查

来源：`C:/Users/29617/Desktop/latest (10).log`，记录时间 2026-08-04 13:42-13:48。

## 结论

IRLights 已被 NeoForge 发现并加载，BBSFSR 外部 Addon 注册、两个 Form 注册、客户端资源重载和 Iris/Sodium 初始化均完成。首个 IRLights 致命错误发生在打开点光编辑器时：

```text
NoSuchMethodError: UITrackpad.limit(double, double)
```

调用点是 `UIPointLightFormPanel`，不是窗口初始化、Mixin 应用或 shaderpack 加载。

## 根因

工作区编译用的 BBSFSR `0.0.11` JAR 暴露了 `UITrackpad.limit(double,double)`，但用户实际实例装载的同名 `BBS_FSR_beta_v0.0.11.jar` 不包含该二参数方法。该版本的兼容重载 `limit(double,double,boolean)` 存在，因此仅使用二参数重载会在编译通过后于运行期链接失败。

## 修复

点光和聚光编辑器的所有数值限制改为 `limit(min, max, false)`，保留原有范围并使用当前与旧版 BBSFSR 0.0.11 都存在的签名。重新执行：

```text
gradlew.bat --no-daemon compileJava --console=plain
gradlew.bat --no-daemon check build --console=plain
```

两项均成功。最终 JAR 字节码核对为 `UITrackpad.limit:(DDZ)L...`，不再引用缺失的 `(DD)` 签名。

## 非根因警告

- Iris `iris.refmap.json` 缺失警告是 Iris 发布 JAR 的已知现象。
- ComplementaryReimagined 中 `BIOME_PALE_GARDEN`、`endFlashIntensity` 等 uniform 警告来自 shaderpack/Iris 表达式兼容，随后 Iris 因窗口尺寸为零重建失败；不是 IRLights 的 Java 崩溃。
- `isometric-renders` 语言文件和可选模组类加载警告与本插件无关。

## 剩余人工验收

需在用户实际实例中重新打开点光和聚光编辑器，并继续验证拖拽、保存重载、关键帧、阴影、cookie、体积光、outline 与七个 shaderpack 补丁的画面效果。日志证明插件核心启动与注册正常，但不能替代这些画面验收。
