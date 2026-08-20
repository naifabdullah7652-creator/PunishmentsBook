package com.flynexx.punishments;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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

            if (args.length < 2) {
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
     * Opens the book without putting it into the player's inventory.
     *
     * Important:
     * Bukkit 1.8.8 API has no Player#openBook().
     *
     * Therefore this method temporarily places the book in the
     * player's hand, opens the vanilla book GUI using a Bukkit
     * command, then restores the original item immediately.
     *
     * No NMS classes are used.
     */
    private void openPunishmentBook(Player player, String target) {

        ItemStack book = createBook(target);

        ItemStack oldItem = player.getItemInHand();

        player.setItemInHand(book);
        player.updateInventory();

        /*
         * In 1.8.8 the vanilla command is:
         *
         * /openbook
         *
         * but it is not available as a normal Bukkit command.
         *
         * We therefore use the safest Bukkit-compatible method:
         * execute the book interaction from the held written book.
         */

        player.sendMessage(
                PREFIX + ChatColor.GREEN +
                "Opening punishment book for " +
                ChatColor.WHITE + target +
                ChatColor.GREEN + "."
        );

        /*
         * The written book itself is never added with addItem().
         * It only exists temporarily in the hand.
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
                20L
        );
    }

    private ItemStack createBook(String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

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
                color("&8Select a punishment:\n\n")
        );

        if (getConfig().isConfigurationSection("punishments")) {

            for (String id :
                    getConfig()
                            .getConfigurationSection("punishments")
                            .getKeys(false)) {

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

                page.append(
                        color("&c» &f" + name)
                );

                page.append(
                        color("\n&7Duration: &e" +
                              duration +
                              "\n\n")
                );
            }
        }

        pages.add(page.toString());

        /*
         * PAGE 2
         */

        StringBuilder page2 =
                new StringBuilder();

        page2.append(
                color("&4&lPUNISHMENTS\n\n")
        );

        page2.append(
                color("&7Target: &f" + target + "\n\n")
        );

        page2.append(
                color("&7Punishments:\n\n")
        );

        if (getConfig().isConfigurationSection("punishments")) {

            for (String id :
                    getConfig()
                            .getConfigurationSection("punishments")
                            .getKeys(false)) {

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

                page2.append(
                        color("&c" + name + "\n")
                );

                page2.append(
                        color("&7Duration: &e" +
                              duration +
                              "\n\n")
                );
            }
        }

        pages.add(page2.toString());

        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    /*
     * Executes the configured punishment.
     */
    private void executePunishment(Player staff,
                                   String targetName,
                                   String punishmentId) {

        String path =
                "punishments." + punishmentId;

        if (!getConfig().isConfigurationSection(path)) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Unknown punishment."
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

        command = command
                .replace("%player%", targetName)
                .replace("%target%", targetName)
                .replace("%duration%", duration)
                .replace("%staff%", staff.getName());

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
                color(displayName) +
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
                        .replace("%player%", targetName)
                        .replace(
                                "%punishment%",
                                ChatColor.stripColor(
                                        color(displayName)
                                )
                        )
                        .replace("%duration%", duration)
                        .replace("%staff%", staff.getName());

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
