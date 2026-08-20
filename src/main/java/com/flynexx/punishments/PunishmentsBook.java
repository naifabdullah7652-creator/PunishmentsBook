package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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

        Bukkit.getPluginManager().registerEvents(
                this,
                this
        );

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

            if (!staff.hasPermission("punishmentsbook.use")) {
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
     * Opens the Minecraft 1.8.8 written book.
     */
    private void openBook(
            Player player,
            String target) {

        try {

            ItemStack book =
                    createBook(target);

            org.bukkit.inventory.ItemStack bukkitBook =
                    CraftItemStack.asBukkitCopy(
                            book
                    );

            final org.bukkit.inventory.ItemStack oldItem =
                    player.getItemInHand();

            player.setItemInHand(
                    bukkitBook
            );

            player.updateInventory();

            EntityPlayer entityPlayer =
                    ((CraftPlayer) player).getHandle();

            entityPlayer.openBook(book);

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
     * Only punishment names are visible.
     * Duration/details are NOT displayed in the book.
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

            tag.set("pages", pages);
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
             * Clicking the punishment executes pmapply.
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
     * Applies a punishment.
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
         * Replace placeholders.
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
            command = command.substring(1);
        }

        boolean success = true;

        /*
         * IMPORTANT:
         *
         * The command is executed by the staff member.
         *
         * For Hacking:
         *
         * jail Player 3 days Using Hacking
         *
         * PunishmentJail remains responsible
         * for the actual jail.
         */
        if (!command.trim().isEmpty()) {

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

        /*
         * Save the punishment.
         *
         * This is used by /pmrevoke and the
         * internal MUTE/BAN handling.
         */
        PunishmentData data =
                new PunishmentData();

        data.uuid =
                target.getUniqueId();

        data.player =
                target.getName();

        data.punishment =
                name;

        data.type =
                type;

        data.duration =
                duration;

        data.reason =
                reason;

        data.staff =
                staff.getName();

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

        /*
         * Staff details.
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
         * Target details.
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
     * Revoke any of the seven punishments.
     */
    private void revokePunishment(
            Player staff,
            String targetName) {

        Player target =
                Bukkit.getPlayerExact(targetName);

        if (target == null) {

            staff.sendMessage(
                    message(
                            "revoke.messages.player-not-online"
                    )
            );

            return;
        }

        PunishmentData data =
                activePunishments.remove(
                        target.getUniqueId()
                );

        if (data == null) {

            staff.sendMessage(
                    message(
                            "revoke.messages.no-punishment"
                    )
            );

            return;
        }

        String type =
                data.type.toUpperCase();

        String command = "";

        /*
         * Jail -> PunishmentJail
         */
        if (type.equals("JAIL")) {

            command =
                    getConfig().getString(
                            "revoke.jail-command",
                            "unjail %player%"
                    );
        }

        /*
         * Mute -> external mute plugin
         */
        else if (type.equals("MUTE")) {

            command =
                    getConfig().getString(
                            "revoke.mute-command",
                            "unmute %player%"
                    );
        }

        /*
         * Ban
         */
        else if (type.equals("BAN")) {

            command =
                    getConfig().getString(
                            "revoke.ban-command",
                            "unban %player%"
                    );
        }

        /*
         * IP Ban
         */
        else if (type.equals("IP-BAN")) {

            command =
                    getConfig().getString(
                            "revoke.ip-ban-command",
                            "unban %player%"
                    );
        }

        if (!command.trim().isEmpty()) {

            command =
                    command
                            .replace(
                                    "%player%",
                                    target.getName()
                            )
                            .replace(
                                    "%staff%",
                                    staff.getName()
                            )
                            .replace(
                                    "%punishment%",
                                    data.punishment
                            );

            if (command.startsWith("/")) {
                command =
                        command.substring(1);
            }

            Bukkit.dispatchCommand(
                    staff,
                    command
            );
        }

        removePunishmentFromFile(
                target.getUniqueId()
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
                        target,
                        data,
                        staff.getName()
                );

        staff.sendMessage(
                prefix + color(staffMessage)
        );

        String targetMessage =
                getConfig().getString(
                        "revoke.messages.target",
                        "&aYour punishment has been revoked by &f%staff%&a."
                );

        targetMessage =
                replaceDetails(
                        targetMessage,
                        target,
                        data,
                        staff.getName()
                );

        target.sendMessage(
                prefix + color(targetMessage)
        );
    }

    private String replaceDetails(
            String text,
            Player target,
            PunishmentData data,
            String staff) {

        return text
                .replace(
                        "%player%",
                        target.getName()
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
                );
    }

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

    /*
     * Internal mute handling.
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

        if (!data.type.equalsIgnoreCase("MUTE")) {
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

    private void checkExpiredPunishments() {

        long now =
                System.currentTimeMillis();

        ArrayList<UUID> ids =
                new ArrayList<UUID>(
                        activePunishments.keySet()
                );

        for (UUID uuid : ids) {

            PunishmentData data =
                    activePunishments.get(uuid);

            if (data == null) {
                continue;
            }

            /*
             * Jail is controlled by PunishmentJail.
             * Do not automatically release it here.
             */
            if (data.type.equalsIgnoreCase("JAIL")) {
                continue;
            }

            if (data.expires > 0L &&
                    data.expires <= now) {

                activePunishments.remove(uuid);

                removePunishmentFromFile(uuid);
            }
        }

        savePunishments();
    }

    private long parseDuration(
            String duration) {

        if (duration == null ||
                duration.trim().isEmpty()) {
            return 0L;
        }

        String value =
                duration.trim().toLowerCase();

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
                        UUID.fromString(uuidString);

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

                data.expires =
                        dataConfig.getLong(
                                path + ".expires"
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
        private long expires;
    }
}
