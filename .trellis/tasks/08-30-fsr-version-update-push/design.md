# Design: FSR version update push

## 数据模型(KV `content:releases`)

```json
{"releases":[{
  "version":"0.0.12",            // \d+\.\d+\.\d+(-beta\.\d+)?
  "channel":"stable|preview",
  "date":"2026-08-30",           // 更新日期,展示用
  "url":"https://…",             // jar 下载地址,必须 https
  "sha256":"[a-f0-9]{64}?",      // 可选;自动安装仅在存在时提供
  "notes":"md 文本",             // ≤200KB
  "mandatory":false,
  "revoked":false,
  "revokedReason":""
}]}
```

- **PUT /api/releases**(Bearer,≤1MB):全量替换;校验:数组 ≤20、版本号合法且
  (version,channel) 不重复、URL https、sha256 格式、notes ≤200KB;非法整体 400。
- **GET /api/version**(公开,`Cache-Control: max-age=300`):原样返回 `{"releases":[…]}`。
  **所有选择逻辑在客户端**(最新版挑选、revoked、skip),服务端只存储+校验——单一实现。
- 广告/新闻端点与 KV key 不变。

## Worker/测试/部署

- `worker.js` 增 `handleReleases`(PUT 校验)+ `handleVersion`(GET);复用 readBody/Bearer。
- `worker.test.mjs` 增用例:发布合法/非法版本号、重复 (version,channel)、>20 条 400、
  http URL 400、GET 返回、撤回后 GET 原样(逻辑在客户端)。
- `deploy.mjs` 无需改动(seed 不含 releases);mock-worker 自动获得新端点。

## 客户端(包 `mchorse.bbs_mod.update`)

### FSRUpdates(单例,检查器)

- `checkAsync(boolean manual)`:`UIDashboard.onOpen` 调 manual=false。节流:距
  `updateLastCheck` ≥ 间隔(每次/1d/7d/14d/30d);manual 绕过节流与"接受更新"以外的一切静默
  (结果总是 toast/状态行)。仅当 `updateEnabled`。
- 线程:复用 FilmHomeContent 模式——daemon POOL + `Minecraft.getInstance().execute` 回主线程。
- HTTP GET `https://fsrapi.twiap.studio/api/version`(常量,10s 超时,2MB cap);
  成功写 `settings/update_cache.json`(payload+时间戳);失败读缓存兜底(状态行标"缓存")。
- **决策顺序**(主线程回调内):
  1. 当前版本 ∈ revoked(全部 channel 的 revoked 条目)→ `UIUpdateOverlayPanel` 警告变体。
     **优先于一切静默规则**(用户明确要求:本地比线上新也要警告)。
  2. 本地版本 ≥ 该频道最新 → 静默(状态行"已是最新")。
  3. 频道最新 == `updateSkippedVersion` → 静默(skip 持久化;**更新的版本号 > 跳过版本**
     时重新提醒,即"等到下一个版本再提醒")。
  4. 有更新 → 非 manual 时等 dashboard 在前台(网络延迟天然晚于首帧)→ 弹窗;
     mandatory 与否只影响跳过按钮。
- 版本比较:解析 int[3] + `-beta.N` 后缀;数值逐段比;同数值 stable > beta.N > beta(N 大者新)。
- 当前版本:`BBSMod` loader access `getModVersion("bbs")`;开发环境取 gradle 注入值。

### 设置(BBSSettings,`builder.category("fsr_updates", Icons.DOWNLOAD)`)

- `updateEnabled` ValueBoolean(默认 true)。
- `updateChannel` ValueInt `.modes(正式版, 预览版)`(默认 0)。
- `updateInterval` ValueInt `.modes(每次, 每天, 每周, 两周, 每月)`(默认 2=每周)。
- `updateSkippedVersion` ValueString(invisible)。
- `updateLastCheck` ValueLong(invisible)。
- `updateCheckTrigger` ValueBoolean(invisible)→ UIValueMap 身份注册自定义 widget:
  [检查更新] 按钮 + 状态 UIText(`.updates()`,读 FSRUpdates 状态:上次检查时间/结果/缓存标记)。

### UIUpdateOverlayPanel

- 更新变体:标题"发现新版本 vX";频道徽标(正式/预览)+ 日期;`MarkdownBody(notes)` 滚动;
  按钮:[立即更新](仅 sha256 存在 && FMLEnvironment.production)、[前往下载]、
  [跳过此版本](mandatory 时隐藏)、[关闭]。
- 撤回变体:标题"当前版本已被撤回";原因 + 日期;[前往下载] [关闭]。

### UpdateInstaller(自动安装)

- 前置:production、url https、sha256 存在;否则按钮不渲染。
- 流程(POOL 线程):
  1. 下载 `url` → `<mods>/fsr-update-<version>.jar.part`(流式,≤100MB,进度 toast);
  2. MessageDigest sha256 校验;不符 → 删 part,notifyError;
  3. 定位当前 jar:`getModFile("bbs")`(production 下=mods 内 jar);
  4. 原子换:当前 jar 改名 `<name>.jar.pre-update`(Windows 允许重命名运行中的 jar)→
     part 移动为原名;任一步失败 → 回滚改名并保留 `<name>.jar.pre-update` 里……(回滚顺序:
     先恢复原名,part 保留供手动);
  5. 成功 → 弹"重启完成更新" [立即退出(Minecraft.stop)] [稍后]。
- 启动清理:客户端启动时扫 `*.jar.pre-update` best-effort 删除(新 jar 已在加载,旧文件不再被锁)。

## WebUI(零依赖不变)

- `server.mjs`:`GET /api/releases`(代理 worker /api/version)、`PUT /api/releases`(带 Bearer 转发)。
- `public/index.html` 新 tab"版本发布":表单(version/channel/date/url/mandatory/sha256
  + 「从 jar 计算」本地文件 crypto.subtle 计算)+ MD 编辑器/预览(复用现有);
  历史表格(徽标/日期/revoked 状态/操作:撤回(prompt 原因)/恢复/编辑回填/删除);
  所有操作改本地数组后整表 PUT;顶部显示 n/20。

## 状态与持久化清单

- Worker KV `content:releases`(唯一真源)。
- 客户端:`updateLastCheck`/`updateSkippedVersion`(BBSSettings 配置文件)、
  `update_cache.json`(最近成功 payload)。
- WebUI 不落盘 releases(总是从 worker 拉,编辑即推送)。

## 验证

- worker.test.mjs;mock E2E:PUT 发布 → GET 校验 → 撤回字段在列。
- WebUI IAB GUI 验收(发布/撤回/jar 哈希回填)。
- 客户端:compileClientJava + filmEditor/uiInputMailbox;决策矩阵用独立 JVM 断言
  (revoked 优先、skip 语义、beta 比较、mandatory 按钮矩阵)。
- 真机部署后手测:改 setting 频率=每次、手动检查、跳过、撤回警告。
