# JsonConvert 开发指南（项目速查）

> 适用于 IntelliJ / Android Studio 平台的「JSON → Model」代码生成插件。
> 版本：1.0.1，基于 IntelliJ Platform Gradle Plugin 2.7.1 + Kotlin 2.1 + IDEA IC 2024.3。

## 1. 项目结构

```
JsonConvert/
├── build.gradle.kts              # 插件构建/签名/发布配置（intellijPlatform 块）
├── settings.gradle.kts
├── src/main/
│   ├── kotlin/com/kevin/jsonconvert/
│   │   ├── JsonToModelAction.kt   # 入口 Action：解析 JSON → 调用生成器 → 写入文件/当前编辑器
│   │   ├── JsonConvertDialog.kt   # 配置对话框（语言选择 + 各语言专属选项 + 输出位置）
│   │   └── JsonConvertConfig.kt   # 配置数据类 EtsConvertConfig + 枚举 + 持久化(PropertiesComponent)
│   └── resources/META-INF/
│       ├── plugin.xml             # 插件清单（Action 注册、依赖模块）
│       └── pluginIcon.svg
└── key/                          # 签名证书（发布用，勿提交明文密码）
```

## 2. 核心概念

### 2.1 枚举（`JsonConvertConfig.kt`）
- `ModelLanguage`：`ARKTS` / `KOTLIN` / `JAVA` / `DART`（目标语言）。
- `KotlinAnnotationType`：`NONE` / `GSON` / `KOTLINX_BASIC` / `KOTLINX_ADVANCED`。
- `FileNameMode`：`MANUAL`（新文件）/ `AUTO` / `CURRENT_FILE`（插入当前文件）。

### 2.2 配置持久化
`EtsConvertConfig` 通过 `PropertiesComponent` 记住选项（`rememberOptions` 开关）。
新增语言选项时，**必须**同时在 `load()` 和 `save()` 中补充对应 key（`PREFIX = "com.kevin.jsonconvert."`），否则选项不会被记住。

### 2.3 生成流程（`JsonToModelAction.actionPerformed`）
1. 根据当前文件扩展名推断 `defaultLang`（`.dart` → `DART`）。
2. 弹出 `JsonConvertDialog`，用户选择语言与可选项。
3. `generateCode(config)` 按 `targetLanguage` 分派到 `parseJsonToXxx(config)`。
4. 根据语言决定扩展名（`DART → "dart"`），写入新文件或插入当前编辑器。

## 3. Dart（Flutter）生成说明（本次新增）

目标语言 `DART`，生成 Flutter 风格的 Dart model。

### 3.1 对话框选项（仅选择 Dart 时可见）
- **Use final**：字段是否带 `final` 关键字（仅“自定义样式”生效，即未勾选 json_serializable 且未勾选 Simplified）。
  - 勾选（默认）→ 字段均为 `final`，构造参数按 nullable/default 规则为 `required this.x` 或 `this.x = 默认值`。
  - 不勾选 → 字段为可变（`Type x;`）。
- **Nullable Types (add ?)**：所有字段类型加 `?`（可空字段构造参数不带 `required`，保持 null，不强加默认值）。
- **Default Values**：非空字段按类型给默认值（string `""` / int `0` / bool `false` / double `0.0` / List `const []`）；可空字段忽略（保持 null）。
- **Use json_serializable annotation**：
  - 勾选 → **忽略其余自定义选项**（Use final / Nullable / Default Values / Simplified），按库官方类型生成：字段用自然类型（非 `final`、非 `?`，unknown 用 `dynamic`），构造参数全部 `required`，fromJson/toJson 委托给 `_\$XxxFromJson / _\$XxxToJson`。需 `import 'package:json_annotation/json_annotation.dart';` + `part 'xxx.g.dart';` 并运行 build_runner。
  - 不勾选（默认）→ 进入“自定义样式”（见下两项）。
- **Simplified style (no type casting, named fromJson)**：生成“简写版”样式，**优先级最高**（高于 json_serializable 与 Use final）。
  - 勾选 → 字段**非 `final`**、已知类型按可空选项加 `?`、未知类型（`dynamic`，对应 JSON `null`）不加 `?`；
    普通构造 `X({ this.x, ... })`（参数均可选，无 `required`）；
    **命名构造** `X.fromJson(Map<String,dynamic> json)` **直接赋值、不加任何 `as` 强转**（`x = json['x'];`，可空保持 null，非空按类型 `?? 默认值`）；
    `toJson()` 用 `final Map<String,dynamic> data = <String,dynamic>{}; ... return data;` 构建。
  - 不勾选（默认）→ 走“标准自定义样式”。

> **优先级**：`Simplified` > `json_serializable` > `标准自定义样式`。构造方法三种样式都会生成。
> **标准自定义样式规则**：`fromJson` 用构造参数赋值（**不使用级联 `..` 语法**）。非空字段带 `required`（有 Default Values 则给默认值、非 `required`）；可空字段不带 `required`，fromJson 用 `as Type?` 保持 null。

### 3.2 类型映射
| JSON | Dart |
|------|------|
| object | `XxxModel`（递归生成嵌套类，命名规则与其他语言一致：`key` → `XxxModel`） |
| array of object | `List<XxxItemModel>` |
| array of primitive | `List<int/double/bool/String>` 或 `List<dynamic>` |
| number（整数） | `int` |
| number（小数） | `double` |
| boolean | `bool` |
| string | `String` |

### 3.3 关键实现位置
- `JsonConvertConfig.kt`：`dartNullable` / `dartDefaultValues` / `dartUseFinal` / `dartUseJsonSerializable` / `dartSimplifiedStyle` 字段 + load/save（均通过 `rememberOptions` 持久化；`dartGenerateConstructor` 已移除，构造方法始终生成）。
- `JsonConvertDialog.kt`：Dart 专属若干 `checkBox`（`visibleIf(dartRb.selected)`），含 “Simplified style” 复选框。
- `JsonToModelAction.kt`：`parseJsonToDart()` 内 `if (dartUseJsonSerializable) { 库官方 } else if (dartSimplifiedStyle) { 简写版 } else { 标准自定义 }` 三分支 + 辅助 `DartField` / `toSnakeCase()` / `primitiveDartType()`。

## 4. 如何新增一门目标语言（通用步骤）

1. `ModelLanguage` 枚举加一项。
2. `EtsConvertConfig` 加语言专属字段；在 `load()`/`save()` 中补持久化。
3. `JsonConvertDialog` 的 `buttonsGroup` 加 `radioButton`，并在 group("Options") 内加专属 `row`（用 `visibleIf(xxxRb.selected)` 控制显隐）。
4. `JsonToModelAction`：
   - `actionPerformed` 的 `finalExt` when 加一项；
   - `defaultLang` 推断加扩展名映射；
   - `generateCode` 的 when 加一项；
   - 新增 `parseJsonToXxx(config): String`（参考 `parseJsonToDart`，用 `getSafeModelName` 递归处理嵌套对象）。
5. （可选）更新 `plugin.xml` 描述、`build.gradle.kts` 的 `changeNotes`。

## 5. 构建与运行

```bash
./gradlew buildPlugin        # 产出 build/distributions/*.zip，可在 IDE 中 Install Plugin From Disk
./gradlew runIde             # 启动沙盒 IDE 调试
./gradlew verifyPlugin      # 插件兼容性校验
```

依赖：`com.intellij.modules.json`（JSON 语言支持，用于编辑框高亮）。
注意 `local.properties` 需要提供签名/发布 token（见 `build.gradle.kts`）。

## 6. 设计约束与约定
- 所有生成函数统一 `try/catch`，异常时返回 `// Parse Error: ...`，不会让对话框崩溃。
- 嵌套类名去重：`getSafeModelName` 保证同一 JSON 内不出现重复类名（冲突时追加序号）。
- 选项 UI 使用固定标签宽度（`FIXED_LABEL_WIDTH = 80`），避免不同语言可见标签宽度不同造成输入框错位。
- 各语言生成逻辑相互独立，新增语言不影响既有语言行为。
