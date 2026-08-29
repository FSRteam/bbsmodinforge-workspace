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
- [x] C4 编译验证 `compileClientJava` 通过(仅既有 deprecation 提示)

## D. 详情弹窗自适应与翻页

- [x] D1 `MarkdownBody` 垂直滚动(滚轮/滚动条点击+拖动/溢出时左键拖动翻页;内容不溢出保留拖选复制)
- [x] D2 `UINewsStrip.openDetails`(≤640×480 或 90% 窗口)、`UIAdBoard.openDetails`(≤400 宽,4:3 图随宽自适应,MD 滚动)
- [x] D3 编译通过;MarkdownBody/overlay 为自包含 UI 改动,无键位/CRUD 回归面

## E. 质量与收尾

- [x] E1 `compileClientJava` + `testThemeCore` 通过;渲染热路径零 IO(远程拉取全在 daemon 池);语义色全部走 BBSSettings token
- [x] E2 端到端:mock(复用真实 worker.js)验证 WebUI→PUT ads/news→KV→GET 回读一致;图片 /api/upload→/img/<sha256>.png 字节一致;浏览器 GUI 验收广告/新闻页、4:3 裁切(300×200→267×200)、推送 toast、线上回填接口
- [x] E3 spec 沉淀 `.trellis/spec/frontend/film-home-remote-content.md`(契约+回退顺序+工具链规则)
- [x] E4 commit:worktree `79b33a0fc`(fsr-film-home-redesign)+ 外层仓库 film-home-publisher 与任务工件
- [ ] E5 待用户:面板补 `fsrapi` CNAME 记录(见 README),之后把 WebUI 设置页与客户端 config remote.json 主用域切换生效
