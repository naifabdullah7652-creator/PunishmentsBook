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
    public boolean onCommand(
            CommandSender sender,
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

            openPunishmentBook(player, args[0]);
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

    /*
     * Opens the book without putting it permanently
     * inside the player's inventory.
     *
     * IMPORTANT:
     * Bukkit 1.8.8 API does not contain Player#openBook().
     *
     * Therefore we temporarily place the book in the
     * player's hand, use the Bukkit inventory update,
     * then restore the previous item.
     */
    private void openPunishmentBook(
            final Player player,
            String target) {

        ItemStack book =
                createBook(target);

        ItemStack previous =
                player.getItemInHand();

        int previousSlot =
                player.getInventory().getHeldItemSlot();

        player.getInventory().setItem(
                previousSlot,
                book
        );

        player.updateInventory();

        /*
         * The book itself is not left in the inventory.
         * After the interaction/update it is restored.
         */
        Bukkit.getScheduler().runTaskLater(
                this,
                new Runnable() {
                    @Override
                    public void run() {

                        if (!player.isOnline()) {
                            return;
                        }

                        player.getInventory().setItem(
                                player.getInventory().getHeldItemSlot(),
                                previous
                        );

                        player.updateInventory();
                    }
                },
                1L
        );
    }

    /*
     * Creates the punishment book.
     */
    private ItemStack createBook(String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        String title =
                getConfig().getString(
                        "settings.book-title",
                        "&4Punishments"
                );

        String author =
                getConfig().getString(
                        "settings.book-author",
                        "FlyNeXx"
                );

        meta.setTitle(color(title));
        meta.setAuthor(author);

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

        page.append(
                color("&7Player: &f" + target + "\n\n")
        );

        page.append(
                color("&8Available punishments:\n\n")
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
                                "punishments." +
                                id +
                                ".name",
                                id
                        );

                String duration =
                        getConfig().getString(
                                "punishments." +
                                id +
                                ".duration",
                                "Permanent"
                        );

                page.append(
                        color(
                                "&c» &f" +
                                name +
                                "\n"
                        )
                );

                page.append(
                        color(
                                "&7Duration: &e" +
                                duration +
                                "\n\n"
                        )
                );

                /*
                 * Prevent extremely large pages.
                 */
                if (page.length() > 190) {

                    pages.add(
                            page.toString()
                    );

                    page =
                            new StringBuilder();

                    page.append(
                            color(
                                    "&4&lPUNISHMENTS\n\n"
                            )
                    );
                }
            }
        }

        if (page.length() > 0) {
            pages.add(
                    page.toString()
            );
        }

        /*
         * INFORMATION PAGE
         */
        StringBuilder info =
                new StringBuilder();

        info.append(
                color("&4&lPUNISHMENTS\n\n")
        );

        info.append(
                color("&7Player: &f" +
                        target +
                        "\n\n")
        );

        info.append(
                color(
                        "&8To apply a punishment,\n" +
                        "&8use the punishment command.\n\n"
                )
        );

        info.append(
                color(
                        "&7Example:\n" +
                        "&f/pmapply " +
                        target +
                        " <id>"
                )
        );

        pages.add(
                info.toString()
        );

        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    /*
     * Executes punishment command from config.
     */
    private void executePunishment(
            Player staff,
            String targetName,
            String punishmentId) {

        String path =
                "punishments." +
                punishmentId;

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
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        command
                );

        if (!success) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Failed to execute punishment."
            );

            return;
        }

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
                        "&4&lPunishment &8┃ " +
                        "&f%player% &7was punished with " +
                        "&c%punishment% &7(&e%duration%&7)"
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

        Bukkit.broadcastMessage(
                color(announcement)
        );
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
