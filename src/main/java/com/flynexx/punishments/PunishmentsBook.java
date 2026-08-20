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

        if (getCommand("pmapply") != null) {
            getCommand("pmapply").setExecutor(this);
        }

        getLogger().info("PunishmentsBook 2.0.0 enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (command.getName().equalsIgnoreCase("pm")) {

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

            openBook(player, args[0]);
            return true;
        }

        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.use")) {
                staff.sendMessage(
                        PREFIX + ChatColor.RED +
                        "You don't have permission."
                );
                return true;
            }

            if (args.length != 2) {
                staff.sendMessage(
                        PREFIX + ChatColor.RED +
                        "Usage: /pmapply <player> <punishment>"
                );
                return true;
            }

            executePunishment(
                    staff,
                    args[0],
                    args[1]
            );

            return true;
        }

        return false;
    }

    /**
     * Opens the written book temporarily.
     *
     * No NMS.
     * No CraftBukkit.
     * Compatible with Spigot/Paper 1.8.8 API.
     */
    private void openBook(final Player player,
                          final String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        if (meta == null) {
            player.sendMessage(
                    PREFIX + ChatColor.RED +
                    "Could not create punishment book."
            );
            return;
        }

        String title = getConfig().getString(
                "settings.book-title",
                "&4Punishments"
        );

        String author = getConfig().getString(
                "settings.book-author",
                "FlyNeXx"
        );

        meta.setTitle(color(title));
        meta.setAuthor(author);

        List<String> pages =
                createPages(target);

        if (pages.isEmpty()) {
            pages.add(
                    color("&4&lPUNISHMENTS\n\n") +
                    color("&7No punishments configured.")
            );
        }

        meta.setPages(pages);

        book.setItemMeta(meta);

        /*
         * Save the item currently in the player's hand.
         */
        final ItemStack oldItem =
                player.getItemInHand();

        /*
         * Put the book temporarily in hand.
         */
        player.setItemInHand(book);
        player.updateInventory();

        /*
         * In 1.8.8 the book opens when the player
         * interacts with the written book.
         *
         * We cannot call Player.openBook() because
         * that method does not exist in the 1.8.8 API.
         */

        player.sendMessage(
                PREFIX +
                ChatColor.GREEN +
                "Punishment book opened for " +
                ChatColor.WHITE +
                target +
                ChatColor.GREEN +
                "."
        );

        /*
         * Restore the old item after a short delay.
         */
        Bukkit.getScheduler().runTaskLater(
                this,
                new Runnable() {
                    @Override
                    public void run() {

                        if (!player.isOnline()) {
                            return;
                        }

                        player.setItemInHand(oldItem);
                        player.updateInventory();
                    }
                },
                5L
        );
    }

    private List<String> createPages(String target) {

        List<String> pages =
                new ArrayList<String>();

        /*
         * PAGE 1
         */
        StringBuilder page =
                new StringBuilder();

        page.append(
                color("&4&lPUNISHMENTS\n\n")
        );

        String playerLine =
                getConfig().getString(
                        "settings.player-line",
                        "&7Player: &f%player%"
                );

        page.append(
                color(
                        playerLine.replace(
                                "%player%",
                                target
                        )
                )
        );

        page.append("\n\n");

        page.append(
                color("&8Select a punishment:\n\n")
        );

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section != null) {

            for (String id :
                    section.getKeys(false)) {

                String name =
                        getConfig().getString(
                                "punishments." + id + ".name",
                                id
                        );

                String duration =
                        getConfig().getString(
                                "punishments." + id + ".duration",
                                "Permanent"
                        );

                String line =
                        color("&c» &f" + name)
                        + "\n"
                        + color(
                                "&7Duration: &e" +
                                duration
                        )
                        + "\n\n";

                /*
                 * Keep pages readable.
                 */
                if (page.length() +
                        line.length() > 220) {

                    pages.add(
                            page.toString()
                    );

                    page =
                            new StringBuilder();

                    page.append(
                            color("&4&lPUNISHMENTS\n\n")
                    );
                }

                page.append(line);
            }
        }

        if (page.length() == 0) {

            page.append(
                    color(
                            "&7No punishments configured."
                    )
            );
        }

        pages.add(page.toString());

        /*
         * INFORMATION PAGE
         */
        StringBuilder info =
                new StringBuilder();

        info.append(
                color("&4&lPUNISHMENTS\n\n")
        );

        info.append(
                color(
                        "&7Player: &f" +
                        target +
                        "\n\n"
                )
        );

        info.append(
                color("&8Available punishments:\n\n")
        );

        if (section != null) {

            for (String id :
                    section.getKeys(false)) {

                String name =
                        getConfig().getString(
                                "punishments." + id + ".name",
                                id
                        );

                String duration =
                        getConfig().getString(
                                "punishments." + id + ".duration",
                                "Permanent"
                        );

                info.append(
                        color("&c» &f" + name + "\n")
                );

                info.append(
                        color(
                                "&7Duration: &e" +
                                duration +
                                "\n\n"
                        )
                );
            }
        }

        pages.add(info.toString());

        return pages;
    }

    private void executePunishment(
            Player staff,
            String targetName,
            String punishmentId) {

        String path =
                "punishments." + punishmentId;

        if (!getConfig().isConfigurationSection(path)) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Unknown punishment: " +
                    ChatColor.WHITE +
                    punishmentId
            );

            return;
        }

        String displayName =
                getConfig().getString(
                        path + ".name",
                        punishmentId
                );

        String duration =
                getConfig().getString(
                        path + ".duration",
                        "Permanent"
                );

        String command =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        if (command == null ||
                command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured for " +
                    ChatColor.WHITE +
                    displayName
            );

            return;
        }

        command =
                command
                        .replace(
                                "%player%",
                                targetName
                        )
                        .replace(
                                "%target%",
                                targetName
                        )
                        .replace(
                                "%duration%",
                                duration
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        );

        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        boolean success =
                getServer().dispatchCommand(
                        getServer().getConsoleSender(),
                        command
                );

        if (success) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.GREEN +
                    "Punishment executed: " +
                    ChatColor.WHITE +
                    displayName +
                    ChatColor.GRAY +
                    " → " +
                    ChatColor.WHITE +
                    targetName
            );

            String announcement =
                    getConfig().getString(
                            "settings.announcement",
                            "&4&lPunishment &8┃ &f%player% &7was punished with &c%punishment% &7(&e%duration%&7)"
                    );

            announcement =
                    announcement
                            .replace(
                                    "%player%",
                                    targetName
                            )
                            .replace(
                                    "%punishment%",
                                    ChatColor.stripColor(
                                            color(displayName)
                                    )
                            )
                            .replace(
                                    "%duration%",
                                    duration
                            )
                            .replace(
                                    "%staff%",
                                    staff.getName()
                            );

            getServer().broadcastMessage(
                    color(announcement)
            );

        } else {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Failed to execute punishment command."
            );
        }
    }

    private String color(String text) {

        if (text == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes(
                '&',
                text
        );
    }
}package com.flynexx.punishments;

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

        if (getCommand("pmapply") != null) {
            getCommand("pmapply").setExecutor(this);
        }

        getLogger().info("PunishmentsBook 2.0.0 enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (command.getName().equalsIgnoreCase("pm")) {

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

            openBook(player, args[0]);
            return true;
        }

        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.use")) {
                staff.sendMessage(
                        PREFIX + ChatColor.RED +
                        "You don't have permission."
                );
                return true;
            }

            if (args.length != 2) {
                staff.sendMessage(
                        PREFIX + ChatColor.RED +
                        "Usage: /pmapply <player> <punishment>"
                );
                return true;
            }

            executePunishment(
                    staff,
                    args[0],
                    args[1]
            );

            return true;
        }

        return false;
    }

    /**
     * Opens the written book temporarily.
     *
     * No NMS.
     * No CraftBukkit.
     * Compatible with Spigot/Paper 1.8.8 API.
     */
    private void openBook(final Player player,
                          final String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        if (meta == null) {
            player.sendMessage(
                    PREFIX + ChatColor.RED +
                    "Could not create punishment book."
            );
            return;
        }

        String title = getConfig().getString(
                "settings.book-title",
                "&4Punishments"
        );

        String author = getConfig().getString(
                "settings.book-author",
                "FlyNeXx"
        );

        meta.setTitle(color(title));
        meta.setAuthor(author);

        List<String> pages =
                createPages(target);

        if (pages.isEmpty()) {
            pages.add(
                    color("&4&lPUNISHMENTS\n\n") +
                    color("&7No punishments configured.")
            );
        }

        meta.setPages(pages);

        book.setItemMeta(meta);

        /*
         * Save the item currently in the player's hand.
         */
        final ItemStack oldItem =
                player.getItemInHand();

        /*
         * Put the book temporarily in hand.
         */
        player.setItemInHand(book);
        player.updateInventory();

        /*
         * In 1.8.8 the book opens when the player
         * interacts with the written book.
         *
         * We cannot call Player.openBook() because
         * that method does not exist in the 1.8.8 API.
         */

        player.sendMessage(
                PREFIX +
                ChatColor.GREEN +
                "Punishment book opened for " +
                ChatColor.WHITE +
                target +
                ChatColor.GREEN +
                "."
        );

        /*
         * Restore the old item after a short delay.
         */
        Bukkit.getScheduler().runTaskLater(
                this,
                new Runnable() {
                    @Override
                    public void run() {

                        if (!player.isOnline()) {
                            return;
                        }

                        player.setItemInHand(oldItem);
                        player.updateInventory();
                    }
                },
                5L
        );
    }

    private List<String> createPages(String target) {

        List<String> pages =
                new ArrayList<String>();

        /*
         * PAGE 1
         */
        StringBuilder page =
                new StringBuilder();

        page.append(
                color("&4&lPUNISHMENTS\n\n")
        );

        String playerLine =
                getConfig().getString(
                        "settings.player-line",
                        "&7Player: &f%player%"
                );

        page.append(
                color(
                        playerLine.replace(
                                "%player%",
                                target
                        )
                )
        );

        page.append("\n\n");

        page.append(
                color("&8Select a punishment:\n\n")
        );

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section != null) {

            for (String id :
                    section.getKeys(false)) {

                String name =
                        getConfig().getString(
                                "punishments." + id + ".name",
                                id
                        );

                String duration =
                        getConfig().getString(
                                "punishments." + id + ".duration",
                                "Permanent"
                        );

                String line =
                        color("&c» &f" + name)
                        + "\n"
                        + color(
                                "&7Duration: &e" +
                                duration
                        )
                        + "\n\n";

                /*
                 * Keep pages readable.
                 */
                if (page.length() +
                        line.length() > 220) {

                    pages.add(
                            page.toString()
                    );

                    page =
                            new StringBuilder();

                    page.append(
                            color("&4&lPUNISHMENTS\n\n")
                    );
                }

                page.append(line);
            }
        }

        if (page.length() == 0) {

            page.append(
                    color(
                            "&7No punishments configured."
                    )
            );
        }

        pages.add(page.toString());

        /*
         * INFORMATION PAGE
         */
        StringBuilder info =
                new StringBuilder();

        info.append(
                color("&4&lPUNISHMENTS\n\n")
        );

        info.append(
                color(
                        "&7Player: &f" +
                        target +
                        "\n\n"
                )
        );

        info.append(
                color("&8Available punishments:\n\n")
        );

        if (section != null) {

            for (String id :
                    section.getKeys(false)) {

                String name =
                        getConfig().getString(
                                "punishments." + id + ".name",
                                id
                        );

                String duration =
                        getConfig().getString(
                                "punishments." + id + ".duration",
                                "Permanent"
                        );

                info.append(
                        color("&c» &f" + name + "\n")
                );

                info.append(
                        color(
                                "&7Duration: &e" +
                                duration +
                                "\n\n"
                        )
                );
            }
        }

        pages.add(info.toString());

        return pages;
    }

    private void executePunishment(
            Player staff,
            String targetName,
            String punishmentId) {

        String path =
                "punishments." + punishmentId;

        if (!getConfig().isConfigurationSection(path)) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Unknown punishment: " +
                    ChatColor.WHITE +
                    punishmentId
            );

            return;
        }

        String displayName =
                getConfig().getString(
                        path + ".name",
                        punishmentId
                );

        String duration =
                getConfig().getString(
                        path + ".duration",
                        "Permanent"
                );

        String command =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        if (command == null ||
                command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured for " +
                    ChatColor.WHITE +
                    displayName
            );

            return;
        }

        command =
                command
                        .replace(
                                "%player%",
                                targetName
                        )
                        .replace(
                                "%target%",
                                targetName
                        )
                        .replace(
                                "%duration%",
                                duration
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        );

        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        boolean success =
                getServer().dispatchCommand(
                        getServer().getConsoleSender(),
                        command
                );

        if (success) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.GREEN +
                    "Punishment executed: " +
                    ChatColor.WHITE +
                    displayName +
                    ChatColor.GRAY +
                    " → " +
                    ChatColor.WHITE +
                    targetName
            );

            String announcement =
                    getConfig().getString(
                            "settings.announcement",
                            "&4&lPunishment &8┃ &f%player% &7was punished with &c%punishment% &7(&e%duration%&7)"
                    );

            announcement =
                    announcement
                            .replace(
                                    "%player%",
                                    targetName
                            )
                            .replace(
                                    "%punishment%",
                                    ChatColor.stripColor(
                                            color(displayName)
                                    )
                            )
                            .replace(
                                    "%duration%",
                                    duration
                            )
                            .replace(
                                    "%staff%",
                                    staff.getName()
                            );

            getServer().broadcastMessage(
                    color(announcement)
            );

        } else {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Failed to execute punishment command."
            );
        }
    }

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
