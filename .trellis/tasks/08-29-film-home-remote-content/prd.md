# FSR 影片首页广告/新闻远程内容(CF Worker 拉取 + 本地 Node 推送 WebUI)

## Goal

把 `08-16-fsr-film-home-redesign` 交付的影片首页内容(广告 4:3 轮播 + 新闻条 + Markdown 详情)从本地 JSON 升级为"远程可推送":运营在本地 WebUI 编辑,经 Cloudflare Worker 分发,客户端异步拉取,失败逐级回退。

## 需求(用户原话归档)

1. 最新版影片首页的广告和新闻改为 worker 拉取/推送;写一个本地启动的 Node WebUI 实现推送;用户已提供 CF API key 并要求帮忙部署。
2. 广告按设计图片规格,限制 4:3 比例(WebUI 上传时校验,可自动居中裁切)。
3. 新闻为 Markdown 格式(正文 markdown 字段,WebUI 提供编辑+预览)。
4. FSR 新闻打开的详情页自适应窗口边框;超出内容可拖动翻页(滚动)。
5. 拉取失败默认使用当前本地内容(现有本地 JSON/默认资源)。
6. 拉取有缓存:失败时使用上次成功的数据。
7. 未拉取完的分区显示加载图标,不得卡住线程(全部异步)。

## Acceptance Criteria

- [ ] 部署后 Worker URL 可公开 GET `/api/ads` `/api/news` `/img/<key>`;无 token 的 PUT 被拒。
- [ ] WebUI 推送后,客户端进入影片首页能显示远程内容(远程 URL 图片流式加载)。
- [ ] 断网/Worker 不可达:客户端显示上次成功缓存;无缓存时显示本地默认;全程 UI 不卡顿。
- [ ] 新闻正文超长时详情弹窗可滚轮/拖动滚动条/拖动内容翻页,弹窗尺寸随窗口自适应。
- [ ] 广告图片非 4:3 时 WebUI 提示并支持一键居中裁切为 4:3。

## 非目标

- 不改绘制单(commissions)的本地逻辑;不做多账号/权限体系;不做 CDN 自定义域名。

## Notes

- 客户端代码位于 `new/` 仓库 worktree `.worktrees/fsr-film-home`(分支 `fsr-film-home-redesign`)。
- CF API key 等敏感信息只进 gitignore 的本地配置,不进仓库。
