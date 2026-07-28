package com.kevin.jsonconvert

import com.intellij.ide.util.PropertiesComponent

enum class FileNameMode {
    MANUAL,      // 手动输入修改
    AUTO,        // 自动生成
    CURRENT_FILE // 在当前文件生成
}

data class EtsConvertConfig(
    var jsonContent: String = "",
    var modelName: String = "DataModel",
    var generateAsClass: Boolean = false, // false = interface, true = class
    var fileNameMode: FileNameMode = FileNameMode.MANUAL,
    var manualFileName: String = "DataModel",
    var useOptionalFields: Boolean = false,
    var useObservedV2: Boolean = false,
    var addDefaultValues: Boolean = false,
    var rememberOptions: Boolean = true
) {
    companion object {
        private const val PREFIX = "com.kevin.jsonconvert."
        
        fun load(project: com.intellij.openapi.project.Project?): EtsConvertConfig {
            val props = PropertiesComponent.getInstance()
            val config = EtsConvertConfig()
            
            if (props.getBoolean(PREFIX + "rememberOptions", true)) {
                config.generateAsClass = props.getBoolean(PREFIX + "generateAsClass", false)
                config.useOptionalFields = props.getBoolean(PREFIX + "useOptionalFields", false)
                config.useObservedV2 = props.getBoolean(PREFIX + "useObservedV2", false)
                config.addDefaultValues = props.getBoolean(PREFIX + "addDefaultValues", false)
                config.fileNameMode = FileNameMode.valueOf(props.getValue(PREFIX + "fileNameMode", FileNameMode.MANUAL.name))
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
                props.setValue(PREFIX + "fileNameMode", config.fileNameMode.name)
            }
        }
    }
}