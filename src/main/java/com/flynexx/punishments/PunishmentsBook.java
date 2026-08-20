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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PunishmentsBook extends JavaPlugin implements Listener {

    private final Map<UUID, PunishmentData> punishments = new HashMap<UUID, PunishmentData>();

    private File dataFile;
    private FileConfiguration dataConfig;

    private String prefix;

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
        getLogger().info("Internal MUTE/BAN/IP-BAN system enabled.");
        getLogger().info("PunishmentJail is used for JAIL punishments.");
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
        // /pm <player>
        // =====================================================

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
                        prefix + color("&cUsage: /pm <player>")
                );
                return true;
            }

            openBook(staff, args[0]);
            return true;
        }

        // =====================================================
        // /pmapply <player> <punishment>
        // =====================================================

        if (command.getName().equalsIgnoreCase("pmapply")) {

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
                        prefix + color("&cUsage: /pmapply <player> <punishment>")
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
        // /pmrevoke <player>
        // =====================================================

        if (command.getName().equalsIgnoreCase("pmrevoke")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.revoke")) {
                staff.sendMessage(
                        revokeMessage("no-permission")
                );
                return true;
            }

            if (args.length != 1) {
                staff.sendMessage(
                        prefix + color("&cUsage: /pmrevoke <player>")
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
    // OPEN BOOK
    // =========================================================

    private void openBook(Player player, String target) {

        try {

            net.minecraft.server.v1_8_R3.ItemStack nmsBook =
                    createBook(target);

            ItemStack bukkitBook =
                    CraftItemStack.asBukkitCopy(nmsBook);

            ItemStack oldItem =
                    player.getItemInHand();

            player.setItemInHand(bukkitBook);
            player.updateInventory();

            EntityPlayer entity =
                    ((CraftPlayer) player).getHandle();

            entity.openBook(nmsBook);

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
                    "Could not open punishment book: "
                            + ex.getClass().getSimpleName()
                            + ": "
                            + ex.getMessage()
            );

            player.sendMessage(
                    prefix
                            + color("&cCould not open punishment book.")
            );
        }
    }

    // =========================================================
    // CREATE BOOK
    // =========================================================

    private net.minecraft.server.v1_8_R3.ItemStack createBook(
            String target
    ) {

        ItemStack base =
                new ItemStack(Material.WRITTEN_BOOK);

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

        String hover =
                getConfig().getString(
                        "book.hover",
                        "Click to apply punishment"
                );

        tag.setString("title", title);
        tag.setString("author", author);

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

        for (String id : section.getKeys(false)) {

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

            json.append(
                    "{\"text\":\""
            );

            json.append(
                    escapeJson(name)
            );

            json.append(
                    "\",\"color\":\"black\",\"underlined\":true,"
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

            json.append(
                    escapeJson(hover + "\n" + name)
            );

            json.append(
                    "\"}}"
            );

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

    // =========================================================
    // APPLY PUNISHMENT
    // =========================================================

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

        if (durationMillis <= 0L) {

            staff.sendMessage(
                    prefix
                            + color(
                            "&cInvalid punishment duration."
                    )
            );

            return;
        }

        // =====================================================
        // JAIL
        // PunishmentJail handles this command.
        // =====================================================

        if (type.equalsIgnoreCase("JAIL")) {

            String configuredCommand =
                    getConfig().getString(
                            path + ".command",
                            ""
                    );

            String jailCommand =
                    replacePlaceholders(
                            configuredCommand,
                            target,
                            staff,
                            name,
                            type,
                            duration,
                            reason
                    );

            boolean success =
                    dispatchCommand(
                            staff,
                            jailCommand
                    );

            if (!success) {

                staff.sendMessage(
                        message(
                                "messages.errors.command-failed"
                        )
                );

                return;
            }

            PunishmentData data =
                    new PunishmentData(
                            target.getUniqueId(),
                            target.getName(),
                            name,
                            type,
                            duration,
                            reason,
                            staff.getName(),
                            System.currentTimeMillis()
                                    + durationMillis
                    );

            punishments.put(
                    target.getUniqueId(),
                    data
            );

            savePunishment(data);

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

            return;
        }

        // =====================================================
        // INTERNAL MUTE / BAN / IP-BAN
        // =====================================================

        PunishmentData data =
                new PunishmentData(
                        target.getUniqueId(),
                        target.getName(),
                        name,
                        type,
                        duration,
                        reason,
                        staff.getName(),
                        System.currentTimeMillis()
                                + durationMillis
                );

        punishments.put(
                target.getUniqueId(),
                data
        );

        savePunishment(data);

        // Kick player for BAN / IP-BAN
        if (type.equalsIgnoreCase("BAN")
                || type.equalsIgnoreCase("IP-BAN")) {

            target.kickPlayer(
                    color(
                            "&cYou have been banned.\n\n"
                                    + "&fPunishment: &7"
                                    + name
                                    + "\n"
                                    + "&fReason: &7"
                                    + reason
                                    + "\n"
                                    + "&fDuration: &7"
                                    + duration
                                    + "\n"
                                    + "&fStaff: &7"
                                    + staff.getName()
                    )
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
    // REVOKE
    // =========================================================

    private void revokePunishment(
            Player staff,
            String targetName
    ) {

        Player target =
                Bukkit.getPlayerExact(targetName);

        UUID uuid = null;

        if (target != null) {
            uuid = target.getUniqueId();
        } else {

            for (PunishmentData data : punishments.values()) {

                if (data.getPlayer()
                        .equalsIgnoreCase(targetName)) {

                    uuid = data.getUuid();
                    break;
                }
            }
        }

        if (uuid == null) {

            staff.sendMessage(
                    revokeMessage("no-punishment")
            );

            return;
        }

        PunishmentData data =
                punishments.get(uuid);

        if (data == null) {

            staff.sendMessage(
                    revokeMessage("no-punishment")
            );

            return;
        }

        String type =
                data.getType();

        // =====================================================
        // JAIL
        // Uses PunishmentJail unjail command.
        // =====================================================

        if (type.equalsIgnoreCase("JAIL")) {

            if (target == null) {

                staff.sendMessage(
                        prefix
                                + color(
                                "&cPlayer must be online to revoke JAIL."
                        )
                );

                return;
            }

            String command =
                    getConfig().getString(
                            "revoke.jail-command",
                            "unjail %player%"
                    );

            command =
                    command.replace(
                            "%player%",
                            target.getName()
                    );

            command =
                    command.replace(
                            "%staff%",
                            staff.getName()
                    );

            if (!dispatchCommand(staff, command)) {

                staff.sendMessage(
                        message(
                                "messages.errors.command-failed"
                        )
                );

                return;
            }
        }

        // =====================================================
        // INTERNAL MUTE
        // No external command.
        // =====================================================

        if (type.equalsIgnoreCase("MUTE")) {

            // Nothing external required.
            // Removing the punishment from our map
            // immediately un-mutes the player.
        }

        // =====================================================
        // INTERNAL BAN
        // No LiteBans / Bukkit BanList.
        // =====================================================

        if (type.equalsIgnoreCase("BAN")) {

            // Nothing external required.
            // Removing the punishment allows login again.
        }

        // =====================================================
        // INTERNAL IP-BAN
        // No LiteBans / Bukkit BanList.
        // =====================================================

        if (type.equalsIgnoreCase("IP-BAN")) {

            // Nothing external required.
            // IP-ban is handled by this plugin's login listener.
        }

        punishments.remove(uuid);

        removePunishmentFromFile(uuid);

        savePunishments();

        String success =
                getConfig().getString(
                        "revoke.messages.success",
                        "&aPunishment &f%punishment% &afor &f%player% &ahas been revoked."
                );

        success =
                success.replace(
                        "%player%",
                        data.getPlayer()
                );

        success =
                success.replace(
                        "%punishment%",
                        data.getPunishment()
                );

        success =
                success.replace(
                        "%staff%",
                        staff.getName()
                );

        staff.sendMessage(
                prefix + color(success)
        );

        if (target != null) {

            String targetMessage =
                    getConfig().getString(
                            "revoke.messages.target",
                            "&aYour punishment has been revoked by &f%staff%&a."
                    );

            targetMessage =
                    targetMessage.replace(
                            "%staff%",
                            staff.getName()
                    );

            target.sendMessage(
                    prefix + color(targetMessage)
            );
        }
    }

    // =========================================================
    // CHAT MUTE
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

        if (!data.getType()
                .equalsIgnoreCase("MUTE")) {

            return;
        }

        if (isExpired(data)) {

            removeExpired(
                    player.getUniqueId()
            );

            return;
        }

        event.setCancelled(true);

        String muted =
                getConfig().getString(
                        "messages.muted",
                        "&cYou are currently muted."
                );

        player.sendMessage(
                prefix
                        + color(muted)
        );

        sendConfigured(
                player,
                "messages.staff.punishment",
                player,
                data.getPunishment(),
                data.getType(),
                data.getDuration(),
                data.getReason(),
                data.getStaff()
        );

        sendConfigured(
                player,
                "messages.staff.duration",
                player,
                data.getPunishment(),
                data.getType(),
                data.getDuration(),
                data.getReason(),
                data.getStaff()
        );

        sendConfigured(
                player,
                "messages.staff.reason",
                player,
                data.getPunishment(),
                data.getType(),
                data.getDuration(),
                data.getReason(),
                data.getStaff()
        );

        sendConfigured(
                player,
                "messages.staff.staff",
                player,
                data.getPunishment(),
                data.getType(),
                data.getDuration(),
                data.getReason(),
                data.getStaff()
        );
    }

    // =========================================================
    // LOGIN BAN / IP-BAN
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

        if (data == null) {
            return;
        }

        if (isExpired(data)) {

            removeExpired(
                    player.getUniqueId()
            );

            return;
        }

        String type =
                data.getType();

        // =====================================================
        // BAN
        // =====================================================

        if (type.equalsIgnoreCase("BAN")) {

            event.disallow(
                    PlayerLoginEvent.Result.KICK_BANNED,
                    getBanMessage(data)
            );

            return;
        }

        // =====================================================
        // IP-BAN
        // =====================================================

        if (type.equalsIgnoreCase("IP-BAN")) {

            event.disallow(
                    PlayerLoginEvent.Result.KICK_BANNED,
                    getBanMessage(data)
            );
        }
    }

    // =========================================================
    // EXPIRATION
    // =========================================================

    private void checkExpiredPunishments() {

        long now =
                System.currentTimeMillis();

        UUID[] ids =
                punishments.keySet()
                        .toArray(
                                new UUID[punishments.size()]
                        );

        for (UUID uuid : ids) {

            PunishmentData data =
                    punishments.get(uuid);

            if (data == null) {
                continue;
            }

            if (data.getExpires() <= now) {

                punishments.remove(uuid);

                removePunishmentFromFile(uuid);
            }
        }

        savePunishments();
    }

    private boolean isExpired(
            PunishmentData data
    ) {

        return data.getExpires()
                <= System.currentTimeMillis();
    }

    private void removeExpired(
            UUID uuid
    ) {

        punishments.remove(uuid);

        removePunishmentFromFile(uuid);

        savePunishments();
    }

    // =========================================================
    // BAN MESSAGE
    // =========================================================

    private String getBanMessage(
            PunishmentData data
    ) {

        return color(
                "&cYou are banned.\n\n"
                        + "&fPunishment: &7"
                        + data.getPunishment()
                        + "\n"
                        + "&fReason: &7"
                        + data.getReason()
                        + "\n"
                        + "&fDuration: &7"
                        + data.getDuration()
                        + "\n"
                        + "&fStaff: &7"
                        + data.getStaff()
        );
    }

    // =========================================================
    // STAFF DETAILS
    // =========================================================

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

    // =========================================================
    // TARGET DETAILS
    // =========================================================

    private void sendTargetDetails(
            Player target,
            String punishment,
            String type,
            String duration,
            String reason,
            String staffName
    ) {

        if (!target.isOnline()) {
            return;
        }

        target.sendMessage("");

        sendConfigured(
                target,
                "messages.target.applied",
                target,
                punishment,
                type,
                duration,
                reason,
                staffName
        );

        sendConfigured(
                target,
                "messages.target.player",
                target,
                punishment,
                type,
                duration,
                reason,
                staffName
        );

        sendConfigured(
                target,
                "messages.target.punishment",
                target,
                punishment,
                type,
                duration,
                reason,
                staffName
        );

        sendConfigured(
                target,
                "messages.target.type",
                target,
                punishment,
                type,
                duration,
                reason,
                staffName
        );

        sendConfigured(
                target,
                "messages.target.duration",
                target,
                punishment,
                type,
                duration,
                reason,
                staffName
        );

        sendConfigured(
                target,
                "messages.target.reason",
                target,
                punishment,
                type,
                duration,
                reason,
                staffName
        );

        sendConfigured(
                target,
                "messages.target.staff",
                target,
                punishment,
                type,
                duration,
                reason,
                staffName
        );
    }

    // =========================================================
    // CONFIG MESSAGE
    // =========================================================

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
    // COMMAND DISPATCH
    // =========================================================

    private boolean dispatchCommand(
            Player staff,
            String command
    ) {

        if (command == null ||
                command.trim().isEmpty()) {

            return true;
        }

        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        return Bukkit.dispatchCommand(
                staff,
                command
        );
    }

    // =========================================================
    // PLACEHOLDERS
    // =========================================================

    private String replacePlaceholders(
            String command,
            Player target,
            Player staff,
            String punishment,
            String type,
            String duration,
            String reason
    ) {

        if (command == null) {
            return "";
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
                        "%punishment%",
                        punishment
                );

        command =
                command.replace(
                        "%type%",
                        type
                );

        command =
                command.replace(
                        "%staff%",
                        staff.getName()
                );

        return command;
    }

    // =========================================================
    // DURATION
    // =========================================================

    private long parseDuration(
            String duration
    ) {

        if (duration == null ||
                duration.trim().isEmpty()) {

            return 0L;
        }

        String value =
                duration.trim()
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

        if (value.contains("second")
                || value.endsWith("s")) {

            return amount * 1000L;
        }

        if (value.contains("minute")
                || value.endsWith("m")) {

            return amount
                    * 60L
                    * 1000L;
        }

        if (value.contains("hour")
                || value.endsWith("h")) {

            return amount
                    * 60L
                    * 60L
                    * 1000L;
        }

        if (value.contains("day")
                || value.endsWith("d")) {

            return amount
                    * 24L
                    * 60L
                    * 60L
                    * 1000L;
        }

        if (value.contains("week")
                || value.endsWith("w")) {

            return amount
                    * 7L
                    * 24L
                    * 60L
                    * 60L
                    * 1000L;
        }

        return 0L;
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
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        dataConfig =
                YamlConfiguration
                        .loadConfiguration(dataFile);
    }

    // =========================================================
    // LOAD
    // =========================================================

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

            try {

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

                long expires =
                        dataConfig.getLong(
                                path + ".expires"
                        );

                if (expires <=
                        System.currentTimeMillis()) {

                    dataConfig.set(
                            path,
                            null
                    );

                    continue;
                }

                PunishmentData data =
                        new PunishmentData(
                                uuid,
                                dataConfig.getString(
                                        path + ".player",
                                        ""
                                ),
                                dataConfig.getString(
                                        path + ".punishment",
                                        ""
                                ),
                                dataConfig.getString(
                                        path + ".type",
                                        ""
                                ),
                                dataConfig.getString(
                                        path + ".duration",
                                        ""
                                ),
                                dataConfig.getString(
                                        path + ".reason",
                                        ""
                                ),
                                dataConfig.getString(
                                        path + ".staff",
                                        ""
                                ),
                                expires
                        );

                punishments.put(
                        uuid,
                        data
                );

            } catch (Exception ex) {

                getLogger().warning(
                        "Could not load punishment: "
                                + key
                );
            }
        }

        savePunishments();
    }

    // =========================================================
    // SAVE ONE
    // =========================================================

    private void savePunishment(
            PunishmentData data
    ) {

        String path =
                "punishments."
                        + data.getUuid().toString();

        dataConfig.set(
                path + ".uuid",
                data.getUuid().toString()
        );

        dataConfig.set(
                path + ".player",
                data.getPlayer()
        );

        dataConfig.set(
                path + ".punishment",
                data.getPunishment()
        );

        dataConfig.set(
                path + ".type",
                data.getType()
        );

        dataConfig.set(
                path + ".duration",
                data.getDuration()
        );

        dataConfig.set(
                path + ".reason",
                data.getReason()
        );

        dataConfig.set(
                path + ".staff",
                data.getStaff()
        );

        dataConfig.set(
                path + ".expires",
                data.getExpires()
        );

        savePunishments();
    }

    // =========================================================
    // REMOVE
    // =========================================================

    private void removePunishmentFromFile(
            UUID uuid
    ) {

        dataConfig.set(
                "punishments."
                        + uuid.toString(),
                null
        );
    }

    // =========================================================
    // SAVE
    // =========================================================

    private void savePunishments() {

        try {

            dataConfig.save(
                    dataFile
            );

        } catch (IOException ex) {

            ex.printStackTrace();
        }
    }

    // =========================================================
    // REVOKE MESSAGE
    // =========================================================

    private String revokeMessage(
            String key
    ) {

        return prefix
                + color(
                getConfig().getString(
                        "revoke.messages." + key,
                        ""
                )
        );
    }

    // =========================================================
    // NORMAL MESSAGE
    // =========================================================

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

    // =========================================================
    // COLOR
    // =========================================================

    private String color(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return ChatColor
                .translateAlternateColorCodes(
                        '&',
                        text
                );
    }

    // =========================================================
    // JSON ESCAPE
    // =========================================================

    private String escapeJson(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    // =========================================================
    // PUNISHMENT DATA
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

        public PunishmentData(
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

        public UUID getUuid() {
            return uuid;
        }

        public String getPlayer() {
            return player;
        }

        public String getPunishment() {
            return punishment;
        }

        public String getType() {
            return type;
        }

        public String getDuration() {
            return duration;
        }

        public String getReason() {
            return reason;
        }

        public String getStaff() {
            return staff;
        }

        public long getExpires() {
            return expires;
        }
    }
}
