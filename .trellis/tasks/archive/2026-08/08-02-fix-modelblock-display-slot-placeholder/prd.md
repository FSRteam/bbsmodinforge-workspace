# 修复模型方块显示槽空槽被解码为占位表单覆盖默认显示

## Goal

模型方块物品的显示目标（default/third-person/first-person/inventory 四槽）在未设置时，被
`ModelProperties.fromData` 经 `MapType.getMap`（缺失返回空 MapType）→ `FormUtils.fromData`
（当前仓 decode 失败返回非空 `MissingForm`）解码成非空占位表单，导致 `getForm(ItemDisplayContext)`
不再回退到默认 form：手持一/三人称与物品栏渲染空白、挪动或中键拾取后重新解析即"回弹"。修正空槽解码为 null。

## Requirements

- 未设置的显示槽在 `fromData` 后必须为 `null`（与 `toData` 写 null 对称），使 `getForm(mode)` 回退到默认 form。
- 保持 FSR 仓 `FormUtils.fromData` 的 `MissingForm` 数据保留特性不被全局回退（其他消费方如 morph/回放依赖它）。
- 修复点限 `ModelProperties.fromData` 四个 form 槽的读取默认值，不触碰 transforms、不改 `FormUtils`。

## Acceptance Criteria

- [x] `ModelProperties.fromData` 中四个 form 槽改读 `data.getMap(key, null)`，未设置槽解码为 `null`。
- [x] 运行时已验证：未设置槽位时 `getForm(mode)` 正确回退到默认 form，手持一/三人称、物品栏、挪动/中键拾取均正常。
- [x] `compileJava` 编译通过。
- [x] 改动已以单提交 `05473fd6` 落在 `new/` 分支 `work/mcaxolotl-ui-defect-batch`（原 `fix/bugfix-batch`，PR #17）。

## Notes

- 根因对照：参考仓 `FormUtils.fromData` 在 decode 失败时返回 `null`，当前仓返回 `new MissingForm(data)`；
  结合 `MapType.getMap(missing)` 返回空 MapType，空槽被解析成非空占位。
- 团队观测到的"bbs:anchor"是空槽占位（MissingForm，存储 id 实为 `missing:form`）在编辑器/渲染中的呈现，
  功能结果与"显示目标被占位"一致。