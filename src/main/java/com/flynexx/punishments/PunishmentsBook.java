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
            ChatColor.DARK_RED + "PunishmentsBook " +
            ChatColor.DARK_GRAY + "┃ ";

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
         * =====================================================
         * /pmapply <player> <punishment>
         * =====================================================
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

                staff.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "You don't have permission."
                );

                return true;
            }

            if (args.length != 2) {

                staff.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Usage: /pmapply <player> <punishment>"
                );

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
     *
     * This is the working 1.8.8 method.
     *
     * The book is placed in the player's hand before
     * EntityPlayer.openBook() is called.
     */
    private void openBook(
            final Player player,
            String target) {

        try {

            org.bukkit.inventory.ItemStack book =
                    createBook(target);

            if (book == null) {

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Could not create punishment book."
                );

                return;
            }

            /*
             * Save current item.
             */
            final org.bukkit.inventory.ItemStack oldItem =
                    player.getItemInHand();

            /*
             * Put book in hand.
             */
            player.setItemInHand(book);

            player.updateInventory();

            /*
             * Convert Bukkit item to NMS.
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
             * Open the written book.
             */
            entityPlayer.openBook(
                    nmsBook
            );

            /*
             * Do not restore immediately.
             *
             * Wait 5 ticks so the client has enough time
             * to receive and display the book.
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
     *
     * The important difference from the previous version:
     *
     * The pages are still strings, but each punishment name
     * contains a Minecraft click event.
     *
     * The duration is NOT displayed.
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
         * =====================================================
         * NO PUNISHMENTS
         * =====================================================
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
         * =====================================================
         * BUILD CLICKABLE PAGES
         * =====================================================
         */
        List<String> pages =
                new ArrayList<String>();

        StringBuilder page =
                new StringBuilder();

        /*
         * Start page.
         */
        page.append(
                "{"
        );

        page.append(
                "\"text\":\"\","
        );

        page.append(
                "\"extra\":["
        );

        /*
         * Title.
         */
        page.append(
                "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\"}"
        );

        int count = 0;

        /*
         * Read every punishment.
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
             * Command that will be executed when clicked.
             *
             * It is sent by the player who opened the book.
             */
            String command =
                    "/pmapply " +
                    target +
                    " " +
                    id;

            /*
             * Add separator.
             */
            page.append(
                    ","
            );

            /*
             * Clickable text.
             */
            page.append(
                    "{"
            );

            page.append(
                    "\"text\":\""
            );

            page.append(
                    escapeJson(name)
            );

            page.append(
                    "\","
            );

            /*
             * Black text.
             */
            page.append(
                    "\"color\":\"black\","
            );

            /*
             * Underline.
             */
            page.append(
                    "\"underlined\":true,"
            );

            /*
             * Click event.
             */
            page.append(
                    "\"clickEvent\":{"
            );

            page.append(
                    "\"action\":\"run_command\","
            );

            page.append(
                    "\"value\":\""
            );

            page.append(
                    escapeJson(command)
            );

            page.append(
                    "\""
            );

            page.append(
                    "}"
            );

            page.append(
                    "}"
            );

            /*
             * New line.
             */
            page.append(
                    ",{\"text\":\"\\n\"}"
            );

            count++;

            /*
             * Ten punishments per page.
             */
            if (count >= 10) {

                page.append(
                        "]}"
                );

                pages.add(
                        page.toString()
                );

                /*
                 * New page.
                 */
                page =
                        new StringBuilder();

                page.append(
                        "{"
                );

                page.append(
                        "\"text\":\"\","
                );

                page.append(
                        "\"extra\":["
                );

                page.append(
                        "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\"}"
                );

                count = 0;
            }
        }

        /*
         * =====================================================
         * FINISH LAST PAGE
         * =====================================================
         */
        if (count > 0) {

            page.append(
                    "]}"
            );

            pages.add(
                    page.toString()
            );
        }

        /*
         * Put the generated pages into the book.
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
     * ESCAPE TEXT
     * =========================================================
     *
     * Used only to make the page text safe.
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
     * APPLY PUNISHMENT
     * =========================================================
     *
     * PunishmentJail remains responsible for the jail.
     *
     * We do NOT create a jail system here.
     *
     * We also do NOT use Console.
     *
     * The administrator who clicked the punishment executes
     * the configured command.
     */
    private void applyPunishment(
            Player staff,
            String target,
            String id) {

        String path =
                "punishments." +
                id;

        /*
         * Check punishment exists.
         */
        if (!getConfig()
                .isConfigurationSection(path)) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Unknown punishment."
            );

            return;
        }

        /*
         * Name.
         */
        String name =
                getConfig().getString(
                        path + ".name",
                        id
                );

        /*
         * Duration.
         *
         * This is used internally.
         * It is NOT displayed in the book.
         */
        String duration =
                getConfig().getString(
                        path + ".duration",
                        ""
                );

        /*
         * Reason.
         */
        String reason =
                getConfig().getString(
                        path + ".reason",
                        name
                );

        /*
         * Command.
         */
        String command =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        /*
         * Replace placeholders.
         */
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

        /*
         * Remove / from beginning.
         */
        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        /*
         * No command configured.
         */
        if (command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured for " +
                    name +
                    "."
            );

            return;
        }

        /*
         * =====================================================
         * EXECUTE AS STAFF
         * =====================================================
         *
         * IMPORTANT:
         *
         * We deliberately do NOT use:
         *
         * Bukkit.dispatchCommand(ConsoleSender, ...)
         *
         * The command is executed by the administrator.
         */
        boolean success =
                staff.performCommand(
                        command
                );

        /*
         * Command failed.
         */
        if (!success) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Punishment command failed."
            );

            return;
        }

        /*
         * Successful punishment.
         */
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
