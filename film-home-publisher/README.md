# film-home-publisher

影片首页(FSR Film Home)广告与新闻的**远程内容发布工具**:

```
本地 WebUI(编辑/推送) ──PUT──▶ Cloudflare Worker(KV) ◀──GET── 客户端(影片首页异步拉取)
```

- Worker 公开地址:`https://fsrapi.twiap.studio`(workers.dev 地址已停用——国内被 DNS 污染,且按需求不保留)
- KV namespace:`bbs_film_home`;脚本名:`bbs-film-home`
- 数据契约与客户端本地 JSON 完全一致(`{"items":[...]}`),`ads.json` 字段 `image/link/markdown`,`news.json` 字段 `id/tag/date/title/summary/markdown/image/url`

## 目录

- `worker/worker.js` — Cloudflare Worker(module):公开 GET `/api/ads` `/api/news` `/img/<key>`,鉴权 PUT/DELETE(Bearer token)
- `worker/worker.test.mjs` — 零依赖逻辑冒烟测试(`node worker.test.mjs`)
- `worker/deploy.mjs` — REST API 一键部署(建 KV、传脚本、开 workers.dev、种子内容)
- `worker/wrangler.toml` — 供日后用 wrangler 管理
- `webui/server.mjs` — 本地推送台后端(纯 Node,绑定 127.0.0.1,token 不出本机)
- `webui/public/index.html` — 推送台页面(广告 4:3 校验/裁切、新闻 Markdown 编辑+预览、一键推送、线上回填)
- `webui/config.local.json` — **gitignored**:CF API token、worker 地址、admin token
- `webui/data/` — 草稿

## 使用

### 1. 启动推送台

```
cd webui
node server.mjs            # http://127.0.0.1:8642  (--open 自动开浏览器,--port 换端口)
```

首次部署后 `config.local.json` 已含全部凭证,页面右上角绿点即 Worker 连通。### 2. 编辑与推送

- **广告**:添加广告位 → 选图(自动校验 4:3,不符可一键居中裁切)→ 填复制链接/说明 MD → 「推送广告到线上」。图片先按内容哈希上传到 `/img/<sha256>.<ext>`(重复推送不重复占空间),再推 JSON。
- **新闻**:标题/tag/日期/摘要/外链/正文 Markdown,可实时预览 → 「推送新闻到线上」。
- 「从线上拉取覆盖」把线上当前内容拉回编辑器;草稿自动保存在 `webui/data/`。
- 客户端拉取间隔默认 30 分钟(`refresh_minutes` 可调),推送后客户端最多等一个间隔即生效。

### 3. 部署 / 迁移(换账号或重部署)

```
cd worker
node deploy.mjs --token <CF_API_TOKEN> \
  --hostname fsrapi.twiap.studio --zone-id <twiap.studio 的 zone id> \
  --seed-ads <ads.json> --seed-news <news.json>   # 种子可选,缺省为空
```

`--hostname/--zone-id` 会通过 API 直接挂自定义域(自动建 DNS 记录与证书);已挂载时可不传。workers.dev 路由部署后总是停用。脚本会把 worker 地址、KV id、admin token(重部署沿用)写回 `webui/config.local.json`。

### 4. 自定义域

部署脚本用 `PUT /accounts/{account}/workers/domains` 挂域并自动停用 workers.dev,无需面板操作。换域名时带新的 `--hostname/--zone-id` 重跑一次即可,并同步改:

1. `webui/config.local.json` 的 `workerUrl`(或设置页)
2. 客户端 `config/bbs/assets/film_home/remote.json` 的 `ads`/`news` URL(数组形式,可配多条回退)

## 客户端回退链(行为约定)

1. 打开首页先渲染本地内容(config 覆盖 → jar 默认),立即出画面;
2. 后台线程拉取远程 JSON(10s 超时),成功 → 写缓存 `config/bbs/cache/film_home_cache/` 并在 UI 线程刷新;
3. 拉取失败 → 静默保留当前内容(若曾成功过,缓存即"上次成功的数据");
4. 未拉取完且本地为空的分区显示加载动画,全程不阻塞渲染线程。
