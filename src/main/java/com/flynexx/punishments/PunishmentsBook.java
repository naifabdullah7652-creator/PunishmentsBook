package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;

import org.bukkit.BanList;
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
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

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

        getLogger().info("PunishmentsBook enabled.");
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
                        prefix + color(
                                "&cYou don't have permission."
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

    /*
     * =========================================================
     * BOOK
     * =========================================================
     */

    private void openBook(
            Player player,
            String target) {

        try {

            ItemStack book =
                    createBook(target);

            EntityPlayer entityPlayer =
                    ((CraftPlayer) player).getHandle();

            entityPlayer.openBook(book);

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

    private ItemStack createBook(
            String target) {

        org.bukkit.inventory.ItemStack bukkitBook =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        BookMeta meta =
                (BookMeta) bukkitBook.getItemMeta();

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

        /*
         * We build the page ourselves using NMS
         * because Spigot 1.8.8 does not support
         * the newer BookMeta Spigot API.
         */
        ItemStack nmsBook =
                CraftItemStack.asNMSCopy(
                        bukkitBook
                );

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

        List<PunishmentConfig> punishments =
                getPunishments();

        if (punishments.isEmpty()) {

            pages.add(
                    new NBTTagString(
                            "{\"text\":\"No Punishments configured\",\"color\":\"red\"}"
                    )
            );

            tag.set(
                    "pages",
                    pages
            );

            nmsBook.setTag(tag);

            return nmsBook;
        }

        /*
         * One page containing the seven punishments.
         */
        StringBuilder page =
                new StringBuilder();

        page.append(
                "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\",\"bold\":true,\"extra\":["
        );

        boolean first = true;

        for (PunishmentConfig punishment :
                punishments) {

            if (!first) {
                page.append(",");
            }

            first = false;

            String command =
                    "/pmapply " +
                    target +
                    " " +
                    punishment.id;

            /*
             * Visible name.
             */
            page.append(
                    "{\"text\":\""
            );

            page.append(
                    escapeJson(
                            punishment.name
                    )
            );

            page.append(
                    "\",\"color\":\"black\",\"underlined\":true"
            );

            /*
             * Click.
             */
            page.append(
                    ",\"clickEvent\":{\"action\":\"run_command\",\"value\":\""
            );

            page.append(
                    escapeJson(
                            command
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

            page.append(
                    escapeJson(
                            punishment.name +
                            "\nType: " +
                            punishment.type +
                            "\nDuration: " +
                            punishment.duration +
                            "\nReason: " +
                            punishment.reason
                    )
            );

            page.append(
                    "\"}"
            );

            page.append(
                    "}"
            );

            page.append(
                    ",{\"text\":\"\\n\"}"
            );
        }

        page.append(
                "]}"
        );

        pages.add(
                new NBTTagString(
                        page.toString()
                )
        );

        tag.set(
                "pages",
                pages
        );

        nmsBook.setTag(tag);

        return nmsBook;
    }

    /*
     * =========================================================
     * PUNISHMENTS CONFIG
     * =========================================================
     *
     * Reads:
     *
     * punishments:
     *   hacking:
     *     name:
     *     type:
     *     ...
     */

    private List<PunishmentConfig> getPunishments() {

        List<PunishmentConfig> result =
                new ArrayList<PunishmentConfig>();

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section == null) {

            getLogger().warning(
                    "Missing config section: punishments"
            );

            return result;
        }

        for (String id :
                section.getKeys(false)) {

            String path =
                    "punishments." + id;

            /*
             * Ignore accidental non-sections.
             */
            if (!getConfig().isConfigurationSection(
                    path)) {
                continue;
            }

            PunishmentConfig punishment =
                    new PunishmentConfig();

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

    private PunishmentConfig getPunishment(
            String id) {

        for (PunishmentConfig punishment :
                getPunishments()) {

            if (punishment.id.equalsIgnoreCase(id)) {
                return punishment;
            }
        }

        return null;
    }

    /*
     * =========================================================
     * APPLY
     * =========================================================
     */

    private void applyPunishment(
            Player staff,
            String targetName,
            String id) {

        PunishmentConfig punishment =
                getPunishment(id);

        if (punishment == null) {

            staff.sendMessage(
                    prefix +
                    color(
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
                    prefix +
                    color(
                            getConfig().getString(
                                    "messages.errors.player-not-online",
                                    "&cPlayer must be online."
                            )
                    )
            );

            return;
        }

        /*
         * IP BAN
         */
        if (punishment.type.equalsIgnoreCase(
                "IP-BAN")) {

            applyIpBan(
                    staff,
                    target,
                    punishment
            );

            return;
        }

        String command =
                punishment.command;

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
                                punishment.duration
                        )
                        .replace(
                                "%reason%",
                                punishment.reason
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        );

        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        if (command.trim().isEmpty()) {

            staff.sendMessage(
                    prefix +
                    color(
                            "&cNo command configured for this punishment."
                    )
            );

            return;
        }

        /*
         * IMPORTANT:
         *
         * Execute as the staff member.
         *
         * This allows PunishmentJail to receive
         * the real administrator as the sender.
         */
        boolean success =
                Bukkit.dispatchCommand(
                        staff,
                        command
                );

        if (!success) {

            staff.sendMessage(
                    prefix +
                    color(
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
                staff.getName(),
                ""
        );

        sendStaffDetails(
                staff,
                target,
                punishment,
                staff.getName()
        );

        sendTargetDetails(
                target,
                punishment,
                staff.getName()
        );
    }

    /*
     * =========================================================
     * IP BAN
     * =========================================================
     */

    private void applyIpBan(
            Player staff,
            Player target,
            PunishmentConfig punishment) {

        if (target.getAddress() == null ||
                target.getAddress().getAddress() == null) {

            staff.sendMessage(
                    prefix +
                    color(
                            "&cCould not determine player's IP."
                    )
            );

            return;
        }

        String ip =
                target.getAddress()
                        .getAddress()
                        .getHostAddress();

        if (ip == null ||
                ip.trim().isEmpty()) {

            return;
        }

        Bukkit.getBanList(
                BanList.Type.IP
        ).addBan(
                ip,
                punishment.reason,
                null,
                staff.getName()
        );

        saveActivePunishment(
                target,
                punishment,
                staff.getName(),
                ip
        );

        sendStaffDetails(
                staff,
                target,
                punishment,
                staff.getName()
        );

        sendTargetDetails(
                target,
                punishment,
                staff.getName()
        );

        target.kickPlayer(
                prefix +
                color(
                        "&cYou have been IP banned.\n" +
                        "&7Reason: &f" +
                        punishment.reason
                )
        );
    }

    /*
     * =========================================================
     * REVOKE
     * =========================================================
     */

    private void revokePunishment(
            Player staff,
            String targetName) {

        PunishmentData data = null;

        for (PunishmentData value :
                activePunishments.values()) {

            if (value.player.equalsIgnoreCase(
                    targetName)) {

                data = value;
                break;
            }
        }

        if (data == null) {

            staff.sendMessage(
                    prefix +
                    color(
                            getConfig().getString(
                                    "revoke.messages.no-punishment",
                                    "&cThis player has no active punishment."
                            )
                    )
            );

            return;
        }

        /*
         * IP-BAN
         */
        if (data.type.equalsIgnoreCase(
                "IP-BAN")) {

            if (data.ip != null &&
                    !data.ip.trim().isEmpty()) {

                Bukkit.getBanList(
                        BanList.Type.IP
                ).pardon(
                        data.ip
                );
            }

        }

        /*
         * JAIL
         */
        else if (data.type.equalsIgnoreCase(
                "JAIL")) {

            String command =
                    getConfig().getString(
                            "revoke.jail-command",
                            "unjail %player%"
                    );

            executeRevokeCommand(
                    staff,
                    command,
                    data.player
            );
        }

        /*
         * MUTE
         */
        else if (data.type.equalsIgnoreCase(
                "MUTE")) {

            String command =
                    getConfig().getString(
                            "revoke.mute-command",
                            "unmute %player%"
                    );

            executeRevokeCommand(
                    staff,
                    command,
                    data.player
            );
        }

        /*
         * BAN
         */
        else if (data.type.equalsIgnoreCase(
                "BAN")) {

            String command =
                    getConfig().getString(
                            "revoke.ban-command",
                            "pardon %player%"
                    );

            executeRevokeCommand(
                    staff,
                    command,
                    data.player
            );
        }

        activePunishments.remove(
                data.uuid
        );

        removePunishmentFromFile(
                data.uuid
        );

        savePunishments();

        String message =
                getConfig().getString(
                        "revoke.messages.success",
                        "&aPunishment &f%punishment% &afor &f%player% &ahas been revoked."
                );

        message =
                replaceDetails(
                        message,
                        data,
                        staff.getName()
                );

        staff.sendMessage(
                prefix +
                color(message)
        );

        Player target =
                Bukkit.getPlayerExact(
                        data.player
                );

        if (target != null) {

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

            target.sendMessage(
                    prefix +
                    color(targetMessage)
            );
        }
    }

    private void executeRevokeCommand(
            Player staff,
            String command,
            String player) {

        if (command == null ||
                command.trim().isEmpty()) {
            return;
        }

        command =
                command
                        .replace(
                                "%player%",
                                player
                        )
                        .replace(
                                "%target%",
                                player
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
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

    /*
     * =========================================================
     * SAVE
     * =========================================================
     */

    private void saveActivePunishment(
            Player target,
            PunishmentConfig punishment,
            String staff,
            String ip) {

        PunishmentData data =
                new PunishmentData();

        data.uuid =
                target.getUniqueId();

        data.player =
                target.getName();

        data.punishment =
                punishment.name;

        data.type =
                punishment.type;

        data.duration =
                punishment.duration;

        data.reason =
                punishment.reason;

        data.staff =
                staff;

        data.ip =
                ip;

        data.expires =
                parseDuration(
                        punishment.duration
                );

        if (data.expires > 0L) {

            data.expires +=
                    System.currentTimeMillis();
        }

        activePunishments.put(
                data.uuid,
                data
        );

        savePunishment(data);
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

            dataConfig.save(
                    dataFile
            );

        } catch (IOException ex) {

            ex.printStackTrace();
        }
    }

    /*
     * =========================================================
     * LOAD
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
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        dataConfig =
                org.bukkit.configuration.file.YamlConfiguration
                        .loadConfiguration(
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

    /*
     * =========================================================
     * MUTE
     * =========================================================
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
                        getConfig().getString(
                                "messages.muted",
                                "&cYou are currently muted."
                        )
                )
        );
    }

    /*
     * =========================================================
     * CHAT DETAILS
     * =========================================================
     */

    private void sendStaffDetails(
            Player staff,
            Player target,
            PunishmentConfig punishment,
            String staffName) {

        sendConfigured(
                staff,
                "messages.staff.applied",
                target,
                punishment,
                staffName
        );

        sendConfigured(
                staff,
                "messages.staff.player",
                target,
                punishment,
                staffName
        );

        sendConfigured(
                staff,
                "messages.staff.punishment",
                target,
                punishment,
                staffName
        );

        sendConfigured(
                staff,
                "messages.staff.type",
                target,
                punishment,
                staffName
        );

        sendConfigured(
                staff,
                "messages.staff.duration",
                target,
                punishment,
                staffName
        );

        sendConfigured(
                staff,
                "messages.staff.reason",
                target,
                punishment,
                staffName
        );

        sendConfigured(
                staff,
                "messages.staff.staff",
                target,
                punishment,
                staffName
        );
    }

    private void sendTargetDetails(
            Player target,
            PunishmentConfig punishment,
            String staff) {

        sendConfigured(
                target,
                "messages.target.applied",
                target,
                punishment,
                staff
        );

        sendConfigured(
                target,
                "messages.target.player",
                target,
                punishment,
                staff
        );

        sendConfigured(
                target,
                "messages.target.punishment",
                target,
                punishment,
                staff
        );

        sendConfigured(
                target,
                "messages.target.type",
                target,
                punishment,
                staff
        );

        sendConfigured(
                target,
                "messages.target.duration",
                target,
                punishment,
                staff
        );

        sendConfigured(
                target,
                "messages.target.reason",
                target,
                punishment,
                staff
        );

        sendConfigured(
                target,
                "messages.target.staff",
                target,
                punishment,
                staff
        );
    }

    private void sendConfigured(
            Player receiver,
            String path,
            Player target,
            PunishmentConfig punishment,
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
                                punishment.name
                        )
                        .replace(
                                "%type%",
                                punishment.type
                        )
                        .replace(
                                "%duration%",
                                punishment.duration
                        )
                        .replace(
                                "%reason%",
                                punishment.reason
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
     * =========================================================
     * UTILITIES
     * =========================================================
     */

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

    /*
     * =========================================================
     * DATA CLASSES
     * =========================================================
     */

    private static class PunishmentConfig {

        private String id;
        private String name;
        private String type;
        private String duration;
        private String reason;
        private String command;
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
