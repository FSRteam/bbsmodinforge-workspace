# FSR version update push (worker + webui + client)

## Goal

在现有 film-home 推送体系上新增"版本推送":推送台可发布/撤回 FSR 更新(正式/预览频道,
MD 更新说明,sha256,jar 下载 URL);客户端每次打开 BBS 异步检查更新,按用户设置静默或
在首屏加载完成后弹窗,支持跳过(等下一版)、撤回警告、强制更新,并支持游戏内自动下载
安装(哈希校验 + 换 jar + 重启提示)。一次性交付全部范围。

## Requirements(用户已拍板)

1. Worker `GET /api/version` 公开返回发布列表;`PUT /api/releases`(Bearer)全量替换,
   校验版本号/频道/https URL/sha256/数量(上限 **20 条**),记录**更新日期**。
2. WebUI 新"版本发布"页:发布表单(版本、频道、日期、下载 URL、sha256 可从本地 jar
   自动计算、MD 编辑器+预览、强制更新开关)+ 历史列表(撤回/恢复/编辑/删除)。
3. 客户端每次打开 BBS(`UIDashboard.onOpen`)触发检查,按频率设置节流(每次/每天/
   每周/两周/每月,默认每周);失败静默 + 磁盘缓存兜底。
4. 版本决策顺序(客户端权威):**撤回检查优先**(当前版本在 revoked 列表 → 警告弹窗,
   即使本地比线上新也不静默)→ 本地≥线上 → 静默 → 被跳过版本 → 静默(跳过=等到
   **下一个版本**再提醒)→ 有更新 → 弹窗(MD 说明,mandatory 不给跳过按钮)。
5. 设置项(BBSSettings 新分类):接受更新开关、频道(正式/预览)、检查频率、手动
   [检查更新] 按钮 + 上次检查状态行。
6. 自动安装:下载(≤100MB)→ sha256 校验(不匹配删除重报错)→ 原子换 jar(旧 jar 改名
   `.pre-update`,下次启动清理)→ 提示重启;仅 production 环境 + 发布带 sha256 时提供;
   否则只给 [前往下载](https,系统浏览器)。

## Non-goals

- 不做账号/遥测;检查请求不携带用户数据。
- 不自动降级;本地开发环境(FMLEnvironment.production=false)只通知不安装。
- 广告/新闻推送链路不动。

## Acceptance Criteria

- [ ] worker.test.mjs 原有 19 项 + 新增 releases 用例全过;mock E2E 发布→撤回→检查链路可跑。
- [ ] WebUI 浏览器 GUI 验收:发布(含 jar 取 sha256)、撤回、恢复、编辑、删除、>20 条拒绝。
- [ ] `compileClientJava` + filmEditor/uiInputMailbox 测试通过。
- [ ] 撤回警告不被"本地更新→静默"规则吞掉;跳过版本后同版不再弹、新版恢复弹。
- [ ] mandatory 弹窗无跳过按钮;非 mandatory 有跳过且持久化。
- [ ] 客户端只在 sha256 存在且 production 时显示 [立即更新];下载哈希不符时报错不安装。
