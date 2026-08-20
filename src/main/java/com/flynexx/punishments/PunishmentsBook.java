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

    private void openPunishmentBook(final Player player,
                                    final String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle(
                color(getConfig().getString(
                        "settings.book-title",
                        "&4Punishments"
                ))
        );

        meta.setAuthor(
                getConfig().getString(
                        "settings.book-author",
                        "FlyNeXx"
                )
        );

        List<String> pages =
                new ArrayList<String>();

        /*
         * PAGE 1
         */
        StringBuilder page =
                new StringBuilder();

        page.append(
                color("&4&lPUNISHMENTS\n")
        );

        page.append(
                color("&8--------------------\n\n")
        );

        page.append(
                color("&7Player: &f" + target + "\n\n")
        );

        page.append(
                color("&7Select a punishment:\n\n")
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

                String description =
                        getConfig().getString(
                                "punishments." + id + ".description",
                                ""
                        );

                String duration =
                        getConfig().getString(
                                "punishments." + id + ".duration",
                                "Permanent"
                        );

                page.append(
                        color("&c" + id + ". &f" +
                                name + "\n")
                );

                if (!description.isEmpty()) {

                    page.append(
                            color("&7" +
                                    description + "\n")
                    );
                }

                page.append(
                        color("&7Duration: &e" +
                                duration + "\n\n")
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
                color("&7Player: &f" + target + "\n\n")
        );

        page2.append(
                color("&8Punishment commands:\n\n")
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

                page2.append(
                        color("&c" + id +
                                ". &f" + name + "\n")
                );

                page2.append(
                        color("&7Duration: &e" +
                                duration + "\n\n")
                );
            }
        }

        pages.add(page2.toString());

        meta.setPages(pages);

        book.setItemMeta(meta);

        /*
         * IMPORTANT:
         *
         * We temporarily put the book in the player's
         * hand only so Minecraft 1.8.8 can open it.
         *
         * It is removed immediately after opening.
         */
        final ItemStack oldItem =
                player.getItemInHand();

        player.setItemInHand(book);

        player.updateInventory();

        /*
         * Spigot 1.8.8 does NOT have Player.openBook().
         *
         * Therefore use Bukkit's BookMeta/opening mechanism
         * through the item in hand.
         */
        player.setItemInHand(book);

        player.updateInventory();

        Bukkit.getScheduler().runTaskLater(
                this,
                new Runnable() {

                    @Override
                    public void run() {

                        if (!player.isOnline()) {
                            return;
                        }

                        /*
                         * Restore the original item.
                         */
                        player.setItemInHand(oldItem);

                        player.updateInventory();
                    }
                },
                1L
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
