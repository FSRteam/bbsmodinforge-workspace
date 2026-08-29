# Implement: FSR version update push

## Steps

- [x] 1. Worker:`content:releases` 校验 + GET /api/version + PUT /api/releases + 测试用例(35/35)
- [x] 2. WebUI server 路由(GET/PUT /api/releases 转发)
- [x] 3. WebUI 版本发布页(表单 + jar 取 sha256 + 历史/撤回/恢复/编辑/删除)
- [x] 4. WebUI mock E2E + IAB GUI 验收(发布/撤回带原因/恢复/最新计算排除 revoked 全过)
- [x] 5. 客户端 BBSSettings 分类 fsr_updates + UIValueMap 自定义 widget(手动检查按钮/状态行)
- [x] 6. FSRUpdates 检查器(节流/缓存/版本比较/决策顺序:撤回优先→静默→skip→弹窗)
- [x] 7. UIUpdateOverlayPanel(更新/撤回两变体)+ UIDashboard.onOpen 挂钩
- [x] 8. UpdateInstaller(下载/sha256/原子换 jar/重启提示/启动清理)
- [x] 9. strings en_us + zh_cn
- [x] 10. 客户端编译 + 测试 + FSRUpdatesSourceTest(版本比较数学 + 决策顺序源级断言)
- [x] 11. 部署真实 Worker + 真链路冒烟(ads/news 完整、0.0.11 基线已种)
- [x] 12. spec 沉淀 + journal + 提交(主仓 + worktree)

## 事故记录(deploy seed 覆盖线上内容)

部署本次 worker 时,deploy.mjs 的 seed 探测经 `api()`(返回 `payload.result`)读 KV
values 端点——该端点返回裸值无信封,`payload.result` 为 undefined → 误判 key 缺失 →
把线上 ads/news 覆盖为空。已从客户端磁盘缓存(`runs/client/config/bbs/settings/
film_home_cache/`)完整恢复(ads 3 条/news 3 条,img 键未受影响),并把 seed 探测改为
裸 fetch:200=跳过、404=才种、其他=中止。

## 决策矩阵自查表(全部经 FSRUpdatesSourceTest/worker.test 验证)

| 场景 | 期望 |
| --- | --- |
| 当前=0.0.11,最新 stable 0.0.12 | 弹窗(可跳过) |
| 当前=0.0.12,最新 0.0.12 | 静默,"已是最新" |
| 当前=0.0.13-dev(比线上新),0.0.12 被撤回 | **撤回警告**(静默规则让位) |
| 当前=0.0.13-dev,无 revoked | 静默 |
| 跳过 0.0.12 后最新仍 0.0.12 | 静默 |
| 跳过 0.0.12 后最新 0.0.13 | 弹窗 |
| 0.0.12-beta.1 vs 0.0.12 | stable 更新 |
| 0.0.12-beta.1 vs 0.0.12-beta.2 | beta.2 更新 |
| mandatory 发布 | 弹窗无跳过按钮 |
| 频道=正式,只有 preview 更新 | 静默 |
| 发布无 sha256 | 无 [立即更新],只有 [前往下载] |
| sha256 不匹配 | 报错,不换 jar,part 删除 |
| 更新检查失败 | 静默(手动检查时提示失败),状态行用缓存 |
