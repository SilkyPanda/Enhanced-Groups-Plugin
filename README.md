# Enhanced Groups (Purpur plugin)

A server-side port of the Fabric mod [Enhanced Groups](https://modrinth.com/mod/enhanced-groups) for Bukkit/Paper/Purpur servers. The project targets Minecraft 26.3 and Java 25.

## Requirements and client behavior

Install the Bukkit/Paper build of Simple Voice Chat 2.6.24 or newer on the server. The plugin uses its Bukkit API service. Players can join the Minecraft server without a Simple Voice Chat client mod; they simply cannot use voice chat or be managed as a voice-chat connection until they connect through a compatible Simple Voice Chat client. Fabric, Forge, NeoForge, and other SVC-supported client loaders are fine.

## Build on GitHub

The included GitHub Actions workflow builds the jar on every push and pull request and can also be started manually:

1. Upload this project to a GitHub repository.
2. Open **Actions → Build plugin → Run workflow** for a manual build. Pushes and pull requests trigger it automatically.
3. Open the completed workflow run and download the `EnhancedGroups-<commit>` artifact. The plugin jar is inside.

GitHub Actions installs Gradle 9.5.1 and Java 25 for the build, so you do not need to install either locally. GitHub Actions artifacts are retained for 30 days.

## Install

Put the downloaded `EnhancedGroups-1.0.0.jar` in the server's `plugins` directory alongside the Simple Voice Chat Bukkit plugin, then restart. Configuration and group data are created under `plugins/EnhancedGroups/`.

## Commands

| Command | Purpose |
|---|---|
| `/instantgroup [range]` | Add nearby voice-connected players who are not already in a group to your current group, or create an open temporary group. Range defaults to 128 blocks. |
| `/persistentgroup list` | List saved groups. |
| `/persistentgroup add <name> [normal\|open\|isolated] [hidden] [password]` | Create a persistent group. Quote names containing spaces, e.g. `/persistentgroup add "Staff Chat" open true`. |
| `/persistentgroup remove <name\|id>` | Remove a persistent group if SVC allows removal. |
| `/autojoingroup set <name> [password]` | Set your personal auto-join group. |
| `/autojoingroup remove` | Remove your personal auto-join setting. |
| `/autojoingroup global set <name>` | Set the global auto-join group (must not have a password). |
| `/autojoingroup global remove` | Clear the global auto-join group. |
| `/autojoingroup global force <true\|false>` | Choose whether global auto-join overrides personal settings. |
| `/forcejoingroup <player>` | Move an online player connected to voice chat into your current group. |

Forced group types, group summaries, and permission defaults can be configured in `config.yml`. The chat prefix is configurable in `messages.yml`. Bukkit permission nodes are `enhancedgroups.instantgroup`, `enhancedgroups.persistentgroup`, `enhancedgroups.autojoingroup`, `enhancedgroups.autojoingroup.global`, and `enhancedgroups.forcejoingroup`. `enhancedgroups.admin` grants the commands regardless of their normal defaults.

Passwords for persistent groups are stored as plain text because auto-join and group restoration require them. Protect the plugin data directory accordingly.

## GitHub Actions output

Workflow: `.github/workflows/build.yml`  
Jar output: `build/libs/EnhancedGroups-1.0.0.jar`
