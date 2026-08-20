package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class PunishmentsBook extends JavaPlugin {

    private static final String PREFIX =
            ChatColor.DARK_RED +
            "PunishmentsBook " +
            ChatColor.DARK_GRAY +
            "┃ ";

    @Override
    public void onEnable() {

        saveDefaultConfig();

        if (getCommand("pm") != null) {
            getCommand("pm").setExecutor(this);
        }

        if (getCommand("pmapply") != null) {
            getCommand("pmapply").setExecutor(this);
        }

        getLogger().info(
                "PunishmentsBook 2.0.0 enabled."
        );
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

            Player player =
                    (Player) sender;

            if (!player.hasPermission(
                    "punishmentsbook.use")) {

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "You don't have permission."
                );

                return true;
            }

            if (args.length != 1) {

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Usage: /pm <player>"
                );

                return true;
            }

            openBook(
                    player,
                    args[0]
            );

            return true;
        }

        /*
         * /pmapply <player> <punishment>
         */
        if (command.getName()
                .equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff =
                    (Player) sender;

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

    /*
     * =========================================================
     * OPEN BOOK
     * =========================================================
     */
    private void openBook(
            final Player player,
            String target) {

        try {

            /*
             * Create the actual Bukkit book.
             */
            org.bukkit.inventory.ItemStack book =
                    createBook(target);

            /*
             * Save current item.
             */
            final org.bukkit.inventory.ItemStack oldItem =
                    player.getItemInHand();

            /*
             * Put the book in the player's hand.
             */
            player.setItemInHand(book);

            player.updateInventory();

            /*
             * Convert the book to NMS.
             */
            ItemStack nmsBook =
                    CraftItemStack.asNMSCopy(
                            book
                    );

            /*
             * Get NMS player.
             */
            EntityPlayer entityPlayer =
                    ((CraftPlayer) player)
                            .getHandle();

            /*
             * Open written book.
             */
            entityPlayer.openBook(
                    nmsBook
            );

            /*
             * Wait before restoring the old item.
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
     * CREATE BOOK
     * =========================================================
     */
    private org.bukkit.inventory.ItemStack createBook(
            String target) {

        org.bukkit.inventory.ItemStack book =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle(
                "Punishments"
        );

        meta.setAuthor(
                "FlyNeXx"
        );

        ConfigurationSection section =
                getConfig()
                        .getConfigurationSection(
                                "punishments"
                        );

        /*
         * No punishments.
         */
        if (section == null ||
                section.getKeys(false).isEmpty()) {

            meta.setPages(
                    "Punishments\n\n" +
                    "No punishments configured."
            );

            book.setItemMeta(meta);

            return book;
        }

        /*
         * Pages.
         */
        List<String> pages =
                new ArrayList<String>();

        StringBuilder page =
                new StringBuilder();

        /*
         * Page title.
         */
        page.append(
                "Punishments\n\n"
        );

        int count = 0;

        /*
         * Read punishments.
         */
        for (String id :
                section.getKeys(false)) {

            String name =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".name",
                            id
                    );

            /*
             * Only show the name.
             *
             * Duration is deliberately not shown.
             */
            page.append(
                    name
            );

            page.append(
                    "\n"
            );

            count++;

            /*
             * Ten punishments per page.
             */
            if (count >= 10) {

                pages.add(
                        page.toString()
                );

                page =
                        new StringBuilder();

                page.append(
                        "Punishments\n\n"
                );

                count = 0;
            }
        }

        /*
         * Add last page.
         */
        if (count > 0) {

            pages.add(
                    page.toString()
            );
        }

        /*
         * Put pages into the book.
         */
        meta.setPages(
                pages
        );

        book.setItemMeta(
                meta
        );

        return book;
    }

    /*
     * =========================================================
     * APPLY PUNISHMENT
     * =========================================================
     *
     * This is kept for the next interactive version.
     */
    private void applyPunishment(
            Player staff,
            String target,
            String id) {

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

        String command =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        command =
                command
                        .replace(
                                "%player%",
                                target
                        )
                        .replace(
                                "%target%",
                                target
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

        if (command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured."
            );

            return;
        }

        /*
         * Execute as the administrator.
         */
        boolean success =
                staff.performCommand(
                        command
                );

        if (!success) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Punishment command failed."
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
                " -> " +
                ChatColor.WHITE +
                target
        );
    }
}
