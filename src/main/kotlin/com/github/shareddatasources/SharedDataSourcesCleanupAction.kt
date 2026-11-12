package com.github.shareddatasources

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Menu action: **Tools → Shared DataSources → Restore Original DataSources**
 *
 * Iterates over all open projects, finds dataSources symlinks created
 * by this plugin, deletes them, and restores the original files from
 * backup (if available).
 *
 * **Run this before uninstalling the plugin** to avoid the broken-
 * symlink issue that prevents projects from opening after the shared
 * directory is cleaned up.
 */
class SharedDataSourcesCleanupAction : AnAction() {

    private val log = Logger.getInstance(SharedDataSourcesCleanupAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val projects = ProjectManager.getInstance().openProjects
        var totalRestored = 0
        var totalRemoved = 0
        val errors = mutableListOf<String>()

        for (project in projects) {
            val basePath = project.basePath ?: continue
            val ideaDir = Path.of(basePath, ".idea")
            if (!Files.isDirectory(ideaDir)) continue

            for (fileName in SharedDataSourcesActivity.DATA_SOURCE_FILES) {
                val targetFile = ideaDir.resolve(fileName)
                if (!Files.isSymbolicLink(targetFile)) continue

                try {
                    Files.delete(targetFile)

                    if (SharedDataSourcesActivity.restoreFromBackup(ideaDir, targetFile, fileName)) {
                        log.info("Restored $fileName from backup in project ${project.name}")
                        totalRestored++
                    } else {
                        log.info("Removed symlink $targetFile (no backup to restore)")
                        totalRemoved++
                    }
                } catch (ex: Exception) {
                    log.error("Failed to cleanup $fileName in project ${project.name}", ex)
                    errors.add("${project.name}/$fileName: ${ex.message}")
                }
            }
        }

        // Show result notification
        val currentProject = e.project ?: projects.firstOrNull() ?: return
        val message = buildString {
            if (totalRestored > 0) append("Restored $totalRestored file(s) from backup. ")
            if (totalRemoved > 0) append("Removed $totalRemoved symlink(s) (no backup). ")
            if (totalRestored == 0 && totalRemoved == 0) append("No symlinks found in any open project — nothing to do. ")
            if (errors.isNotEmpty()) append("\nErrors: ${errors.joinToString("; ")}")
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("SharedDataSources")
            .createNotification(
                "Shared DataSources Cleanup",
                message.trim(),
                if (errors.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING
            )
            .notify(currentProject)
    }
}
