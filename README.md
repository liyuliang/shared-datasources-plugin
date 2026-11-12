# Shared DataSources

![Plugin logo](src/main/resources/META-INF/pluginIcon.svg)

An IntelliJ Platform plugin that keeps **JetBrains Database data sources in sync across all your projects**.

Instead of re-importing the same database connections in every project, the plugin replaces the
project-level `.idea/dataSources.xml` and `.idea/dataSources.local.xml` files with **symbolic links**
pointing at a single shared copy. Create a data source once — every project picks it up.

## Features

- **Automatic sync on project open** — runs as a post-startup activity, no manual steps.
- **Symlink-based** — the shared files are the single source of truth; edits made in any project are visible everywhere.
- **Safe by default** — existing regular files are backed up before being replaced.
- **Idempotent** — correct symlinks are skipped; wrong ones are re-created.
- **Notifications** — success and failure are reported via IDE balloon notifications.
- **Configurable** — shared directory, auto-sync, and auto-backup can be toggled in settings.

## How It Works

On every project open (when enabled), the plugin:

1. Reads the shared directory from settings (must exist and contain `dataSources.xml` / `dataSources.local.xml`).
2. Checks the project's `.idea` directory (skips projects without one).
3. For each data source file:

   | Current state            | Action                                                              |
   |--------------------------|---------------------------------------------------------------------|
   | Correct symlink          | Skipped                                                             |
   | Wrong symlink            | Removed and re-created                                              |
   | Regular file             | Backed up (optional), then replaced with a symlink                  |
   | Missing                  | Fresh symlink created                                               |

4. Backups are written to `.idea/dataSources_backup_auto/` (only if no backup for that file exists yet).
5. Shows a notification listing how many files were linked, or any errors encountered.

## Requirements

- **IDE**: Any JetBrains IDE based on IntelliJ Platform **2024.1 (241)** through **2025.2 (252.\*)**
  with the *Database* plugin (`com.intellij.database`) — e.g. GoLand, IntelliJ IDEA Ultimate.
  (Built and tested against GoLand 2024.1.4.)
- **JDK 17+** for building.
- **Symlink support on Windows**: creating symlinks requires either an elevated shell or
  [Windows Developer Mode](https://learn.microsoft.com/windows/apps/tutorials/enable-dev-mode)
  enabled.

## Installation

### Build from source

```bash
# Using Make (Windows / macOS / Linux)
make plugin          # builds the ZIP and copies it to ./out/

# Or invoke Gradle directly
./gradlew.bat buildPlugin      # Windows
./gradlew buildPlugin          # macOS / Linux
```

The plugin ZIP is produced at `build/distributions/` (also copied to `./out/` when using `make`).

### Install in the IDE

1. **Settings/Preferences → Plugins → ⚙️ → Install Plugin from Disk…**
2. Select the built ZIP file.
3. Restart the IDE.

## Configuration

**Settings/Preferences → Tools → Shared DataSources**

| Option                              | Default                        | Description                                                                  |
|-------------------------------------|--------------------------------|------------------------------------------------------------------------------|
| Shared directory                    | `${java.io.tmpdir}\.shared-datasources` (e.g. `C:\Users\<you>\AppData\Local\Temp\.shared-datasources` on Windows, `/tmp/.shared-datasources` on Linux) | Directory containing the shared `dataSources.xml` and `dataSources.local.xml`. |
| Enable automatic sync on project open | Enabled                     | Run the sync automatically each time a project is opened.                    |
| Auto backup existing files before replacing | Enabled                | Back up existing regular files to `.idea/dataSources_backup_auto/` first.    |

To get started:

1. Create (or pick) a shared directory and place `dataSources.xml` and `dataSources.local.xml` in it —
   you can copy them from any project's `.idea` folder. By default the plugin looks under the
   system temp directory (`.shared-datasources`), which you can simply create if it doesn't exist yet.
2. Point the plugin at that directory in settings.
3. Open any project: the files under `.idea` will be linked to the shared copies.

## Development

```bash
make run           # launch a sandbox IDE with the plugin installed
make test          # run tests
make clean         # remove build outputs and ./out
make distclean     # also remove .gradle and .intellijPlatform caches
```

Equivalent Gradle tasks: `runIde`, `test`, `clean`.

> **Keeping docs in sync:** this README is the source of truth for the plugin's
> JetBrains Marketplace overview. When you change features or settings, update both
> this file and the `<description>` in `src/main/resources/META-INF/plugin.xml`.

### Project structure

```
src/main/kotlin/com/github/shareddatasources/
├── SharedDataSourcesActivity.kt       # Post-startup sync logic (symlink creation, backup)
├── SharedDataSourcesConfigurable.kt   # Settings UI (Settings → Tools → Shared DataSources)
└── SharedDataSourcesSettings.kt       # Persistent application-level settings (sharedDataSources.xml)

src/main/resources/META-INF/
├── plugin.xml                         # Plugin descriptor & extension registrations
├── pluginIcon.svg                     # Plugin logo (light theme)
└── pluginIcon_dark.svg                # Plugin logo (dark theme)

build.gradle.kts                       # IntelliJ Platform Gradle Plugin 2.x build
gradle.properties                      # Plugin coordinates & platform versions
Makefile                               # Convenience build/run wrappers
```

## Notes & Limitations

- The default shared directory lives under the **OS temp directory**, which the system may clean
  automatically (e.g. on reboot or by Storage Sense). Once you start relying on the shared data
  sources, point the setting at a persistent location instead.
- The sync only runs **on project open**; changes made to the shared files while a project is
  already open are picked up by the IDE naturally, since it is the same underlying file.
- The plugin never deletes data silently: regular files are backed up first (when auto-backup is on),
  and backup files are not overwritten if they already exist.
- Shared file names are fixed: `dataSources.xml` and `dataSources.local.xml`.
