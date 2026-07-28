package com.kevin.jsonconvert

import com.intellij.ide.util.PropertiesComponent

enum class ModelLanguage {
    ARKTS, KOTLIN, JAVA
}

enum class KotlinAnnotationType {
    NONE, GSON, KOTLINX
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

    // Java 特有
    var javaUseSerializedName: Boolean = false,
    var javaAutoImport: Boolean = true
) {
    companion object {
        private const val PREFIX = "com.kevin.jsonconvert."

        fun load(project: com.intellij.openapi.project.Project?, defaultLang: ModelLanguage): EtsConvertConfig {
            val props = PropertiesComponent.getInstance()
            val config = EtsConvertConfig()
            
            // 默认语言根据当前文件识别
            config.targetLanguage = defaultLang
            
            if (props.getBoolean(PREFIX + "rememberOptions", true)) {
                config.generateAsClass = props.getBoolean(PREFIX + "generateAsClass", true)
                config.useOptionalFields = props.getBoolean(PREFIX + "useOptionalFields", false)
                config.useObservedV2 = props.getBoolean(PREFIX + "useObservedV2", false)
                config.addDefaultValues = props.getBoolean(PREFIX + "addDefaultValues", false)
                
                config.kotlinAnnotation = KotlinAnnotationType.valueOf(props.getValue(PREFIX + "kotlinAnnotation", KotlinAnnotationType.NONE.name))
                config.kotlinAutoImport = props.getBoolean(PREFIX + "kotlinAutoImport", true)
                
                config.javaUseSerializedName = props.getBoolean(PREFIX + "javaUseSerializedName", false)
                config.javaAutoImport = props.getBoolean(PREFIX + "javaAutoImport", true)
                
                config.rememberOptions = true
            }
            return config
        }

        fun save(project: com.intellij.openapi.project.Project?, config: EtsConvertConfig) {
            val props = PropertiesComponent.getInstance()
            props.setValue(PREFIX + "rememberOptions", config.rememberOptions)
            if (config.rememberOptions) {
                props.setValue(PREFIX + "generateAsClass", config.generateAsClass)
                props.setValue(PREFIX + "useOptionalFields", config.useOptionalFields)
                props.setValue(PREFIX + "useObservedV2", config.useObservedV2)
                props.setValue(PREFIX + "addDefaultValues", config.addDefaultValues)
                props.setValue(PREFIX + "kotlinAnnotation", config.kotlinAnnotation.name)
                props.setValue(PREFIX + "kotlinAutoImport", config.kotlinAutoImport)
                props.setValue(PREFIX + "javaUseSerializedName", config.javaUseSerializedName)
                props.setValue(PREFIX + "javaAutoImport", config.javaAutoImport)
            }
        }
    }
}