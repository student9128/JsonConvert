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

class JsonToEtsAction : AnAction() {

    override fun actionPerformed(@NotNull event: AnActionEvent) {
        val project = event.project ?: return

        // 1. 检测当前文件类型
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val extension = virtualFile?.extension?.lowercase() ?: ""
        val defaultLang = when (extension) {
            "kt" -> ModelLanguage.KOTLIN
            "java" -> ModelLanguage.JAVA
            "ets", "ts" -> ModelLanguage.ARKTS
            else -> ModelLanguage.ARKTS
        }

        val dialog = EtsConvertDialog(project, defaultLang)
        if (dialog.showAndGet()) {
            val config = dialog.config
            val code = generateCode(config)

            val finalExt = when (config.targetLanguage) {
                ModelLanguage.ARKTS -> "ets"
                ModelLanguage.KOTLIN -> "kt"
                ModelLanguage.JAVA -> "java"
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