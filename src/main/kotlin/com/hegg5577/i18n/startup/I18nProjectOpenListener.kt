package com.hegg5577.i18n.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.hegg5577.i18n.settings.I18nSettings

/**
 * 项目打开时初始化 i18n 配置（包括 YAML）。
 */
class I18nProjectOpenListener : ProjectActivity {

    /** 使用平台项目启动扩展，确保项目打开后真正加载 YAML 配置。 */
    override suspend fun execute(project: Project) {
        val settings = I18nSettings.getInstance(project)
        settings.initProject(project)
    }
}
