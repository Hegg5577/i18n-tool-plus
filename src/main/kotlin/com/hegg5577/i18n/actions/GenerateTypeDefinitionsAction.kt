package com.hegg5577.i18n.actions

import com.hegg5577.i18n.generator.TypeScriptCodeGenerator
import com.hegg5577.i18n.settings.I18nContext
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.io.File

/**
 * 生成 TypeScript 类型定义。
 *
 * 从当前项目的翻译键自动生成类型定义，支持：
 * - 完整的 TypeScript 类型
 * - 结构化的键 Schema（用于编辑器补全）
 * - Vue 3 组件类型
 */
class GenerateTypeDefinitionsAction : AnAction("Generate i18n Types") {

    /** 生成器与编辑器使用同一仓库，包含全部目录和命名空间。 */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        try {
            val projectRoot = File(project.basePath ?: return)
            val context = I18nContext.of(project)
            val repository = context.repository() ?: return

            // 生成主类型定义
            val typesDef = TypeScriptCodeGenerator.generateTypeDefinitions(
                projectRoot,
                context.localesPath,
                context.pathMatcher,
                context.keyStyle,
                "src/types/i18n.ts",
                repository = repository,
            )

            // 生成结构化 Schema
            val schemaDef = TypeScriptCodeGenerator.generateStructuredSchema(
                projectRoot,
                context.localesPath,
                context.pathMatcher,
                context.keyStyle,
                "src/types/i18n-schema.ts",
                repository = repository,
            )

            // 生成 Vue 3 类型（如果项目包含 Vue）
            val vueTypeDef = if (File(projectRoot, "package.json").exists() &&
                File(projectRoot, "package.json").readText().contains("vue")) {
                TypeScriptCodeGenerator.generateVueComponentTypes(
                    projectRoot,
                    context.localesPath,
                    context.pathMatcher,
                    context.keyStyle,
                    "src/types/i18n-vue.ts",
                    repository = repository,
                )
            } else {
                null
            }

            // 写入文件
            var success = true
            success = success && TypeScriptCodeGenerator.writeToFile(typesDef, projectRoot)
            success = success && TypeScriptCodeGenerator.writeToFile(schemaDef, projectRoot)
            if (vueTypeDef != null) {
                success = success && TypeScriptCodeGenerator.writeToFile(vueTypeDef, projectRoot)
            }

            if (success) {
                Messages.showInfoMessage(
                    project,
                    "已生成类型定义:\n" +
                            "  • ${typesDef.filePath}\n" +
                            "  • ${schemaDef.filePath}" +
                            (if (vueTypeDef != null) "\n  • ${vueTypeDef.filePath}" else ""),
                    "生成成功",
                )
            } else {
                Messages.showErrorDialog(project, "生成过程中出错", "生成失败")
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: "未知错误", "错误")
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
