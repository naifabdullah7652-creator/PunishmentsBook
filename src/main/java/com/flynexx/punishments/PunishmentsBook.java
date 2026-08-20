package com.flynexx.punishments;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class PunishmentsBook extends JavaPlugin {

    private static final String PREFIX =
            ChatColor.DARK_RED + "PunishmentsBook " +
            ChatColor.GRAY + "┃ ";

    @Override
    public void onEnable() {

        saveDefaultConfig();

        if (getCommand("pm") != null) {
            getCommand("pm").setExecutor(this);
        }

        getLogger().info("PunishmentsBook 2.0.0 enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!command.getName().equalsIgnoreCase("pm")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("punishmentsbook.use")) {
            player.sendMessage(
                    PREFIX + ChatColor.RED +
                    "You don't have permission."
            );
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(
                    PREFIX + ChatColor.RED +
                    "Usage: /pm <player>"
            );
            return true;
        }

        openPunishmentBook(player, args[0]);

        return true;
    }

    /**
     * Opens the written book directly.
     *
     * The book is NEVER placed inside the player's inventory.
     */
    private void openPunishmentBook(Player player, String target) {

        ItemStack book = createBook(target);

        if (!openBook(player, book)) {
            player.sendMessage(
                    PREFIX + ChatColor.RED +
                    "Unable to open the punishment book."
            );

            getLogger().warning(
                    "Could not open book for " + player.getName()
            );
        }
    }

    /**
     * Creates the written book.
     *
     * Only the punishment names are displayed.
     * No duration or reason is displayed.
     */
    private ItemStack createBook(String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle("Punishments");
        meta.setAuthor("FlyNeXx");

        List<String> pages =
                new ArrayList<String>();

        pages.add(createPunishmentPage(target));

        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    /**
     * Creates the clickable JSON book page.
     *
     * Important:
     * The JSON is encoded into the book internally.
     * The player only sees the normal punishment names.
     */
    private String createPunishmentPage(String target) {

        ConfigurationSection section =
                getConfig().getConfigurationSection("punishments");

        if (section == null) {
            return "{\"text\":\"No punishments configured.\"}";
        }

        StringBuilder json =
                new StringBuilder();

        json.append("{");
        json.append("\"text\":\"\",");
        json.append("\"extra\":[");

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

            /*
             * Black text only.
             */
            String text =
                    "§0" + name + "§r\n";

            /*
             * The command is hidden from the player.
             */
            String command =
                    "/pmapply " + target + " " + id;

            json.append("{");

            json.append("\"text\":\"")
                    .append(escapeJson(text))
                    .append("\",");

            json.append("\"color\":\"black\",");

            json.append("\"clickEvent\":{");
            json.append("\"action\":\"run_command\",");
            json.append("\"value\":\"")
                    .append(escapeJson(command))
                    .append("\"");
            json.append("}");

            json.append("}");
        }

        json.append("]");
        json.append("}");

        return json.toString();
    }

    /**
     * Executes the selected punishment.
     */
    private void executePunishment(Player staff,
                                   String target,
                                   String id) {

        String path =
                "punishments." + id;

        if (!getConfig().isConfigurationSection(path)) {

            staff.sendMessage(
                    PREFIX + ChatColor.RED +
                    "Unknown punishment."
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
                        "Permanent"
                );

        String reason =
                getConfig().getString(
                        path + ".reason",
                        "Punishment"
                );

        String command =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        if (command == null ||
                command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX + ChatColor.RED +
                    "No command configured for " +
                    name
            );

            return;
        }

        /*
         * Supported placeholders.
         */
        command = command
                .replace("%player%", target)
                .replace("%target%", target)
                .replace("%duration%", duration)
                .replace("%time%", duration)
                .replace("%reason%", reason)
                .replace("%staff%", staff.getName());

        /*
         * Remove leading slash.
         */
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        boolean success =
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        command
                );

        if (!success) {

            staff.sendMessage(
                    PREFIX + ChatColor.RED +
                    "Failed to execute punishment."
            );

            return;
        }

        staff.sendMessage(
                PREFIX +
                ChatColor.GREEN +
                "Punishment executed: " +
                ChatColor.WHITE +
                name +
                ChatColor.GRAY +
                " → " +
                ChatColor.WHITE +
                target
        );

        /*
         * Optional broadcast.
         */
        String announcement =
                getConfig().getString(
                        "settings.announcement",
                        ""
                );

        if (announcement != null &&
                !announcement.trim().isEmpty()) {

            announcement =
                    announcement
                            .replace("%player%", target)
                            .replace("%punishment%", name)
                            .replace("%duration%", duration)
                            .replace("%reason%", reason)
                            .replace("%staff%", staff.getName());

            Bukkit.broadcastMessage(
                    color(announcement)
            );
        }
    }

    /**
     * Opens a written book without giving it to the player.
     *
     * No PacketPlayOutOpenBook is used.
     *
     * Works through reflection with 1.8_R3.
     */
    private boolean openBook(Player player,
                             ItemStack book) {

        try {

            /*
             * Get CraftPlayer handle.
             */
            Method getHandle =
                    player.getClass()
                            .getMethod("getHandle");

            Object entityPlayer =
                    getHandle.invoke(player);

            /*
             * CraftItemStack.asNMSCopy(...)
             */
            Class<?> craftItemStack =
                    Class.forName(
                            "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
                    );

            Method asNMSCopy =
                    craftItemStack.getMethod(
                            "asNMSCopy",
                            ItemStack.class
                    );

            Object nmsBook =
                    asNMSCopy.invoke(
                            null,
                            book
                    );

            /*
             * Find EntityHuman.openBook(ItemStack).
             */
            Method openBookMethod = null;

            Class<?> current =
                    entityPlayer.getClass();

            while (current != null) {

                Method[] methods =
                        current.getDeclaredMethods();

                for (Method method : methods) {

                    if (!method.getName()
                            .equals("openBook")) {
                        continue;
                    }

                    if (method.getParameterTypes()
                            .length != 1) {
                        continue;
                    }

                    openBookMethod = method;
                    break;
                }

                if (openBookMethod != null) {
                    break;
                }

                current = current.getSuperclass();
            }

            if (openBookMethod == null) {

                getLogger().warning(
                        "EntityHuman.openBook(ItemStack) was not found."
                );

                return false;
            }

            openBookMethod.setAccessible(true);

            openBookMethod.invoke(
                    entityPlayer,
                    nmsBook
            );

            return true;

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Failed to open 1.8.8 book: " +
                    throwable.getClass().getSimpleName() +
                    ": " +
                    throwable.getMessage()
            );

            return false;
        }
    }

    /**
     * Escapes JSON safely.
     */
    private String escapeJson(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Translate & colors.
     */
    private String color(String text) {

        if (text == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes(
                '&',
                text
        );
    }
}
