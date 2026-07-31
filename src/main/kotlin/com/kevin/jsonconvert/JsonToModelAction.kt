package com.kevin.jsonconvert

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.NotNull

class JsonToModelAction : AnAction() {

    override fun actionPerformed(@NotNull event: AnActionEvent) {
        val project = event.project ?: return

        // 1. 检测当前文件类型
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val extension = virtualFile?.extension?.lowercase() ?: ""
        val defaultLang = when (extension) {
            "kt" -> ModelLanguage.KOTLIN
            "java" -> ModelLanguage.JAVA
            "ets", "ts" -> ModelLanguage.ARKTS
            "dart" -> ModelLanguage.DART
            else -> ModelLanguage.ARKTS
        }

        val dialog = JsonConvertDialog(project, defaultLang)
        if (dialog.showAndGet()) {
            val config = dialog.config
            val code = generateCode(config)

            val finalExt = when (config.targetLanguage) {
                ModelLanguage.ARKTS -> "ets"
                ModelLanguage.KOTLIN -> "kt"
                ModelLanguage.JAVA -> "java"
                ModelLanguage.DART -> "dart"
            }

            if (config.fileNameMode == FileNameMode.CURRENT_FILE) {
                insertIntoCurrentEditor(project, event, code)
            } else {
                val fileName = if (config.manualFileName.endsWith(".$finalExt"))
                    config.manualFileName else "${config.manualFileName}.$finalExt"
                createFile(project, event, fileName, code, finalExt)
            }
        }
    }

    private fun generateCode(config: EtsConvertConfig): String {
        return when (config.targetLanguage) {
            ModelLanguage.ARKTS -> parseJsonToArkTS(config)
            ModelLanguage.KOTLIN -> parseJsonToKotlin(config)
            ModelLanguage.JAVA -> parseJsonToJava(config)
            ModelLanguage.DART -> parseJsonToDart(config)
        }
    }

    private fun parseJsonToArkTS(config: EtsConvertConfig): String {
        try {
            val rootElement = JsonParser.parseString(config.jsonContent)
            val sb = StringBuilder()
            val generatedModelNames = mutableSetOf<String>()

            val actualUseOptional = if (config.generateAsClass) config.useOptionalFields else false
            val actualUseObservedV2 = if (config.generateAsClass) config.useObservedV2 else false
            val actualAddDefault = if (config.generateAsClass) config.addDefaultValues else false

            fun getSafeModelName(baseName: String): String {
                var candidate = baseName.replaceFirstChar { it.uppercase() } + "Model"
                if (candidate == config.modelName && generatedModelNames.isNotEmpty()) {
                    candidate = baseName.replaceFirstChar { it.uppercase() } + "InfoModel"
                }
                var finalName = candidate
                var count = 1
                while (generatedModelNames.contains(finalName)) {
                    finalName = candidate.replace("Model", "") + "${count++}Model"
                }
                return finalName
            }

            fun generateModel(className: String, element: JsonElement) {
                if (generatedModelNames.contains(className)) return
                generatedModelNames.add(className)

                val obj = when {
                    element.isJsonObject -> element.asJsonObject
                    element.isJsonArray && element.asJsonArray.size() > 0 && element.asJsonArray[0].isJsonObject ->
                        element.asJsonArray[0].asJsonObject
                    else -> null
                } ?: return

                if (actualUseObservedV2) sb.append("@ObservedV2\n")
                val keyword = if (config.generateAsClass) "class" else "interface"
                sb.append("export $keyword $className {\n")

                val nestedTasks = mutableListOf<Pair<String, JsonElement>>()

                obj.entrySet().forEach { entry ->
                    val key = entry.key
                    val value = entry.value

                    if (actualUseObservedV2) sb.append("  @Trace ") else sb.append("  ")

                    var typeName = "ESObject"
                    var defaultValue = "null"

                    when {
                        value.isJsonObject -> {
                            typeName = getSafeModelName(key)
                            defaultValue = "new $typeName()"
                            nestedTasks.add(typeName to value)
                        }
                        value.isJsonArray -> {
                            val array = value.asJsonArray
                            if (array.size() > 0 && array[0].isJsonObject) {
                                val innerName = getSafeModelName(key.replace("List", "") + "Item")
                                typeName = "$innerName[]"
                                defaultValue = "[]"
                                nestedTasks.add(innerName to array[0])
                            } else {
                                typeName = "ESObject[]"
                                defaultValue = "[]"
                            }
                        }
                        value.isJsonPrimitive -> {
                            val p = value.asJsonPrimitive
                            when {
                                p.isNumber -> { typeName = "number"; defaultValue = "0" }
                                p.isBoolean -> { typeName = "boolean"; defaultValue = "false" }
                                else -> { typeName = "string"; defaultValue = "\"\"" }
                            }
                        }
                    }

                    val optional = if (actualUseOptional) "?" else ""
                    sb.append("$key$optional: $typeName")
                    if (actualAddDefault) sb.append(" = $defaultValue")
                    sb.append(";\n")
                }
                sb.append("}\n\n")
                nestedTasks.forEach { (subName, subEl) -> generateModel(subName, subEl) }
            }

            generateModel(config.modelName, rootElement)
            return sb.toString()
        } catch (e: Exception) {
            return "// Parse Error: ${e.message}"
        }
    }

    private fun parseJsonToKotlin(config: EtsConvertConfig): String {
        try {
            val rootElement = JsonParser.parseString(config.jsonContent)
            val sb = StringBuilder()
            val generatedModelNames = mutableSetOf<String>()

            if (config.kotlinAutoImport) {
                when (config.kotlinAnnotation) {
                    KotlinAnnotationType.GSON ->
                        sb.append("import com.google.gson.annotations.SerializedName\n")
                    KotlinAnnotationType.KOTLINX_BASIC ->
                        sb.append("import kotlinx.serialization.Serializable\n")
                    KotlinAnnotationType.KOTLINX_ADVANCED ->
                        sb.append("import kotlinx.serialization.Serializable\nimport kotlinx.serialization.SerialName\n")
                    else -> {}
                }
                sb.append("\n")
            }

            fun getSafeModelName(baseName: String): String {
                var candidate = baseName.replaceFirstChar { it.uppercase() } + "Model"
                if (candidate == config.modelName && generatedModelNames.isNotEmpty()) {
                    candidate = baseName.replaceFirstChar { it.uppercase() } + "InfoModel"
                }
                var finalName = candidate
                var count = 1
                while (generatedModelNames.contains(finalName)) {
                    finalName = candidate.replace("Model", "") + "${count++}Model"
                }
                return finalName
            }

            fun generateModel(className: String, element: JsonElement) {
                if (generatedModelNames.contains(className)) return
                generatedModelNames.add(className)

                val obj = when {
                    element.isJsonObject -> element.asJsonObject
                    element.isJsonArray && element.asJsonArray.size() > 0 && element.asJsonArray[0].isJsonObject ->
                        element.asJsonArray[0].asJsonObject
                    else -> null
                } ?: return

                if (config.kotlinAnnotation == KotlinAnnotationType.KOTLINX_BASIC
                    || config.kotlinAnnotation == KotlinAnnotationType.KOTLINX_ADVANCED) sb.append("@Serializable\n")
                sb.append("data class $className(\n")

                val nestedTasks = mutableListOf<Pair<String, JsonElement>>()
                val entries = obj.entrySet().toList()

                entries.forEachIndexed { index, entry ->
                    val key = entry.key
                    val value = entry.value

                    if (config.kotlinAnnotation == KotlinAnnotationType.GSON) sb.append("    @SerializedName(\"$key\")\n")
                    if (config.kotlinAnnotation == KotlinAnnotationType.KOTLINX_ADVANCED) sb.append("    @SerialName(\"$key\")\n")

                    var typeName = "Any"
                    var defaultValue = "null"

                    when {
                        value.isJsonObject -> {
                            typeName = getSafeModelName(key)
                            defaultValue = "$typeName()"
                            nestedTasks.add(typeName to value)
                        }
                        value.isJsonArray -> {
                            val array = value.asJsonArray
                            if (array.size() > 0 && array[0].isJsonObject) {
                                val innerName = getSafeModelName(key.replace("List", "") + "Item")
                                typeName = "List<$innerName>"
                                defaultValue = "emptyList()"
                                nestedTasks.add(innerName to array[0])
                            } else {
                                typeName = "List<Any>"
                                defaultValue = "emptyList()"
                            }
                        }
                        value.isJsonPrimitive -> {
                            val p = value.asJsonPrimitive
                            when {
                                p.isNumber -> { typeName = "Double"; defaultValue = "0.0" }
                                p.isBoolean -> { typeName = "Boolean"; defaultValue = "false" }
                                else -> { typeName = "String"; defaultValue = "\"\"" }
                            }
                        }
                    }

                    // 可空类型：所有字段加 ?，默认值统一为 null
                    val finalType = if (config.kotlinNullable) "$typeName?" else typeName
                    val finalDefault = if (config.kotlinNullable) "null" else defaultValue

                    sb.append("    val $key: $finalType")
                    if (config.kotlinDefaultValues) sb.append(" = $finalDefault")
                    if (index < entries.size - 1) sb.append(",")
                    sb.append("\n")
                }
                sb.append(")\n\n")
                nestedTasks.forEach { (subName, subEl) -> generateModel(subName, subEl) }
            }

            generateModel(config.modelName, rootElement)
            return sb.toString()
        } catch (e: Exception) {
            return "// Parse Error: ${e.message}"
        }
    }

    private fun parseJsonToJava(config: EtsConvertConfig): String {
        try {
            val rootElement = JsonParser.parseString(config.jsonContent)
            val sb = StringBuilder()
            val generatedModelNames = mutableSetOf<String>()

            if (config.javaAutoImport) {
                if (config.javaUseSerializedName) sb.append("import com.google.gson.annotations.SerializedName;\n")
                sb.append("import java.util.List;\n\n")
            }

            fun getSafeModelName(baseName: String): String {
                var candidate = baseName.replaceFirstChar { it.uppercase() } + "Model"
                if (candidate == config.modelName && generatedModelNames.isNotEmpty()) {
                    candidate = baseName.replaceFirstChar { it.uppercase() } + "InfoModel"
                }
                var finalName = candidate
                var count = 1
                while (generatedModelNames.contains(finalName)) {
                    finalName = candidate.replace("Model", "") + "${count++}Model"
                }
                return finalName
            }

            fun generateModel(className: String, element: JsonElement) {
                if (generatedModelNames.contains(className)) return
                generatedModelNames.add(className)

                val obj = when {
                    element.isJsonObject -> element.asJsonObject
                    element.isJsonArray && element.asJsonArray.size() > 0 && element.asJsonArray[0].isJsonObject ->
                        element.asJsonArray[0].asJsonObject
                    else -> null
                } ?: return

                sb.append("public class $className {\n")
                val nestedTasks = mutableListOf<Pair<String, JsonElement>>()
                val fields = mutableListOf<Pair<String, String>>() // key -> typeName

                obj.entrySet().forEach { entry ->
                    val key = entry.key
                    val value = entry.value

                    if (config.javaUseSerializedName) sb.append("    @SerializedName(\"$key\")\n")

                    var typeName = "Object"
                    when {
                        value.isJsonObject -> {
                            typeName = getSafeModelName(key)
                            nestedTasks.add(typeName to value)
                        }
                        value.isJsonArray -> {
                            val array = value.asJsonArray
                            if (array.size() > 0 && array[0].isJsonObject) {
                                val innerName = getSafeModelName(key.replace("List", "") + "Item")
                                typeName = "List<$innerName>"
                                nestedTasks.add(innerName to array[0])
                            } else {
                                typeName = "List<Object>"
                            }
                        }
                        value.isJsonPrimitive -> {
                            val p = value.asJsonPrimitive
                            typeName = when {
                                p.isNumber -> "double"
                                p.isBoolean -> "boolean"
                                else -> "String"
                            }
                        }
                    }
                    fields.add(key to typeName)
                }

                // 字段声明
                if (config.javaUseGetSet) {
                    fields.forEach { (key, typeName) ->
                        sb.append("    private $typeName $key;\n")
                    }
                    sb.append("\n")
                    // getter / setter
                    fields.forEach { (key, typeName) ->
                        val cap = key.replaceFirstChar { it.uppercase() }
                        sb.append("    public $typeName get$cap() {\n")
                        sb.append("        return $key;\n")
                        sb.append("    }\n\n")
                        sb.append("    public void set$cap($typeName $key) {\n")
                        sb.append("        this.$key = $key;\n")
                        sb.append("    }\n\n")
                    }
                } else {
                    fields.forEach { (key, typeName) ->
                        sb.append("    public $typeName $key;\n")
                    }
                }
                sb.append("}\n\n")
                nestedTasks.forEach { (subName, subEl) -> generateModel(subName, subEl) }
            }

            generateModel(config.modelName, rootElement)
            return sb.toString()
        } catch (e: Exception) {
            return "// Parse Error: ${e.message}"
        }
    }

    private data class DartField(
        val key: String,
        val typeName: String,
        val defaultLiteral: String?,
        val isObject: Boolean,
        val isListOfObjects: Boolean,
        val innerName: String?
    )

    private fun toSnakeCase(input: String): String {
        return input.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
    }

    private fun primitiveDartType(p: com.google.gson.JsonPrimitive): String {
        return when {
            p.isBoolean -> "bool"
            p.isNumber -> {
                val numStr = p.asNumber.toString()
                if (numStr.matches(Regex("^-?\\d+$"))) "int" else "double"
            }
            else -> "String"
        }
    }

    private fun primitiveDartTypeWithDefault(p: com.google.gson.JsonPrimitive): Pair<String, String?> {
        return when {
            p.isBoolean -> "bool" to "false"
            p.isNumber -> {
                val numStr = p.asNumber.toString()
                if (numStr.matches(Regex("^-?\\d+$"))) "int" to "0" else "double" to "0.0"
            }
            else -> "String" to "\"\""
        }
    }

    private fun parseJsonToDart(config: EtsConvertConfig): String {
        try {
            val rootElement = JsonParser.parseString(config.jsonContent)
            val sb = StringBuilder()
            val generatedModelNames = mutableSetOf<String>()

            // json_serializable 模式所需的 import / part 指令
            if (config.dartUseJsonSerializable) {
                sb.append("import 'package:json_annotation/json_annotation.dart';\n\n")
                val partName = toSnakeCase(config.manualFileName.substringBefore(".")) + ".g.dart"
                sb.append("part '$partName';\n\n")
            }

            fun getSafeModelName(baseName: String): String {
                var candidate = baseName.replaceFirstChar { it.uppercase() } + "Model"
                if (candidate == config.modelName && generatedModelNames.isNotEmpty()) {
                    candidate = baseName.replaceFirstChar { it.uppercase() } + "InfoModel"
                }
                var finalName = candidate
                var count = 1
                while (generatedModelNames.contains(finalName)) {
                    finalName = candidate.replace("Model", "") + "${count++}Model"
                }
                return finalName
            }

            fun generateModel(className: String, element: JsonElement) {
                if (generatedModelNames.contains(className)) return
                generatedModelNames.add(className)

                val obj = when {
                    element.isJsonObject -> element.asJsonObject
                    element.isJsonArray && element.asJsonArray.size() > 0 && element.asJsonArray[0].isJsonObject ->
                        element.asJsonArray[0].asJsonObject
                    else -> null
                } ?: return

                if (config.dartUseJsonSerializable) {
                    sb.append("@JsonSerializable(explicitToJson: true)\n")
                }
                sb.append("class $className {\n")

                val nestedTasks = mutableListOf<Pair<String, JsonElement>>()
                val fields = mutableListOf<DartField>()

                obj.entrySet().forEach { entry ->
                    val key = entry.key
                    val value = entry.value

                    var typeName = "dynamic"
                    var defaultLiteral: String? = null
                    var isObject = false
                    var isListOfObjects = false
                    var innerName: String? = null

                    when {
                        value.isJsonObject -> {
                            typeName = getSafeModelName(key)
                            isObject = true
                            nestedTasks.add(typeName to value)
                        }
                        value.isJsonArray -> {
                            val array = value.asJsonArray
                            if (array.size() > 0 && array[0].isJsonObject) {
                                innerName = getSafeModelName(key.replace("List", "") + "Item")
                                typeName = "List<$innerName>"
                                defaultLiteral = "const []"
                                isListOfObjects = true
                                nestedTasks.add(innerName to array[0])
                            } else {
                                val innerType = if (array.size() > 0 && array[0].isJsonPrimitive) {
                                    primitiveDartType(array[0].asJsonPrimitive)
                                } else "dynamic"
                                typeName = "List<$innerType>"
                                defaultLiteral = "const []"
                            }
                        }
                        value.isJsonPrimitive -> {
                            val (t, d) = primitiveDartTypeWithDefault(value.asJsonPrimitive)
                            typeName = t
                            defaultLiteral = d
                        }
                    }

                    fields.add(DartField(key, typeName, defaultLiteral, isObject, isListOfObjects, innerName))
                }

                if (config.dartUseJsonSerializable) {
                    // ===== json_serializable 模式：忽略其余自定义选项，按库官方类型生成，序列化交给 code-gen =====
                    fields.forEach { f ->
                        val t = if (f.typeName == "dynamic") "dynamic"
                        else if (config.dartNullable) "${f.typeName}?" else f.typeName
                        sb.append("  $t ${f.key};\n")
                    }
                    sb.append("\n")

                    // 可空时字段带 ?，构造函数用可选命名参数（去掉 required，避免编译报错）
                    sb.append("  $className({\n")
                    if (config.dartNullable) {
                        fields.forEach { f -> sb.append("    this.${f.key},\n") }
                    } else {
                        fields.forEach { f -> sb.append("    required this.${f.key},\n") }
                    }
                    sb.append("  });\n\n")

                    sb.append("  factory $className.fromJson(Map<String, dynamic> json) => _\$${className}FromJson(json);\n")
                    sb.append("  Map<String, dynamic> toJson() => _\$${className}ToJson(this);\n")
                } else if (config.dartSimplifiedStyle) {
                    // ===== 简化样式（自定义）：字段非 final、可选参数、直接赋值、不强制类型转换 =====
                    // 字段声明：已知类型按可空选项加 ?；未知类型(dynamic，对应 JSON null) 不加 ?
                    fields.forEach { f ->
                        if (f.typeName == "dynamic") {
                            sb.append("  dynamic ${f.key};\n")
                        } else {
                            val t = if (config.dartNullable) "${f.typeName}?" else f.typeName
                            sb.append("  $t ${f.key};\n")
                        }
                    }
                    sb.append("\n")

                    // 普通构造方法（可选命名参数，无 required）；勾选 Default Values 时给字段默认值
                    sb.append("  $className({\n")
                    fields.forEach { f ->
                        val defaultExpr = when {
                            f.isObject -> "${f.typeName}()"
                            f.defaultLiteral != null -> f.defaultLiteral
                            else -> null
                        }
                        if (config.dartDefaultValues && defaultExpr != null) {
                            sb.append("    this.${f.key} = $defaultExpr,\n")
                        } else {
                            sb.append("    this.${f.key},\n")
                        }
                    }
                    sb.append("  });\n\n")

                    // 简写版 fromJson（命名构造，不带强转；可空保持 null，非空按类型给默认值）
                    sb.append("  $className.fromJson(Map<String, dynamic> json) {\n")
                    fields.forEach { f ->
                        val expr = when {
                            f.isObject -> {
                                if (config.dartNullable)
                                    "json['${f.key}'] != null ? ${f.typeName}.fromJson(json['${f.key}']) : null"
                                else
                                    "${f.typeName}.fromJson(json['${f.key}'])"
                            }
                            f.isListOfObjects -> {
                                val inner = f.innerName!!
                                if (config.dartNullable)
                                    "json['${f.key}'] != null ? json['${f.key}'].map((e) => $inner.fromJson(e)).toList() : null"
                                else if (config.dartDefaultValues)
                                    "json['${f.key}'] != null ? json['${f.key}'].map((e) => $inner.fromJson(e)).toList() : const []"
                                else
                                    "json['${f.key}'].map((e) => $inner.fromJson(e)).toList()"
                            }
                            else -> {
                                if (config.dartNullable)
                                    "json['${f.key}']"
                                else if (config.dartDefaultValues && f.defaultLiteral != null)
                                    "json['${f.key}'] ?? ${f.defaultLiteral}"
                                else
                                    "json['${f.key}']"
                            }
                        }
                        sb.append("    ${f.key} = $expr;\n")
                    }
                    sb.append("  }\n\n")

                    // 简写版 toJson（Map builder，去掉冗余 new 关键字）
                    sb.append("  Map<String, dynamic> toJson() {\n")
                    sb.append("    final Map<String, dynamic> data = <String, dynamic>{};\n")
                    fields.forEach { f ->
                        when {
                            f.isObject -> {
                                val nullableObj = config.dartNullable || f.typeName == "dynamic"
                                if (nullableObj) sb.append("    data['${f.key}'] = this.${f.key}?.toJson();\n")
                                else sb.append("    data['${f.key}'] = this.${f.key}.toJson();\n")
                            }
                            f.isListOfObjects ->
                                sb.append("    data['${f.key}'] = this.${f.key}?.map((e) => e.toJson()).toList();\n")
                            else ->
                                sb.append("    data['${f.key}'] = this.${f.key};\n")
                        }
                    }
                    sb.append("    return data;\n")
                    sb.append("  }\n")
                } else {
                    // ===== 标准样式（自定义）：构造参数赋值，非空字段带 required；可空保持 null，非空按类型给默认值 =====

                    // 字段声明
                    fields.forEach { f ->
                        val finalType = if (config.dartNullable) "${f.typeName}?" else f.typeName
                        when {
                            config.dartUseFinal -> sb.append("  final $finalType ${f.key};\n")
                            else -> sb.append("  $finalType ${f.key};\n")
                        }
                    }
                    sb.append("\n")

                    // 构造函数
                    sb.append("  $className({\n")
                    fields.forEach { f ->
                        when {
                            // 可空字段：不带 required，也不强行给默认值（保持 null）
                            config.dartNullable -> sb.append("    this.${f.key},\n")
                            // 非空字段：有默认值则给默认值（非 required），否则 required
                            else -> {
                                if (config.dartDefaultValues && f.defaultLiteral != null)
                                    sb.append("    this.${f.key} = ${f.defaultLiteral},\n")
                                else
                                    sb.append("    required this.${f.key},\n")
                            }
                        }
                    }
                    sb.append("  });\n\n")

                    // 手写 fromJson（通过构造参数赋值）
                    sb.append("  factory $className.fromJson(Map<String, dynamic> json) {\n")
                    sb.append("    return $className(\n")
                    fields.forEach { f ->
                        when {
                            f.isObject -> {
                                if (config.dartNullable)
                                    sb.append("      ${f.key}: json['${f.key}'] != null ? ${f.typeName}.fromJson(json['${f.key}'] as Map<String, dynamic>) : null,\n")
                                else
                                    sb.append("      ${f.key}: ${f.typeName}.fromJson(json['${f.key}'] as Map<String, dynamic>),\n")
                            }
                            f.isListOfObjects -> {
                                val inner = f.innerName!!
                                if (config.dartNullable)
                                    sb.append("      ${f.key}: json['${f.key}'] != null ? (json['${f.key}'] as List<dynamic>).map((e) => $inner.fromJson(e as Map<String, dynamic>)).toList() : null,\n")
                                else if (config.dartDefaultValues)
                                    sb.append("      ${f.key}: (json['${f.key}'] as List<dynamic>? ?? const []).map((e) => $inner.fromJson(e as Map<String, dynamic>)).toList(),\n")
                                else
                                    sb.append("      ${f.key}: (json['${f.key}'] as List<dynamic>).map((e) => $inner.fromJson(e as Map<String, dynamic>)).toList(),\n")
                            }
                            else -> {
                                val asExpr = when {
                                    config.dartNullable -> "json['${f.key}'] as ${f.typeName}?"
                                    config.dartDefaultValues && f.defaultLiteral != null -> "json['${f.key}'] as ${f.typeName}? ?? ${f.defaultLiteral}"
                                    else -> "json['${f.key}'] as ${f.typeName}"
                                }
                                sb.append("      ${f.key}: $asExpr,\n")
                            }
                        }
                    }
                    sb.append("    );\n")
                    sb.append("  }\n\n")

                    // 手写 toJson
                    sb.append("  Map<String, dynamic> toJson() {\n")
                    sb.append("    return {\n")
                    fields.forEach { f ->
                        when {
                            f.isObject -> {
                                if (config.dartNullable)
                                    sb.append("      '${f.key}': ${f.key}?.toJson(),\n")
                                else
                                    sb.append("      '${f.key}': ${f.key}.toJson(),\n")
                            }
                            f.isListOfObjects ->
                                sb.append("      '${f.key}': ${f.key}.map((e) => e.toJson()).toList(),\n")
                            else ->
                                sb.append("      '${f.key}': ${f.key},\n")
                        }
                    }
                    sb.append("    };\n")
                    sb.append("  }\n")

                    // 使用 final 时字段不可重新赋值，额外生成 copyWith 便于拷贝后局部修改
                    if (config.dartUseFinal) {
                        sb.append("\n")
                        sb.append("  $className copyWith({\n")
                        fields.forEach { f ->
                            val paramType = if (f.typeName == "dynamic") "dynamic" else "${f.typeName}?"
                            sb.append("    $paramType ${f.key},\n")
                        }
                        sb.append("  }) {\n")
                        sb.append("    return $className(\n")
                        fields.forEach { f -> sb.append("      ${f.key}: ${f.key} ?? this.${f.key},\n") }
                        sb.append("    );\n")
                        sb.append("  }\n")
                    }
                }

                sb.append("}\n\n")
                nestedTasks.forEach { (subName, subEl) -> generateModel(subName, subEl) }
            }

            generateModel(config.modelName, rootElement)
            return sb.toString()
        } catch (e: Exception) {
            return "// Parse Error: ${e.message}"
        }
    }

    private fun createFile(project: Project, event: AnActionEvent, fileName: String, content: String, extension: String) {
        val selectedFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val targetDir = if (selectedFile != null && selectedFile.isDirectory) {
            selectedFile
        } else {
            selectedFile?.parent ?: project.guessProjectDir()
        }

        if (targetDir == null) {
            Messages.showErrorDialog(project, "Could not find target directory", "Error")
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                var targetFile = targetDir.findChild(fileName)
                if (targetFile == null) {
                    targetFile = targetDir.createChildData(this, fileName)
                }
                targetFile.setBinaryContent(content.toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to create file: ${e.message}", "Error")
            }
        }
    }

    private fun insertIntoCurrentEditor(project: Project, event: AnActionEvent, content: String) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        if (editor == null) {
            Messages.showErrorDialog(project, "No active editor found", "Error")
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val offset = editor.caretModel.offset
            editor.document.insertString(offset, "\n\n$content\n")
        }
    }

    override fun update(@NotNull event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}