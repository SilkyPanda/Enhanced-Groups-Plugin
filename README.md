# Enhanced Groups

Enhanced Groups adds server-side management for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) groups. It is a Bukkit plugin port inspired by the original [Enhanced Groups Fabric mod](https://modrinth.com/mod/enhanced-groups), with credit to its original author.

## Requirements

- Minecraft **26.3** server. The plugin is built for **Purpur 26.3 build 2643**.
- **Java 25**.
- The **Bukkit** version of Simple Voice Chat **2.6.24 or newer**, installed on the server.
- Paper 26.3 may be compatible, but has not been tested separately. Spigot is not a supported target.

Players do not install Enhanced Groups. Players who want to use voice chat still need a compatible Simple Voice Chat client; clients using SVC-supported mod loaders can connect. Players without the SVC client can join Minecraft, but cannot use voice chat or voice-group features.

## Download and install

1. Install the **Bukkit server plugin** version of [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), version **2.6.24 or newer**, for your Minecraft server version. It is a required dependency; the Fabric client mod by itself is not enough on the server.
2. Download the Enhanced Groups JAR from this project's GitHub **Releases**. If no release is available, download the `EnhancedGroups-...` artifact from the latest successful **Build plugin** run under **Actions** and extract the JAR from the ZIP.
3. Put both plugin JARs in the server's `plugins` folder. The Simple Voice Chat JAR must be its Bukkit/Paper server plugin build.
4. Start or restart the server. Enhanced Groups will load after Simple Voice Chat.
5. Configure `plugins/EnhancedGroups/config.yml` and `messages.yml`, then restart the server to apply changes.

Players do not install Enhanced Groups. Players who want to use voice chat need a compatible Simple Voice Chat client, available for supported mod loaders. Players without the voice chat client can still join the Minecraft server, but cannot use voice chat or voice-group features.

The plugin creates its configuration, messages, saved groups, and auto-join data in `plugins/EnhancedGroups/`.

## Features

- Create temporary groups with nearby voice-connected players.
- Save persistent groups and restore them after server restarts.
- Set personal or server-wide auto-join groups.
- Move another voice-connected player into your current group.
- Optionally force the group type used when players create groups through the SVC GUI.
- Show players a join summary for visible voice groups when they enter the server.

## Commands

| Command | What it does |
|---|---|
| `/instantgroup [range]` | Add nearby voice-connected players who are not already in a group to your current group. If you are not in a group, create an open temporary group. The range defaults to 128 blocks. |
| `/persistentgroup list` | List saved persistent groups. |
| `/persistentgroup add <name> [normal\|open\|isolated] [hidden] [password]` | Create a persistent group. Use `true` or `false` for `hidden`; quote names with spaces, for example `/persistentgroup add "Staff Chat" open true`. |
| `/persistentgroup remove <name\|id>` | Remove a saved group, if Simple Voice Chat allows it. |
| `/autojoingroup set <name> [password]` | Set your personal auto-join group. |
| `/autojoingroup remove` | Clear your personal auto-join setting. |
| `/autojoingroup global set <name>` | Set the server-wide auto-join group. The group must not have a password. |
| `/autojoingroup global remove` | Clear the server-wide auto-join group. |
| `/autojoingroup global force <true\|false>` | Choose whether the server-wide setting overrides personal settings. |
| `/forcejoingroup <player>` | Move an online player with an SVC connection into your current voice group. |

The group types are Simple Voice Chat's `normal`, `open`, and `isolated` types. In `config.yml`, `force_group_type` can be `OFF`, `NORMAL`, `OPEN`, or `ISOLATED`; it applies to groups created through the SVC GUI, not groups explicitly created with `/persistentgroup add`.

## Permissions and configuration

Permission nodes:

- `enhancedgroups.instantgroup`
- `enhancedgroups.persistentgroup`
- `enhancedgroups.autojoingroup`
- `enhancedgroups.autojoingroup.global`
- `enhancedgroups.forcejoingroup`
- `enhancedgroups.admin` — bypasses the command permission checks.

The default command permission levels are configurable in `config.yml`: `EVERYONE`, `OPS`, or `NOONE`. A permission plugin can also grant individual nodes. The same file controls the instant-group range and name, group summaries, and forced group type. Text and the chat prefix can be changed in `messages.yml`.

Persistent-group passwords are stored in plain text so the plugin can restore groups and use passwords for auto-join. Restrict access to the plugin data folder accordingly.

## Original project

This plugin is a server-side port inspired by [Enhanced Groups for Fabric](https://modrinth.com/mod/enhanced-groups). It uses the Simple Voice Chat API on the server; players do not need the original Fabric mod.
