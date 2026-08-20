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
import org.bukkit.event.player.PlayerLoginEvent;
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

    private File dataFile;
    private org.bukkit.configuration.file.FileConfiguration dataConfig;

    private String prefix;

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
                        prefix +
                        color("&cPlayers only.")
                );
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.use")) {

                staff.sendMessage(
                        prefix +
                        color("&cYou don't have permission.")
                );

                return true;
            }

            if (args.length != 1) {

                staff.sendMessage(
                        prefix +
                        color("&cUsage: /pm <player>")
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
                    color("&cCould not open punishment book.")
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
                            "punishments." +
                            id +
                            ".name",
                            id
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

            /*
             * اسم العقوبة فقط.
             * لا يتم عرض المدة داخل الكتاب.
             */
            json.append(
                    "{\"text\":\""
            );

            json.append(
                    escapeJson(name)
            );

            json.append(
                    "\",\"color\":\"black\",\"underlined\":true,"
            );

            /*
             * الضغط ينفذ pmapply مباشرة.
             */
            json.append(
                    "\"clickEvent\":{\"action\":\"run_command\",\"value\":\""
            );

            json.append(
                    escapeJson(command)
            );

            json.append(
                    "\"}"
            );

            json.append(
                    ",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\""
            );

            json.append(
                    escapeJson(
                            "Apply " + name
                    )
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
                    prefix +
                    color("&cUnknown punishment.")
            );

            return;
        }

        Player target =
                Bukkit.getPlayerExact(targetName);

        if (target == null) {

            staff.sendMessage(
                    prefix +
                    color("&cPlayer is not online.")
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

        String configuredCommand =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        /*
         * تنفيذ أمر PunishmentJail أو أي نظام
         * خارجي يتم تحديده من config.yml.
         *
         * يتم التنفيذ باسم الإداري الذي ضغط،
         * وليس من Console.
         */
        String command =
                configuredCommand
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

        boolean commandSuccess = true;

        if (!command.trim().isEmpty()) {

            commandSuccess =
                    Bukkit.dispatchCommand(
                            staff,
                            command
                    );
        }

        /*
         * MUTE داخلي في PunishmentsBook.
         */
        if (type.equalsIgnoreCase("MUTE")) {

            long durationMillis =
                    parseDuration(duration);

            if (durationMillis > 0L) {

                PunishmentData data =
                        new PunishmentData();

                data.uuid =
                        target.getUniqueId();

                data.player =
                        target.getName();

                data.punishment =
                        name;

                data.type =
                        "MUTE";

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

                savePunishment(data);
            }
        }

        if (!commandSuccess) {

            staff.sendMessage(
                    prefix +
                    color("&cPunishment command failed.")
            );

            return;
        }

        /*
         * رسالة الإداري.
         */
        staff.sendMessage("");

        staff.sendMessage(
                prefix +
                color("&aPunishment Applied")
        );

        staff.sendMessage(
                color("&7Player: &f") +
                target.getName()
        );

        staff.sendMessage(
                color("&7Punishment: &f") +
                name
        );

        staff.sendMessage(
                color("&7Type: &f") +
                type
        );

        staff.sendMessage(
                color("&7Duration: &f") +
                duration
        );

        staff.sendMessage(
                color("&7Reason: &f") +
                reason
        );

        staff.sendMessage(
                color("&7Staff: &f") +
                staff.getName()
        );

        /*
         * رسالة اللاعب.
         */
        target.sendMessage("");

        target.sendMessage(
                prefix +
                color("&cYou have been punished.")
        );

        target.sendMessage(
                color("&7Punishment: &f") +
                name
        );

        target.sendMessage(
                color("&7Type: &f") +
                type
        );

        target.sendMessage(
                color("&7Duration: &f") +
                duration
        );

        target.sendMessage(
                color("&7Reason: &f") +
                reason
        );

        target.sendMessage(
                color("&7Staff: &f") +
                staff.getName()
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

            removePunishmentFromFile(
                    player.getUniqueId()
            );

            savePunishments();

            return;
        }

        event.setCancelled(true);

        player.sendMessage(
                prefix +
                color("&cYou are muted.")
        );

        player.sendMessage(
                color("&7Reason: &f") +
                data.reason
        );

        player.sendMessage(
                color("&7Duration: &f") +
                data.duration
        );

        player.sendMessage(
                color("&7Staff: &f") +
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

        if (data.type.equalsIgnoreCase("BAN") ||
                data.type.equalsIgnoreCase("IP-BAN")) {

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

    private String color(
            String text) {

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
