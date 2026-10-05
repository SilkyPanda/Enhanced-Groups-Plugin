package dev.enhancedgroups;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Bukkit/Purpur port of Simple Voice Chat Enhanced Groups. */
public final class EnhancedGroupsPlugin extends JavaPlugin implements Listener {
    private final ConcurrentMap<UUID, SavedGroup> persistentGroups = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> runtimeToSaved = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> personalAutoJoin = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> pluginCreatedGroups = ConcurrentHashMap.newKeySet();
    private volatile UUID globalAutoJoin;
    private volatile boolean forceGlobalAutoJoin;
    private volatile VoicechatServerApi voicechat;
    private volatile String forcedType = "OFF";
    private volatile YamlConfiguration messages;
    private YamlConfiguration persistentConfig;
    private YamlConfiguration autoJoinConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        forcedType = getConfig().getString("force_group_type", "OFF").toUpperCase(Locale.ROOT);
        configurePermissionDefaults();
        loadStores();
        getServer().getPluginManager().registerEvents(this, this);
        for (String name : List.of("instantgroup", "persistentgroup", "autojoingroup", "forcejoingroup")) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(new Commands());
                command.setTabCompleter(new Commands());
            }
        }
        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            getLogger().severe("Simple Voice Chat's Bukkit API service was not found; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        service.registerPlugin(new SvcHook());
        getLogger().info("Enhanced Groups enabled; voice features will be available to players connected to Simple Voice Chat.");
    }

    @Override
    public void onDisable() {
        saveStores();
        voicechat = null;
        runtimeToSaved.clear();
    }

    private void loadStores() {
        File groupsFile = new File(getDataFolder(), "persistent-groups.yml");
        persistentConfig = YamlConfiguration.loadConfiguration(groupsFile);
        ConfigurationSection section = persistentConfig.getConfigurationSection("groups");
        if (section != null) for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection g = section.getConfigurationSection(key);
                if (g != null) persistentGroups.put(id, new SavedGroup(id, g.getString("name", ""), g.getString("password"),
                        g.getString("type", "NORMAL"), g.getBoolean("hidden")));
            } catch (IllegalArgumentException e) { getLogger().warning("Ignoring invalid persistent group id: " + key); }
        }
        autoJoinConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "auto-join-groups.yml"));
        ConfigurationSection personal = autoJoinConfig.getConfigurationSection("players");
        if (personal != null) for (String player : personal.getKeys(false)) try {
            personalAutoJoin.put(UUID.fromString(player), UUID.fromString(personal.getString(player)));
        } catch (RuntimeException e) { getLogger().warning("Ignoring invalid auto-join entry for " + player); }
        globalAutoJoin = parseUuid(autoJoinConfig.getString("global_group"));
        forceGlobalAutoJoin = autoJoinConfig.getBoolean("force_global", false);
    }

    private void saveStores() {
        if (persistentConfig == null || autoJoinConfig == null) return;
        YamlConfiguration p = new YamlConfiguration();
        persistentGroups.values().stream().sorted(Comparator.comparing(g -> g.id.toString())).forEach(g -> {
            String path = "groups." + g.id;
            p.set(path + ".name", g.name); p.set(path + ".password", g.password);
            p.set(path + ".type", g.type); p.set(path + ".hidden", g.hidden);
        });
        persistentConfig = p;
        YamlConfiguration a = new YamlConfiguration();
        personalAutoJoin.forEach((id, group) -> a.set("players." + id, group.toString()));
        a.set("global_group", globalAutoJoin == null ? null : globalAutoJoin.toString());
        a.set("force_global", forceGlobalAutoJoin);
        autoJoinConfig = a;
        atomicSave(p, new File(getDataFolder(), "persistent-groups.yml"));
        atomicSave(a, new File(getDataFolder(), "auto-join-groups.yml"));
    }

    private void atomicSave(YamlConfiguration config, File target) {
        try {
            getDataFolder().mkdirs();
            File temp = new File(getDataFolder(), target.getName() + ".tmp");
            config.save(temp);
            try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (Exception ignored) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) { getLogger().severe("Could not save " + target.getName() + ": " + e.getMessage()); }
    }

    private UUID parseUuid(String value) { try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException e) { return null; } }

    private void configurePermissionDefaults() {
        Map<String, String> nodes = Map.of(
                "instantgroup", "enhancedgroups.instantgroup", "persistentgroup", "enhancedgroups.persistentgroup",
                "autojoingroup", "enhancedgroups.autojoingroup", "autojoingroup_global", "enhancedgroups.autojoingroup.global",
                "forcejoingroup", "enhancedgroups.forcejoingroup");
        nodes.forEach((key, node) -> {
            Permission permission = getServer().getPluginManager().getPermission(node);
            if (permission != null) permission.setDefault(switch (getConfig().getString("permission_defaults." + key, "OPS").toUpperCase(Locale.ROOT)) {
                case "EVERYONE" -> PermissionDefault.TRUE;
                case "NOONE" -> PermissionDefault.FALSE;
                default -> PermissionDefault.OP;
            });
        });
    }

    private boolean hasPermission(CommandSender sender, String node, String setting) {
        return sender.hasPermission("enhancedgroups.admin") || sender.hasPermission(node);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("group_summary", true)) return;
        Bukkit.getScheduler().runTaskLater(this, () -> sendSummary(event.getPlayer()), 20L);
    }

    private void sendSummary(Player joining) {
        VoicechatServerApi api = voicechat;
        if (api == null || !joining.isOnline()) return;
        Map<UUID, Group> groups = new java.util.LinkedHashMap<>();
        int memberCount = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            VoicechatConnection connection = api.getConnectionOf(online.getUniqueId());
            if (connection == null || !connection.isInGroup()) continue;
            Group group = connection.getGroup();
            if (group == null || group.isHidden()) continue;
            memberCount++;
            groups.putIfAbsent(group.getId(), group);
        }
        if (memberCount == 0) return;
        joining.sendMessage(Component.text("There are currently " + memberCount + " player(s) in voice groups:", NamedTextColor.GRAY));
        for (Group group : groups.values()) {
            Component row = Component.text("- ", NamedTextColor.DARK_GRAY).append(Component.text(group.getName(), NamedTextColor.GRAY));
            if (!group.hasPassword()) row = row.append(Component.text(" ")).append(Component.text("[Join]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/voicechat join " + group.getId())));
            joining.sendMessage(row);
        }
    }

    private void msg(CommandSender sender, String text) {
        String prefix = messages == null ? "&8[&aEnhancedGroups&8] &r" : messages.getString("prefix", "&8[&aEnhancedGroups&8] &r");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + text));
    }
    private Group.Type groupType(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) { case "OPEN" -> Group.Type.OPEN; case "ISOLATED" -> Group.Type.ISOLATED; default -> Group.Type.NORMAL; };
    }

    private final class SvcHook implements VoicechatPlugin {
        public String getPluginId() { return "enhancedgroups"; }
        public void initialize(de.maxhenkel.voicechat.api.VoicechatApi api) { }
        public void registerEvents(EventRegistration events) {
            events.registerEvent(VoicechatServerStartedEvent.class, this::started);
            events.registerEvent(VoicechatServerStoppedEvent.class, e -> { voicechat = null; runtimeToSaved.clear(); });
            events.registerEvent(PlayerConnectedEvent.class, this::playerConnected);
            events.registerEvent(CreateGroupEvent.class, this::createGroup);
        }
        private void started(VoicechatServerStartedEvent event) {
            voicechat = event.getVoicechat();
            runtimeToSaved.clear();
            for (SavedGroup saved : persistentGroups.values()) try {
                Group group = voicechat.groupBuilder().setPersistent(true).setName(saved.name).setPassword(saved.password)
                        .setType(groupType(saved.type)).setHidden(saved.hidden).setId(saved.id).build();
                runtimeToSaved.put(group.getId(), saved.id);
            } catch (RuntimeException e) { getLogger().warning("Could not restore persistent group '" + saved.name + "': " + e.getMessage()); }
            getLogger().info("Restored " + runtimeToSaved.size() + " persistent voice groups.");
        }
        private void playerConnected(PlayerConnectedEvent event) {
            UUID player = event.getConnection().getPlayer().getUuid();
            UUID savedId = forceGlobalAutoJoin && globalAutoJoin != null
                    ? globalAutoJoin
                    : personalAutoJoin.getOrDefault(player, globalAutoJoin);
            if (savedId == null) return;
            UUID runtimeId = runtimeToSaved.entrySet().stream().filter(e -> e.getValue().equals(savedId)).map(Map.Entry::getKey).findFirst().orElse(null);
            Group group = runtimeId == null ? null : event.getVoicechat().getGroup(runtimeId);
            if (group != null) event.getConnection().setGroup(group);
        }
        private void createGroup(CreateGroupEvent event) {
            if ("OFF".equals(forcedType) || event.getConnection() == null) return;
            Group original = event.getGroup();
            if (original == null || pluginCreatedGroups.remove(original.getId()) || original.getType().equals(groupType(forcedType))) return;
            if (!List.of("NORMAL", "OPEN", "ISOLATED").contains(forcedType)) return;
            String password = readPassword(original);
            if (original.hasPassword() && password == null) {
                event.cancel();
                getLogger().warning("Cancelled group type replacement because the group password could not be read safely.");
                return;
            }
            event.cancel();
            try {
                Group replacement = event.getVoicechat().groupBuilder().setType(groupType(forcedType)).setPassword(password)
                        .setName(original.getName()).setPersistent(original.isPersistent()).setHidden(original.isHidden()).build();
                pluginCreatedGroups.add(replacement.getId());
                event.getConnection().setGroup(replacement);
            } catch (RuntimeException e) { getLogger().warning("Could not replace group type: " + e.getMessage()); }
        }
    }

    private String readPassword(Group group) {
        try {
            Field f = group.getClass().getDeclaredField("group"); f.setAccessible(true); Object model = f.get(group);
            for (Class<?> c = model.getClass(); c != null; c = c.getSuperclass()) try {
                Field p = c.getDeclaredField("password"); p.setAccessible(true); return (String) p.get(model);
            } catch (NoSuchFieldException ignored) { }
        } catch (ReflectiveOperationException | RuntimeException e) { getLogger().warning("Unable to read voice group password for forced type change."); }
        return null;
    }

    private final class Commands implements CommandExecutor, TabCompleter {
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            String root = command.getName().toLowerCase(Locale.ROOT);
            if (root.equals("instantgroup")) return instant(sender, args);
            if (root.equals("persistentgroup")) return persistent(sender, args);
            if (root.equals("autojoingroup")) return autoJoin(sender, args);
            if (root.equals("forcejoingroup")) return forceJoin(sender, args);
            return false;
        }
        @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> options = switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "persistentgroup" -> args.length == 1 ? List.of("list", "add", "remove") : args.length == 3 && args[0].equalsIgnoreCase("add") ? List.of("normal", "open", "isolated") : groupNames();
                case "autojoingroup" -> args.length == 1 ? List.of("set", "remove", "global") : args.length == 2 && args[0].equalsIgnoreCase("global") ? List.of("set", "remove", "force") : args.length == 3 && args[1].equalsIgnoreCase("force") ? List.of("true", "false") : groupNames();
                case "forcejoingroup" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
            String start = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
            return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(start)).toList();
        }
        private List<String> groupNames() { return persistentGroups.values().stream().map(g -> g.name).toList(); }

        private boolean instant(CommandSender sender, String[] args) {
            if (!hasPermission(sender, "enhancedgroups.instantgroup", "instantgroup")) return deny(sender);
            if (!(sender instanceof Player player)) { msg(sender, "This command can only be used by a player."); return true; }
            VoicechatServerApi api = voicechat; if (api == null) return unavailable(sender);
            double range = getConfig().getDouble("default_instant_group_range", 128D);
            try { if (args.length > 1) throw new NumberFormatException(); if (args.length == 1) range = Double.parseDouble(args[0]); }
            catch (NumberFormatException e) { msg(sender, "&cRange must be a positive number."); return true; }
            if (!Double.isFinite(range) || range < 1) { msg(sender, "&cRange must be at least 1."); return true; }
            VoicechatConnection own = api.getConnectionOf(player.getUniqueId());
            if (own == null) return unavailable(sender);
            Group target = own.isInGroup() ? own.getGroup() : null;
            boolean created = false;
            if (target == null) try {
                target = api.groupBuilder().setName(getConfig().getString("instant_group_name", "Instant Group")).setType(Group.Type.OPEN).build();
                created = true;
            } catch (RuntimeException e) { msg(sender, "&cInvalid instant group name in config.yml."); return true; }
            AtomicInteger added = new AtomicInteger();
            Location center = player.getLocation();
            for (Player nearby : player.getWorld().getPlayers()) {
                if (Math.abs(nearby.getLocation().getX() - center.getX()) > range || Math.abs(nearby.getLocation().getY() - center.getY()) > range || Math.abs(nearby.getLocation().getZ() - center.getZ()) > range) continue;
                VoicechatConnection connection = api.getConnectionOf(nearby.getUniqueId());
                if (connection == null || connection.isInGroup()) continue;
                connection.setGroup(target); added.incrementAndGet();
            }
            msg(sender, "&aAdded " + added.get() + " player(s) to " + target.getName() + (created ? "." : " (your current group)."));
            return true;
        }

        private boolean persistent(CommandSender sender, String[] args) {
            if (!hasPermission(sender, "enhancedgroups.persistentgroup", "persistentgroup")) return deny(sender);
            if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                if (persistentGroups.isEmpty()) { msg(sender, "There are no persistent groups."); return true; }
                for (SavedGroup g : persistentGroups.values()) msg(sender, "&f" + g.name + " &7(" + g.type.toLowerCase(Locale.ROOT) + (g.hidden ? ", hidden" : "") + ") &8ID: " + g.id);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
                if (voicechat == null) return unavailable(sender);
                // Syntax supports a quoted name: add "Staff Chat" [type] [hidden] [password]
                List<String> tokens = parseArgs(String.join(" ", args).substring(args[0].length()).trim());
                if (tokens.isEmpty()) { usage(sender, "/persistentgroup add <name> [normal|open|isolated] [hidden] [password]"); return true; }
                String name = tokens.get(0); int i = 1; String type = "normal"; boolean hidden = false; String password = null;
                if (i < tokens.size() && List.of("normal", "open", "isolated").contains(tokens.get(i).toLowerCase(Locale.ROOT))) type = tokens.get(i++);
                if (i < tokens.size() && (tokens.get(i).equalsIgnoreCase("true") || tokens.get(i).equalsIgnoreCase("false"))) hidden = Boolean.parseBoolean(tokens.get(i++));
                if (i < tokens.size()) password = String.join(" ", tokens.subList(i, tokens.size()));
                try {
                    Group group = voicechat.groupBuilder().setPersistent(true).setName(name).setPassword(password).setType(groupType(type)).setHidden(hidden).build();
                    SavedGroup saved = new SavedGroup(UUID.randomUUID(), group.getName(), password, type.toUpperCase(Locale.ROOT), hidden);
                    persistentGroups.put(saved.id, saved); runtimeToSaved.put(group.getId(), saved.id); saveStores();
                    msg(sender, "&aCreated persistent group " + saved.name + " (ID " + saved.id + ").");
                } catch (RuntimeException e) { msg(sender, "&cCould not create group: " + e.getMessage()); }
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) {
                SavedGroup saved = findGroup(String.join(" ", args).substring(args[0].length()).trim());
                if (saved == null) { msg(sender, "&cPersistent group not found."); return true; }
                UUID runtimeId = runtimeToSaved.entrySet().stream().filter(e -> e.getValue().equals(saved.id)).map(Map.Entry::getKey).findFirst().orElse(null);
                if (voicechat == null || runtimeId == null || !voicechat.removeGroup(runtimeId)) { msg(sender, "&cCould not remove group; it may still have members."); return true; }
                persistentGroups.remove(saved.id); runtimeToSaved.remove(runtimeId); saveStores(); msg(sender, "&aRemoved " + saved.name + "."); return true;
            }
            usage(sender, "/persistentgroup <list|add|remove>"); return true;
        }

        private boolean autoJoin(CommandSender sender, String[] args) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("global")) {
                if (!hasPermission(sender, "enhancedgroups.autojoingroup.global", "autojoingroup_global")) return deny(sender);
                if (args.length == 2 && args[1].equalsIgnoreCase("remove")) { globalAutoJoin = null; saveStores(); msg(sender, "&aGlobal auto-join removed."); return true; }
                if (args.length == 3 && args[1].equalsIgnoreCase("force")) {
                    if (!args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) { usage(sender, "/autojoingroup global force <true|false>"); return true; }
                    forceGlobalAutoJoin = Boolean.parseBoolean(args[2]); saveStores(); msg(sender, "&aForced global auto-join set to " + forceGlobalAutoJoin + "."); return true;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
                    List<String> groupArgs = parseArgs(String.join(" ", args).substring((args[0] + " " + args[1]).length()).trim());
                    SavedGroup g = groupArgs.isEmpty() ? null : findGroup(groupArgs.get(0));
                    if (g == null) { msg(sender, "&cPersistent group not found."); return true; }
                    if (g.password != null) { msg(sender, "&cGlobal auto-join groups cannot have a password."); return true; }
                    globalAutoJoin = g.id; saveStores(); msg(sender, "&aGlobal auto-join group set to " + g.name + "."); return true;
                }
                usage(sender, "/autojoingroup global <set <group>|remove|force <true|false>>"); return true;
            }
            if (!hasPermission(sender, "enhancedgroups.autojoingroup", "autojoingroup")) return deny(sender);
            if (!(sender instanceof Player player)) { msg(sender, "This command can only be used by a player."); return true; }
            if (args.length == 1 && args[0].equalsIgnoreCase("remove")) { personalAutoJoin.remove(player.getUniqueId()); saveStores(); msg(sender, "&aPersonal auto-join removed."); return true; }
            if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
                String rem = String.join(" ", args).substring(args[0].length()).trim(); List<String> parts = parseArgs(rem);
                if (parts.isEmpty()) { usage(sender, "/autojoingroup set <group> [password]"); return true; }
                SavedGroup g = findGroup(parts.get(0));
                if (g == null) { msg(sender, "&cPersistent group not found."); return true; }
                String password = parts.size() > 1 ? String.join(" ", parts.subList(1, parts.size())) : null;
                if (g.password != null && !g.password.equals(password)) { msg(sender, "&cWrong password."); return true; }
                personalAutoJoin.put(player.getUniqueId(), g.id); saveStores(); msg(sender, "&aYou will auto-join " + g.name + "."); return true;
            }
            usage(sender, "/autojoingroup <set <group> [password]|remove>"); return true;
        }

        private boolean forceJoin(CommandSender sender, String[] args) {
            if (!hasPermission(sender, "enhancedgroups.forcejoingroup", "forcejoingroup")) return deny(sender);
            if (!(sender instanceof Player executor)) { msg(sender, "This command can only be used by a player."); return true; }
            if (voicechat == null) return unavailable(sender);
            if (args.length != 1) { usage(sender, "/forcejoingroup <player>"); return true; }
            Player target = Bukkit.getPlayerExact(args[0]); if (target == null) { msg(sender, "&cPlayer is not online."); return true; }
            VoicechatConnection own = voicechat.getConnectionOf(executor.getUniqueId()), other = voicechat.getConnectionOf(target.getUniqueId());
            if (own == null || !own.isInGroup()) { msg(sender, "&cYou are not connected to voice chat or not in a group."); return true; }
            if (other == null) { msg(sender, "&cThat player is not connected to voice chat."); return true; }
            other.setGroup(own.getGroup()); msg(sender, "&aMoved " + target.getName() + " into your group."); return true;
        }

        private SavedGroup findGroup(String nameOrId) {
            try { SavedGroup byId = persistentGroups.get(UUID.fromString(nameOrId)); if (byId != null) return byId; } catch (IllegalArgumentException ignored) { }
            String name = unquote(nameOrId).trim(); return persistentGroups.values().stream().filter(g -> g.name.equalsIgnoreCase(name)).findFirst().orElse(null);
        }
        private List<String> parseArgs(String value) {
            List<String> out = new ArrayList<>(); StringBuilder b = new StringBuilder(); boolean quote = false;
            for (int i=0;i<value.length();i++) { char c=value.charAt(i); if (c=='"') quote=!quote; else if (Character.isWhitespace(c)&&!quote) { if (!b.isEmpty()) { out.add(b.toString()); b.setLength(0); } } else b.append(c); }
            if (!b.isEmpty()) out.add(b.toString()); return out;
        }
        private String unquote(String value) { return value.replaceAll("^\\\"|\\\"$", ""); }
        private boolean deny(CommandSender s) { msg(s, "&cYou do not have permission."); return true; }
        private boolean unavailable(CommandSender s) { msg(s, "&cVoice chat is not currently connected, or you are not connected to it."); return true; }
        private void usage(CommandSender s, String value) { msg(s, "&cUsage: " + value); }
    }

    private record SavedGroup(UUID id, String name, String password, String type, boolean hidden) { }
}
