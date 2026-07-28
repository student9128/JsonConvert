package com.kevin.jsonconvert

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.json.JsonLanguage
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.awt.Dimension
import javax.swing.JComponent

class EtsConvertDialog(val project: Project?) : DialogWrapper(project) {

    val config = EtsConvertConfig.load(project)
    private lateinit var fileNameTextField: Cell<JBTextField>

    private val jsonEditorField = object : LanguageTextField(JsonLanguage.INSTANCE, project, "", false) {
        override fun createEditor(): EditorEx {
            val editor = super.createEditor()
            editor.setVerticalScrollbarVisible(true)
            editor.setHorizontalScrollbarVisible(true)
            val settings = editor.settings
            settings.isLineNumbersShown = true
            settings.isAutoCodeFoldingEnabled = true
            settings.isFoldingOutlineShown = true
            settings.isAllowSingleLogicalLineFolding = true
            settings.isRightMarginShown = true
            editor.setPlaceholder("Enter or paste JSON content here...")
            return editor
        }
    }

    init {
        title = "JSON to ArkTS Model"
        config.manualFileName = config.modelName
        setResizable(true)
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                cell(jsonEditorField)
                    .align(Align.FILL)
                    .label("JSON Input:", LabelPosition.TOP)
                    .focused()
            }.resizableRow()

            group("Options") {
                row("Model Name:") {
                    textField()
                        .bindText(config::modelName)
                        .align(AlignX.FILL)
                        .onChanged {
                            if (::fileNameTextField.isInitialized) {
                                fileNameTextField.component.text = it.text
                            }
                        }
                }

                lateinit var classRadio: Cell<javax.swing.JRadioButton>
                buttonsGroup {
                    row("Generate Type:") {
                        radioButton("interface", false)
                        classRadio = radioButton("class", true)

                    }
                }.bind({ config.generateAsClass }, { config.generateAsClass = it })

                row {
                    checkBox("Add ? (optional fields)")
                        .bindSelected(config::useOptionalFields)
                        .enabledIf(classRadio.selected)
                        .gap(RightGap.SMALL)
                    checkBox("Add @ObservedV2 and @Trace")
                        .bindSelected(config::useObservedV2)
                        .enabledIf(classRadio.selected)
                        .onChanged {
                            if (it.isSelected) {
                                config.addDefaultValues = true
                            }
                        }

                    checkBox("Add Default Values")
                        .bindSelected(config::addDefaultValues)
                        .enabledIf(classRadio.selected)
                }

                buttonsGroup {
                    row("Output To:") {
                        radioButton("New File", FileNameMode.MANUAL)
                        radioButton("Current File", FileNameMode.CURRENT_FILE)
                    }
                }.bind(
                    { if (config.fileNameMode == FileNameMode.CURRENT_FILE) FileNameMode.CURRENT_FILE else FileNameMode.MANUAL },
                    { config.fileNameMode = it })

                row("File Name:") {
                    fileNameTextField = textField()
                        .bindText(config::manualFileName)
                        .align(AlignX.FILL)
                }

                // 记忆功能选项
                row {
                    checkBox("Remember Options")
                        .bindSelected(config::rememberOptions)
                }
            }
        }.apply {
            preferredSize = Dimension(800, 600)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return jsonEditorField
    }

    override fun createActions(): Array<javax.swing.Action> {
        setOKButtonText("Generate")
        return super.createActions()
    }

    override fun createLeftSideActions(): Array<javax.swing.Action> {
        val formatAction = object : DialogWrapperAction("Format JSON") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                formatJsonText()
            }
        }
        return arrayOf(formatAction)
    }

    private fun formatJsonText(): Boolean {
        val rawText = jsonEditorField.text.trim()
        if (rawText.isBlank()) return false
        try {
            val element = JsonParser.parseString(rawText)
            val gson = GsonBuilder().setPrettyPrinting().create()
            jsonEditorField.text = gson.toJson(element)
            return true
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Invalid JSON: \n${e.message}", "Error")
            return false
        }
    }

    override fun doOKAction() {
        if (!formatJsonText()) return
        applyFields()
        config.jsonContent = jsonEditorField.text

        if (!config.generateAsClass) {
            config.useOptionalFields = false
            config.useObservedV2 = false
            config.addDefaultValues = false
        }

        // 保存配置到 PropertiesComponent
        EtsConvertConfig.save(project, config)

        super.doOKAction()
    }
}