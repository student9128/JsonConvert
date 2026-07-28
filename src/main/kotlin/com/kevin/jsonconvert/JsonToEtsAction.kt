package com.kevin.jsonconvert

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.NotNull

class JsonToEtsAction : AnAction() {

    override fun actionPerformed(@NotNull event: AnActionEvent) {
        val project = event.project ?: return

        val dialog = EtsConvertDialog(project)
        if (dialog.showAndGet()) {
            val config = dialog.config
            val etsCode = parseJsonToArkTS(config)

            if (config.fileNameMode == FileNameMode.CURRENT_FILE) {
                insertIntoCurrentEditor(project, event, etsCode)
            } else {
                val fileName = if (config.manualFileName.endsWith(".ets")) {
                    config.manualFileName
                } else {
                    "${config.manualFileName}.ets"
                }
                createEtsFile(project, event, fileName, etsCode)
            }
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

            // 获取不冲突的模型名称
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

                // 递归生成子类
                nestedTasks.forEach { (subName, subEl) -> generateModel(subName, subEl) }
            }

            generateModel(config.modelName, rootElement)
            return sb.toString()
        } catch (e: Exception) {
            return "// Parse Error: ${e.message}"
        }
    }

    private fun createEtsFile(project: Project, event: AnActionEvent, fileName: String, content: String) {
        val selectedFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val targetDir = if (selectedFile != null && selectedFile.isDirectory) {
            selectedFile
        } else {
            project.guessProjectDir()
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