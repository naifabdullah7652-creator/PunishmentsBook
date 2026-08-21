package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PunishmentsBook extends JavaPlugin implements Listener {

    private final Map<UUID, PunishmentData> punishments =
            new HashMap<UUID, PunishmentData>();

    private final Map<String, PunishmentData> ipPunishments =
            new HashMap<String, PunishmentData>();

    private File dataFile;
    private FileConfiguration dataConfig;

    private String prefix;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        prefix = color(getConfig().getString(
                "settings.prefix",
                "&cPunishments &7┃ "
        ));

        setupDataFile();
        loadPunishments();

        if (getCommand("pm") != null) {
            getCommand("pm").setExecutor(this);
        }

        if (getCommand("pmapply") != null) {
            getCommand("pmapply").setExecutor(this);
        }

        if (getCommand("pmrevoke") != null) {
            getCommand("pmrevoke").setExecutor(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(
                this,
                new Runnable() {
                    @Override
                    public void run() {
                        checkExpiredPunishments();
                    }
                },
                20L,
                20L
        );

        getLogger().info("PunishmentsBook enabled.");
    }

    @Override
    public void onDisable() {
        savePunishments();
    }

    // =========================================================
    // COMMANDS
    // =========================================================

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        // =====================================================
        // /pm
        // =====================================================

        if (command.getName().equalsIgnoreCase("pm")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(message(
                        "messages.errors.players-only"
                ));
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.use"
            )) {
                staff.sendMessage(message(
                        "messages.errors.no-permission"
                ));
                return true;
            }

            if (args.length != 1) {

                staff.sendMessage(
                        prefix + color(
                                "&cUsage: /pm <player>"
                        )
                );

                return true;
            }

            Player target =
                    Bukkit.getPlayerExact(args[0]);

            if (target == null) {

                staff.sendMessage(
                        message(
                                "messages.errors.player-not-online"
                        )
                );

                return true;
            }

            openBook(
                    staff,
                    target.getName()
            );

            return true;
        }

        // =====================================================
        // /pmapply
        // =====================================================

        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {

                sender.sendMessage(
                        message(
                                "messages.errors.players-only"
                        )
                );

                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.use"
            )) {

                staff.sendMessage(
                        message(
                                "messages.errors.no-permission"
                        )
                );

                return true;
            }

            if (args.length != 2) {

                staff.sendMessage(
                        prefix + color(
                                "&cUsage: /pmapply <player> <punishment>"
                        )
                );

                return true;
            }

            applyPunishment(
                    staff,
                    args[0],
                    args[1]
            );

            return true;
        }

        // =====================================================
        // /pmrevoke
        // =====================================================

        if (command.getName().equalsIgnoreCase("pmrevoke")) {

            /*
             * Console IS allowed.
             */

            if (!sender.hasPermission(
                    "punishmentsbook.revoke"
            )) {

                sender.sendMessage(
                        message(
                                "revoke.messages.no-permission"
                        )
                );

                return true;
            }

            if (args.length != 1) {

                sender.sendMessage(
                        prefix + color(
                                "&cUsage: /pmrevoke <player>"
                        )
                );

                return true;
            }

            revokePunishment(
                    sender,
                    args[0]
            );

            return true;
        }

        return false;
    }

    // =========================================================
    // BOOK
    // =========================================================

    private void openBook(
            Player player,
            String target
    ) {

        try {

            net.minecraft.server.v1_8_R3.ItemStack nmsBook =
                    createBook(target);

            ItemStack bukkitBook =
                    CraftItemStack.asBukkitCopy(nmsBook);

            ItemStack oldItem =
                    player.getItemInHand();

            player.setItemInHand(
                    bukkitBook
            );

            player.updateInventory();

            EntityPlayer entity =
                    ((CraftPlayer) player).getHandle();

            entity.openBook(nmsBook);

            Bukkit.getScheduler().runTaskLater(
                    this,
                    new Runnable() {
                        @Override
                        public void run() {

                            player.setItemInHand(
                                    oldItem
                            );

                            player.updateInventory();
                        }
                    },
                    5L
            );

        } catch (Throwable ex) {

            getLogger().warning(
                    "Could not open punishment book: "
                            + ex.getClass().getSimpleName()
                            + ": "
                            + ex.getMessage()
            );

            player.sendMessage(
                    prefix + color(
                            "&cCould not open punishment book."
                    )
            );
        }
    }

    private net.minecraft.server.v1_8_R3.ItemStack createBook(
            String target
    ) {

        ItemStack base =
                new ItemStack(
                        Material.WRITTEN_BOOK
                );

        net.minecraft.server.v1_8_R3.ItemStack book =
                CraftItemStack.asNMSCopy(base);

        NBTTagCompound tag =
                new NBTTagCompound();

        String title =
                getConfig().getString(
                        "book.title",
                        "Punishments"
                );

        String author =
                getConfig().getString(
                        "book.author",
                        "PunishmentsBook"
                );

        tag.setString(
                "title",
                title
        );

        tag.setString(
                "author",
                author
        );

        NBTTagList pages =
                new NBTTagList();

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section == null ||
                section.getKeys(false).isEmpty()) {

            pages.add(
                    new NBTTagString(
                            "{\"text\":\"No punishments configured.\",\"color\":\"red\"}"
                    )
            );

            tag.set(
                    "pages",
                    pages
            );

            book.setTag(tag);

            return book;
        }

        StringBuilder json =
                new StringBuilder();

        json.append(
                "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\",\"bold\":true,\"extra\":["
        );

        boolean first = true;

        for (String id :
                section.getKeys(false)) {

            String name =
                    getConfig().getString(
                            "punishments."
                                    + id
                                    + ".name",
                            id
                    );

            if (!first) {
                json.append(",");
            }

            first = false;

            String command =
                    "/pmapply "
                            + target
                            + " "
                            + id;

            json.append("{");

            json.append(
                    "\"text\":\""
            );

            json.append(
                    escapeJson(name)
            );

            json.append(
                    "\","
            );

            json.append(
                    "\"color\":\"black\","
            );

            json.append(
                    "\"underlined\":true,"
            );

            json.append(
                    "\"clickEvent\":{\"action\":\"run_command\",\"value\":\""
            );

            json.append(
                    escapeJson(command)
            );

            json.append(
                    "\"},"
            );

            json.append(
                    "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\""
            );

            String hover =
                    getConfig().getString(
                            "book.hover",
                            "Click to apply punishment"
                    );

            json.append(
                    escapeJson(hover)
            );

            json.append(
                    "\"}"
            );

            json.append("}");

            json.append(
                    ",{\"text\":\"\\n\"}"
            );
        }

        json.append(
                "]}"
        );

        pages.add(
                new NBTTagString(
                        json.toString()
                )
        );

        tag.set(
                "pages",
                pages
        );

        book.setTag(tag);

        return book;
    }

    // =========================================================
    // APPLY
    // =========================================================

    private void applyPunishment(
            Player staff,
            String targetName,
            String id
    ) {

        String path =
                "punishments."
                        + id;

        if (!getConfig().isConfigurationSection(
                path
        )) {

            staff.sendMessage(
                    message(
                            "messages.errors.unknown-punishment"
                    )
            );

            return;
        }

        Player target =
                Bukkit.getPlayerExact(
                        targetName
                );

        if (target == null) {

            staff.sendMessage(
                    message(
                            "messages.errors.player-not-online"
                    )
            );

            return;
        }

        // =====================================================
        // RANK PRIORITY
        // =====================================================

        if (getConfig().getBoolean(
                "rank-priority.enabled",
                true
        )) {

            int staffRank =
                    getRankPriority(staff);

            int targetRank =
                    getRankPriority(target);

            if (staffRank < 0 ||
                    targetRank < 0) {

                staff.sendMessage(
                        color(
                                getConfig().getString(
                                        "rank-priority.messages.unknown-rank",
                                        "&cCould not determine your rank or the target's rank."
                                )
                        )
                );

                return;
            }

            /*
             * Higher rank number = higher authority.
             *
             * Staff 80 cannot punish target 90.
             * Staff 80 cannot punish target 80.
             * Staff 80 can punish target 70.
             */

            if (targetRank > staffRank) {

                staff.sendMessage(
                        color(
                                getConfig().getString(
                                        "rank-priority.messages.higher-rank",
                                        "&cYou cannot punish a staff member with a higher rank!"
                                )
                        )
                );

                return;
            }

            if (targetRank == staffRank) {

                staff.sendMessage(
                        color(
                                getConfig().getString(
                                        "rank-priority.messages.same-rank",
                                        "&cYou cannot punish a staff member with the same rank!"
                                )
                        )
                );

                return;
            }
        }

        String name =
                getConfig().getString(
                        path + ".name",
                        id
                );

        String type =
                getConfig().getString(
                        path + ".type",
                        "MUTE"
                );

        String duration =
                getConfig().getString(
                        path + ".duration",
                        ""
                );

        String reason =
                getConfig().getString(
                        path + ".reason",
                        name
                );

        long durationMillis =
                parseDuration(duration);

        long expires = 0L;

        if (durationMillis > 0L) {

            expires =
                    System.currentTimeMillis()
                            + durationMillis;
        }

        removeExistingPunishment(
                target
        );

        PunishmentData data =
                new PunishmentData(
                        target.getUniqueId(),
                        target.getName(),
                        name,
                        type,
                        duration,
                        reason,
                        staff.getName(),
                        expires,
                        getPlayerIP(target)
                );

        punishments.put(
                target.getUniqueId(),
                data
        );

        if (type.equalsIgnoreCase(
                "IP-BAN"
        )) {

            String ip =
                    getPlayerIP(target);

            if (ip != null) {

                ipPunishments.put(
                        ip,
                        data
                );
            }
        }

        savePunishment(data);

        // =====================================================
        // JAIL
        // =====================================================

        if (type.equalsIgnoreCase(
                "JAIL"
        )) {

            String configuredCommand =
                    getConfig().getString(
                            path + ".command",
                            ""
                    );

            executeConfiguredCommand(
                    staff,
                    configuredCommand,
                    target,
                    duration,
                    reason
            );
        }

        // =====================================================
        // BAN
        // =====================================================

        if (type.equalsIgnoreCase(
                "BAN"
        )) {

            String kickMessage =
                    getConfig().getString(
                            "messages.banned.message",
                            "&cYou are banned."
                    );

            kickMessage =
                    replaceData(
                            kickMessage,
                            data
                    );

            target.kickPlayer(
                    color(kickMessage)
            );
        }

        // =====================================================
        // IP BAN
        // =====================================================

        if (type.equalsIgnoreCase(
                "IP-BAN"
        )) {

            String kickMessage =
                    getConfig().getString(
                            "messages.ip-banned.message",
                            "&cYour IP is banned."
                    );

            kickMessage =
                    replaceData(
                            kickMessage,
                            data
                    );

            target.kickPlayer(
                    color(kickMessage)
            );
        }

        sendStaffDetails(
                staff,
                target,
                name,
                type,
                duration,
                reason
        );

        sendTargetDetails(
                target,
                name,
                type,
                duration,
                reason,
                staff.getName()
        );
    }

    // =========================================================
    // RANK PRIORITY
    // =========================================================

    private int getRankPriority(
            Player player
    ) {

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "rank-priority.groups"
                );

        if (section == null) {
            return -1;
        }

        int highestRank =
                -1;

        for (String rank :
                section.getKeys(false)) {

            int priority =
                    section.getInt(
                            rank,
                            -1
                    );

            if (priority < 0) {
                continue;
            }

            String permission =
                    "punishmentsbook.rank."
                            + priority;

            if (player.hasPermission(
                    permission
            )) {

                if (priority > highestRank) {

                    highestRank =
                            priority;
                }
            }
        }

        return highestRank;
    }

    // =========================================================
    // REVOKE
    // =========================================================

    private void revokePunishment(
            CommandSender sender,
            String targetName
    ) {

        Player onlineTarget =
                Bukkit.getPlayerExact(
                        targetName
                );

        UUID targetUUID = null;

        if (onlineTarget != null) {

            targetUUID =
                    onlineTarget.getUniqueId();

        } else {

            /*
             * Offline player:
             * Search saved punishment by name.
             */

            for (PunishmentData data :
                    punishments.values()) {

                if (data.player.equalsIgnoreCase(
                        targetName
                )) {

                    targetUUID =
                            data.uuid;

                    break;
                }
            }
        }

        if (targetUUID == null) {

            sender.sendMessage(
                    message(
                            "revoke.messages.no-punishment"
                    )
            );

            return;
        }

        PunishmentData data =
                punishments.get(
                        targetUUID
                );

        if (data == null) {

            sender.sendMessage(
                    message(
                            "revoke.messages.no-punishment"
                    )
            );

            return;
        }

        String type =
                data.type;

        boolean success =
                true;

        String staffName;

        if (sender instanceof Player) {
            staffName =
                    ((Player) sender).getName();
        } else {
            staffName =
                    "Console";
        }

        // =====================================================
        // JAIL
        // =====================================================

        if (type.equalsIgnoreCase(
                "JAIL"
        )) {

            boolean enabled =
                    getConfig().getBoolean(
                            "revoke.jail.enabled",
                            true
                    );

            if (enabled) {

                String command =
                        getConfig().getString(
                                "revoke.jail.command",
                                "unjail %player%"
                        );

                /*
                 * If the sender is a player:
                 * execute as player.
                 *
                 * If Console:
                 * execute as console.
                 *
                 * This is required because an offline player
                 * cannot execute commands.
                 */

                command =
                        replaceCommandData(
                                command,
                                data,
                                staffName
                        );

                if (command.startsWith("/")) {
                    command =
                            command.substring(1);
                }

                success =
                        Bukkit.dispatchCommand(
                                sender,
                                command
                        );
            }
        }

        // =====================================================
        // MUTE
        // =====================================================

        else if (type.equalsIgnoreCase(
                "MUTE"
        )) {

            success =
                    getConfig().getBoolean(
                            "revoke.mute.enabled",
                            true
                    );
        }

        // =====================================================
        // BAN
        // =====================================================

        else if (type.equalsIgnoreCase(
                "BAN"
        )) {

            success =
                    getConfig().getBoolean(
                            "revoke.ban.enabled",
                            true
                    );
        }

        // =====================================================
        // IP BAN
        // =====================================================

        else if (type.equalsIgnoreCase(
                "IP-BAN"
        )) {

            success =
                    getConfig().getBoolean(
                            "revoke.ip-ban.enabled",
                            true
                    );

            if (data.ip != null) {

                ipPunishments.remove(
                        data.ip
                );
            }
        }

        if (!success) {

            sender.sendMessage(
                    message(
                            "messages.errors.command-failed"
                    )
            );

            return;
        }

        punishments.remove(
                targetUUID
        );

        removePunishmentFromFile(
                targetUUID
        );

        if (onlineTarget != null) {

            removeIPPunishment(
                    onlineTarget
            );
        }

        savePunishments();

        String successMessage =
                getConfig().getString(
                        "revoke.messages.success",
                        "&aPunishment &f%punishment% &afor &f%player% &ahas been revoked."
                );

        successMessage =
                successMessage
                        .replace(
                                "%player%",
                                data.player
                        )
                        .replace(
                                "%punishment%",
                                data.punishment
                        )
                        .replace(
                                "%staff%",
                                staffName
                        );

        sender.sendMessage(
                prefix + color(
                        successMessage
                )
        );

        if (onlineTarget != null) {

            String targetMessage =
                    getConfig().getString(
                            "revoke.messages.target",
                            "&aYour punishment has been revoked by &f%staff%&a."
                    );

            targetMessage =
                    targetMessage.replace(
                            "%staff%",
                            staffName
                    );

            onlineTarget.sendMessage(
                    prefix + color(
                            targetMessage
                    )
            );
        }
    }

    // =========================================================
    // CHAT / MUTE
    // =========================================================

    @EventHandler
    public void onChat(
            AsyncPlayerChatEvent event
    ) {

        Player player =
                event.getPlayer();

        PunishmentData data =
                punishments.get(
                        player.getUniqueId()
                );

        if (data == null) {
            return;
        }

        if (!data.type.equalsIgnoreCase(
                "MUTE"
        )) {
            return;
        }

        if (isExpired(data)) {

            removePunishment(
                    player.getUniqueId()
            );

            return;
        }

        event.setCancelled(true);

        sendMuteMessage(
                player,
                data
        );
    }

    private void sendMuteMessage(
            Player player,
            PunishmentData data
    ) {

        String msg =
                getConfig().getString(
                        "messages.muted.message",
                        "&cYou are currently muted."
                );

        player.sendMessage(
                prefix + color(
                        replaceData(
                                msg,
                                data
                        )
                )
        );

        String reason =
                getConfig().getString(
                        "messages.muted.reason",
                        ""
                );

        if (!reason.isEmpty()) {

            player.sendMessage(
                    prefix + color(
                            replaceData(
                                    reason,
                                    data
                            )
                    )
            );
        }

        String duration =
                getConfig().getString(
                        "messages.muted.duration",
                        ""
                );

        if (!duration.isEmpty()) {

            player.sendMessage(
                    prefix + color(
                            replaceData(
                                    duration,
                                    data
                            )
                    )
            );
        }

        String staff =
                getConfig().getString(
                        "messages.muted.staff",
                        ""
                );

        if (!staff.isEmpty()) {

            player.sendMessage(
                    prefix + color(
                            replaceData(
                                    staff,
                                    data
                            )
                    )
            );
        }
    }

    // =========================================================
    // LOGIN BAN CHECK
    // =========================================================

    @EventHandler
    public void onLogin(
            PlayerLoginEvent event
    ) {

        Player player =
                event.getPlayer();

        PunishmentData data =
                punishments.get(
                        player.getUniqueId()
                );

        if (data != null &&
                isExpired(data)) {

            removePunishment(
                    player.getUniqueId()
            );

            data = null;
        }

        // =====================================================
        // NORMAL BAN
        // =====================================================

        if (data != null &&
                data.type.equalsIgnoreCase(
                        "BAN"
                )) {

            String message =
                    getConfig().getString(
                            "messages.banned.message",
                            "&cYou are banned."
                    );

            message =
                    replaceData(
                            message,
                            data
                    );

            String punishment =
                    getConfig().getString(
                            "messages.banned.punishment",
                            ""
                    );

            String reason =
                    getConfig().getString(
                            "messages.banned.reason",
                            ""
                    );

            String duration =
                    getConfig().getString(
                            "messages.banned.duration",
                            ""
                    );

            String staff =
                    getConfig().getString(
                            "messages.banned.staff",
                            ""
                    );

            message =
                    message
                            + "\n\n"
                            + replaceData(
                                    punishment,
                                    data
                            )
                            + "\n"
                            + replaceData(
                                    reason,
                                    data
                            )
                            + "\n"
                            + replaceData(
                                    duration,
                                    data
                            )
                            + "\n"
                            + replaceData(
                                    staff,
                                    data
                            );

            event.disallow(
                    PlayerLoginEvent.Result.KICK_BANNED,
                    color(message)
            );

            return;
        }

        // =====================================================
        // IP BAN
        // =====================================================

        String ip =
                getLoginIP(event);

        if (ip != null) {

            PunishmentData ipData =
                    ipPunishments.get(ip);

            if (ipData != null) {

                if (isExpired(ipData)) {

                    ipPunishments.remove(
                            ip
                    );

                } else {

                    String message =
                            getConfig().getString(
                                    "messages.ip-banned.message",
                                    "&cYour IP is banned."
                            );

                    message =
                            replaceData(
                                    message,
                                    ipData
                            );

                    String punishment =
                            getConfig().getString(
                                    "messages.ip-banned.punishment",
                                    ""
                            );

                    String reason =
                            getConfig().getString(
                                    "messages.ip-banned.reason",
                                    ""
                            );

                    String duration =
                            getConfig().getString(
                                    "messages.ip-banned.duration",
                                    ""
                            );

                    String staff =
                            getConfig().getString(
                                    "messages.ip-banned.staff",
                                    ""
                            );

                    message =
                            message
                                    + "\n\n"
                                    + replaceData(
                                            punishment,
                                            ipData
                                    )
                                    + "\n"
                                    + replaceData(
                                            reason,
                                            ipData
                                    )
                                    + "\n"
                                    + replaceData(
                                            duration,
                                            ipData
                                    )
                                    + "\n"
                                    + replaceData(
                                            staff,
                                            ipData
                                    );

                    event.disallow(
                            PlayerLoginEvent.Result.KICK_BANNED,
                            color(message)
                    );
                }
            }
        }
    }

    // =========================================================
    // COMMAND EXECUTION
    // =========================================================

    private boolean executeConfiguredCommand(
            Player staff,
            String command,
            Player target,
            String duration,
            String reason
    ) {

        if (command == null ||
                command.trim().isEmpty()) {

            return true;
        }

        command =
                command.replace(
                        "%player%",
                        target.getName()
                );

        command =
                command.replace(
                        "%target%",
                        target.getName()
                );

        command =
                command.replace(
                        "%duration%",
                        duration
                );

        command =
                command.replace(
                        "%reason%",
                        reason
                );

        command =
                command.replace(
                        "%staff%",
                        staff.getName()
                );

        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        return Bukkit.dispatchCommand(
                staff,
                command
        );
    }

    private String replaceCommandData(
            String command,
            PunishmentData data,
            String staff
    ) {

        if (command == null) {
            return "";
        }

        return command
                .replace(
                        "%player%",
                        data.player
                )
                .replace(
                        "%target%",
                        data.player
                )
                .replace(
                        "%duration%",
                        data.duration
                )
                .replace(
                        "%reason%",
                        data.reason
                )
                .replace(
                        "%staff%",
                        staff
                );
    }

    // =========================================================
    // DATA
    // =========================================================

    private void removeExistingPunishment(
            Player player
    ) {

        PunishmentData old =
                punishments.remove(
                        player.getUniqueId()
                );

        if (old != null) {

            removePunishmentFromFile(
                    player.getUniqueId()
            );

            if (old.ip != null) {

                ipPunishments.remove(
                        old.ip
                );
            }
        }

        removeIPPunishment(
                player
        );
    }

    private void removePunishment(
            UUID uuid
    ) {

        PunishmentData data =
                punishments.remove(uuid);

        if (data != null) {

            removePunishmentFromFile(
                    uuid
            );

            if (data.ip != null) {

                ipPunishments.remove(
                        data.ip
                );
            }
        }

        savePunishments();
    }

    private void removeIPPunishment(
            Player player
    ) {

        String ip =
                getPlayerIP(player);

        if (ip != null) {

            ipPunishments.remove(
                    ip
            );
        }
    }

    private boolean isExpired(
            PunishmentData data
    ) {

        return data.expires > 0L &&
                data.expires <=
                        System.currentTimeMillis();
    }

    // =========================================================
    // DATA FILE
    // =========================================================

    private void setupDataFile() {

        if (!getDataFolder().exists()) {

            getDataFolder().mkdirs();
        }

        dataFile =
                new File(
                        getDataFolder(),
                        "data.yml"
                );

        if (!dataFile.exists()) {

            try {

                dataFile.createNewFile();

            } catch (IOException e) {

                e.printStackTrace();
            }
        }

        dataConfig =
                YamlConfiguration.loadConfiguration(
                        dataFile
                );
    }

    private void loadPunishments() {

        ConfigurationSection section =
                dataConfig.getConfigurationSection(
                        "punishments"
                );

        if (section == null) {
            return;
        }

        for (String key :
                section.getKeys(false)) {

            try {

                String path =
                        "punishments."
                                + key;

                String uuidString =
                        dataConfig.getString(
                                path + ".uuid"
                        );

                if (uuidString == null) {
                    continue;
                }

                UUID uuid =
                        UUID.fromString(
                                uuidString
                        );

                String player =
                        dataConfig.getString(
                                path + ".player",
                                ""
                        );

                String punishment =
                        dataConfig.getString(
                                path + ".punishment",
                                ""
                        );

                String type =
                        dataConfig.getString(
                                path + ".type",
                                ""
                        );

                String duration =
                        dataConfig.getString(
                                path + ".duration",
                                ""
                        );

                String reason =
                        dataConfig.getString(
                                path + ".reason",
                                ""
                        );

                String staff =
                        dataConfig.getString(
                                path + ".staff",
                                ""
                        );

                long expires =
                        dataConfig.getLong(
                                path + ".expires",
                                0L
                        );

                String ip =
                        dataConfig.getString(
                                path + ".ip",
                                null
                        );

                PunishmentData data =
                        new PunishmentData(
                                uuid,
                                player,
                                punishment,
                                type,
                                duration,
                                reason,
                                staff,
                                expires,
                                ip
                        );

                if (isExpired(data)) {
                    continue;
                }

                punishments.put(
                        uuid,
                        data
                );

                if (type.equalsIgnoreCase(
                        "IP-BAN"
                ) &&
                        ip != null) {

                    ipPunishments.put(
                            ip,
                            data
                    );
                }

            } catch (Exception ignored) {
            }
        }
    }

    private void savePunishment(
            PunishmentData data
    ) {

        String path =
                "punishments."
                        + data.uuid.toString();

        dataConfig.set(
                path + ".uuid",
                data.uuid.toString()
        );

        dataConfig.set(
                path + ".player",
                data.player
        );

        dataConfig.set(
                path + ".punishment",
                data.punishment
        );

        dataConfig.set(
                path + ".type",
                data.type
        );

        dataConfig.set(
                path + ".duration",
                data.duration
        );

        dataConfig.set(
                path + ".reason",
                data.reason
        );

        dataConfig.set(
                path + ".staff",
                data.staff
        );

        dataConfig.set(
                path + ".expires",
                data.expires
        );

        dataConfig.set(
                path + ".ip",
                data.ip
        );

        savePunishments();
    }

    private void removePunishmentFromFile(
            UUID uuid
    ) {

        dataConfig.set(
                "punishments."
                        + uuid.toString(),
                null
        );
    }

    private void savePunishments() {

        try {

            dataConfig.save(
                    dataFile
            );

        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    // =========================================================
    // EXPIRE
    // =========================================================

    private void checkExpiredPunishments() {

        long now =
                System.currentTimeMillis();

        ArrayList<UUID> expired =
                new ArrayList<UUID>();

        for (Map.Entry<UUID, PunishmentData> entry :
                punishments.entrySet()) {

            PunishmentData data =
                    entry.getValue();

            if (data.expires > 0L &&
                    data.expires <= now) {

                expired.add(
                        entry.getKey()
                );
            }
        }

        for (UUID uuid :
                expired) {

            PunishmentData data =
                    punishments.remove(
                            uuid
                    );

            if (data != null) {

                if (data.ip != null) {

                    ipPunishments.remove(
                            data.ip
                    );
                }

                removePunishmentFromFile(
                        uuid
                );
            }
        }

        savePunishments();
    }

    // =========================================================
    // MESSAGES
    // =========================================================

    private void sendStaffDetails(
            Player staff,
            Player target,
            String punishment,
            String type,
            String duration,
            String reason
    ) {

        sendConfigured(
                staff,
                "messages.staff.applied",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendConfigured(
                staff,
                "messages.staff.player",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendConfigured(
                staff,
                "messages.staff.punishment",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendConfigured(
                staff,
                "messages.staff.type",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendConfigured(
                staff,
                "messages.staff.duration",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendConfigured(
                staff,
                "messages.staff.reason",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendConfigured(
                staff,
                "messages.staff.staff",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );
    }

    private void sendTargetDetails(
            Player target,
            String punishment,
            String type,
            String duration,
            String reason,
            String staff
    ) {

        sendConfigured(
                target,
                "messages.target.applied",
                target,
                punishment,
                type,
                duration,
                reason,
                staff
        );

        sendConfigured(
                target,
                "messages.target.player",
                target,
                punishment,
                type,
                duration,
                reason,
                staff
        );

        sendConfigured(
                target,
                "messages.target.punishment",
                target,
                punishment,
                type,
                duration,
                reason,
                staff
        );

        sendConfigured(
                target,
                "messages.target.type",
                target,
                punishment,
                type,
                duration,
                reason,
                staff
        );

        sendConfigured(
                target,
                "messages.target.duration",
                target,
                punishment,
                type,
                duration,
                reason,
                staff
        );

        sendConfigured(
                target,
                "messages.target.reason",
                target,
                punishment,
                type,
                duration,
                reason,
                staff
        );

        sendConfigured(
                target,
                "messages.target.staff",
                target,
                punishment,
                type,
                duration,
                reason,
                staff
        );
    }

    private void sendConfigured(
            Player receiver,
            String path,
            Player target,
            String punishment,
            String type,
            String duration,
            String reason,
            String staff
    ) {

        String text =
                getConfig().getString(
                        path,
                        ""
                );

        if (text == null ||
                text.isEmpty()) {

            return;
        }

        text =
                text.replace(
                        "%player%",
                        target.getName()
                );

        text =
                text.replace(
                        "%punishment%",
                        punishment
                );

        text =
                text.replace(
                        "%type%",
                        type
                );

        text =
                text.replace(
                        "%duration%",
                        duration
                );

        text =
                text.replace(
                        "%reason%",
                        reason
                );

        text =
                text.replace(
                        "%staff%",
                        staff
                );

        receiver.sendMessage(
                color(text)
        );
    }

    // =========================================================
    // UTILS
    // =========================================================

    private long parseDuration(
            String duration
    ) {

        if (duration == null ||
                duration.trim().isEmpty()) {

            return 0L;
        }

        String value =
                duration
                        .trim()
                        .toLowerCase();

        String number =
                value.replaceAll(
                        "[^0-9]",
                        ""
                );

        if (number.isEmpty()) {
            return 0L;
        }

        long amount;

        try {

            amount =
                    Long.parseLong(number);

        } catch (NumberFormatException e) {

            return 0L;
        }

        if (value.contains("second") ||
                value.matches(".*\\d+s.*")) {

            return amount *
                    1000L;
        }

        if (value.contains("minute") ||
                value.matches(".*\\d+m.*")) {

            return amount *
                    60L *
                    1000L;
        }

        if (value.contains("hour") ||
                value.matches(".*\\d+h.*")) {

            return amount *
                    60L *
                    60L *
                    1000L;
        }

        if (value.contains("day") ||
                value.matches(".*\\d+d.*")) {

            return amount *
                    24L *
                    60L *
                    60L *
                    1000L;
        }

        if (value.contains("week") ||
                value.matches(".*\\d+w.*")) {

            return amount *
                    7L *
                    24L *
                    60L *
                    60L *
                    1000L;
        }

        return 0L;
    }

    private String getPlayerIP(
            Player player
    ) {

        if (player == null ||
                player.getAddress() == null ||
                player.getAddress().getAddress() == null) {

            return null;
        }

        return player
                .getAddress()
                .getAddress()
                .getHostAddress();
    }

    private String getLoginIP(
            PlayerLoginEvent event
    ) {

        if (event.getAddress() == null) {
            return null;
        }

        return event
                .getAddress()
                .getHostAddress();
    }

    private String message(
            String path
    ) {

        return prefix +
                color(
                        getConfig().getString(
                                path,
                                ""
                        )
                );
    }

    private String replaceData(
            String text,
            PunishmentData data
    ) {

        if (text == null) {
            return "";
        }

        return text
                .replace(
                        "%player%",
                        data.player
                )
                .replace(
                        "%punishment%",
                        data.punishment
                )
                .replace(
                        "%type%",
                        data.type
                )
                .replace(
                        "%duration%",
                        data.duration
                )
                .replace(
                        "%reason%",
                        data.reason
                )
                .replace(
                        "%staff%",
                        data.staff
                );
    }

    private String color(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes(
                '&',
                text
        );
    }

    private String escapeJson(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                );
    }

    // =========================================================
    // DATA CLASS
    // =========================================================

    private static class PunishmentData {

        private final UUID uuid;
        private final String player;
        private final String punishment;
        private final String type;
        private final String duration;
        private final String reason;
        private final String staff;
        private final long expires;
        private final String ip;

        private PunishmentData(
                UUID uuid,
                String player,
                String punishment,
                String type,
                String duration,
                String reason,
                String staff,
                long expires,
                String ip
        ) {

            this.uuid = uuid;
            this.player = player;
            this.punishment = punishment;
            this.type = type;
            this.duration = duration;
            this.reason = reason;
            this.staff = staff;
            this.expires = expires;
            this.ip = ip;
        }
    }
}
