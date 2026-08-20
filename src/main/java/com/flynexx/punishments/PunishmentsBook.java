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
     *
     * Minecraft 1.8.8 requires the written book to actually
     * exist in the player's hand when openBook() is called.
     */
    private void openBook(
            final Player player,
            String target) {

        try {

            /*
             * Create the complete Bukkit written book.
             */
            org.bukkit.inventory.ItemStack book =
                    createBukkitBook(target);

            if (book == null) {

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Could not create punishment book."
                );

                return;
            }

            /*
             * Save the current item.
             */
            final org.bukkit.inventory.ItemStack oldItem =
                    player.getItemInHand();

            /*
             * Put the written book in the player's hand.
             */
            player.setItemInHand(book);

            player.updateInventory();

            /*
             * Convert Bukkit book to NMS.
             */
            ItemStack nmsBook =
                    CraftItemStack.asNMSCopy(book);

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
             * Wait two ticks before restoring the old item.
             *
             * This gives the client time to receive the
             * book-open packet and read the book contents.
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
                    2L
            );

        } catch (Throwable ex) {

            getLogger().severe(
                    "Failed to open punishment book."
            );

            getLogger().severe(
                    ex.getClass().getName()
            );

            if (ex.getMessage() != null) {

                getLogger().severe(
                        ex.getMessage()
                );
            }

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
     * CREATE BUKKIT BOOK
     * =========================================================
     */
    private org.bukkit.inventory.ItemStack createBukkitBook(
            String target) {

        /*
         * Create written book.
         */
        org.bukkit.inventory.ItemStack book =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        /*
         * Get BookMeta.
         */
        BookMeta meta =
                (BookMeta) book.getItemMeta();

        /*
         * Title.
         */
        meta.setTitle(
                "Punishments"
        );

        /*
         * Author.
         */
        meta.setAuthor(
                "FlyNeXx"
        );

        /*
         * Punishments section.
         */
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
                    "{\"text\":\"Punishments\\n\\nNo punishments configured.\",\"color\":\"black\"}"
            );

            book.setItemMeta(
                    meta
            );

            return book;
        }

        /*
         * =====================================================
         * CREATE PAGES
         * =====================================================
         */
        List<String> pages =
                new ArrayList<String>();

        /*
         * Current page.
         */
        StringBuilder page =
                createPageStart();

        int count = 0;

        /*
         * Go through punishments.
         */
        for (String id :
                section.getKeys(false)) {

            /*
             * Punishment name.
             */
            String name =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".name",
                            id
                    );

            /*
             * Command executed when clicked.
             */
            String command =
                    "/pmapply " +
                    target +
                    " " +
                    id;

            /*
             * =================================================
             * CLICKABLE PUNISHMENT
             * =================================================
             */
            page.append(",");

            page.append("{");

            /*
             * Visible text.
             */
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
             * Text color.
             */
            page.append(
                    "\"color\":\"black\","
            );

            /*
             * Underline makes it obvious that it is clickable.
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
             * Maximum 10 punishments per page.
             */
            if (count >= 10) {

                /*
                 * Finish page.
                 */
                page.append(
                        "]}"
                );

                pages.add(
                        page.toString()
                );

                /*
                 * Start next page.
                 */
                page =
                        createPageStart();

                count = 0;
            }
        }

        /*
         * =====================================================
         * FINAL PAGE
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
         * Put pages into BookMeta.
         */
        meta.setPages(
                pages
        );

        /*
         * Apply BookMeta.
         */
        book.setItemMeta(
                meta
        );

        return book;
    }

    /*
     * =========================================================
     * PAGE START
     * =========================================================
     */
    private StringBuilder createPageStart() {

        StringBuilder page =
                new StringBuilder();

        page.append("{");

        page.append(
                "\"text\":\"\","
        );

        page.append(
                "\"extra\":["
        );

        /*
         * Page title.
         */
        page.append(
                "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\"}"
        );

        return page;
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
     * APPLY PUNISHMENT
     * =========================================================
     *
     * This currently executes the configured command AS THE
     * STAFF MEMBER instead of Console.
     *
     * PunishmentJail remains responsible for jail.
     */
    private void applyPunishment(
            Player staff,
            String target,
            String id) {

        String path =
                "punishments." +
                id;

        /*
         * Check punishment.
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
         * Configured command.
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
         * Remove leading slash.
         */
        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        /*
         * No command.
         */
        if (command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured."
            );

            return;
        }

        /*
         * =====================================================
         * IMPORTANT
         * =====================================================
         *
         * Execute as the administrator.
         *
         * NOT Console.
         */
        boolean success =
                staff.performCommand(
                        command
                );

        /*
         * Failed.
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
         * Success.
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
