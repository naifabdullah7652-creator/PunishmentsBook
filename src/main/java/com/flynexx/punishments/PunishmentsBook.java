package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.ban.BanList;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PunishmentsBook extends JavaPlugin implements Listener {

    private String prefix;

    private File dataFile;
    private org.bukkit.configuration.file.FileConfiguration dataConfig;

    private final Map<UUID, PunishmentData> activePunishments =
            new HashMap<UUID, PunishmentData>();

    @Override
    public void onEnable() {

        saveDefaultConfig();

        prefix = color(
                getConfig().getString(
                        "settings.prefix",
                        "&8[&4PunishmentsBook&8] &7┃ "
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

        /*
         * Check expired punishments every second.
         */
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

        getLogger().info(
                "PunishmentsBook enabled."
        );
    }

    @Override
    public void onDisable() {
        savePunishments();
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        /*
         * /pm <player>
         */
        if (command.getName().equalsIgnoreCase("pm")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(
                        prefix + color("&cPlayers only.")
                );
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.use")) {

                staff.sendMessage(
                        message(
                                "messages.errors.no-permission"
                        )
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
         * /pmapply <player> <punishment>
         */
        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.use")) {
                return true;
            }

            if (args.length != 2) {
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
         * /pmrevoke <player>
         */
        if (command.getName().equalsIgnoreCase("pmrevoke")) {

            if (!(sender instanceof Player)) {

                sender.sendMessage(
                        prefix + color("&cPlayers only.")
                );

                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.revoke")) {

                staff.sendMessage(
                        message(
                                "revoke.messages.no-permission"
                        )
                );

                return true;
            }

            if (args.length != 1) {

                staff.sendMessage(
                        prefix + color(
                                "&cUsage: /pmrevoke <player>"
                        )
                );

                return true;
            }

            revokePunishment(
                    staff,
                    args[0]
            );

            return true;
        }

        return false;
    }

    /*
     * Open virtual written book.
     */
    private void openBook(
            Player player,
            String target) {

        try {

            ItemStack book =
                    createBook(target);

            org.bukkit.inventory.ItemStack bukkitBook =
                    CraftItemStack.asBukkitCopy(book);

            final org.bukkit.inventory.ItemStack oldItem =
                    player.getItemInHand();

            player.setItemInHand(bukkitBook);
            player.updateInventory();

            EntityPlayer entityPlayer =
                    ((CraftPlayer) player).getHandle();

            entityPlayer.openBook(book);

            Bukkit.getScheduler().runTaskLater(
                    this,
                    new Runnable() {
                        @Override
                        public void run() {

                            player.setItemInHand(oldItem);
                            player.updateInventory();
                        }
                    },
                    5L
            );

        } catch (Throwable ex) {

            getLogger().warning(
                    "Could not open punishment book: " +
                    ex.getClass().getSimpleName() +
                    ": " +
                    ex.getMessage()
            );

            player.sendMessage(
                    prefix +
                    color(
                            "&cCould not open punishment book."
                    )
            );
        }
    }

    /*
     * Creates the interactive book.
     *
     * The page shows punishment names only.
     * Duration is NOT displayed.
     */
    private ItemStack createBook(
            String target) {

        org.bukkit.inventory.ItemStack base =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        ItemStack book =
                CraftItemStack.asNMSCopy(base);

        NBTTagCompound tag =
                new NBTTagCompound();

        tag.setString(
                "title",
                getConfig().getString(
                        "book.title",
                        "Punishments"
                )
        );

        tag.setString(
                "author",
                getConfig().getString(
                        "book.author",
                        "PunishmentsBook"
                )
        );

        NBTTagList pages =
                new NBTTagList();

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section == null) {

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

        String bookColor =
                getConfig().getString(
                        "book.color",
                        "black"
                );

        String hover =
                getConfig().getString(
                        "book.hover",
                        "Click to apply punishment"
                );

        for (String id :
                section.getKeys(false)) {

            String name =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".name",
                            id
                    );

            if (!first) {
                json.append(",");
            }

            first = false;

            String runCommand =
                    "/pmapply " +
                    target +
                    " " +
                    id;

            json.append(
                    "{\"text\":\""
            );

            json.append(
                    escapeJson(name)
            );

            json.append(
                    "\",\"color\":\""
            );

            json.append(
                    escapeJson(bookColor)
            );

            json.append(
                    "\",\"underlined\":true"
            );

            /*
             * Click -> /pmapply player punishment
             */
            json.append(
                    ",\"clickEvent\":{\"action\":\"run_command\",\"value\":\""
            );

            json.append(
                    escapeJson(runCommand)
            );

            json.append(
                    "\"}"
            );

            /*
             * Hover text.
             */
            json.append(
                    ",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\""
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

        book.setTag(tag);

        return book;
    }

    /*
     * Apply punishment.
     */
    private void applyPunishment(
            Player staff,
            String targetName,
            String id) {

        String path =
                "punishments." + id;

        if (!getConfig().isConfigurationSection(path)) {

            staff.sendMessage(
                    message(
                            "messages.errors.unknown-punishment"
                    )
            );

            return;
        }

        Player target =
                Bukkit.getPlayerExact(targetName);

        if (target == null) {

            staff.sendMessage(
                    message(
                            "messages.errors.player-not-online"
                    )
            );

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

        String command =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        /*
         * Flooding = real IP ban.
         */
        if (type.equalsIgnoreCase("IP-BAN")) {

            applyIpBan(
                    staff,
                    target,
                    name,
                    duration,
                    reason
            );

            return;
        }

        /*
         * Other punishments.
         */
        command =
                command
                        .replace(
                                "%player%",
                                target.getName()
                        )
                        .replace(
                                "%target%",
                                target.getName()
                        )
                        .replace(
                                "%duration%",
                                duration
                        )
                        .replace(
                                "%reason%",
                                reason
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        );

        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        boolean success = true;

        if (!command.trim().isEmpty()) {

            /*
             * Execute as the administrator.
             */
            success =
                    Bukkit.dispatchCommand(
                            staff,
                            command
                    );
        }

        if (!success) {

            staff.sendMessage(
                    message(
                            "messages.errors.command-failed"
                    )
            );

            return;
        }

        saveActivePunishment(
                target,
                name,
                type,
                duration,
                reason,
                staff.getName(),
                ""
        );

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

    /*
     * Real IP-Ban.
     *
     * Uses Bukkit's native IP ban list.
     */
    private void applyIpBan(
            Player staff,
            Player target,
            String punishment,
            String duration,
            String reason) {

        String ip =
                target.getAddress()
                        .getAddress()
                        .getHostAddress();

        if (ip == null ||
                ip.trim().isEmpty()) {

            staff.sendMessage(
                    prefix +
                    color(
                            "&cCould not determine player's IP."
                    )
            );

            return;
        }

        Bukkit.getBanList(
                BanList.Type.IP
        ).addBan(
                ip,
                reason,
                null,
                staff.getName()
        );

        saveActivePunishment(
                target,
                punishment,
                "IP-BAN",
                duration,
                reason,
                staff.getName(),
                ip
        );

        sendStaffDetails(
                staff,
                target,
                punishment,
                "IP-BAN",
                duration,
                reason
        );

        sendTargetDetails(
                target,
                punishment,
                "IP-BAN",
                duration,
                reason,
                staff.getName()
        );

        /*
         * Kick the player after IP ban.
         */
        target.kickPlayer(
                prefix +
                color(
                        "&cYou have been IP banned.\n" +
                        "&7Reason: &f" +
                        reason
                )
        );
    }

    /*
     * Revoke punishment.
     */
    private void revokePunishment(
            Player staff,
            String targetName) {

        Player target =
                Bukkit.getPlayerExact(targetName);

        PunishmentData data = null;

        /*
         * First try online player.
         */
        if (target != null) {

            data =
                    activePunishments.get(
                            target.getUniqueId()
                    );
        }

        /*
         * If target is offline, search by name.
         */
        if (data == null) {

            for (PunishmentData value :
                    activePunishments.values()) {

                if (value.player.equalsIgnoreCase(
                        targetName)) {

                    data = value;
                    break;
                }
            }
        }

        if (data == null) {

            staff.sendMessage(
                    message(
                            "revoke.messages.no-punishment"
                    )
            );

            return;
        }

        /*
         * IP-BAN.
         */
        if (data.type.equalsIgnoreCase(
                "IP-BAN")) {

            revokeIpBan(
                    staff,
                    data
            );
        }

        /*
         * JAIL.
         */
        else if (data.type.equalsIgnoreCase(
                "JAIL")) {

            String command =
                    getConfig().getString(
                            "revoke.jail-command",
                            "unjail %player%"
                    );

            command =
                    command
                            .replace(
                                    "%player%",
                                    data.player
                            )
                            .replace(
                                    "%staff%",
                                    staff.getName()
                            );

            executeRevokeCommand(
                    staff,
                    command
            );
        }

        /*
         * MUTE.
         */
        else if (data.type.equalsIgnoreCase(
                "MUTE")) {

            String command =
                    getConfig().getString(
                            "revoke.mute-command",
                            "unmute %player%"
                    );

            command =
                    command
                            .replace(
                                    "%player%",
                                    data.player
                            )
                            .replace(
                                    "%staff%",
                                    staff.getName()
                            );

            executeRevokeCommand(
                    staff,
                    command
            );
        }

        /*
         * BAN.
         */
        else if (data.type.equalsIgnoreCase(
                "BAN")) {

            String command =
                    getConfig().getString(
                            "revoke.ban-command",
                            "pardon %player%"
                    );

            command =
                    command
                            .replace(
                                    "%player%",
                                    data.player
                            )
                            .replace(
                                    "%staff%",
                                    staff.getName()
                            );

            executeRevokeCommand(
                    staff,
                    command
            );
        }

        activePunishments.remove(
                data.uuid
        );

        removePunishmentFromFile(
                data.uuid
        );

        savePunishments();

        String staffMessage =
                getConfig().getString(
                        "revoke.messages.success",
                        "&aPunishment revoked successfully."
                );

        staffMessage =
                replaceDetails(
                        staffMessage,
                        data,
                        staff.getName()
                );

        staff.sendMessage(
                prefix +
                color(staffMessage)
        );

        /*
         * Notify target if online.
         */
        Player onlineTarget =
                Bukkit.getPlayerExact(
                        data.player
                );

        if (onlineTarget != null) {

            String targetMessage =
                    getConfig().getString(
                            "revoke.messages.target",
                            "&aYour punishment has been revoked by &f%staff%&a."
                    );

            targetMessage =
                    replaceDetails(
                            targetMessage,
                            data,
                            staff.getName()
                    );

            onlineTarget.sendMessage(
                    prefix +
                    color(targetMessage)
            );
        }
    }

    /*
     * Revoke native IP ban.
     */
    private void revokeIpBan(
            Player staff,
            PunishmentData data) {

        if (data.ip == null ||
                data.ip.trim().isEmpty()) {

            staff.sendMessage(
                    prefix +
                    color(
                            "&cThe stored IP could not be found."
                    )
            );

            return;
        }

        Bukkit.getBanList(
                BanList.Type.IP
        ).pardon(
                data.ip
        );
    }

    /*
     * Execute revoke command.
     */
    private void executeRevokeCommand(
            Player staff,
            String command) {

        if (command == null ||
                command.trim().isEmpty()) {
            return;
        }

        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        Bukkit.dispatchCommand(
                staff,
                command
        );
    }

    /*
     * Save punishment.
     */
    private void saveActivePunishment(
            Player target,
            String punishment,
            String type,
            String duration,
            String reason,
            String staff,
            String ip) {

        PunishmentData data =
                new PunishmentData();

        data.uuid =
                target.getUniqueId();

        data.player =
                target.getName();

        data.punishment =
                punishment;

        data.type =
                type;

        data.duration =
                duration;

        data.reason =
                reason;

        data.staff =
                staff;

        data.ip =
                ip;

        long millis =
                parseDuration(duration);

        data.expires =
                millis > 0L
                        ? System.currentTimeMillis() + millis
                        : 0L;

        activePunishments.put(
                data.uuid,
                data
        );

        savePunishment(data);
    }

    /*
     * Expiration handling.
     */
    private void checkExpiredPunishments() {

        long now =
                System.currentTimeMillis();

        ArrayList<PunishmentData> expired =
                new ArrayList<PunishmentData>();

        for (PunishmentData data :
                activePunishments.values()) {

            if (data.expires <= 0L) {
                continue;
            }

            if (data.expires > now) {
                continue;
            }

            expired.add(data);
        }

        for (PunishmentData data :
                expired) {

            /*
             * IP-BAN must be automatically revoked.
             */
            if (data.type.equalsIgnoreCase(
                    "IP-BAN")) {

                if (data.ip != null &&
                        !data.ip.isEmpty()) {

                    Bukkit.getBanList(
                            BanList.Type.IP
                    ).pardon(data.ip);
                }
            }

            /*
             * MUTE is handled internally.
             *
             * Jail is NOT automatically released here.
             * PunishmentJail controls the jail duration.
             */

            activePunishments.remove(
                    data.uuid
            );

            removePunishmentFromFile(
                    data.uuid
            );
        }

        if (!expired.isEmpty()) {
            savePunishments();
        }
    }

    /*
     * Internal mute.
     */
    @EventHandler
    public void onChat(
            AsyncPlayerChatEvent event) {

        Player player =
                event.getPlayer();

        PunishmentData data =
                activePunishments.get(
                        player.getUniqueId()
                );

        if (data == null) {
            return;
        }

        if (!data.type.equalsIgnoreCase(
                "MUTE")) {
            return;
        }

        if (data.expires > 0L &&
                data.expires <=
                        System.currentTimeMillis()) {

            activePunishments.remove(
                    player.getUniqueId()
            );

            removePunishmentFromFile(
                    player.getUniqueId()
            );

            savePunishments();

            return;
        }

        event.setCancelled(true);

        player.sendMessage(
                prefix +
                color(
                        "&cYou are currently muted."
                )
        );
    }

    /*
     * Details to staff.
     */
    private void sendStaffDetails(
            Player staff,
            Player target,
            String punishment,
            String type,
            String duration,
            String reason) {

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
     * Details to target.
     */
    private void sendTargetDetails(
            Player target,
            String punishment,
            String type,
            String duration,
            String reason,
            String staff) {

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
            String staff) {

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
                text
                        .replace(
                                "%player%",
                                target.getName()
                        )
                        .replace(
                                "%punishment%",
                                punishment
                        )
                        .replace(
                                "%type%",
                                type
                        )
                        .replace(
                                "%duration%",
                                duration
                        )
                        .replace(
                                "%reason%",
                                reason
                        )
                        .replace(
                                "%staff%",
                                staff
                        );

        receiver.sendMessage(
                color(text)
        );
    }

    private String replaceDetails(
            String text,
            PunishmentData data,
            String staff) {

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
                        staff
                )
                .replace(
                        "%ip%",
                        data.ip == null
                                ? ""
                                : data.ip
                );
    }

    private String message(
            String path) {

        return prefix +
                color(
                        getConfig().getString(
                                path,
                                ""
                        )
                );
    }

    private String color(
            String text) {

        if (text == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes(
                '&',
                text
        );
    }

    private String escapeJson(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private long parseDuration(
            String duration) {

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
        } catch (NumberFormatException ex) {
            return 0L;
        }

        if (value.contains("second")) {
            return amount * 1000L;
        }

        if (value.contains("minute")) {
            return amount * 60L * 1000L;
        }

        if (value.contains("hour")) {
            return amount * 60L * 60L * 1000L;
        }

        if (value.contains("day")) {
            return amount * 24L * 60L * 60L * 1000L;
        }

        if (value.contains("week")) {
            return amount * 7L * 24L * 60L * 60L * 1000L;
        }

        return 0L;
    }

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
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        dataConfig =
                org.bukkit.configuration.file.YamlConfiguration
                        .loadConfiguration(dataFile);
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

            String path =
                    "punishments." + key;

            String uuidString =
                    dataConfig.getString(
                            path + ".uuid"
                    );

            if (uuidString == null) {
                continue;
            }

            try {

                PunishmentData data =
                        new PunishmentData();

                data.uuid =
                        UUID.fromString(
                                uuidString
                        );

                data.player =
                        dataConfig.getString(
                                path + ".player",
                                ""
                        );

                data.punishment =
                        dataConfig.getString(
                                path + ".punishment",
                                ""
                        );

                data.type =
                        dataConfig.getString(
                                path + ".type",
                                ""
                        );

                data.duration =
                        dataConfig.getString(
                                path + ".duration",
                                ""
                        );

                data.reason =
                        dataConfig.getString(
                                path + ".reason",
                                ""
                        );

                data.staff =
                        dataConfig.getString(
                                path + ".staff",
                                ""
                        );

                data.ip =
                        dataConfig.getString(
                                path + ".ip",
                                ""
                        );

                data.expires =
                        dataConfig.getLong(
                                path + ".expires",
                                0L
                        );

                activePunishments.put(
                        data.uuid,
                        data
                );

            } catch (Exception ignored) {
            }
        }
    }

    private void savePunishment(
            PunishmentData data) {

        String path =
                "punishments." +
                data.uuid.toString();

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
                path + ".ip",
                data.ip
        );

        dataConfig.set(
                path + ".expires",
                data.expires
        );

        savePunishments();
    }

    private void removePunishmentFromFile(
            UUID uuid) {

        dataConfig.set(
                "punishments." +
                uuid.toString(),
                null
        );
    }

    private void savePunishments() {

        try {
            dataConfig.save(dataFile);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static class PunishmentData {

        private UUID uuid;
        private String player;
        private String punishment;
        private String type;
        private String duration;
        private String reason;
        private String staff;
        private String ip;
        private long expires;
    }
}
