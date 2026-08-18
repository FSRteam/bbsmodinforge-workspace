"""Build the 300-step chapter skeleton.

Step counts are NOT proportional to label volume -- that would give settings
41 steps and pixel-art 4. They are weighted by teaching value: how much a
beginner must actually *do* to become productive in that area.

Budget discipline: 300 steps total. Core paths (伪装/影片) get the most;
reference-style areas (设置/枪械/图形计算器) get the minimum that still
covers their real UI labels.
"""
import io
import json
import os
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
DOM = json.load(io.open(os.path.join(HERE, "ui-domains.json"), encoding="utf-8"))
KEYS = json.load(io.open(os.path.join(HERE, "keybinds-verified.json"), encoding="utf-8"))
OUT = os.path.join(HERE, "chapter-plan.json")

# stage, title, label domains the steps must quote from, step count
PLAN = [
    # ---------- 第一部：入门（30）----------
    ("入门", "认识 Dashboard 与九大面板", ["dashboard"], 7),
    ("入门", "按键体系与 F9 自查", ["keys_labels", "general"], 6),
    ("入门", "第一次变身：伪装基础", ["morphing", "forms_other"], 7),
    ("入门", "姿势编辑器入门（FK）", ["forms_pose"], 6),
    ("入门", "保存、预设与热键", ["presets", "forms_other"], 4),
    # ---------- 第二部：伪装深入（62）----------
    ("伪装", "形态类型（上）：模型与生物", ["forms_other"], 7),
    ("伪装", "形态类型（下）：方块/物品/文字/广告牌", ["forms_other"], 7),
    ("伪装", "特殊形态：拖影/拉伸/声音/原版粒子", ["forms_other"], 6),
    ("伪装", "IK 链：从痛点到解法", ["forms_model"], 7),
    ("伪装", "IK 进阶：极向量/拉伸/柔软度/权重", ["forms_model"], 7),
    ("伪装", "物理骨链：重力/阻尼/刚度", ["forms_model"], 7),
    ("伪装", "物理进阶：风力/阵风/世界碰撞", ["forms_model"], 6),
    ("伪装", "骨骼约束：角度限制", ["forms_model"], 5),
    ("伪装", "形态键与附魔光效", ["forms_pose"], 5),
    ("伪装", "动作与动画状态机", ["forms_actions"], 5),
    # ---------- 第三部：影片系统（88）----------
    ("影片", "影片编辑器总览与第一个影片", ["film_core"], 7),
    ("影片", "关键帧基础：插入/选择/移动", ["keyframes"], 7),
    ("影片", "关键帧进阶：插值/曲线/批量调值", ["keyframes"], 7),
    ("影片", "回放系统：录制你的动作", ["film_replays"], 7),
    ("影片", "录制组：仅位置/仅旋转/摇杆", ["film_replays"], 6),
    ("影片", "演员控制与行为编辑器", ["film_replays"], 6),
    ("影片", "回放管理：类别/复制/从摄像机生成", ["film_replays"], 6),
    ("影片", "运动路径与洋葱皮", ["film_motion_path"], 6),
    ("影片", "摄像机剪辑（上）：空闲/路径/环绕", ["film_clips"], 7),
    ("影片", "摄像机剪辑（中）：推轨/变焦/注视/抖动", ["film_clips"], 7),
    ("影片", "摄像机剪辑（下）：曲线/追踪/重映射/数学", ["film_clips"], 7),
    ("影片", "剪辑图层、启停与时长调整", ["film_clips"], 6),
    ("影片", "音频剪辑与麦克风录音", ["audio", "film_core"], 5),
    ("影片", "字幕系统", ["film_core"], 4),
    ("影片", "撤销历史与影片详情", ["film_core"], 4),
    # ---------- 第四部：创作工具（74）----------
    ("创作", "模型编辑器入门：骨骼与结构", ["model_editor"], 7),
    ("创作", "模型编辑器进阶：查找/折叠/历史", ["model_editor"], 6),
    ("创作", "纹理管理器与图层", ["textures"], 6),
    ("创作", "像素画编辑器：画笔/填充/选区", ["textures_pixels"], 7),
    ("创作", "纹理进阶：缩放/提取/取色", ["textures"], 5),
    ("创作", "模型方块：放置与属性", ["model_blocks"], 5),
    ("创作", "粒子系统入门：方案与发射率", ["particles"], 7),
    ("创作", "粒子外观：材质/光照/着色", ["particles"], 6),
    ("创作", "粒子运动：形状/空间/生命周期", ["particles"], 7),
    ("创作", "粒子碰撞与事件", ["particles"], 6),
    ("创作", "粒子曲线与 Molang 表达式", ["particles"], 6),
    ("创作", "BBS 枪：弹道/碰撞/命令", ["gun"], 6),
    # ---------- 第五部：导出（26）----------
    ("导出", "FFmpeg 配置与排错", ["video_export"], 5),
    ("导出", "视频导出：F4 手动与 F6 影片", ["video_export"], 7),
    ("导出", "导出参数：分辨率/帧率/编码", ["video_export"], 6),
    ("导出", "批量导出队列", ["video_export"], 4),
    ("导出", "素材导入与格式转换", ["importer"], 4),
    # ---------- 第六部：系统（20）----------
    ("系统", "设置总览：外观/性能/录制", ["settings"], 5),
    ("系统", "主题与皮肤系统", ["skins_themes"], 4),
    ("系统", "插件管理与热重载", ["plugins"], 5),
    ("系统", "实体选择器与图形计算器", ["selectors", "graph"], 3),
    ("系统", "音频/语言编辑器与教程录制选项", ["utility", "settings"], 3),
]

chapters = []
for i, (stage, title, doms, n) in enumerate(PLAN, 1):
    avail = sum(DOM["domains"].get(d, {}).get("count", 0) for d in doms)
    chapters.append(OrderedDict([
        ("order", i),
        ("id", "ch%02d" % i),
        ("stage", stage),
        ("titleZh", title),
        ("labelDomains", doms),
        ("availableLabels", avail),
        ("plannedSteps", n),
        ("status", "skeleton"),
    ]))

total = sum(c["plannedSteps"] for c in chapters)
by_stage = OrderedDict()
for c in chapters:
    s = by_stage.setdefault(c["stage"], {"chapters": 0, "steps": 0})
    s["chapters"] += 1
    s["steps"] += c["plannedSteps"]

doc = OrderedDict([
    ("_meta", OrderedDict([
        ("purpose", "300 步教程的章节骨架；每章限定可引用的真实 UI 文案域"),
        ("totalChapters", len(chapters)),
        ("totalSteps", total),
        ("labelSource", DOM["_meta"]["label_source"]),
        ("labelTotal", DOM["_meta"]["label_total"]),
        ("keybindSource", KEYS["_meta"]["source_file"]),
        ("keybindTotal", KEYS["_meta"]["total"]),
        ("rules", [
            "每步 instruction/userWillSee 只能引用本章 labelDomains 内的真实文案",
            "编辑器快捷键必须写「默认」，并提示可按 F9 查看实际绑定",
            "涉及 F4/F5/F6/B/M/0 时必须说明面板开启或关闭的状态差异",
            "每步都要有男版（高冷）与女版（猫娘）两套台词",
            "台词经 Fish Audio TTS 合成语音；API key 只存本地设置，不入 jar、不写日志",
        ]),
        ("byStage", by_stage),
    ])),
    ("chapters", chapters),
])

with io.open(OUT, "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, indent=2)

print("chapters:", len(chapters), " steps:", total)
print()
for s, v in by_stage.items():
    print("  %-6s %2d 章 %3d 步" % (s, v["chapters"], v["steps"]))
print()
thin = [c for c in chapters if c["availableLabels"] < c["plannedSteps"]]
print("文案量不足的章节:", len(thin))
for c in thin:
    print("  ch%02d %-36s %d labels / %d steps"
          % (c["order"], c["titleZh"], c["availableLabels"], c["plannedSteps"]))
