package com.github.shareddatasources

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class SharedDataSourcesActivity : ProjectActivity {

    private val log = Logger.getInstance(SharedDataSourcesActivity::class.java)

    companion object {
        /** Shared constant so other classes (e.g. CleanupAction) can reuse it. */
        val DATA_SOURCE_FILES = listOf("dataSources.xml", "dataSources.local.xml")
        private const val BACKUP_DIR_NAME = "dataSources_backup_auto"

        /**
         * Backup a regular file into `.idea/dataSources_backup_auto/`.
         * Does nothing if a backup already exists for this file name.
         */
        fun backupFile(ideaDir: Path, targetFile: Path, fileName: String) {
            val backupDir = ideaDir.resolve(BACKUP_DIR_NAME)
            Files.createDirectories(backupDir)
            val backupFile = backupDir.resolve(fileName)
            if (!Files.exists(backupFile)) {
                Files.copy(targetFile, backupFile, StandardCopyOption.COPY_ATTRIBUTES)
            }
        }

        /**
         * Try to restore a dataSources file from its backup.
         * @return true if a backup was found and restored.
         */
        fun restoreFromBackup(ideaDir: Path, targetFile: Path, fileName: String): Boolean {
            val backupFile = ideaDir.resolve(BACKUP_DIR_NAME).resolve(fileName)
            if (Files.isRegularFile(backupFile)) {
                Files.copy(backupFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES)
                return true
            }
            return false
        }
    }

    override suspend fun execute(project: Project) {
        val settings = SharedDataSourcesSettings.getInstance().state

        val projectBasePath = project.basePath ?: return
        val ideaDir = Path.of(projectBasePath, ".idea")
        if (!Files.isDirectory(ideaDir)) return

        // ── Phase 1: repair any broken symlinks ──────────────────────────
        // This runs **unconditionally** (even if sync is disabled) so that
        // dangling symlinks left behind after the shared directory was
        // cleaned up — or after the plugin was re-installed — are fixed
        // before the Database plugin tries to read them.
        repairBrokenSymlinks(ideaDir, project)

        // ── Phase 2: normal sync (only when enabled) ─────────────────────
        if (!settings.enabled) return

        val sharedDir = Path.of(settings.sharedDirectory)
        if (!Files.isDirectory(sharedDir)) {
            log.warn("Shared dataSources directory does not exist: $sharedDir")
            notify(
                project,
                "Shared DataSources",
                "Shared directory does not exist: $sharedDir — sync skipped.",
                NotificationType.WARNING
            )
            return
        }

        var synced = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        for (fileName in DATA_SOURCE_FILES) {
            val sharedFile = sharedDir.resolve(fileName)
            if (!Files.isRegularFile(sharedFile)) {
                log.warn("Shared file not found: $sharedFile")
                continue
            }

            val targetFile = ideaDir.resolve(fileName)

            try {
                // Already a correct symlink — skip
                if (Files.isSymbolicLink(targetFile)) {
                    val linkTarget = Files.readSymbolicLink(targetFile)
                    if (linkTarget == sharedFile || targetFile.resolveSibling(linkTarget).normalize() == sharedFile.normalize()) {
                        skipped++
                        continue
                    }
                    // Wrong symlink target — remove and re-create
                    Files.delete(targetFile)
                }

                // Regular file exists — backup first
                if (Files.exists(targetFile) && !Files.isSymbolicLink(targetFile)) {
                    if (settings.autoBackup) {
                        backupFile(ideaDir, targetFile, fileName)
                        log.info("Backed up $targetFile")
                    }
                    Files.delete(targetFile)
                }

                // Create symbolic link
                if (!Files.exists(targetFile)) {
                    Files.createSymbolicLink(targetFile, sharedFile)
                    log.info("Created symlink: $targetFile -> $sharedFile")
                    synced++
                }
            } catch (e: Exception) {
                log.error("Failed to sync $fileName for project ${project.name}", e)
                errors.add("$fileName: ${e.message}")
            }
        }

        // Show notification
        if (synced > 0) {
            notify(
                project,
                "Shared DataSources synced",
                "Linked $synced file(s) to shared configuration for project \"${project.name}\".",
                NotificationType.INFORMATION
            )
        }
        if (errors.isNotEmpty()) {
            notify(
                project,
                "Shared DataSources error",
                "Failed to sync: ${errors.joinToString("; ")}",
                NotificationType.WARNING
            )
        }
    }

    // ── Broken-symlink repair ────────────────────────────────────────────

    /**
     * Scans the `.idea` directory for dataSources symlinks whose targets
     * no longer exist. For each one found the symlink is deleted and, if
     * a backup exists in `dataSources_backup_auto/`, the original file is
     * restored.
     *
     * This prevents the IDE from failing to open the project when the
     * Database plugin encounters a dangling symlink.
     */
    private fun repairBrokenSymlinks(ideaDir: Path, project: Project) {
        var repaired = 0
        for (fileName in DATA_SOURCE_FILES) {
            val targetFile = ideaDir.resolve(fileName)
            if (!Files.isSymbolicLink(targetFile)) continue

            val linkTarget = try {
                Files.readSymbolicLink(targetFile)
            } catch (e: Exception) {
                log.warn("Cannot read symlink $targetFile", e)
                continue
            }

            val resolvedTarget = targetFile.resolveSibling(linkTarget).normalize()
            if (Files.exists(resolvedTarget)) continue  // link is healthy

            // ── Broken symlink detected ──
            log.warn("Broken symlink: $targetFile -> $linkTarget (target missing)")

            try {
                Files.delete(targetFile)

                if (restoreFromBackup(ideaDir, targetFile, fileName)) {
                    log.info("Restored $fileName from backup for project ${project.name}")
                } else {
                    log.info("Removed broken symlink $targetFile (no backup available)")
                }
                repaired++
            } catch (e: Exception) {
                log.error("Failed to repair broken symlink $targetFile", e)
            }
        }

        if (repaired > 0) {
            notify(
                project,
                "Shared DataSources: repaired",
                "Fixed $repaired broken symlink(s) in project \"${project.name}\".",
                NotificationType.INFORMATION
            )
        }
    }

    // ── Notifications ────────────────────────────────────────────────────

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SharedDataSources")
            .createNotification(title, content, type)
            .notify(project)
    }
}
