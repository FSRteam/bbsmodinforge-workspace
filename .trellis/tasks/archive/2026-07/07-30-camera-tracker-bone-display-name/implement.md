# 追踪器骨骼名显示一致性 - 实现计划

## 步骤

### Step 1: UIAnchorKeyframeFactory.java - 修改 displayAttachments()
- 文件: `new/src/client/java/mchorse/bbs_mod/ui/film/clips/UIAnchorKeyframeFactory.java`
- 约 line 69-112 的 `displayAttachments()` 方法
- 在 label 循环中添加 else 分支：

```java
// 原有代码
for (int i = 0; i < labels.size(); i++)
{
    String label = labels.get(i);
    Form path = FormUtils.getForm(form, label);
    if (path != null)
    {
        labels.set(i, path.getTrackName(label));
    }
    // 新增：对普通骨骼使用 BoneHierarchy 的显示名称
    else
    {
        labels.set(i, FormUtilsClient.getBoneLabel(form, label));
    }
}
```

### 需要添加的 import
```java
import mchorse.bbs_mod.forms.FormUtilsClient;
```

### 验证命令
```bash
cd new && ./gradlew compileClientJava
# 手动检查追踪器骨骼名称与 Pose 轨道一致
```
