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

    /*
     * =========================================================
     * ENABLE
     * =========================================================
     */

    @Override
    public void onEnable() {

        saveDefaultConfig();

        prefix = color(
                getConfig().getString(
                        "settings.prefix",
                        "&cPunishments &7┃ "
                )
        );

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

    /*
     * =========================================================
     * COMMANDS
     * =========================================================
     */

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        /*
         * /pm
         */

        if (command.getName().equalsIgnoreCase("pm")) {

            if (!(sender instanceof Player)) {

                sender.sendMessage(
                        message("messages.errors.players-only")
                );

                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.use")) {

                staff.sendMessage(
                        message("messages.errors.no-permission")
                );

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

            openBook(
                    staff,
                    args[0]
            );

            return true;
        }

        /*
         * /pmapply
         */

        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {

                sender.sendMessage(
                        message("messages.errors.players-only")
                );

                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.use")) {

                staff.sendMessage(
                        message("messages.errors.no-permission")
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

        /*
         * /pmrevoke
         *
         * IMPORTANT:
         * Console IS allowed.
         */

        if (command.getName().equalsIgnoreCase("pmrevoke")) {

            if (!sender.hasPermission("punishmentsbook.revoke")) {

                sender.sendMessage(
                        message("revoke.messages.no-permission")
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

    /*
     * =========================================================
     * BOOK
     * =========================================================
     */

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

            entity.openBook(
                    nmsBook
            );

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
                CraftItemStack.asNMSCopy(
                        base
                );

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

            book.setTag(
                    tag
            );

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
                            "punishments." + id + ".name",
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

            json.append("\"text\":\"");
            json.append(
                    escapeJson(name)
            );
            json.append("\",");

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

        json.append("]}");

        pages.add(
                new NBTTagString(
                        json.toString()
                )
        );

        tag.set(
                "pages",
                pages
        );

        book.setTag(
                tag
        );

        return book;
    }

    /*
     * =========================================================
     * RANK PRIORITY
     * =========================================================
     *
     * No Vault.
     * No LuckPerms API.
     *
     * Uses Bukkit permission system.
     *
     * 90 = Owner
     * 80 = Manager
     * 70 = Supervisor
     * 60 = Helper
     * 50 = Builder
     * 40 = VIP
     * 10 = Default
     *
     * Higher number = higher rank.
     */

    private int getRankPriority(
            Player player
    ) {

        if (player == null) {
            return -1;
        }

        int[] priorities = {
                90,
                80,
                70,
                60,
                50,
                40,
                10
        };

        for (int priority :
                priorities) {

            if (player.hasPermission(
                    "punishmentsbook.rank."
                            + priority
            )) {

                return priority;
            }
        }

        return -1;
    }

    /*
     * =========================================================
     * CHECK WHETHER STAFF CAN PUNISH TARGET
     * =========================================================
     */

    private boolean canPunish(
            Player staff,
            Player target
    ) {

        if (!getConfig().getBoolean(
                "rank-priority.enabled",
                true
        )) {

            return true;
        }

        int staffRank =
                getRankPriority(staff);

        int targetRank =
                getRankPriority(target);

        /*
         * If neither rank can be determined,
         * block the punishment for safety.
         */

        if (staffRank == -1 ||
                targetRank == -1) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "rank-priority.messages.unknown-rank",
                                    "&cCould not determine your rank or the target's rank."
                            )
                    )
            );

            return false;
        }

        /*
         * Target has higher rank.
         */

        if (targetRank > staffRank) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "rank-priority.messages.higher-rank",
                                    "&cYou cannot punish a staff member with a higher rank!"
                            )
                    )
            );

            return false;
        }

        /*
         * Same rank.
         */

        if (targetRank == staffRank) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "rank-priority.messages.same-rank",
                                    "&cYou cannot punish a staff member with the same rank!"
                            )
                    )
            );

            return false;
        }

        /*
         * Target is lower.
         */

        return true;
    }

    /*
     * =========================================================
     * APPLY PUNISHMENT
     * =========================================================
     */

    private void applyPunishment(
            Player staff,
            String targetName,
            String id
    ) {

        String path =
                "punishments." + id;

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

        /*
         * RANK PRIORITY CHECK
         */

        if (!canPunish(
                staff,
                target
        )) {

            return;
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
                parseDuration(
                        duration
                );

        long expires;

        if (durationMillis <= 0L) {
            expires = 0L;
        } else {
            expires =
                    System.currentTimeMillis()
                            + durationMillis;
        }

        /*
         * Remove previous punishment.
         */

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
                        expires
                );

        punishments.put(
                target.getUniqueId(),
                data
        );

        /*
         * IP BAN
         */

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

        /*
         * Save.
         */

        savePunishment(
                data
        );

        /*
         * JAIL
         *
         * PunishmentJail command is executed
         * by the STAFF, not console.
         */

        if (type.equalsIgnoreCase(
                "JAIL"
        )) {

            String configuredCommand =
                    getConfig().getString(
                            path + ".command",
                            ""
                    );

            boolean success =
                    executeConfiguredCommand(
                            staff,
                            configuredCommand,
                            target,
                            duration,
                            reason
                    );

            if (!success) {

                punishments.remove(
                        target.getUniqueId()
                );

                removePunishmentFromFile(
                        target.getUniqueId()
                );

                savePunishments();

                staff.sendMessage(
                        message(
                                "messages.errors.command-failed"
                        )
                );

                return;
            }
        }

        /*
         * Details to staff.
         */

        sendStaffDetails(
                staff,
                target,
                name,
                type,
                duration,
                reason
        );

        /*
         * Details to target.
         */

        sendTargetDetails(
                target,
                name,
                type,
                duration,
                reason,
                staff.getName()
        );
    }

    /*
     * =========================================================
     * REVOKE
     * =========================================================
     *
     * Supports:
     *
     * /pmrevoke player
     *
     * from PLAYER or CONSOLE.
     */

    private void revokePunishment(
            CommandSender sender,
            String targetName
    ) {

        Player target =
                Bukkit.getPlayerExact(
                        targetName
                );

        if (target == null) {

            sender.sendMessage(
                    message(
                            "messages.errors.player-not-online"
                    )
            );

            return;
        }

        PunishmentData data =
                punishments.get(
                        target.getUniqueId()
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

        boolean success = true;

        /*
         * JAIL
         */

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
                 * If player executes revoke:
                 * execute as player.
                 *
                 * If console executes revoke:
                 * execute as console.
                 */

                success =
                        executeRevokeCommand(
                                sender,
                                command,
                                target,
                                data.duration,
                                data.reason
                        );
            }
        }

        /*
         * MUTE
         */

        else if (type.equalsIgnoreCase(
                "MUTE"
        )) {

            success =
                    getConfig().getBoolean(
                            "revoke.mute.enabled",
                            true
                    );
        }

        /*
         * BAN
         */

        else if (type.equalsIgnoreCase(
                "BAN"
        )) {

            success =
                    getConfig().getBoolean(
                            "revoke.ban.enabled",
                            true
                    );
        }

        /*
         * IP BAN
         */

        else if (type.equalsIgnoreCase(
                "IP-BAN"
        )) {

            success =
                    getConfig().getBoolean(
                            "revoke.ip-ban.enabled",
                            true
                    );

            String ip =
                    getPlayerIP(target);

            if (ip != null) {
                ipPunishments.remove(ip);
            }
        }

        /*
         * Failed command.
         */

        if (!success) {

            sender.sendMessage(
                    message(
                            "messages.errors.command-failed"
                    )
            );

            return;
        }

        /*
         * Remove punishment.
         */

        punishments.remove(
                target.getUniqueId()
        );

        removePunishmentFromFile(
                target.getUniqueId()
        );

        removeIPPunishment(
                target
        );

        savePunishments();

        /*
         * Staff name.
         */

        String staffName;

        if (sender instanceof Player) {
            staffName =
                    sender.getName();
        } else {
            staffName =
                    "Console";
        }

        /*
         * Success message.
         */

        String successMessage =
                getConfig().getString(
                        "revoke.messages.success",
                        "&aPunishment &f%punishment% &afor &f%player% &ahas been revoked."
                );

        successMessage =
                successMessage
                        .replace(
                                "%player%",
                                target.getName()
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

        /*
         * Target message.
         */

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

        target.sendMessage(
                prefix + color(
                        targetMessage
                )
        );
    }

    /*
     * =========================================================
     * REVOKE COMMAND EXECUTION
     * =========================================================
     */

    private boolean executeRevokeCommand(
            CommandSender sender,
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

        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        return Bukkit.dispatchCommand(
                sender,
                command
        );
    }

    /*
     * =========================================================
     * CHAT MUTE
     * =========================================================
     */

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

    /*
     * =========================================================
     * LOGIN BAN / IP BAN
     * =========================================================
     */

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

        /*
         * BAN
         */

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
                            + color(
                            replaceData(
                                    punishment,
                                    data
                            )
                    )
                            + "\n"
                            + color(
                            replaceData(
                                    reason,
                                    data
                            )
                    )
                            + "\n"
                            + color(
                            replaceData(
                                    duration,
                                    data
                            )
                    )
                            + "\n"
                            + color(
                            replaceData(
                                    staff,
                                    data
                            )
                    );

            event.disallow(
                    PlayerLoginEvent.Result.KICK_BANNED,
                    color(message)
            );

            return;
        }

        /*
         * IP BAN
         */

        String ip =
                getLoginIP(event);

        if (ip == null) {
            return;
        }

        PunishmentData ipData =
                ipPunishments.get(ip);

        if (ipData == null) {
            return;
        }

        if (isExpired(ipData)) {

            ipPunishments.remove(ip);

            return;
        }

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
                        + color(
                        replaceData(
                                punishment,
                                ipData
                        )
                )
                        + "\n"
                        + color(
                        replaceData(
                                reason,
                                ipData
                        )
                )
                        + "\n"
                        + color(
                        replaceData(
                                duration,
                                ipData
                        )
                )
                        + "\n"
                        + color(
                        replaceData(
                                staff,
                                ipData
                        )
                );

        event.disallow(
                PlayerLoginEvent.Result.KICK_BANNED,
                color(message)
        );
    }

    /*
     * =========================================================
     * PUNISHMENT COMMAND
     * =========================================================
     */

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

        /*
         * PunishmentJail command is executed
         * as the staff member.
         */

        return Bukkit.dispatchCommand(
                staff,
                command
        );
    }

    /*
     * =========================================================
     * DATA
     * =========================================================
     */

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
        }

        savePunishments();
    }

    private void removeIPPunishment(
            Player player
    ) {

        String ip =
                getPlayerIP(player);

        if (ip != null) {

            ipPunishments.remove(ip);
        }
    }

    private boolean isExpired(
            PunishmentData data
    ) {

        return data.expires > 0L
                && data.expires <=
                System.currentTimeMillis();
    }

    /*
     * =========================================================
     * DATA FILE
     * =========================================================
     */

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
                        "punishments." + key;

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

                PunishmentData data =
                        new PunishmentData(
                                uuid,
                                player,
                                punishment,
                                type,
                                duration,
                                reason,
                                staff,
                                expires
                        );

                if (isExpired(data)) {
                    continue;
                }

                punishments.put(
                        uuid,
                        data
                );

                /*
                 * Rebuild IP punishment.
                 */

                if (type.equalsIgnoreCase(
                        "IP-BAN"
                )) {

                    Player online =
                            Bukkit.getPlayer(uuid);

                    if (online != null) {

                        String ip =
                                getPlayerIP(online);

                        if (ip != null) {

                            ipPunishments.put(
                                    ip,
                                    data
                            );
                        }
                    }
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

    /*
     * =========================================================
     * EXPIRED PUNISHMENTS
     * =========================================================
     */

    private void checkExpiredPunishments() {

        long now =
                System.currentTimeMillis();

        ArrayList<UUID> expired =
                new ArrayList<UUID>();

        for (
                Map.Entry<UUID, PunishmentData> entry :
                punishments.entrySet()
        ) {

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

                if (data.type.equalsIgnoreCase(
                        "IP-BAN"
                )) {

                    Player player =
                            Bukkit.getPlayer(uuid);

                    if (player != null) {

                        removeIPPunishment(
                                player
                        );
                    }
                }

                removePunishmentFromFile(
                        uuid
                );
            }
        }

        savePunishments();
    }

    /*
     * =========================================================
     * STAFF MESSAGES
     * =========================================================
     */

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

    /*
     * =========================================================
     * TARGET MESSAGES
     * =========================================================
     */

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

    /*
     * =========================================================
     * CONFIGURED MESSAGE
     * =========================================================
     */

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
                prefix + color(text)
        );
    }

    /*
     * =========================================================
     * DURATION
     * =========================================================
     */

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

            return amount * 1000L;
        }

        if (value.contains("minute") ||
                value.matches(".*\\d+m.*")) {

            return amount
                    * 60L
                    * 1000L;
        }

        if (value.contains("hour") ||
                value.matches(".*\\d+h.*")) {

            return amount
                    * 60L
                    * 60L
                    * 1000L;
        }

        if (value.contains("day") ||
                value.matches(".*\\d+d.*")) {

            return amount
                    * 24L
                    * 60L
                    * 60L
                    * 1000L;
        }

        if (value.contains("week") ||
                value.matches(".*\\d+w.*")) {

            return amount
                    * 7L
                    * 24L
                    * 60L
                    * 60L
                    * 1000L;
        }

        return 0L;
    }

    /*
     * =========================================================
     * IP
     * =========================================================
     */

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

    /*
     * =========================================================
     * MESSAGE
     * =========================================================
     */

    private String message(
            String path
    ) {

        return prefix
                + color(
                getConfig().getString(
                        path,
                        ""
                )
        );
    }

    /*
     * =========================================================
     * REPLACE DATA
     * =========================================================
     */

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

    /*
     * =========================================================
     * COLOR
     * =========================================================
     */

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

    /*
     * =========================================================
     * JSON ESCAPE
     * =========================================================
     */

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

    /*
     * =========================================================
     * PUNISHMENT DATA
     * =========================================================
     */

    private static class PunishmentData {

        private final UUID uuid;
        private final String player;
        private final String punishment;
        private final String type;
        private final String duration;
        private final String reason;
        private final String staff;
        private final long expires;

        private PunishmentData(
                UUID uuid,
                String player,
                String punishment,
                String type,
                String duration,
                String reason,
                String staff,
                long expires
        ) {

            this.uuid = uuid;
            this.player = player;
            this.punishment = punishment;
            this.type = type;
            this.duration = duration;
            this.reason = reason;
            this.staff = staff;
            this.expires = expires;
        }
    }
}
