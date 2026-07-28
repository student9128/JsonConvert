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
import javax.swing.JLabel
import javax.swing.JRadioButton
import javax.swing.SwingConstants

class JsonConvertDialog(val project: Project?, defaultLang: ModelLanguage = ModelLanguage.ARKTS) : DialogWrapper(project) {

    val config = EtsConvertConfig.load(project, defaultLang)
    private lateinit var fileNameTextField: Cell<JBTextField>

    // 固定标签列宽度，避免不同语言下可见标签宽度不同导致字段输入框位置偏移
    private companion object {
        const val FIXED_LABEL_WIDTH = 80
    }

    private fun fixedLabel(text: String): JLabel {
        val label = JLabel(text)
        label.horizontalAlignment = SwingConstants.LEFT
        label.preferredSize = Dimension(FIXED_LABEL_WIDTH, label.preferredSize.height)
        return label
    }

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
        title = "JSON to Model Generator"
        config.manualFileName = config.modelName
        setResizable(true)
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                cell(jsonEditorField)
                    .align(Align.FILL)
                    .label("JSON Content:", LabelPosition.TOP)
                    .focused()
            }.resizableRow()

            lateinit var arktsRb: Cell<JRadioButton>
            lateinit var kotlinRb: Cell<JRadioButton>
            lateinit var javaRb: Cell<JRadioButton>

            // 2. 语言选择
            buttonsGroup {
                row("Target Language:") {
                    arktsRb = radioButton("ArkTS", ModelLanguage.ARKTS)
                    kotlinRb = radioButton("Kotlin", ModelLanguage.KOTLIN)
                    javaRb = radioButton("Java", ModelLanguage.JAVA)
                }
            }.bind({ config.targetLanguage }, { config.targetLanguage = it })

            // 3. 配置项区
            group("Options") {
                row(fixedLabel("Model Name:")) {
                    textField()
                        .bindText(config::modelName)
                        .align(AlignX.FILL)
                        .onChanged {
                            if (::fileNameTextField.isInitialized) {
                                fileNameTextField.component.text = it.text
                            }
                        }
                }.topGap(TopGap.NONE)

                // --- ArkTS 专属配置 ---
                lateinit var classRadio: Cell<JRadioButton>
                buttonsGroup {
                    row(fixedLabel("Type:")) {
                        radioButton("interface", false)
                        classRadio = radioButton("class", true)
                    }
                }.bind({ config.generateAsClass }, { config.generateAsClass = it })
                 .visibleIf(arktsRb.selected)

                row {
                    checkBox("Nullable Types (add ?)")
                        .bindSelected(config::useOptionalFields)
                        .enabledIf(classRadio.selected)
                        .gap(RightGap.SMALL)
                    checkBox("@ObservedV2 and @Trace").bindSelected(config::useObservedV2).enabledIf(classRadio.selected)
                    checkBox("Default Values").bindSelected(config::addDefaultValues).enabledIf(classRadio.selected)
                }.visibleIf(arktsRb.selected)

                // --- Kotlin 专属配置 ---
                buttonsGroup {
                    row(fixedLabel("Annotations:")) {
                        radioButton("None", KotlinAnnotationType.NONE)
                        radioButton("Gson @SerializedName", KotlinAnnotationType.GSON)
                        radioButton("Kotlinx @Serializable (class only)", KotlinAnnotationType.KOTLINX_BASIC)
                        radioButton("Kotlinx @Serializable + @SerialName", KotlinAnnotationType.KOTLINX_ADVANCED)
                    }
                }.bind({ config.kotlinAnnotation }, { config.kotlinAnnotation = it })
                 .visibleIf(kotlinRb.selected)

                row {
                    checkBox("Auto Import").bindSelected(config::kotlinAutoImport)
                }.visibleIf(kotlinRb.selected)
                row {
                    checkBox("Nullable Types (add ?)").bindSelected(config::kotlinNullable)
                    checkBox("Default Values").bindSelected(config::kotlinDefaultValues)
                }.visibleIf(kotlinRb.selected)

                // --- Java 专属配置 ---
                row {
                    checkBox("Add Gson @SerializedName").bindSelected(config::javaUseSerializedName)
                }.visibleIf(javaRb.selected)
                row {
                    checkBox("Auto Import").bindSelected(config::javaAutoImport)
                }.visibleIf(javaRb.selected)
                row {
                    checkBox("Generate get/set Methods").bindSelected(config::javaUseGetSet)
                }.visibleIf(javaRb.selected)

                // --- 通用输出配置 ---
                buttonsGroup {
                    row(fixedLabel("Output To:")) {
                        radioButton("New File", FileNameMode.MANUAL)
                        radioButton("Current File", FileNameMode.CURRENT_FILE)
                    }
                }.bind(
                    { if (config.fileNameMode == FileNameMode.CURRENT_FILE) FileNameMode.CURRENT_FILE else FileNameMode.MANUAL },
                    { config.fileNameMode = it })

                row(fixedLabel("File Name:")) {
                    fileNameTextField = textField().bindText(config::manualFileName).align(AlignX.FILL)
                }

                row {
                    checkBox("Remember Options").bindSelected(config::rememberOptions)
                }
            }.topGap(TopGap.NONE)
        }.apply { preferredSize = Dimension(700, 600) }
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

    override fun getPreferredFocusedComponent(): JComponent = jsonEditorField



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
