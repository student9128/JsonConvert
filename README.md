# JsonConvert

适用于 IntelliJ / Android Studio / DevEco Studio 的 JSON → Model 代码生成插件。

支持一键将 JSON 转换为 **ArkTS / Kotlin / Java / Dart(Flutter)** 模型类，自动推断类型、处理嵌套对象，并支持写入新文件或插入当前文件。

---

## 使用方式

1. 在编辑器中右键，选择 **JSONConvertToModel**（默认快捷键 `⌥⇧J` / `Ctrl+Alt+J`）。

   ![右键菜单](snapshots/snapshot_1.jpg)

2. 在弹出的对话框中粘贴 JSON，选择目标语言与生成选项，点击 **Generate**。

   ![Kotlin 选项](snapshots/snapshot_2.jpg)

---

## 支持的语言与选项

### ArkTS

- 生成 `interface` 或 `class`
- 可空类型（`?`）、`@ObservedV2` / `@Trace`、`Default Values`

   ![ArkTS 选项](snapshots/snapshot_3.jpg)

### Kotlin

- 注解：`None` / `Gson @SerializedName` / `Kotlinx @Serializable`
- 可空类型、默认值、自动 import

### Java

- `Gson @SerializedName`、自动 import、生成 get/set 方法

### Dart (Flutter)

- `final` 字段、可空类型、默认值
- `json_serializable` 官方样式或自定义简化样式

   ![Dart 选项](snapshots/snapshot_4.jpg)

---

## 输出方式

- **New File**：按 `File Name` 生成新文件
- **Current File**：将结果插入当前打开的文件
- 勾选 **Remember Options** 可记住本次选项

---

## 构建与运行

```bash
./gradlew buildPlugin   # 打包插件，产物在 build/distributions/*.zip
./gradlew runIde        # 启动沙盒 IDE 调试
./gradlew verifyPlugin  # 插件兼容性校验
```

---

## 更多说明

- 详细开发/扩展说明见：[JsonConvert_development.md](JsonConvert_development.md)
- 嵌套对象会自动生成子 Model，重名时自动追加序号保证唯一。
- 生成异常时会在结果中返回 `// Parse Error: ...`，不会导致 IDE 崩溃。

---

## 环境

- IntelliJ Platform Gradle Plugin 2.7.1
- Kotlin 2.1
- IDEA IC 2024.3
- 版本：1.0.1
- License：[Apache License 2.0](LICENSE)
