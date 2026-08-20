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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getLogger().info("PunishmentsBook 2.0.0 enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("PunishmentsBook disabled.");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (!command.getName().equalsIgnoreCase("pm")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(
                    ChatColor.RED + "Players only."
            );
            return true;
        }

        if (!sender.hasPermission("punishmentsbook.use")) {
            sender.sendMessage(
                    ChatColor.RED + "You don't have permission."
            );
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(
                    ChatColor.RED + "Usage: /pm <player>"
            );
            return true;
        }

        Player player = (Player) sender;

        openBook(player, args[0]);

        return true;
    }

    private void openBook(Player player, String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        if (meta == null) {
            player.sendMessage(
                    ChatColor.RED +
                    "Could not create punishment book."
            );
            return;
        }

        /*
         * Book title
         */
        String title = getConfig().getString(
                "settings.book-title",
                "&4Punishments"
        );

        /*
         * Book author
         */
        String author = getConfig().getString(
                "settings.book-author",
                "FlyNeXx"
        );

        /*
         * 1.8.8 BookMeta supports these methods.
         */
        meta.setTitle(color(title));
        meta.setAuthor(author);

        /*
         * Pages
         */
        List<String> pages =
                new ArrayList<String>();

        StringBuilder page =
                new StringBuilder();

        /*
         * Header
         */
        page.append(
                color("&4&lPUNISHMENTS")
        );

        page.append("\n\n");

        /*
         * Player line
         */
        String playerLine =
                getConfig().getString(
                        "settings.player-line",
                        "&7Player: &f%player%"
                );

        playerLine =
                playerLine.replace(
                        "%player%",
                        target
                );

        page.append(color(playerLine));

        page.append("\n\n");

        /*
         * Punishments section
         */
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
                                "&cPunishment"
                        );

                String duration =
                        getConfig().getString(
                                "punishments." +
                                id +
                                ".duration",
                                "Permanent"
                        );

                String line =
                        color(
                                "&c» " +
                                name
                        )
                        + "\n"
                        + color(
                                "&7Duration: &f" +
                                duration
                        )
                        + "\n\n";

                /*
                 * Keep the page small enough
                 * for Minecraft 1.8.8.
                 */
                if (page.length() +
                        line.length() > 220) {

                    pages.add(
                            page.toString()
                    );

                    page =
                            new StringBuilder();
                }

                page.append(line);
            }
        }

        /*
         * No punishments
         */
        if (section == null ||
                section.getKeys(false).isEmpty()) {

            page.append(
                    color(
                            "&7No punishments configured."
                    )
            );
        }

        /*
         * Add final page
         */
        if (page.length() > 0) {
            pages.add(
                    page.toString()
            );
        }

        /*
         * Apply pages
         */
        meta.setPages(pages);

        /*
         * Apply BookMeta
         */
        book.setItemMeta(meta);

        /*
         * Save current item.
         */
        final ItemStack oldItem =
                player.getItemInHand();

        /*
         * Put book in player's hand.
         */
        player.setItemInHand(book);

        player.updateInventory();

        /*
         * Try to open the book without
         * directly depending on NMS.
         */
        tryOpenBook(player, book);

        /*
         * Restore previous item shortly after.
         */
        Bukkit.getScheduler().runTaskLater(
                this,
                new Runnable() {

                    @Override
                    public void run() {

                        if (player.isOnline()) {

                            player.setItemInHand(
                                    oldItem
                            );

                            player.updateInventory();
                        }
                    }

                },
                2L
        );
    }

    /**
     * Attempts to open the book using
     * the Bukkit API if available.
     *
     * This method intentionally uses
     * Reflection so the project does not
     * require CraftBukkit/NMS imports.
     */
    private void tryOpenBook(
            Player player,
            ItemStack book) {

        try {

            java.lang.reflect.Method method =
                    Player.class.getMethod(
                            "openBook",
                            ItemStack.class
                    );

            method.invoke(
                    player,
                    book
            );

        } catch (NoSuchMethodException e) {

            /*
             * Bukkit 1.8.8 does not expose
             * Player.openBook().
             *
             * The book remains in the player's
             * hand and the server continues safely.
             */

        } catch (Exception e) {

            getLogger().warning(
                    "Could not open punishment book: "
                    + e.getClass().getSimpleName()
            );
        }
    }

    /**
     * Translate & color codes.
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
