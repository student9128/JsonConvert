package com.kevin.jsonconvert

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.json.JsonLanguage
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBCheckBox
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
            lateinit var dartRb: Cell<JRadioButton>

            // 2. 语言选择
            buttonsGroup {
                row("Target Language:") {
                    arktsRb = radioButton("ArkTS", ModelLanguage.ARKTS)
                    kotlinRb = radioButton("Kotlin", ModelLanguage.KOTLIN)
                    javaRb = radioButton("Java", ModelLanguage.JAVA)
                    dartRb = radioButton("Dart (Flutter)", ModelLanguage.DART)
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

                // --- Dart 专属配置 ---
                lateinit var dartFinalCb: Cell<JBCheckBox>
                lateinit var dartNullableCb: Cell<JBCheckBox>
                lateinit var dartDefaultCb: Cell<JBCheckBox>
                lateinit var dartJsonSerCb: Cell<JBCheckBox>
                lateinit var dartSimplifiedCb: Cell<JBCheckBox>

                row {
                    dartFinalCb = checkBox("Use final").bindSelected(config::dartUseFinal)
                    dartNullableCb = checkBox("Nullable Types (add ?)").bindSelected(config::dartNullable)
                    dartDefaultCb = checkBox("Default Values").bindSelected(config::dartDefaultValues)
                }.visibleIf(dartRb.selected)
                row {
                    dartJsonSerCb = checkBox("Use json_serializable annotation (generate fromJson/toJson via code-gen)")
                        .bindSelected(config::dartUseJsonSerializable)
                }.visibleIf(dartRb.selected)
                row {
                    // 简化样式：命名构造 fromJson 直接赋值、不加 final、不强制类型转换
                    dartSimplifiedCb = checkBox("Simplified style (no type casting, named fromJson)")
                        .bindSelected(config::dartSimplifiedStyle)
                }.visibleIf(dartRb.selected)

                // 选项联动（参考 ArkTS interface/class 的隐藏/置灰逻辑）：
                // 1) 勾选 json_serializable：final / Default Values 直接隐藏并清零（置灰改为不显示）；
                //    nullable 仍可勾选（官方之外给用户多一个选择）；Simplified 置灰（始终显示，不可勾选）
                // 2) Final 与 Simplified 互斥：任一勾选则另一不可勾选。Simplified 始终显示（置灰即可），
                //    Final 在被互斥时隐藏（不显示）
                val applyDartConsistency = {
                    val jsonSer = dartJsonSerCb.component.isSelected
                    val simplified = dartSimplifiedCb.component.isSelected

                    // 互斥与清理：
                    // - annotation 勾选：final / Default Values 清零（隐藏，不再置灰）
                    // - simplified 与 final 互斥时以 simplified 优先（req c：simplified 勾选时 final 不可勾选/隐藏），
                    //   因此清除 final 而非 simplified，保证用户已选的 Simplified 不被丢弃（修复重开后 Simplified 被置灰）
                    if (jsonSer) {
                        dartFinalCb.component.isSelected = false
                        config.dartUseFinal = false
                        dartDefaultCb.component.isSelected = false
                        config.dartDefaultValues = false
                    }
                    val finalNow = dartFinalCb.component.isSelected
                    if (simplified && finalNow) {
                        dartFinalCb.component.isSelected = false
                        config.dartUseFinal = false
                    }

                    val jsonSer2 = dartJsonSerCb.component.isSelected
                    val finalSel2 = dartFinalCb.component.isSelected
                    val simplified2 = dartSimplifiedCb.component.isSelected

                    // Final：annotation 或 simplified 勾选时隐藏；否则显示且可选
                    dartFinalCb.visible(!jsonSer2 && !simplified2)
                    // Default Values：annotation 勾选时隐藏；否则显示且可选
                    dartDefaultCb.visible(!jsonSer2)
                    dartDefaultCb.enabled(true)
                    // Nullable：始终显示且可选
                    dartNullableCb.visible(true)
                    dartNullableCb.enabled(true)
                    // Simplified：始终显示；final 或 annotation 勾选时置灰（不可勾选）
                    dartSimplifiedCb.visible(true)
                    dartSimplifiedCb.enabled(!finalSel2 && !jsonSer2)
                }
                dartJsonSerCb.component.addActionListener { applyDartConsistency() }
                dartFinalCb.component.addActionListener { applyDartConsistency() }
                dartSimplifiedCb.component.addActionListener { applyDartConsistency() }
                applyDartConsistency()

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
