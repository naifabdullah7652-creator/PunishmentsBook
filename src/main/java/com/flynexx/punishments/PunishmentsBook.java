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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PunishmentsBook extends JavaPlugin implements Listener {

    private static final String PREFIX =
            ChatColor.DARK_RED + "PunishmentsBook " +
            ChatColor.DARK_GRAY + "┃ ";

    /*
     * Active punishments.
     */
    private final Map<UUID, PunishmentData> punishments =
            new HashMap<UUID, PunishmentData>();

    /*
     * Previous locations for jailed players.
     */
    private final Map<UUID, Location> previousLocations =
            new HashMap<UUID, Location>();

    /*
     * Persistent punishment file.
     */
    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        setupDataFile();

        loadPunishments();

        if (getCommand("pm") != null) {
            getCommand("pm").setExecutor(this);
        }

        if (getCommand("pmsetjail") != null) {
            getCommand("pmsetjail").setExecutor(this);
        }

        Bukkit.getPluginManager().registerEvents(
                this,
                this
        );

        /*
         * Check punishments every second.
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
                "PunishmentsBook 3.0.0 enabled."
        );
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
            String[] args) {

        /*
         * =====================================================
         * /pm <player>
         * =====================================================
         */
        if (command.getName().equalsIgnoreCase("pm")) {

            if (!(sender instanceof Player)) {

                sender.sendMessage(
                        ChatColor.RED +
                        "Players only."
                );

                return true;
            }

            Player staff =
                    (Player) sender;

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
         * =====================================================
         * /pmsetjail
         * =====================================================
         */
        if (command.getName()
                .equalsIgnoreCase("pmsetjail")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player player =
                    (Player) sender;

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

    /*
     * =========================================================
     * OPEN BOOK
     * =========================================================
     */
    private void openBook(
            final Player player,
            String target) {

        try {

            ItemStack nmsBook =
                    createNMSBook(
                            target
                    );

            if (nmsBook == null) {

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Could not create punishment book."
                );

                return;
            }

            org.bukkit.inventory.ItemStack bukkitBook =
                    CraftItemStack.asBukkitCopy(
                            nmsBook
                    );

            final org.bukkit.inventory.ItemStack oldItem =
                    player.getItemInHand();

            player.setItemInHand(
                    bukkitBook
            );

            player.updateInventory();

            EntityPlayer entityPlayer =
                    ((CraftPlayer) player)
                            .getHandle();

            entityPlayer.openBook(
                    nmsBook
            );

            /*
             * Restore the previous item after opening.
             */
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

            getLogger().severe(
                    "Could not open punishment book."
            );

            ex.printStackTrace();

            player.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment book."
            );
        }
    }

    /*
     * =========================================================
     * CREATE INTERACTIVE BOOK
     * =========================================================
     */
    private ItemStack createNMSBook(
            String target) {

        org.bukkit.inventory.ItemStack base =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        ItemStack book =
                CraftItemStack.asNMSCopy(
                        base
                );

        NBTTagCompound tag =
                new NBTTagCompound();

        tag.setString(
                "title",
                "Punishments"
        );

        tag.setString(
                "author",
                "FlyNeXx"
        );

        NBTTagList pages =
                new NBTTagList();

        ConfigurationSection section =
                getConfig()
                        .getConfigurationSection(
                                "punishments"
                        );

        if (section == null ||
                section.getKeys(false).isEmpty()) {

            pages.add(
                    new NBTTagString(
                            "Punishments\n\n" +
                            "No punishments configured."
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

        List<String> ids =
                new ArrayList<String>(
                        section.getKeys(false)
                );

        /*
         * =====================================================
         * MAIN PAGE
         * =====================================================
         */
        StringBuilder page =
                new StringBuilder();

        page.append(
                "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\",\"extra\":["
        );

        boolean first =
                true;

        int count =
                0;

        for (String id : ids) {

            String name =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".name",
                            id
                    );

            if (!first) {
                page.append(",");
            }

            first = false;

            /*
             * Clicking the punishment opens its details page.
             */
            String detailsCommand =
                    "/pmdetails " +
                    target +
                    " " +
                    id;

            page.append(
                    "{\"text\":\""
            );

            page.append(
                    escapeJson(name)
            );

            page.append(
                    "\",\"color\":\"black\",\"underlined\":true,"
            );

            page.append(
                    "\"clickEvent\":{\"action\":\"run_command\",\"value\":\""
            );

            page.append(
                    escapeJson(
                            detailsCommand
                    )
            );

            page.append(
                    "\"}}"
            );

            page.append(
                    ",{\"text\":\"\\n\"}"
            );

            count++;

            if (count >= 10) {

                page.append(
                        "]}"
                );

                pages.add(
                        new NBTTagString(
                                page.toString()
                        )
                );

                page =
                        new StringBuilder();

                page.append(
                        "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\",\"extra\":["
                );

                first =
                        true;

                count =
                        0;
            }
        }

        if (count > 0) {

            page.append(
                    "]}"
            );

            pages.add(
                    new NBTTagString(
                            page.toString()
                    )
            );
        }

        /*
         * =====================================================
         * DETAIL PAGES
         * =====================================================
         *
         * Every punishment gets its own page.
         *
         * Duration is shown here only.
         */
        for (String id : ids) {

            String name =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".name",
                            id
                    );

            String type =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".type",
                            "UNKNOWN"
                    );

            String duration =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".duration",
                            ""
                    );

            String reason =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".reason",
                            name
                    );

            String applyCommand =
                    "/pmapply " +
                    target +
                    " " +
                    id;

            String detailPage =
                    "{"
                    + "\"text\":\"\","
                    + "\"extra\":["
                    + "{\"text\":\""
                    + escapeJson(name)
                    + "\\n\\n\",\"color\":\"dark_red\",\"bold\":true},"
                    + "{\"text\":\"Type: \",\"color\":\"gray\"},"
                    + "{\"text\":\""
                    + escapeJson(type)
                    + "\\n\",\"color\":\"black\"},"
                    + "{\"text\":\"Duration: \",\"color\":\"gray\"},"
                    + "{\"text\":\""
                    + escapeJson(duration)
                    + "\\n\",\"color\":\"black\"},"
                    + "{\"text\":\"Reason: \",\"color\":\"gray\"},"
                    + "{\"text\":\""
                    + escapeJson(reason)
                    + "\\n\\n\",\"color\":\"black\"},"
                    + "{\"text\":\"[ APPLY PUNISHMENT ]\","
                    + "\"color\":\"dark_red\","
                    + "\"bold\":true,"
                    + "\"underlined\":true,"
                    + "\"clickEvent\":{"
                    + "\"action\":\"run_command\","
                    + "\"value\":\""
                    + escapeJson(applyCommand)
                    + "\"}},"
                    + "{\"text\":\"\\n\\n\"},"
                    + "{\"text\":\"[ BACK ]\","
                    + "\"color\":\"gray\","
                    + "\"underlined\":true,"
                    + "\"clickEvent\":{"
                    + "\"action\":\"run_command\","
                    + "\"value\":\"/pm "
                    + escapeJson(target)
                    + "\"}}"
                    + "]"
                    + "}";

            pages.add(
                    new NBTTagString(
                            detailPage
                    )
            );
        }

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
     * /pmdetails
     * =========================================================
     *
     * Opens the details page.
     *
     * The command is hidden from the player because it is
     * executed by the book click event.
     */
    private void openDetails(
            Player staff,
            String target,
            String id) {

        try {

            /*
             * We simply create a book containing only the
             * requested punishment details.
             */
            ItemStack book =
                    createDetailsBook(
                            target,
                            id
                    );

            org.bukkit.inventory.ItemStack bukkitBook =
                    CraftItemStack.asBukkitCopy(
                            book
                    );

            final org.bukkit.inventory.ItemStack oldItem =
                    staff.getItemInHand();

            staff.setItemInHand(
                    bukkitBook
            );

            staff.updateInventory();

            EntityPlayer entityPlayer =
                    ((CraftPlayer) staff)
                            .getHandle();

            entityPlayer.openBook(
                    book
            );

            Bukkit.getScheduler().runTaskLater(
                    this,
                    new Runnable() {

                        @Override
                        public void run() {
                            staff.setItemInHand(
                                    oldItem
                            );
                            staff.updateInventory();
                        }

                    },
                    5L
            );

        } catch (Throwable ex) {

            ex.printStackTrace();

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment details."
            );
        }
    }

    /*
     * =========================================================
     * CREATE DETAILS BOOK
     * =========================================================
     */
    private ItemStack createDetailsBook(
            String target,
            String id) {

        org.bukkit.inventory.ItemStack base =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        ItemStack book =
                CraftItemStack.asNMSCopy(
                        base
                );

        NBTTagCompound tag =
                new NBTTagCompound();

        tag.setString(
                "title",
                "Punishment"
        );

        tag.setString(
                "author",
                "FlyNeXx"
        );

        NBTTagList pages =
                new NBTTagList();

        String path =
                "punishments." +
                id;

        String name =
                getConfig().getString(
                        path + ".name",
                        id
                );

        String type =
                getConfig().getString(
                        path + ".type",
                        "UNKNOWN"
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

        String apply =
                "/pmapply " +
                target +
                " " +
                id;

        String back =
                "/pm " +
                target;

        String page =
                "{"
                + "\"text\":\"\","
                + "\"extra\":["
                + "{\"text\":\""
                + escapeJson(name)
                + "\\n\\n\","
                + "\"color\":\"dark_red\","
                + "\"bold\":true},"

                + "{\"text\":\"Type: \","
                + "\"color\":\"gray\"},"

                + "{\"text\":\""
                + escapeJson(type)
                + "\\n\","
                + "\"color\":\"black\"},"

                + "{\"text\":\"Duration: \","
                + "\"color\":\"gray\"},"

                + "{\"text\":\""
                + escapeJson(duration)
                + "\\n\","
                + "\"color\":\"black\"},"

                + "{\"text\":\"Reason: \","
                + "\"color\":\"gray\"},"

                + "{\"text\":\""
                + escapeJson(reason)
                + "\\n\\n\","
                + "\"color\":\"black\"},"

                + "{\"text\":\"[ APPLY PUNISHMENT ]\","
                + "\"color\":\"dark_red\","
                + "\"bold\":true,"
                + "\"underlined\":true,"
                + "\"clickEvent\":{"
                + "\"action\":\"run_command\","
                + "\"value\":\""
                + escapeJson(apply)
                + "\"}},"

                + "{\"text\":\"\\n\\n\"},"

                + "{\"text\":\"[ BACK ]\","
                + "\"color\":\"gray\","
                + "\"underlined\":true,"
                + "\"clickEvent\":{"
                + "\"action\":\"run_command\","
                + "\"value\":\""
                + escapeJson(back)
                + "\"}}"

                + "]"
                + "}";

        pages.add(
                new NBTTagString(
                        page
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
     * COMMAND HANDLER FOR BOOK DETAILS
     * =========================================================
     */
    private boolean handleDetailsCommand(
            CommandSender sender,
            String[] args) {

        if (!(sender instanceof Player)) {
            return true;
        }

        Player player =
                (Player) sender;

        if (args.length != 2) {
            return true;
        }

        if (!player.hasPermission(
                "punishmentsbook.use")) {

            return true;
        }

        openDetails(
                player,
                args[0],
                args[1]
        );

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
            String id) {

        Player target =
                Bukkit.getPlayerExact(
                        targetName
                );

        String path =
                "punishments." +
                id;

        if (!getConfig()
                .isConfigurationSection(path)) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Unknown punishment."
            );

            return;
        }

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

        String type =
                getConfig().getString(
                        path + ".type",
                        "UNKNOWN"
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

        long expires =
                System.currentTimeMillis()
                + durationMillis;

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
                expires;

        /*
         * Save active punishment.
         */
        punishments.put(
                target.getUniqueId(),
                data
        );

        savePunishment(
                data
        );

        /*
         * =====================================================
         * APPLY TYPE
         * =====================================================
         */
        if (type.equalsIgnoreCase("JAIL")) {

            jailPlayer(
                    target
            );

        } else if (type.equalsIgnoreCase("MUTE")) {

            /*
             * Mute is handled by the chat event below.
             */

        } else if (type.equalsIgnoreCase("BAN")) {

            target.kickPlayer(
                    ChatColor.RED +
                    "You have been banned.\n\n" +
                    ChatColor.WHITE +
                    "Reason: " +
                    reason +
                    "\n" +
                    "Duration: " +
                    duration +
                    "\n" +
                    "Staff: " +
                    staff.getName()
            );

        } else if (type.equalsIgnoreCase("IP_BAN")) {

            String address =
                    target.getAddress()
                            .getAddress()
                            .getHostAddress();

            Bukkit.getBanList(
                    org.bukkit.BanList.Type.IP
            ).addBan(
                    address,
                    reason,
                    new java.util.Date(expires),
                    staff.getName()
            );

            target.kickPlayer(
                    ChatColor.RED +
                    "Your IP has been banned.\n\n" +
                    ChatColor.WHITE +
                    "Reason: " +
                    reason +
                    "\n" +
                    "Duration: " +
                    duration
            );
        }

        /*
         * =====================================================
         * STAFF MESSAGE
         * =====================================================
         */
        staff.sendMessage(
                ""
        );

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

        /*
         * IMPORTANT:
         * This is the actual staff name,
         * not the rank.
         */
        staff.sendMessage(
                ChatColor.GRAY +
                "Staff: " +
                ChatColor.WHITE +
                staff.getName()
        );

        /*
         * =====================================================
         * TARGET MESSAGE
         * =====================================================
         */
        target.sendMessage(
                ""
        );

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

        savePunishments();
    }

    /*
     * =========================================================
     * JAIL
     * =========================================================
     */
    private void jailPlayer(
            Player player) {

        Location jail =
                getJailLocation();

        if (jail == null) {

            getLogger().warning(
                    "Jail location has not been configured."
            );

            return;
        }

        previousLocations.put(
                player.getUniqueId(),
                player.getLocation()
        );

        player.teleport(
                jail
        );
    }

    /*
     * =========================================================
     * GET JAIL LOCATION
     * =========================================================
     */
    private Location getJailLocation() {

        String worldName =
                getConfig().getString(
                        "settings.jail-world",
                        ""
                );

        World world =
                Bukkit.getWorld(
                        worldName
                );

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

    /*
     * =========================================================
     * CHAT MUTE
     * =========================================================
     */
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

        if (!data.type.equalsIgnoreCase(
                "MUTE")) {

            return;
        }

        if (data.expires <=
                System.currentTimeMillis()) {

            return;
        }

        event.setCancelled(
                true
        );

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

    /*
     * =========================================================
     * BAN ON LOGIN
     * =========================================================
     */
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

            removePunishment(
                    player.getUniqueId()
            );

            return;
        }

        if (data.type.equalsIgnoreCase(
                "BAN") ||
                data.type.equalsIgnoreCase(
                        "IP_BAN") ||
                data.type.equalsIgnoreCase(
                        "JAIL")) {

            event.disallow(
                    PlayerLoginEvent.Result.KICK_BANNED,
                    ChatColor.RED +
                    "You are punished.\n\n" +
                    ChatColor.WHITE +
                    "Punishment: " +
                    data.punishment +
                    "\n" +
                    "Reason: " +
                    data.reason +
                    "\n" +
                    "Duration: " +
                    data.duration +
                    "\n" +
                    "Staff: " +
                    data.staff
            );
        }
    }

    /*
     * =========================================================
     * KEEP JAILED PLAYER IN JAIL
     * =========================================================
     */
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

        if (!data.type.equalsIgnoreCase(
                "JAIL")) {

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

        /*
         * Prevent leaving the jail area/world.
         */
        if (!player.getWorld()
                .getName()
                .equals(
                        jail.getWorld().getName()
                )) {

            event.setTo(
                    jail
            );
        }
    }

    /*
     * =========================================================
     * PREVENT TELEPORT FROM JAIL
     * =========================================================
     */
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

        if (!data.type.equalsIgnoreCase(
                "JAIL")) {

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

            event.setTo(
                    jail
            );
        }
    }

    /*
     * =========================================================
     * PLAYER QUIT
     * =========================================================
     */
    @EventHandler
    public void onQuit(
            PlayerQuitEvent event) {

        /*
         * Nothing needed here.
         *
         * Punishments remain saved.
         */
    }

    /*
     * =========================================================
     * CHECK EXPIRATIONS
     * =========================================================
     */
    private void checkExpiredPunishments() {

        List<UUID> expired =
                new ArrayList<UUID>();

        long now =
                System.currentTimeMillis();

        for (Map.Entry<UUID, PunishmentData> entry :
                punishments.entrySet()) {

            PunishmentData data =
                    entry.getValue();

            if (data.expires <= now) {

                expired.add(
                        entry.getKey()
                );
            }
        }

        for (UUID uuid :
                expired) {

            expirePunishment(
                    uuid
            );
        }
    }

    /*
     * =========================================================
     * EXPIRE
     * =========================================================
     */
    private void expirePunishment(
            UUID uuid) {

        PunishmentData data =
                punishments.get(
                        uuid
                );

        if (data == null) {
            return;
        }

        Player player =
                Bukkit.getPlayer(
                        uuid
                );

        /*
         * Remove Bukkit IP ban if applicable.
         */
        if (data.type.equalsIgnoreCase(
                "IP_BAN")) {

            if (player != null &&
                    player.getAddress() != null) {

                String ip =
                        player.getAddress()
                                .getAddress()
                                .getHostAddress();

                Bukkit.getBanList(
                        org.bukkit.BanList.Type.IP
                ).pardon(
                        ip
                );
            }
        }

        /*
         * Restore player after jail.
         */
        if (data.type.equalsIgnoreCase(
                "JAIL")) {

            if (player != null) {

                Location old =
                        previousLocations.remove(
                                uuid
                        );

                if (old != null) {

                    player.teleport(
                            old
                    );
                }
            }
        }

        punishments.remove(
                uuid
        );

        removePunishmentFromFile(
                uuid
        );

        savePunishments();

        if (player != null) {

            player.sendMessage(
                    PREFIX +
                    ChatColor.GREEN +
                    "Your punishment has expired."
            );
        }
    }

    /*
     * =========================================================
     * REMOVE
     * =========================================================
     */
    private void removePunishment(
            UUID uuid) {

        punishments.remove(
                uuid
        );

        removePunishmentFromFile(
                uuid
        );

        savePunishments();
    }

    /*
     * =========================================================
     * DURATION PARSER
     * =========================================================
     */
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

            return amount *
                    60L *
                    60L *
                    1000L;
        }

        if (value.contains("day")) {

            return amount *
                    24L *
                    60L *
                    60L *
                    1000L;
        }

        if (value.contains("week")) {

            return amount *
                    7L *
                    24L *
                    60L *
                    60L *
                    1000L;
        }

        /*
         * Default to days.
         */
        return amount *
                24L *
                60L *
                60L *
                1000L;
    }

    /*
     * =========================================================
     * DATA FILE
     * =========================================================
     */
    private void setupDataFile() {

        dataFile =
                new File(
                        getDataFolder(),
                        "data.yml"
                );

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        if (!dataFile.exists()) {

            try {
                dataFile.createNewFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        dataConfig =
                YamlConfiguration
                        .loadConfiguration(
                                dataFile
                        );
    }

    /*
     * =========================================================
     * LOAD
     * =========================================================
     */
    private void loadPunishments() {

        ConfigurationSection section =
                dataConfig
                        .getConfigurationSection(
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

            } catch (Exception ex) {

                getLogger().warning(
                        "Could not load punishment " +
                        key
                );
            }
        }
    }

    /*
     * =========================================================
     * SAVE ALL
     * =========================================================
     */
    private void savePunishments() {

        if (dataConfig == null) {
            return;
        }

        dataConfig.set(
                "punishments",
                null
        );

        for (PunishmentData data :
                punishments.values()) {

            savePunishmentToConfig(
                    data
            );
        }

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
     * SAVE ONE
     * =========================================================
     */
    private void savePunishment(
            PunishmentData data) {

        savePunishmentToConfig(
                data
        );

        try {

            dataConfig.save(
                    dataFile
            );

        } catch (IOException ex) {

            ex.printStackTrace();
        }
    }

    private void savePunishmentToConfig(
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
    }

    /*
     * =========================================================
     * REMOVE FROM DATA FILE
     * =========================================================
     */
    private void removePunishmentFromFile(
            UUID uuid) {

        dataConfig.set(
                "punishments." +
                uuid.toString(),
                null
        );
    }

    /*
     * =========================================================
     * JSON ESCAPE
     * =========================================================
     */
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

    /*
     * =========================================================
     * DATA CLASS
     * =========================================================
     */
    private static class PunishmentData {

        private UUID uuid;

        private String player;

        private String punishment;

        private String type;

        private String duration;

        private String reason;

        /*
         * Actual administrator name.
         *
         * NOT rank.
         */
        private String staff;

        private long expires;
    }
}
