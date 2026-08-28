# 设计:影片首页远程内容(CF Worker + WebUI + 客户端拉取)

## 数据契约(不变,沿用 08-16)

- `ads.json`:`{"items":[{"image","link","markdown"}]}`;`image` 为 4:3 图,远程模式下是绝对 URL(`https://<worker>/img/<key>`)。
- `news.json`:`{"items":[{"id","tag","date","title","summary","markdown","image","url"}]}`;`markdown` 为正文。
- 客户端 `WebImages.resolve` 已支持 http(s) 绝对 URL(异步+磁盘缓存+加载动画),故远程 JSON 无需改图片管线。

## 1. CF Worker(`film-home-publisher/worker/`)

KV 存储(namespace `bbs_film_home`):

| key | 值 |
|---|---|
| `content:ads` / `content:news` | 上表 JSON 原文 |
| `img:<sha256>.<ext>` | 图片二进制(KV metadata 记 content-type) |

路由(module worker,单文件 `worker.js`):

- `GET /api/ads`、`GET /api/news` → KV 读,`Cache-Control: public,max-age=60`,CORS `*`;404 时返回 `{"items":[]}`。
- `GET /img/<key>` → KV `img:<key>`,按 metadata content-type 返回,`Cache-Control: public,max-age=86400`。
- `PUT /api/ads` / `PUT /api/news` / `PUT /img/<key>` → `Authorization: Bearer $ADMIN_TOKEN`(secret_text 绑定)校验通过才写 KV;失败 401。
- 无 token 一律只读。

部署走 CF REST API(零依赖 `deploy.mjs`,Node ≥18):verify token → 取 account → 建 KV → multipart PUT script(绑定 kv_namespace + secret_text)→ 启用 workers.dev subdomain → 种子默认内容 → 打印 URL。token/账号/namespace/admin token 写入 `film-home-publisher/webui/config.local.json`(gitignored)。

## 2. 本地 Node WebUI(`film-home-publisher/webui/`)

- `node server.mjs [--port 8642]`,纯 Node 内置模块(http/fs/crypto),无 npm 依赖。
- 单页 `public/index.html` 原生 JS:顶部 Tab(广告/新闻/设置)。
  - 广告:列表管理;上传图片即时预览,canvas 测宽高比,偏离 4:3 超 1% 提示并可一键居中裁切为 4:3 PNG;字段 link/markdown;拖拽排序、删除。
  - 新闻:id/tag(update|notice|tutorial)/date/title/summary/url/markdown(等宽 textarea)+ 轻量 MD 实时预览;可选头图上传。
  - 设置:Worker URL、admin token(首次部署后自动写入 config.local.json);「测试连接」「拉取线上内容回填编辑器」。
- 推送顺序:图片先 `PUT /img/<sha256>.<ext>` → 再 `PUT /api/ads|news` JSON;推送后展示结果。草稿与线上副本缓存在 `webui/data/`。

## 3. 客户端(`.worktrees/fsr-film-home`)

### 3.1 远程拉取(FilmHomeContent 内扩展)

- 新配置 `film_home/remote.json`(config 覆盖 → jar 默认):`{"enabled":true,"ads":"<url>","news":"<url>","refresh_minutes":30}`。jar 默认文件带部署后的真实 URL。
- `FilmHomeContent` 增加:
  - `enum Source { LOCAL, CACHED, REMOTE }` 与 `volatile boolean fetchingNews/fetchingAds`。
  - `refreshAsync(Runnable onApplied)`:单 daemon 线程池;GET JSON → 解析成功 → 写缓存(`BBSMod.getSettingsPath("film_home_cache")/ads.json|news.json`)+ 置内存列表 → `Minecraft.getInstance().execute(onApplied)`;任何失败:保留当前列表不变(拉取前已用本地/缓存填充)。
  - 读取顺序:本地 config/jar(立即可用)→ 异步远程成功则覆盖并写缓存;失败不覆盖(天然"失败用上次成功")。
  - `isFetching()` 供 UI 画加载图标;时间戳节流(距上次成功 < refresh_minutes 不重复拉)。
- `UIFilmHomePanel.applyContent()`:同步本地填充后调 `refreshAsync(this::applyContentOnly)`,内容变化时在 UI 线程重填;`UINewsStrip`/`UIAdBoard` 在"列表为空且 fetching"时画 spinner(不隐藏分区)。

### 3.2 新闻详情弹窗自适应 + 溢出翻页

- `MarkdownBody` 增加垂直滚动:内部记录 layout 总高;滚轮滚动;右侧 4px 滚动条可点击/拖动;内容溢出时按住左键拖动 = 纵向滑动翻页(位移超过阈值按滚动处理,松手不复制);内容不溢出时保持原拖选复制。选中 hitTest 坐标随 scroll 偏移换算。
- `UINewsStrip.openDetails` / `UIAdBoard.openDetails`:弹窗尺寸改为随窗口自适应(`min(内容理想宽, 屏宽*0.85)`、高度同理),调用 `UIOverlay.addOverlay` 时用动态宽高;`MarkdownBody` 高度吃满剩余空间。

## 4. 兼容与回退

- remote.json 缺失/enabled=false → 完全走现本地路径,零行为变化。
- Worker 404/5xx/超时(10s)/JSON 坏 → 静默回退,仅 LOGGER.warn 语义日志(沿用 `[BBS-SEM] topic=film_home`)。
- WebUI 推送 JSON 与本地 schema 完全一致,旧客户端(无远程逻辑)不受影响。

## 5. 风险

- workers.dev 在部分网络不可达 → 客户端已有缓存回退,WebUI 部署输出里提示可换自定义域(非目标)。
- KV 最终一致(秒级)→ 推送后 WebUI 用 GET 复读校验。
- 拖动翻页与拖选复制的手势冲突 → 以"内容是否溢出"为开关,溢出时翻页优先。
