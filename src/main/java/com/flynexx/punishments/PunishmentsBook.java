package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PunishmentsBook extends JavaPlugin implements Listener {

    private static final String PREFIX =
            ChatColor.DARK_RED + "PunishmentsBook " +
            ChatColor.DARK_GRAY + "┃ ";

    private final Map<UUID, PunishmentData> punishments =
            new HashMap<UUID, PunishmentData>();

    private final Map<UUID, Location> previousLocations =
            new HashMap<UUID, Location>();

    private File dataFile;

    private org.bukkit.configuration.file.FileConfiguration dataConfig;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        setupDataFile();
        loadPunishments();

        if (getCommand("pm") != null) {
            getCommand("pm").setExecutor(this);
        }

        if (getCommand("pmapply") != null) {
            getCommand("pmapply").setExecutor(this);
        }

        if (getCommand("pmsetjail") != null) {
            getCommand("pmsetjail").setExecutor(this);
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
                "PunishmentsBook 3.1.0 enabled."
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
                        ChatColor.RED +
                        "Players only."
                );
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.use")) {

                staff.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "You don't have permission."
                );

                return true;
            }

            if (args.length != 1) {

                staff.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Usage: /pm <player>"
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
         * /pmsetjail
         */
        if (command.getName().equalsIgnoreCase("pmsetjail")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission(
                    "punishmentsbook.setjail")) {

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "You don't have permission."
                );

                return true;
            }

            Location loc =
                    player.getLocation();

            getConfig().set(
                    "settings.jail-world",
                    loc.getWorld().getName()
            );

            getConfig().set(
                    "settings.jail-x",
                    loc.getX()
            );

            getConfig().set(
                    "settings.jail-y",
                    loc.getY()
            );

            getConfig().set(
                    "settings.jail-z",
                    loc.getZ()
            );

            getConfig().set(
                    "settings.jail-yaw",
                    loc.getYaw()
            );

            getConfig().set(
                    "settings.jail-pitch",
                    loc.getPitch()
            );

            saveConfig();

            player.sendMessage(
                    PREFIX +
                    ChatColor.GREEN +
                    "Jail location saved."
            );

            return true;
        }

        return false;
    }

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

            EntityPlayer entity =
                    ((CraftPlayer) player).getHandle();

            entity.openBook(book);

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
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment book."
            );
        }
    }

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
                "Punishments"
        );

        tag.setString(
                "author",
                "PunishmentsBook"
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

        for (String id :
                section.getKeys(false)) {

            String name =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".name",
                            id
                    );

            String command =
                    "/pmapply " +
                    target +
                    " " +
                    id;

            if (!first) {
                json.append(",");
            }

            first = false;

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
                    "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Apply "
            );

            json.append(
                    escapeJson(name)
            );

            json.append(
                    "\"}}"
            );

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

    private void applyPunishment(
            Player staff,
            String targetName,
            String id) {

        String path =
                "punishments." +
                id;

        if (!getConfig().isConfigurationSection(path)) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Unknown punishment."
            );

            return;
        }

        Player target =
                Bukkit.getPlayerExact(targetName);

        if (target == null) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Player is not online."
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
                        "JAIL"
                );

        long durationMillis =
                parseDuration(duration);

        if (durationMillis <= 0L) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Invalid duration."
            );

            return;
        }

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
                        + durationMillis;

        punishments.put(
                target.getUniqueId(),
                data
        );

        if (type.equalsIgnoreCase("JAIL")) {

            jailPlayer(target);

        } else if (type.equalsIgnoreCase("MUTE")) {

            // Handled by chat event.

        } else if (type.equalsIgnoreCase("BAN")) {

            target.kickPlayer(
                    ChatColor.RED +
                    "You have been banned.\n\n" +
                    ChatColor.WHITE +
                    "Punishment: " +
                    name +
                    "\nReason: " +
                    reason +
                    "\nDuration: " +
                    duration +
                    "\nStaff: " +
                    staff.getName()
            );
        }

        savePunishment(data);

        /*
         * ADMIN MESSAGE
         */
        staff.sendMessage("");

        staff.sendMessage(
                PREFIX +
                ChatColor.GREEN +
                "Punishment Applied"
        );

        staff.sendMessage(
                ChatColor.GRAY +
                "Player: " +
                ChatColor.WHITE +
                target.getName()
        );

        staff.sendMessage(
                ChatColor.GRAY +
                "Punishment: " +
                ChatColor.WHITE +
                name
        );

        staff.sendMessage(
                ChatColor.GRAY +
                "Type: " +
                ChatColor.WHITE +
                type
        );

        staff.sendMessage(
                ChatColor.GRAY +
                "Duration: " +
                ChatColor.WHITE +
                duration
        );

        staff.sendMessage(
                ChatColor.GRAY +
                "Reason: " +
                ChatColor.WHITE +
                reason
        );

        staff.sendMessage(
                ChatColor.GRAY +
                "Staff: " +
                ChatColor.WHITE +
                staff.getName()
        );

        /*
         * TARGET MESSAGE
         */
        if (!type.equalsIgnoreCase("BAN")) {

            target.sendMessage("");

            target.sendMessage(
                    ChatColor.DARK_RED +
                    "Punishment"
            );

            target.sendMessage(
                    ChatColor.GRAY +
                    "Punishment: " +
                    ChatColor.WHITE +
                    name
            );

            target.sendMessage(
                    ChatColor.GRAY +
                    "Type: " +
                    ChatColor.WHITE +
                    type
            );

            target.sendMessage(
                    ChatColor.GRAY +
                    "Duration: " +
                    ChatColor.WHITE +
                    duration
            );

            target.sendMessage(
                    ChatColor.GRAY +
                    "Reason: " +
                    ChatColor.WHITE +
                    reason
            );

            target.sendMessage(
                    ChatColor.GRAY +
                    "Staff: " +
                    ChatColor.WHITE +
                    staff.getName()
            );
        }
    }

    private void jailPlayer(
            Player player) {

        Location jail =
                getJailLocation();

        if (jail == null) {

            player.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Jail location is not configured."
            );

            return;
        }

        previousLocations.put(
                player.getUniqueId(),
                player.getLocation()
        );

        player.teleport(jail);
    }

    private Location getJailLocation() {

        String worldName =
                getConfig().getString(
                        "settings.jail-world",
                        ""
                );

        World world =
                Bukkit.getWorld(worldName);

        if (world == null) {
            return null;
        }

        double x =
                getConfig().getDouble(
                        "settings.jail-x"
                );

        double y =
                getConfig().getDouble(
                        "settings.jail-y"
                );

        double z =
                getConfig().getDouble(
                        "settings.jail-z"
                );

        float yaw =
                (float) getConfig().getDouble(
                        "settings.jail-yaw",
                        0
                );

        float pitch =
                (float) getConfig().getDouble(
                        "settings.jail-pitch",
                        0
                );

        return new Location(
                world,
                x,
                y,
                z,
                yaw,
                pitch
        );
    }

    @EventHandler
    public void onChat(
            AsyncPlayerChatEvent event) {

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

            return;
        }

        event.setCancelled(true);

        player.sendMessage(
                PREFIX +
                ChatColor.RED +
                "You are muted."
        );

        player.sendMessage(
                ChatColor.GRAY +
                "Reason: " +
                ChatColor.WHITE +
                data.reason
        );

        player.sendMessage(
                ChatColor.GRAY +
                "Duration: " +
                ChatColor.WHITE +
                data.duration
        );

        player.sendMessage(
                ChatColor.GRAY +
                "Staff: " +
                ChatColor.WHITE +
                data.staff
        );
    }

    @EventHandler
    public void onLogin(
            PlayerLoginEvent event) {

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

        if (data.type.equalsIgnoreCase("BAN")) {

            event.disallow(
                    PlayerLoginEvent.Result.KICK_BANNED,
                    ChatColor.RED +
                    "You are banned.\n\n" +
                    ChatColor.WHITE +
                    "Punishment: " +
                    data.punishment +
                    "\nReason: " +
                    data.reason +
                    "\nDuration: " +
                    data.duration +
                    "\nStaff: " +
                    data.staff
            );
        }
    }

    @EventHandler
    public void onMove(
            PlayerMoveEvent event) {

        Player player =
                event.getPlayer();

        PunishmentData data =
                punishments.get(
                        player.getUniqueId()
                );

        if (data == null) {
            return;
        }

        if (!data.type.equalsIgnoreCase("JAIL")) {
            return;
        }

        if (data.expires <=
                System.currentTimeMillis()) {
            return;
        }

        Location jail =
                getJailLocation();

        if (jail == null) {
            return;
        }

        if (!player.getWorld()
                .getName()
                .equals(
                        jail.getWorld().getName()
                )) {

            event.setTo(jail);
        }
    }

    @EventHandler
    public void onTeleport(
            PlayerTeleportEvent event) {

        Player player =
                event.getPlayer();

        PunishmentData data =
                punishments.get(
                        player.getUniqueId()
                );

        if (data == null) {
            return;
        }

        if (!data.type.equalsIgnoreCase("JAIL")) {
            return;
        }

        if (data.expires <=
                System.currentTimeMillis()) {
            return;
        }

        Location jail =
                getJailLocation();

        if (jail == null) {
            return;
        }

        if (!event.getTo()
                .getWorld()
                .getName()
                .equals(
                        jail.getWorld().getName()
                )) {

            event.setTo(jail);
        }
    }

    private void checkExpiredPunishments() {

        long now =
                System.currentTimeMillis();

        for (UUID uuid :
                new java.util.ArrayList<UUID>(
                        punishments.keySet()
                )) {

            PunishmentData data =
                    punishments.get(uuid);

            if (data != null &&
                    data.expires <= now) {

                expirePunishment(uuid);
            }
        }
    }

    private void expirePunishment(
            UUID uuid) {

        PunishmentData data =
                punishments.get(uuid);

        if (data == null) {
            return;
        }

        Player player =
                Bukkit.getPlayer(uuid);

        if (data.type.equalsIgnoreCase("JAIL")) {

            if (player != null) {

                Location old =
                        previousLocations.remove(uuid);

                if (old != null) {
                    player.teleport(old);
                }

                player.sendMessage(
                        PREFIX +
                        ChatColor.GREEN +
                        "Your jail punishment has expired."
                );
            }
        }

        if (player != null) {

            player.sendMessage(
                    PREFIX +
                    ChatColor.GREEN +
                    "Your punishment has expired."
            );
        }

        punishments.remove(uuid);

        removePunishmentFromFile(uuid);

        savePunishments();
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
                    "punishments." +
                    key;

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

                if (data.expires >
                        System.currentTimeMillis()) {

                    punishments.put(
                            data.uuid,
                            data
                    );
                }

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
