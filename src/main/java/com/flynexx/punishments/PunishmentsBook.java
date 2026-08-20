package com.flynexx.punishments;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import net.minecraft.server.v1_8_R3.EntityPlayer;

import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PunishmentsBook extends JavaPlugin implements Listener {

    private String prefix;

    private File dataFile;
    private FileConfiguration dataConfig;

    private final Map<UUID, ActivePunishment> activePunishments =
            new HashMap<UUID, ActivePunishment>();

    @Override
    public void onEnable() {

        saveDefaultConfig();

        prefix = color(
                getConfig().getString(
                        "settings.prefix",
                        "&8[&4PunishmentsBook&8] &7┃ "
                )
        );

        setupData();

        loadActivePunishments();

        if (getCommand("pm") != null) {
            getCommand("pm").setExecutor(this);
        }

        if (getCommand("pmapply") != null) {
            getCommand("pmapply").setExecutor(this);
        }

        if (getCommand("pmrevoke") != null) {
            getCommand("pmrevoke").setExecutor(this);
        }

        Bukkit.getPluginManager().registerEvents(
                this,
                this
        );

        getLogger().info(
                "PunishmentsBook enabled."
        );

        getLogger().info(
                "Loaded punishments: " +
                getPunishments().size()
        );
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // =========================================================
    // COMMANDS
    // =========================================================

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
                        prefix + color(
                                "&cPlayers only."
                        )
                );

                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.use")) {

                staff.sendMessage(
                        prefix + color(
                                getConfig().getString(
                                        "messages.errors.no-permission",
                                        "&cYou don't have permission."
                                )
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

            Player target =
                    Bukkit.getPlayerExact(args[0]);

            if (target == null) {

                staff.sendMessage(
                        prefix + color(
                                getConfig().getString(
                                        "messages.errors.player-not-online",
                                        "&cPlayer must be online."
                                )
                        )
                );

                return true;
            }

            openPunishmentBook(
                    staff,
                    target.getName()
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
         * /pmrevoke <player>
         */
        if (command.getName().equalsIgnoreCase("pmrevoke")) {

            if (!(sender instanceof Player)) {

                sender.sendMessage(
                        prefix + color(
                                "&cPlayers only."
                        )
                );

                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.revoke")) {

                staff.sendMessage(
                        prefix + color(
                                getConfig().getString(
                                        "revoke.messages.no-permission",
                                        "&cYou don't have permission to revoke punishments."
                                )
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

    // =========================================================
    // BOOK
    // =========================================================

    private void openPunishmentBook(
            Player staff,
            String targetName) {

        try {

            ItemStack book =
                    createPunishmentBook(
                            targetName
                    );

            net.minecraft.server.v1_8_R3.ItemStack nmsBook =
                    CraftItemStack.asNMSCopy(
                            book
                    );

            EntityPlayer entityPlayer =
                    ((CraftPlayer) staff).getHandle();

            entityPlayer.openBook(
                    nmsBook
            );

        } catch (Throwable throwable) {

            getLogger().severe(
                    "Could not open punishment book."
            );

            throwable.printStackTrace();

            staff.sendMessage(
                    prefix + color(
                            "&cCould not open punishment book."
                    )
            );
        }
    }

    private ItemStack createPunishmentBook(
            String targetName) {

        ItemStack book =
                new ItemStack(
                        Material.WRITTEN_BOOK
                );

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle(
                getConfig().getString(
                        "book.title",
                        "Punishments"
                )
        );

        meta.setAuthor(
                getConfig().getString(
                        "book.author",
                        "PunishmentsBook"
                )
        );

        List<Punishment> punishments =
                getPunishments();

        if (punishments.isEmpty()) {

            meta.addPage(
                    "{\"text\":\"No Punishments configured\",\"color\":\"red\"}"
            );

            book.setItemMeta(meta);

            return book;
        }

        /*
         * Main page.
         */
        StringBuilder page =
                new StringBuilder();

        page.append(
                "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\",\"bold\":true,\"extra\":["
        );

        boolean first = true;

        int number = 1;

        for (Punishment punishment :
                punishments) {

            if (!first) {
                page.append(",");
            }

            first = false;

            /*
             * Number.
             */
            page.append(
                    "{\"text\":\""
            );

            page.append(
                    number
            );

            page.append(
                    ". \",\"color\":\"black\"}"
            );

            /*
             * Punishment name.
             */
            page.append(",");

            page.append(
                    "{\"text\":\""
            );

            page.append(
                    escapeJson(
                            punishment.name
                    )
            );

            page.append(
                    "\",\"color\":\"dark_red\",\"bold\":true"
            );

            /*
             * Click command.
             */
            page.append(
                    ",\"clickEvent\":{\"action\":\"run_command\",\"value\":\""
            );

            page.append(
                    escapeJson(
                            "/pmapply " +
                            targetName +
                            " " +
                            punishment.id
                    )
            );

            page.append(
                    "\"}"
            );

            /*
             * Hover details.
             */
            page.append(
                    ",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\""
            );

            String hover =
                    punishment.name +
                    "\n\n" +
                    "Type: " +
                    punishment.type +
                    "\n" +
                    "Duration: " +
                    punishment.duration +
                    "\n" +
                    "Reason: " +
                    punishment.reason +
                    "\n\n" +
                    getConfig().getString(
                            "book.hover",
                            "Click to apply punishment"
                    );

            page.append(
                    escapeJson(
                            hover
                    )
            );

            page.append(
                    "\"}"
            );

            page.append(
                    "}"
            );

            /*
             * New line.
             */
            page.append(
                    ",{\"text\":\"\\n\"}"
            );

            number++;
        }

        page.append(
                "]}"
        );

        /*
         * Spigot 1.8.8 BookMeta accepts the
         * raw JSON page string.
         */
        meta.addPage(
                page.toString()
        );

        book.setItemMeta(meta);

        return book;
    }

    // =========================================================
    // READ CONFIG
    // =========================================================

    private List<Punishment> getPunishments() {

        List<Punishment> result =
                new ArrayList<Punishment>();

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section == null) {

            getLogger().warning(
                    "Config section 'punishments' was not found."
            );

            return result;
        }

        for (String id :
                section.getKeys(false)) {

            String path =
                    "punishments." + id;

            if (!getConfig().isConfigurationSection(
                    path)) {

                continue;
            }

            Punishment punishment =
                    new Punishment();

            punishment.id =
                    id;

            punishment.name =
                    getConfig().getString(
                            path + ".name",
                            id
                    );

            punishment.type =
                    getConfig().getString(
                            path + ".type",
                            "MUTE"
                    );

            punishment.duration =
                    getConfig().getString(
                            path + ".duration",
                            ""
                    );

            punishment.reason =
                    getConfig().getString(
                            path + ".reason",
                            punishment.name
                    );

            punishment.command =
                    getConfig().getString(
                            path + ".command",
                            ""
                    );

            result.add(
                    punishment
            );
        }

        return result;
    }

    private Punishment getPunishment(
            String id) {

        for (Punishment punishment :
                getPunishments()) {

            if (punishment.id.equalsIgnoreCase(
                    id)) {

                return punishment;
            }
        }

        return null;
    }

    // =========================================================
    // APPLY PUNISHMENT
    // =========================================================

    private void applyPunishment(
            Player staff,
            String targetName,
            String punishmentId) {

        Punishment punishment =
                getPunishment(
                        punishmentId
                );

        if (punishment == null) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "messages.errors.unknown-punishment",
                                    "&cUnknown punishment."
                            )
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
                    prefix + color(
                            getConfig().getString(
                                    "messages.errors.player-not-online",
                                    "&cPlayer must be online."
                            )
                    )
            );

            return;
        }

        String command =
                punishment.command;

        /*
         * Replace variables.
         */
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
                        punishment.duration
                );

        command =
                command.replace(
                        "%reason%",
                        punishment.reason
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

        if (command.trim().isEmpty()) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "messages.errors.command-failed",
                                    "&cPunishment command failed."
                            )
                    )
            );

            return;
        }

        /*
         * IMPORTANT:
         *
         * The command is executed by the actual
         * administrator who clicked the punishment.
         *
         * NOT console.
         *
         * Therefore PunishmentJail receives the
         * administrator as CommandSender.
         */
        boolean success =
                Bukkit.dispatchCommand(
                        staff,
                        command
                );

        if (!success) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "messages.errors.command-failed",
                                    "&cPunishment command failed."
                            )
                    )
            );

            return;
        }

        saveActivePunishment(
                target,
                punishment,
                staff.getName()
        );

        /*
         * Staff messages.
         */
        sendStaffMessage(
                staff,
                "messages.staff.applied",
                target,
                punishment,
                staff.getName()
        );

        sendStaffMessage(
                staff,
                "messages.staff.player",
                target,
                punishment,
                staff.getName()
        );

        sendStaffMessage(
                staff,
                "messages.staff.punishment",
                target,
                punishment,
                staff.getName()
        );

        sendStaffMessage(
                staff,
                "messages.staff.type",
                target,
                punishment,
                staff.getName()
        );

        sendStaffMessage(
                staff,
                "messages.staff.duration",
                target,
                punishment,
                staff.getName()
        );

        sendStaffMessage(
                staff,
                "messages.staff.reason",
                target,
                punishment,
                staff.getName()
        );

        sendStaffMessage(
                staff,
                "messages.staff.staff",
                target,
                punishment,
                staff.getName()
        );

        /*
         * Target messages.
         */
        sendTargetMessage(
                target,
                "messages.target.applied",
                punishment,
                staff.getName()
        );

        sendTargetMessage(
                target,
                "messages.target.player",
                punishment,
                staff.getName()
        );

        sendTargetMessage(
                target,
                "messages.target.punishment",
                punishment,
                staff.getName()
        );

        sendTargetMessage(
                target,
                "messages.target.type",
                punishment,
                staff.getName()
        );

        sendTargetMessage(
                target,
                "messages.target.duration",
                punishment,
                staff.getName()
        );

        sendTargetMessage(
                target,
                "messages.target.reason",
                punishment,
                staff.getName()
        );

        sendTargetMessage(
                target,
                "messages.target.staff",
                punishment,
                staff.getName()
        );
    }

    // =========================================================
    // REVOKE
    // =========================================================

    private void revokePunishment(
            Player staff,
            String targetName) {

        ActivePunishment active =
                findActivePunishment(
                        targetName
                );

        if (active == null) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "revoke.messages.no-punishment",
                                    "&cThis player has no active punishment."
                            )
                    )
            );

            return;
        }

        String command = "";

        if (active.type.equalsIgnoreCase(
                "JAIL")) {

            command =
                    getConfig().getString(
                            "revoke.jail-command",
                            "unjail %player%"
                    );

        } else if (active.type.equalsIgnoreCase(
                "MUTE")) {

            command =
                    getConfig().getString(
                            "revoke.mute-command",
                            "unmute %player%"
                    );

        } else if (active.type.equalsIgnoreCase(
                "BAN")) {

            command =
                    getConfig().getString(
                            "revoke.ban-command",
                            "pardon %player%"
                    );

        } else if (active.type.equalsIgnoreCase(
                "IP-BAN")) {

            command =
                    getConfig().getString(
                            "revoke.ip-ban-command",
                            "pardon-ip %ip%"
                    );
        }

        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        command =
                command.replace(
                        "%player%",
                        active.player
                );

        command =
                command.replace(
                        "%target%",
                        active.player
                );

        command =
                command.replace(
                        "%staff%",
                        staff.getName()
                );

        command =
                command.replace(
                        "%ip%",
                        active.ip == null
                                ? ""
                                : active.ip
                );

        if (!command.trim().isEmpty()) {

            Bukkit.dispatchCommand(
                    staff,
                    command
            );
        }

        activePunishments.remove(
                active.uuid
        );

        dataConfig.set(
                "punishments." +
                active.uuid.toString(),
                null
        );

        saveData();

        String success =
                getConfig().getString(
                        "revoke.messages.success",
                        "&aPunishment &f%punishment% &afor &f%player% &ahas been revoked."
                );

        success =
                replaceActive(
                        success,
                        active,
                        staff.getName()
                );

        staff.sendMessage(
                prefix +
                color(success)
        );

        Player target =
                Bukkit.getPlayerExact(
                        active.player
                );

        if (target != null) {

            String targetMessage =
                    getConfig().getString(
                            "revoke.messages.target",
                            "&aYour punishment has been revoked by &f%staff%&a."
                    );

            targetMessage =
                    replaceActive(
                            targetMessage,
                            active,
                            staff.getName()
                    );

            target.sendMessage(
                    prefix +
                    color(targetMessage)
            );
        }
    }

    private ActivePunishment findActivePunishment(
            String playerName) {

        for (ActivePunishment active :
                activePunishments.values()) {

            if (active.player.equalsIgnoreCase(
                    playerName)) {

                return active;
            }
        }

        return null;
    }

    // =========================================================
    // ACTIVE PUNISHMENTS
    // =========================================================

    private void saveActivePunishment(
            Player target,
            Punishment punishment,
            String staff) {

        ActivePunishment active =
                new ActivePunishment();

        active.uuid =
                target.getUniqueId();

        active.player =
                target.getName();

        active.punishment =
                punishment.name;

        active.type =
                punishment.type;

        active.duration =
                punishment.duration;

        active.reason =
                punishment.reason;

        active.staff =
                staff;

        if (target.getAddress() != null &&
                target.getAddress().getAddress() != null) {

            active.ip =
                    target.getAddress()
                            .getAddress()
                            .getHostAddress();

        } else {

            active.ip = "";
        }

        active.expires =
                System.currentTimeMillis() +
                parseDuration(
                        punishment.duration
                );

        activePunishments.put(
                active.uuid,
                active
        );

        String path =
                "punishments." +
                active.uuid.toString();

        dataConfig.set(
                path + ".uuid",
                active.uuid.toString()
        );

        dataConfig.set(
                path + ".player",
                active.player
        );

        dataConfig.set(
                path + ".punishment",
                active.punishment
        );

        dataConfig.set(
                path + ".type",
                active.type
        );

        dataConfig.set(
                path + ".duration",
                active.duration
        );

        dataConfig.set(
                path + ".reason",
                active.reason
        );

        dataConfig.set(
                path + ".staff",
                active.staff
        );

        dataConfig.set(
                path + ".ip",
                active.ip
        );

        dataConfig.set(
                path + ".expires",
                active.expires
        );

        saveData();
    }

    // =========================================================
    // MUTE
    // =========================================================

    @EventHandler
    public void onChat(
            AsyncPlayerChatEvent event) {

        Player player =
                event.getPlayer();

        ActivePunishment active =
                activePunishments.get(
                        player.getUniqueId()
                );

        if (active == null) {
            return;
        }

        if (!active.type.equalsIgnoreCase(
                "MUTE")) {

            return;
        }

        if (active.expires > 0 &&
                System.currentTimeMillis() >=
                        active.expires) {

            activePunishments.remove(
                    player.getUniqueId()
            );

            dataConfig.set(
                    "punishments." +
                    player.getUniqueId().toString(),
                    null
            );

            saveData();

            return;
        }

        event.setCancelled(true);

        player.sendMessage(
                prefix +
                color(
                        getConfig().getString(
                                "messages.muted",
                                "&cYou are currently muted."
                        )
                )
        );
    }

    // =========================================================
    // CHAT MESSAGES
    // =========================================================

    private void sendStaffMessage(
            Player staff,
            String path,
            Player target,
            Punishment punishment,
            String staffName) {

        String message =
                getConfig().getString(
                        path,
                        ""
                );

        if (message == null ||
                message.isEmpty()) {

            return;
        }

        message =
                message.replace(
                        "%player%",
                        target.getName()
                );

        message =
                message.replace(
                        "%punishment%",
                        punishment.name
                );

        message =
                message.replace(
                        "%type%",
                        punishment.type
                );

        message =
                message.replace(
                        "%duration%",
                        punishment.duration
                );

        message =
                message.replace(
                        "%reason%",
                        punishment.reason
                );

        message =
                message.replace(
                        "%staff%",
                        staffName
                );

        staff.sendMessage(
                color(message)
        );
    }

    private void sendTargetMessage(
            Player target,
            String path,
            Punishment punishment,
            String staffName) {

        String message =
                getConfig().getString(
                        path,
                        ""
                );

        if (message == null ||
                message.isEmpty()) {

            return;
        }

        message =
                message.replace(
                        "%player%",
                        target.getName()
                );

        message =
                message.replace(
                        "%punishment%",
                        punishment.name
                );

        message =
                message.replace(
                        "%type%",
                        punishment.type
                );

        message =
                message.replace(
                        "%duration%",
                        punishment.duration
                );

        message =
                message.replace(
                        "%reason%",
                        punishment.reason
                );

        message =
                message.replace(
                        "%staff%",
                        staffName
                );

        target.sendMessage(
                color(message)
        );
    }

    // =========================================================
    // DATA FILE
    // =========================================================

    private void setupData() {

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
                org.bukkit.configuration.file
                        .YamlConfiguration
                        .loadConfiguration(
                                dataFile
                        );
    }

    private void loadActivePunishments() {

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

                ActivePunishment active =
                        new ActivePunishment();

                active.uuid =
                        UUID.fromString(
                                uuidString
                        );

                active.player =
                        dataConfig.getString(
                                path + ".player",
                                ""
                        );

                active.punishment =
                        dataConfig.getString(
                                path + ".punishment",
                                ""
                        );

                active.type =
                        dataConfig.getString(
                                path + ".type",
                                ""
                        );

                active.duration =
                        dataConfig.getString(
                                path + ".duration",
                                ""
                        );

                active.reason =
                        dataConfig.getString(
                                path + ".reason",
                                ""
                        );

                active.staff =
                        dataConfig.getString(
                                path + ".staff",
                                ""
                        );

                active.ip =
                        dataConfig.getString(
                                path + ".ip",
                                ""
                        );

                active.expires =
                        dataConfig.getLong(
                                path + ".expires",
                                0L
                        );

                activePunishments.put(
                        active.uuid,
                        active
                );

            } catch (Exception ignored) {
            }
        }
    }

    private void saveData() {

        try {

            dataConfig.save(
                    dataFile
            );

        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    // =========================================================
    // UTILITIES
    // =========================================================

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

    private String replaceActive(
            String message,
            ActivePunishment active,
            String staff) {

        return message
                .replace(
                        "%player%",
                        active.player
                )
                .replace(
                        "%punishment%",
                        active.punishment
                )
                .replace(
                        "%type%",
                        active.type
                )
                .replace(
                        "%duration%",
                        active.duration
                )
                .replace(
                        "%reason%",
                        active.reason
                )
                .replace(
                        "%staff%",
                        staff
                )
                .replace(
                        "%ip%",
                        active.ip == null
                                ? ""
                                : active.ip
                );
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
                    Long.parseLong(
                            number
                    );

        } catch (NumberFormatException e) {

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

    // =========================================================
    // CLASSES
    // =========================================================

    private static class Punishment {

        private String id;
        private String name;
        private String type;
        private String duration;
        private String reason;
        private String command;
    }

    private static class ActivePunishment {

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
