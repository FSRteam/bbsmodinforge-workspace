# 实施清单

## A. CF Worker + 部署(用户已给 key)

- [x] A1 `film-home-publisher/worker/worker.js`(module worker:GET api/ads|news、GET/PUT img、PUT api 带 Bearer 校验、CORS)
- [x] A2 `film-home-publisher/worker/wrangler.toml`(KV 绑定样板,供日后 wrangler 使用)
- [x] A3 `film-home-publisher/worker/deploy.mjs`(REST API 部署:token→account→KV→script→subdomain→种子→打印 URL;凭证写入 webui/config.local.json)
- [x] A4 部署完成:`https://bbs-film-home.xbwhome.workers.dev`(KV `bbs_film_home` 已种子);本地逻辑单测 19 项全过(`worker.test.mjs`)。**workers.dev 国内被 DNS 污染,本地 curl 不通;自定义域绑定需 zone 权限,留给用户面板操作**(见 README)

## B. 本地 Node WebUI

- [x] B1 `webui/server.mjs`(零依赖 http 服务:静态页 + push/upload/current/drafts/config 代理;绑定 127.0.0.1;路径穿越防护)
- [x] B2 `webui/public/index.html`(广告 4:3 校验/居中裁切、新闻 MD 编辑+预览、设置、推送、线上回填)
- [x] B3 `webui/data/` 草稿持久化;`film-home-publisher/.gitignore` 排除 config.local.json/data
- [x] B4 `film-home-publisher/README.md`(启动、部署、推送、自定义域流程)
- [x] B5 接口冒烟:config/drafts/health/static/404/traversal 通过;health 因网络污染显示 fetch failed(预期)

## C. 客户端远程拉取(`.worktrees/fsr-film-home`)

- [x] C1 `FilmHomeContent` 扩展:Source(CACHED/REMOTE)/fetching 状态、refreshAsync(URL 链+节流)、缓存读写、remote.json 读取、load() 不回滚远程数据
- [x] C2 jar 默认 `assets/bbs/assets/film_home/remote.json`(workers.dev URL,数组形式支持自定义域回退链)
- [x] C3 `UIFilmHomePanel.applyContent` 接异步刷新(引用比较,避免轮播页重置);`UINewsStrip`/`UIAdBoard` 空数据 + fetching → spinner
- [ ] C4 编译验证 `compileClientJava`(进行中)

## D. 详情弹窗自适应与翻页

- [x] D1 `MarkdownBody` 垂直滚动(滚轮/滚动条点击+拖动/溢出时左键拖动翻页;内容不溢出保留拖选复制)
- [x] D2 `UINewsStrip.openDetails`(≤640×480 或 90% 窗口)、`UIAdBoard.openDetails`(≤400 宽,4:3 图随宽自适应,MD 滚动)
- [ ] D3 编译 + 必要 source test

## E. 质量与收尾

- [ ] E1 `trellis-check`(编译/主题红线/渲染热路径零 IO 复核)
- [ ] E2 端到端自测:WebUI 推送 → Worker GET → 客户端缓存文件验证
- [ ] E3 spec 沉淀(首页远程内容契约)
- [ ] E4 commit(worktree 分支 + 外层仓库)
