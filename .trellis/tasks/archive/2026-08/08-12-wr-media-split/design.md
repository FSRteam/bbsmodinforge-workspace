# 设计：media.js 拆分

前置：`08-12-wr-code-hygiene` 已完成（clamp 归位 ns.Style）。

## 1. 目标文件布局（2026-08-16 行号基准，media.js 2255 行）

| 新文件 | 搬移内容（media.js 行号） | 预计行数 | ns 域 |
|---|---|---|---|
| `render.js` | CanvasRenderer（61-897）、flipDecodedVertically（899-915）、disposeDecodedImage（917-925）、sha256Hex（927-935） | ~860 | `ns.Render` |
| `cache.js` | readBoundedResponse（937-968）、ResourceCache（970-1143）、SurfaceCache（1699-1838） | ~330 | `ns.Cache` |
| `transport.js` | RTC 常量（1840-1848）、rtcUint32/boundedRtcIceServers（1849-1882）、RtcSurfaceCache（1883-1996）、HybridMediaTransport（1997-末尾） | ~420 | `ns.Transport` |
| `media.js`（留守） | requestedMediaFps/normalizeCustomMediaSettings/boundedText、MediaMetrics、LinkFeedback、AdaptiveQualityController、CUSTOM_MEDIA_LIMITS/DEFAULT_CUSTOM_MEDIA/QUALITY_PREFERENCES + `ns.Media` 门面 | ~600 | `ns.Media` |

全部 < 1000 行 ✓。

## 2. 依赖与加载序

`protocol → i18n → error → style → render → cache → transport → media → input → control → ui → app`

- render 依赖 protocol（ProtocolError）+ style（颜色工具、clamp）。
- cache 依赖 protocol + render（解码工具/类型引用）。SurfaceCache 构造参数含 renderer 实例与 mediaMetrics/adaptiveQuality 回调——运行时注入，无静态依赖。
- transport 依赖 protocol + i18n（state 枚举文案）。RtcSurfaceCache 与 renderer 交互经构造注入。
- media（留守）依赖 protocol + style；门面聚合 ns.Render/ns.Cache/ns.Transport。
- 单向无环 ✓。

## 3. ns.Media 门面（保形状）

```js
const R = ns.Render, K = ns.Cache, T = ns.Transport;
ns.Media = Object.freeze({
    clamp: ns.Style.clamp,            // 兼容转发（code-hygiene 起 canonical 在 ns.Style）
    requestedMediaFps, normalizeCustomMediaSettings, boundedText,
    CUSTOM_MEDIA_LIMITS, DEFAULT_CUSTOM_MEDIA, RTC_NEGOTIATION_TIMEOUT_MS: T.RTC_NEGOTIATION_TIMEOUT_MS,
    CanvasRenderer: R.CanvasRenderer, flipDecodedVertically: R.flipDecodedVertically,
    disposeDecodedImage: R.disposeDecodedImage, sha256Hex: R.sha256Hex,
    ResourceCache: K.ResourceCache, SurfaceCache: K.SurfaceCache,
    RtcSurfaceCache: T.RtcSurfaceCache, HybridMediaTransport: T.HybridMediaTransport,
    boundedRtcIceServers: T.boundedRtcIceServers, rtcUint32: T.rtcUint32,
    MediaMetrics, LinkFeedback, AdaptiveQualityController
});
```

**导出键集合与拆分前 ns.Media 完全一致**（逐键核对）→ app.js 解构行、WebTest 导出、全部 webTest 零改动语义。RTC_NEGOTIATION_TIMEOUT_MS 仍在 ns.Media 键下（值从 T 转发）。

## 4. HybridMediaTransport.state 内部枚举 + i18n

- 内部 state 改为 `"negotiating" | "jpeg-fallback" | ...` 枚举（小写蛇形）。
- `stateLabel()` 或读取处经 `ns.I18n.t("transport.state." + state)` 映射；i18n.js 增目录键（zh-CN 值与现值逐字相同：「协商中」「JPEG 回退」「关闭」等）。
- app.js 读 `hybridMedia.state` 的诊断处同步走 t()。webrtc-hybrid.test.js 若断言中文 state 值，保持输出逐字不变。

## 5. 发布同步（每个新文件 ×9）

index.html script 标签（顺序见上）、webRuntimeScripts、`webSyntaxTaskDescriptions`、FIXED_RESOURCE_PATHS、smoke 逐资源断言（marker：render.js=`ns.Render`、cache.js=`ns.Cache`、transport.js=`ns.Transport`）、smoke 缓存断言列表、12 个 webTest require 链（插在 media.js 之前对应位置）、app.js 无需改（门面保形）、WebTest 无需改。`verifyWeb` 语法任务由 webRuntimeScripts 循环自动覆盖。

## 6. 明确不做

- 不改任何类的方法体逻辑（纯搬移 + state 枚举化）；不改 App；不动 input/control/ui。
- 不新增 webTest（PRD 只要求资源断言）。
