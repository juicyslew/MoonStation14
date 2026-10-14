# Local development launcher

Run `./ms14.ps1 -Worktree main|01|02|03|04|05 -Clients 1|2` (defaults: `main`, one client). `./ms14.ps1 -Stop` stops verified development server and client JVMs across the registered MoonStation14 worktree family.

The launcher resolves worktrees from Git registration and their canonical family directory names, not the currently checked-out branch. It passes the selected worktree explicitly to Gradle with `--project-dir`, so invocation from another working directory still prepares and launches the selected tree. It uses NeoForge ModDev 2.0.141's own `createServerLaunchScript`, `createClientLaunchScript`, and optionally `createClient2LaunchScript` tasks through `tooling/dev_launcher/prepare.init.gradle`; it does not recreate Java classpaths or modify run configurations. The second client uses `run/client2` while server/client retain their configured run directories.

Before stopping anything or removing a world, launch-script preparation and manifest validation must succeed. The launcher then stops only Java processes whose command identifies `net.neoforged.devlaunch.Main` and an exact role-specific argument file under a registered worktree's `build/moddev`. It excludes GameTest JVMs and never kills a process based on port ownership. If the configured server port remains occupied by an unverified process, launch aborts without deleting the world.

Successful launches remove only the selected worktree's exact `run/world` directory after processes have stopped and reparse-point / `level-name` checks pass. This is an intentional destructive reset, not a backup or migration. Existing run configuration, `server.properties`, and all other run directories are preserved. Server/launcher command wrappers and one per-launch lifetime monitor run hidden in the background; the monitor shuts down that launch's verified server after its last launcher-started client exits. `-Stop` and replacement launches also stop the owned monitor. Per-role output is captured under `run/logs/dev-launcher-<role>.log`; Minecraft clients create their normal GUI windows. Forced JVM termination may lose unsaved world changes. Client windows start immediately after the server process is launched; the launcher does not wait for server readiness.

`-Stop` does not prepare builds or touch worlds. It leaves unrelated Java processes, Gradle processes, foreign port holders, and GameTest processes alone.

On a worktree's first server run, accept the vanilla server EULA manually in `run/eula.txt` if required; the launcher does not accept terms or modify that file.

Run the isolated fake-process lifecycle fixtures with `pwsh -NoProfile -File tooling/dev_launcher/test_launcher.ps1`. They use a temporary Git repository and fake Gradle/launch scripts; they do not start Minecraft.
