package com.kevin.jsonconvert

import com.intellij.ide.util.PropertiesComponent

enum class ModelLanguage {
    ARKTS, KOTLIN, JAVA, DART
}

enum class KotlinAnnotationType {
    NONE, GSON, KOTLINX_BASIC, KOTLINX_ADVANCED
}

enum class FileNameMode {
    MANUAL, AUTO, CURRENT_FILE
}

data class EtsConvertConfig(
    var targetLanguage: ModelLanguage = ModelLanguage.ARKTS,
    var jsonContent: String = "",
    var modelName: String = "DataModel",
    var generateAsClass: Boolean = true, 
    var fileNameMode: FileNameMode = FileNameMode.MANUAL,
    var manualFileName: String = "DataModel",
    var rememberOptions: Boolean = true,

    // ArkTS 特有
    var useOptionalFields: Boolean = false,
    var useObservedV2: Boolean = false,
    var addDefaultValues: Boolean = false,

    // Kotlin 特有
    var kotlinAnnotation: KotlinAnnotationType = KotlinAnnotationType.NONE,
    var kotlinAutoImport: Boolean = true,
    var kotlinNullable: Boolean = false,
    var kotlinDefaultValues: Boolean = true,

    // Dart 特有
    var dartNullable: Boolean = false,
    var dartDefaultValues: Boolean = true,
    var dartUseFinal: Boolean = true,
    var dartUseJsonSerializable: Boolean = false,
    var dartSimplifiedStyle: Boolean = false,

    // Java 特有
    var javaUseSerializedName: Boolean = false,
    var javaAutoImport: Boolean = true,
    var javaUseGetSet: Boolean = false
) {
    companion object {
        private const val PREFIX = "com.kevin.jsonconvert."

        fun load(project: com.intellij.openapi.project.Project?, defaultLang: ModelLanguage): EtsConvertConfig {
            val props = PropertiesComponent.getInstance()
            val config = EtsConvertConfig()

            // 默认语言根据当前文件识别；remember 时优先使用上次保存的语言
            config.targetLanguage = defaultLang

            // 始终读取 rememberOptions，确保与存储值一致（修复之前跳过 if 导致该值恒为 true 的问题）
            val remember = props.getBoolean(PREFIX + "rememberOptions", true)
            config.rememberOptions = remember

            if (remember) {
                config.targetLanguage = loadTargetLanguage(props, defaultLang)
                config.generateAsClass = props.getBoolean(PREFIX + "generateAsClass", true)
                config.useOptionalFields = props.getBoolean(PREFIX + "useOptionalFields", false)
                config.useObservedV2 = props.getBoolean(PREFIX + "useObservedV2", false)
                config.addDefaultValues = props.getBoolean(PREFIX + "addDefaultValues", false)

                config.kotlinAnnotation = loadKotlinAnnotation(props)
                config.kotlinAutoImport = props.getBoolean(PREFIX + "kotlinAutoImport", true)
                config.kotlinNullable = props.getBoolean(PREFIX + "kotlinNullable", false)
                config.kotlinDefaultValues = props.getBoolean(PREFIX + "kotlinDefaultValues", true)

                config.dartNullable = props.getBoolean(PREFIX + "dartNullable", false)
                config.dartDefaultValues = props.getBoolean(PREFIX + "dartDefaultValues", true)
                config.dartUseFinal = props.getBoolean(PREFIX + "dartUseFinal", true)
                config.dartUseJsonSerializable = props.getBoolean(PREFIX + "dartUseJsonSerializable", false)
                config.dartSimplifiedStyle = props.getBoolean(PREFIX + "dartSimplifiedStyle", false)

                config.javaUseSerializedName = props.getBoolean(PREFIX + "javaUseSerializedName", false)
                config.javaAutoImport = props.getBoolean(PREFIX + "javaAutoImport", true)
                config.javaUseGetSet = props.getBoolean(PREFIX + "javaUseGetSet", false)

                // 输出位置选项（之前遗漏，未持久化）
                config.fileNameMode = loadFileNameMode(props)
            }
            return config
        }

        private fun loadKotlinAnnotation(props: PropertiesComponent): KotlinAnnotationType {
            val raw = props.getValue(PREFIX + "kotlinAnnotation") ?: return KotlinAnnotationType.NONE
            return try {
                KotlinAnnotationType.valueOf(raw)
            } catch (e: IllegalArgumentException) {
                // 兼容旧版本存储的 KOTLINX（原先同时加 class 与 @SerialName，对应高级选项）
                if (raw == "KOTLINX") KotlinAnnotationType.KOTLINX_ADVANCED else KotlinAnnotationType.NONE
            }
        }

        private fun loadFileNameMode(props: PropertiesComponent): FileNameMode {
            val raw = props.getValue(PREFIX + "fileNameMode") ?: return FileNameMode.MANUAL
            return try {
                FileNameMode.valueOf(raw)
            } catch (e: IllegalArgumentException) {
                FileNameMode.MANUAL
            }
        }

        private fun loadTargetLanguage(props: PropertiesComponent, defaultLang: ModelLanguage): ModelLanguage {
            val raw = props.getValue(PREFIX + "targetLanguage") ?: return defaultLang
            return try {
                ModelLanguage.valueOf(raw)
            } catch (e: IllegalArgumentException) {
                defaultLang
            }
        }

        fun save(project: com.intellij.openapi.project.Project?, config: EtsConvertConfig) {
            val props = PropertiesComponent.getInstance()
            props.setValue(PREFIX + "rememberOptions", config.rememberOptions)
            if (config.rememberOptions) {
                props.setValue(PREFIX + "targetLanguage", config.targetLanguage.name)
                props.setValue(PREFIX + "generateAsClass", config.generateAsClass)
                props.setValue(PREFIX + "useOptionalFields", config.useOptionalFields)
                props.setValue(PREFIX + "useObservedV2", config.useObservedV2)
                props.setValue(PREFIX + "addDefaultValues", config.addDefaultValues)
                props.setValue(PREFIX + "kotlinAnnotation", config.kotlinAnnotation.name)
                props.setValue(PREFIX + "kotlinAutoImport", config.kotlinAutoImport)
                props.setValue(PREFIX + "kotlinNullable", config.kotlinNullable)
                props.setValue(PREFIX + "kotlinDefaultValues", config.kotlinDefaultValues)
                props.setValue(PREFIX + "dartNullable", config.dartNullable)
                props.setValue(PREFIX + "dartDefaultValues", config.dartDefaultValues)
                props.setValue(PREFIX + "dartUseFinal", config.dartUseFinal)
                props.setValue(PREFIX + "dartUseJsonSerializable", config.dartUseJsonSerializable)
                props.setValue(PREFIX + "dartSimplifiedStyle", config.dartSimplifiedStyle)
                props.setValue(PREFIX + "javaUseSerializedName", config.javaUseSerializedName)
                props.setValue(PREFIX + "javaAutoImport", config.javaAutoImport)
                props.setValue(PREFIX + "javaUseGetSet", config.javaUseGetSet)
                props.setValue(PREFIX + "fileNameMode", config.fileNameMode.name)
            }
        }
    }
}