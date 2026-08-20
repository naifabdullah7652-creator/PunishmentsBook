package com.flynexx.punishments;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import org.bukkit.plugin.java.JavaPlugin;

public class PunishmentsBook extends JavaPlugin implements Listener {

    private final Map<UUID, PunishmentData> punishments =
            new HashMap<UUID, PunishmentData>();

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

    /*
     * ============================================================
     * COMMANDS
     * ============================================================
     */

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        String cmd = command.getName().toLowerCase();

        /*
         * /pm <player>
         */
        if (cmd.equals("pm")) {

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
                        prefix + color("&cUsage: /pm <player>")
                );
                return true;
            }

            openBook(staff, args[0]);
            return true;
        }

        /*
         * /pmapply <player> <punishment>
         */
        if (cmd.equals("pmapply")) {

            if (!(sender instanceof Player)) {
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
                        prefix +
                                color(
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
        if (cmd.equals("pmrevoke")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.revoke")) {
                staff.sendMessage(
                        message("revoke.messages.no-permission")
                );
                return true;
            }

            if (args.length != 1) {
                staff.sendMessage(
                        prefix +
                                color(
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
     * ============================================================
     * BOOK
     * ============================================================
     */

    private void openBook(Player player, String target) {

        try {

            /*
             * IMPORTANT:
             * This is NMS ItemStack.
             * Required by EntityPlayer.openBook() in 1.8.8.
             */
            net.minecraft.server.v1_8_R3.ItemStack nmsBook =
                    createBook(target);

            /*
             * Convert NMS -> Bukkit only when putting it
             * into the player's inventory.
             */
            org.bukkit.inventory.ItemStack bukkitBook =
                    CraftItemStack.asBukkitCopy(nmsBook);

            org.bukkit.inventory.ItemStack oldItem =
                    player.getItemInHand();

            player.setItemInHand(bukkitBook);
            player.updateInventory();

            EntityPlayer entity =
                    ((CraftPlayer) player).getHandle();

            /*
             * 1.8.8 method.
             */
            entity.openBook(nmsBook);

            Bukkit.getScheduler().runTaskLater(
                    this,
                    new Runnable() {
                        @Override
                        public void run() {

                            if (player.isOnline()) {
                                player.setItemInHand(oldItem);
                                player.updateInventory();
                            }
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
                    prefix +
                            color("&cCould not open punishment book.")
            );
        }
    }

    private net.minecraft.server.v1_8_R3.ItemStack createBook(
            String target
    ) {

        net.minecraft.server.v1_8_R3.ItemStack book =
                CraftItemStack.asNMSCopy(
                        new org.bukkit.inventory.ItemStack(
                                Material.WRITTEN_BOOK
                        )
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

            tag.set("pages", pages);
            book.setTag(tag);

            return book;
        }

        StringBuilder json =
                new StringBuilder();

        json.append(
                "{\"text\":\"Punishments\\n\\n\","
                        + "\"color\":\"dark_red\","
                        + "\"bold\":true,"
                        + "\"extra\":["
        );

        boolean first = true;

        for (String id : section.getKeys(false)) {

            String path =
                    "punishments." + id;

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
                            ""
                    );

            if (!first) {
                json.append(",");
            }

            first = false;

            String command =
                    "/pmapply " +
                            target +
                            " " +
                            id;

            String hover =
                    "Punishment: " +
                            name +
                            "\\nType: " +
                            type +
                            "\\nDuration: " +
                            duration +
                            "\\nReason: " +
                            reason;

            json.append("{");

            json.append("\"text\":\"")
                    .append(escapeJson(name))
                    .append("\",");

            json.append("\"color\":\"black\",");
            json.append("\"underlined\":true,");

            json.append("\"clickEvent\":{");
            json.append("\"action\":\"run_command\",");
            json.append("\"value\":\"")
                    .append(escapeJson(command))
                    .append("\"");
            json.append("},");

            json.append("\"hoverEvent\":{");
            json.append("\"action\":\"show_text\",");
            json.append("\"value\":\"")
                    .append(escapeJson(hover))
                    .append("\"");
            json.append("}");

            json.append("}");

            json.append(",");

            json.append(
                    "{\"text\":\"\\n\"}"
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
     * ============================================================
     * APPLY PUNISHMENT
     * ============================================================
     */

    private void applyPunishment(
            Player staff,
            String targetName,
            String id
    ) {

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

        String type =
                getConfig().getString(
                        path + ".type",
                        "MUTE"
                );

        String command =
                getConfig().getString(
                        path + ".command",
                        ""
                );

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

        /*
         * Execute punishment as the ADMIN.
         */
        if (!command.trim().isEmpty()) {

            if (command.startsWith("/")) {
                command =
                        command.substring(1);
            }

            boolean success =
                    Bukkit.dispatchCommand(
                            staff,
                            command
                    );

            if (!success) {

                staff.sendMessage(
                        message(
                                "messages.errors.command-failed"
                        )
                );

                return;
            }
        }

        /*
         * Save local punishment data.
         */
        long milliseconds =
                parseDuration(duration);

        if (milliseconds > 0L) {

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

            data.expires =
                    System.currentTimeMillis()
                            + milliseconds;

            punishments.put(
                    target.getUniqueId(),
                    data
            );

            savePunishment(data);
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

    /*
     * ============================================================
     * REVOKE
     * ============================================================
     */

    private void revokePunishment(
            Player staff,
            String targetName
    ) {

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

        PunishmentData data =
                punishments.get(
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

        String revokeCommand;

        /*
         * JAIL
         *
         * This uses the command configured for
         * PunishmentJail.
         */
        if (data.type.equalsIgnoreCase("JAIL")) {

            revokeCommand =
                    getConfig().getString(
                            "revoke.jail-command",
                            "unjail %player%"
                    );

        } else if (data.type.equalsIgnoreCase("MUTE")) {

            revokeCommand =
                    getConfig().getString(
                            "revoke.mute-command",
                            "unmute %player%"
                    );

        } else if (data.type.equalsIgnoreCase("BAN")) {

            revokeCommand =
                    getConfig().getString(
                            "revoke.ban-command",
                            "pardon %player%"
                    );

        } else if (data.type.equalsIgnoreCase("IP-BAN")) {

            revokeCommand =
                    getConfig().getString(
                            "revoke.ip-ban-command",
                            "pardon-ip %ip%"
                    );

        } else {

            staff.sendMessage(
                    message(
                            "messages.errors.command-failed"
                    )
            );

            return;
        }

        String ip = "";

        if (target.getAddress() != null &&
                target.getAddress().getAddress() != null) {

            ip =
                    target.getAddress()
                            .getAddress()
                            .getHostAddress();
        }

        revokeCommand =
                revokeCommand
                        .replace(
                                "%player%",
                                target.getName()
                        )
                        .replace(
                                "%target%",
                                target.getName()
                        )
                        .replace(
                                "%ip%",
                                ip
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        );

        if (revokeCommand.startsWith("/")) {
            revokeCommand =
                    revokeCommand.substring(1);
        }

        /*
         * IMPORTANT:
         *
         * The ADMIN executes revoke.
         * NOT console.
         */
        boolean success =
                Bukkit.dispatchCommand(
                        staff,
                        revokeCommand
                );

        if (!success) {

            staff.sendMessage(
                    message(
                            "messages.errors.command-failed"
                    )
            );

            return;
        }

        punishments.remove(
                target.getUniqueId()
        );

        removePunishmentFromFile(
                target.getUniqueId()
        );

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
                                target.getName()
                        )
                        .replace(
                                "%punishment%",
                                data.punishment
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        );

        staff.sendMessage(
                color(successMessage)
        );

        String targetMessage =
                getConfig().getString(
                        "revoke.messages.target",
                        "&aYour punishment has been revoked by &f%staff%&a."
                );

        targetMessage =
                targetMessage
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
                                staff.getName()
                        );

        target.sendMessage(
                color(targetMessage)
        );
    }

    /*
     * ============================================================
     * STAFF MESSAGES
     * ============================================================
     */

    private void sendStaffDetails(
            Player staff,
            Player target,
            String punishment,
            String type,
            String duration,
            String reason
    ) {

        staff.sendMessage("");

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
     * ============================================================
     * TARGET MESSAGES
     * ============================================================
     */

    private void sendTargetDetails(
            Player target,
            String punishment,
            String type,
            String duration,
            String reason,
            String staff
    ) {

        target.sendMessage("");

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
     * ============================================================
     * MUTE
     * ============================================================
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

        if (!data.type.equalsIgnoreCase("MUTE")) {
            return;
        }

        if (data.expires <=
                System.currentTimeMillis()) {

            punishments.remove(
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
     * ============================================================
     * BAN
     * ============================================================
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

        if (data == null) {
            return;
        }

        if (data.expires <=
                System.currentTimeMillis()) {

            punishments.remove(
                    player.getUniqueId()
            );

            removePunishmentFromFile(
                    player.getUniqueId()
            );

            savePunishments();

            return;
        }

        if (data.type.equalsIgnoreCase("BAN")
                || data.type.equalsIgnoreCase("IP-BAN")) {

            event.disallow(
                    PlayerLoginEvent.Result.KICK_BANNED,
                    color(
                            "&cYou are banned.\n\n"
                                    + "&fPunishment: "
                                    + data.punishment
                                    + "\n&fReason: "
                                    + data.reason
                                    + "\n&fDuration: "
                                    + data.duration
                                    + "\n&fStaff: "
                                    + data.staff
                    )
            );
        }
    }

    /*
     * ============================================================
     * EXPIRED PUNISHMENTS
     * ============================================================
     */

    private void checkExpiredPunishments() {

        long now =
                System.currentTimeMillis();

        ArrayList<UUID> ids =
                new ArrayList<UUID>(
                        punishments.keySet()
                );

        for (UUID uuid : ids) {

            PunishmentData data =
                    punishments.get(uuid);

            if (data != null &&
                    data.expires <= now) {

                punishments.remove(uuid);

                removePunishmentFromFile(uuid);
            }
        }

        savePunishments();
    }

    /*
     * ============================================================
     * DURATION
     * ============================================================
     */

    private long parseDuration(
            String duration
    ) {

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

    /*
     * ============================================================
     * DATA
     * ============================================================
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

        try {

            if (!dataFile.exists()) {
                dataFile.createNewFile();
            }

        } catch (IOException ex) {

            ex.printStackTrace();
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
                                path + ".expires",
                                0L
                        );

                if (data.expires >
                        System.currentTimeMillis()) {

                    punishments.put(
                            data.uuid,
                            data
                    );
                }

            } catch (Exception ex) {

                getLogger().warning(
                        "Could not load punishment: "
                                + key
                );
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

            dataConfig.save(dataFile);

        } catch (IOException ex) {

            ex.printStackTrace();
        }
    }

    /*
     * ============================================================
     * HELPERS
     * ============================================================
     */

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

    /*
     * ============================================================
     * PUNISHMENT DATA
     * ============================================================
     */

    private static class PunishmentData {

        UUID uuid;
        String player;
        String punishment;
        String type;
        String duration;
        String reason;
        String staff;
        long expires;
    }
}
