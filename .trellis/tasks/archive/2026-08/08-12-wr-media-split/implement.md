# 执行计划：media.js 拆分

验证命令在 `bbs-web-remote/` 运行：`gradlew.bat verifyWeb`、`gradlew.bat testServerSmoke`。

## Step 1（拆 render.js）

- [x] CanvasRenderer + flipDecodedVertically + disposeDecodedImage + sha256Hex 整块搬入 render.js（`ns.Render = Object.freeze({ CanvasRenderer, flipDecodedVertically, disposeDecodedImage, sha256Hex })`；前置校验 protocol+style）
- [x] clamp 引用改 `ns.Style.clamp`（模块顶部解构）
- [x] 9 处同步（script 标签插 style 之后 / webRuntimeScripts / 描述表 / FIXED_RESOURCE_PATHS / smoke 断言 `/render.js` marker `ns.Render` / 缓存断言列表 / 12 个 webTest require 插 style.js 后）
- 验证：`node --check` 全部 + `gradlew.bat verifyWeb` 全绿 + `testServerSmoke` 全绿

## Step 2（拆 cache.js）

- [x] readBoundedResponse + ResourceCache + SurfaceCache 搬入 cache.js（`ns.Cache = Object.freeze({ ResourceCache, SurfaceCache })`；readBoundedResponse 为模块私有；decodeImageBytes 仅被 cache 两类使用，随迁为 cache.js 模块私有）
- [x] 9 处同步（加载位 render 之后）
- 验证：同上

## Step 3（拆 transport.js + state 枚举化）

- [x] RTC 常量 + rtcUint32 + boundedRtcIceServers + RtcSurfaceCache + HybridMediaTransport 搬入 transport.js（`ns.Transport`；导出 RtcSurfaceCache/HybridMediaTransport/boundedRtcIceServers/rtcUint32/RTC_NEGOTIATION_TIMEOUT_MS）
- [x] HybridMediaTransport.state 中文串改内部枚举（jpeg/webrtc/negotiating/jpeg-fallback），status() 经 `ns.I18n.t("transport.state." + state)` 映射；i18n.js 目录补 4 键（zh-CN 逐字同现值）；firstDrawable 的 `state === "WEBRTC"` 逻辑判断与赋值同步改为 `"webrtc"`，行为等价
- [x] app.js 诊断读 state 处同步 t()（输出不变）
- [x] 9 处同步（加载位 cache 之后、media 之前）
- 验证：同上 + webrtc-hybrid.test.js 绿

## Step 4（media.js 瘦身 + 门面）

- [x] media.js 删除已搬块，留守指标/自适应/画质函数；`ns.Media` 门面按 design.md 第 3 节逐键转发（与拆分前键集合 diff 校验为零）
- [x] `rg "CanvasRenderer" app.js` 确认 smoke marker 仍在
- 验证：verifyWeb + testServerSmoke + 浏览器冒烟（离线演示可开、连接表单可用）——浏览器冒烟未在本会话执行，留待人工复核

## Step 5（收尾）

- [x] 各文件行数 < 1000 核对（render.js 868 / cache.js 395 / transport.js 409 / media.js 631）
- [x] implement.md 勾选、任务归档（归档走 /trellis:finish-work）
