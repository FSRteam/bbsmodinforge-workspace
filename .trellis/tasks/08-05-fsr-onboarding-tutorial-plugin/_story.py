"""Build the narrative outline (剧情目录) across all chapters.

The user asked for a story spine, not a feature list: each 幕 (act) has a
concrete deliverable the learner walks away with, so 300 steps read as
"building toward something" instead of a pile of button presses.

Merges the written skeletons with the still-planned chapters so the whole
arc is visible in one place, and reports which chapters exist as real steps.
"""
import io
import json
import os
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "story-outline.json")

SKELETONS = [
    "chapters-01-10-skeleton.json",
    "chapters-11-20-skeleton.json",
    "chapters-19-25-skeleton.json",
]

written = {}
for name in SKELETONS:
    path = os.path.join(HERE, name)
    if not os.path.exists(path):
        continue
    doc = json.load(io.open(path, encoding="utf-8"))
    for c in doc["chapters"]:
        written[c["order"]] = {
            "id": c["id"],
            "titleZh": c["titleZh"],
            "steps": len(c["steps"]),
            "file": name,
        }

# act -> (mission the learner completes, narrative hook, chapter titles)
ACTS = OrderedDict([
    ("第一幕 · 入门", {
        "mission": "让角色站到镜头前：打开工作台、变身、摆出第一个姿势并存下来",
        "stakes": "从「装了个 mod 不知道干什么」到「我能让角色变成任何样子」",
        "chapters": [1, 2, 3, 4, 5],
    }),
    ("第二幕 · 塑形", {
        "mission": "做出一个能一键召唤的完整角色：正确的关节、会飘的头发、会变的表情",
        "stakes": "从「能变身」到「这个角色是活的」——头发会甩、关节不会反折、表情会变",
        "chapters": [6, 7, 8, 9, 10, 11, 12, 13, 14, 15],
    }),
    ("第三幕 · 动起来", {
        "mission": "拍出第一个 10 秒镜头：录下动作、修好曲线、让参数随时间变化",
        "stakes": "从「静态雕像」到「会演戏」——这一幕的转折是发现「什么都能打关键帧」",
        "chapters": [16, 17, 18, 19, 20, 21, 22],
    }),
    ("第四幕 · 调度", {
        "mission": "把一个人的动作变成一场戏：分组录制、控制演员、让世界配合演出",
        "stakes": "从「录了一段动作」到「导演一场戏」——学会看见时间（运动路径与洋葱皮）",
        "chapters": [23, 24, 25, 26, 27],
    }),
    ("第五幕 · 运镜", {
        "mission": "给那场戏配上电影级镜头运动：环绕、推轨、跟随、手持抖动、字幕与配音",
        "stakes": "从「有画面」到「有镜头语言」",
        "chapters": [28, 29, 30, 31, 32, 33],
    }),
    ("第六幕 · 造物", {
        "mission": "亲手画一张贴图，并把现成模型配置成自己的角色（含挂物品、绑盔甲、隐藏骨骼）",
        "stakes": "从「只会用别人做好的东西」到「贴图我自己画、模型我自己配」",
        "note": "修正：FSR 的模型编辑器不能做几何。类注释 UIModelEditorPanel.java:84-88 明确「Models are assets, so create/rename/delete are intentionally off」，且零处 addCube/createBone/setUV。几何要在 Blockbench 侧做，模型编辑器只改 ModelConfig（运行时配置）。所以本幕的「造物」= 画贴图 + 配模型，不是建模。",
        "chapters": [34, 35, 36, 37, 38, 39],
    }),
    ("第七幕 · 特效", {
        "mission": "做出属于自己的粒子特效与弹道物件",
        "stakes": "从「静态场景」到「有魔法、有爆炸、有子弹」",
        "chapters": [40, 41, 42, 43, 44, 45],
    }),
    ("第八幕 · 交付", {
        "mission": "把作品导出成视频文件发出去，并把整套环境调成自己顺手的样子",
        "stakes": "从「游戏里好看」到「拿得出手的成品」",
        "chapters": [46, 47, 48, 49, 50],
    }),
])

# planned chapters not yet written as steps
PLANNED = {
    # 第四幕 · 调度
    23: "录制组：只录位置还是连旋转一起录",
    24: "演员控制：接管角色与轨道摄像机",
    25: "行为编辑器：让世界配合演出",
    26: "回放管理：类别、复制、从摄像机生成、动画转姿势",
    27: "运动路径与洋葱皮：看见时间",
    # 第五幕 · 运镜
    28: "摄像机剪辑（上）：空闲、路径、环绕",
    29: "摄像机剪辑（中）：推轨、变焦、注视、抖动",
    30: "摄像机剪辑（下）：曲线、追踪、重映射、数学",
    31: "剪辑图层、启停、时长与淡入淡出",
    32: "音频剪辑与麦克风录音",
    33: "字幕系统",
    # 第六幕 · 造物
    34: "像素画编辑器（上）：六种工具与前景背景色",
    35: "像素画编辑器（下）：图层、选区与合并",
    36: "纹理管理器：导入、缩放、提取",
    37: "模型配置（上）：绑纹理、隐藏骨骼、缩放",
    38: "模型配置（下）：挂手持物品、绑盔甲、朝向注视",
    39: "模型方块：把模型放进世界",
    # 第七幕 · 特效
    40: "粒子系统入门：方案与发射率",
    41: "粒子外观：材质、光照、着色",
    42: "粒子运动：形状、空间、生命周期",
    43: "粒子碰撞与事件",
    44: "粒子曲线与 Molang 表达式",
    45: "BBS 枪：弹道、碰撞、命令",
    # 第八幕 · 交付
    46: "FFmpeg 配置与排错",
    47: "视频导出：F4 手动与 F6 影片",
    48: "导出参数与批量队列",
    49: "素材导入与格式转换",
    50: "设置、主题、插件与教程录制选项",
}

acts_out = OrderedDict()
total_written_steps = 0
for act, info in ACTS.items():
    rows = []
    for order in info["chapters"]:
        if order in written:
            w = written[order]
            rows.append(OrderedDict([
                ("order", order),
                ("titleZh", w["titleZh"]),
                ("steps", w["steps"]),
                ("status", "written"),
            ]))
            total_written_steps += w["steps"]
        else:
            rows.append(OrderedDict([
                ("order", order),
                ("titleZh", PLANNED.get(order, "(待定)")),
                ("steps", None),
                ("status", "planned"),
            ]))
    acts_out[act] = OrderedDict([
        ("mission", info["mission"]),
        ("stakes", info["stakes"]),
        ("chapters", rows),
    ])

doc = OrderedDict([
    ("_meta", OrderedDict([
        ("purpose", "剧情目录：六幕结构，每幕有一个学习者真正做出来的成品"),
        ("narrativeRule", "每幕开头给出本幕要做出的东西，每章是朝它推进的一步；猫娘版有情绪起伏，高冷版只做简短确认"),
        ("writtenChapters", len(written)),
        ("writtenSteps", total_written_steps),
        ("plannedChapters", len(PLANNED)),
        ("targetSteps", 300),
    ])),
    ("acts", acts_out),
])

with io.open(OUT, "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, indent=2)

print("已写章节 :", len(written), " 已写步数 :", total_written_steps)
print("待写章节 :", len(PLANNED))
print()
for act, info in acts_out.items():
    done = sum(1 for r in info["chapters"] if r["status"] == "written")
    print("%s  (%d/%d 章已写)" % (act, done, len(info["chapters"])))
    print("   目标: %s" % info["mission"])
    for r in info["chapters"]:
        mark = "%3d步" % r["steps"] if r["steps"] else " 待写"
        print("     %2d. %-42s %s" % (r["order"], r["titleZh"][:40], mark))
    print()
