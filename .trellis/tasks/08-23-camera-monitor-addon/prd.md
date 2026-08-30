# 任务:多机位导播监视器插件(bbs-camera-addon)

> 交接文档。本文件供下一个 AI/开发者无缝接续,包含全部上下文。
> 更新时间:2026-08-23。

## 1. 背景与目标

用户("xue snow",非程序员,要求大白话沟通)在 FSR(BBSFSR,mchorse bbs_mod 的
NeoForge 1.21.1 分支,主工程位于 `new/`)上想要"摄像机"能力,最终形态:

1. 场景里可摆放多台**摄像机实体**,每台有名字、FOV;
2. **影片编辑器内多画面**:主画面保持自由飞行不受影响,其余是各摄像机的实时画面(画中画/监视器);
3. 监视器数量可编辑,**Blender 式切割布局**(添加=把选中的切成两半),可拖拽;
4. **每台监视器独立设置**:运动路径、洋葱皮、相机模式(画面来源)、下方按钮条可见性、音频波形可视化、右上角伪装(角色)名称显示;逐台有光影暂不可行(Iris 整帧一条管线,字段预留);
5. (未开始)**每台摄像机有自己的镜头轨道**(FSR 关键帧格式),影片播放时所有监视器跟随同一播放头同步运镜;
6. (未开始)切镜标记(Blender marker 式:时间线上决定某段主画面是哪台);
7. 策略:**先独立插件验证,成熟后整合进 `new/` 主工程**(阶段三,未开始)。

参考物:Blender 摄像机机制(scene.camera 活动相机、标记绑定相机切换 `BKE_scene_camera_switch_find`、焦距/传感器/裁剪参数)。源码已克隆到工作区:
`temp/blender-src`(稀疏检出 camera.cc / scene.cc / rna_camera.cc)、
`temp/ref-cameramod`(TGGamesYT/cameramod,MC 第二视角离屏渲染参照)。

## 2. 工程与构建

- 工程:`D:\bbsinneoforge\bbsmodinforge\bbsmodinforge\bbs-camera-addon`
- 产物:`bbs-camera-addon\build\libs\bbs-camera-addon-0.1.0.jar`(连同 `new\build\libs\BBS_FSR_beta_v0.0.11.jar` 一起放进玩家 mods 文件夹)
- 版本:MC 1.21.1 / NeoForge 21.1.222 / Java 21 / BBSFSR 0.0.11(compileOnly 本地 jar)
- 构建:`gradlew.bat build`;校验任务 verifyBbsCoreJar 钉住用到的 BBS 入口类
- 用户游戏实例在 PCL 启动器下(不在本工作区),靠用户手动换 jar 实测反馈

## 3. 阶段与验收

- 阶段一(MVP):实体+指令 ✅;画中画管线 ✅;~~每台轨道同步~~ ❌ 未做
- 阶段二(导播):切割布局/配置界面/逐台开关/拖拽/点击 ✅;音频波形 ✅;伪装名 ✅;黑边/裁切 ✅;切镜标记 ❌ 未做
- 阶段三(整合进 new/):❌ 未开始
