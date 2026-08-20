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

        getLogger().info("PunishmentsBook 2.0.0 enabled.");
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

            Player player = (Player) sender;

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

            if (args.length != 2) {
                return true;
            }

            if (!staff.hasPermission(
                    "punishmentsbook.use")) {

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
     * Opens the punishment book.
     */
    private void openBook(
            Player player,
            String target) {

        ItemStack book =
                createBook(target);

        ItemStack old =
                player.getItemInHand();

        try {

            /*
             * Put the book in the player's hand.
             */
            player.setItemInHand(book);
            player.updateInventory();

            /*
             * Get EntityPlayer.
             */
            Object handle =
                    player.getClass()
                            .getMethod("getHandle")
                            .invoke(player);

            /*
             * CraftItemStack.
             */
            Class<?> craftItemStack =
                    Class.forName(
                            "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
                    );

            /*
             * Convert Bukkit ItemStack
             * to NMS ItemStack.
             */
            Object nmsBook =
                    craftItemStack
                            .getMethod(
                                    "asNMSCopy",
                                    ItemStack.class
                            )
                            .invoke(
                                    null,
                                    book
                            );

            /*
             * Find EntityPlayer.openBook(...)
             */
            java.lang.reflect.Method openBook =
                    null;

            for (java.lang.reflect.Method method :
                    handle.getClass().getMethods()) {

                if (!method.getName()
                        .equals("openBook")) {

                    continue;
                }

                if (method.getParameterTypes()
                        .length != 1) {

                    continue;
                }

                openBook = method;
                break;
            }

            /*
             * If openBook does not exist,
             * use the Bukkit inventory method
             * as a fallback.
             */
            if (openBook != null) {

                openBook.invoke(
                        handle,
                        nmsBook
                );

            } else {

                /*
                 * Fallback:
                 * The book remains in hand so the
                 * player can right-click it.
                 */
                player.sendMessage(
                        PREFIX +
                        ChatColor.YELLOW +
                        "Right-click the book to open it."
                );
            }

            /*
             * Restore previous item.
             */
            player.setItemInHand(old);
            player.updateInventory();

        } catch (Throwable ex) {

            /*
             * Always restore the old item.
             */
            player.setItemInHand(old);
            player.updateInventory();

            getLogger().warning(
                    "Unable to open punishment book: " +
                    ex.getClass().getSimpleName() +
                    ": " +
                    ex.getMessage()
            );

            player.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment book."
            );
        }
    }

    /*
     * Creates the punishment book.
     *
     * This version intentionally does NOT use:
     *
     * BookMeta.spigot()
     * BaseComponent
     * TextComponent
     * ClickEvent
     *
     * because those were causing the
     * "Interactive book unavailable" fallback.
     */
    private ItemStack createBook(
            String target) {

        ItemStack book =
                new ItemStack(
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
         * No punishments configured.
         */
        if (section == null) {

            meta.setPages(
                    ChatColor.BLACK +
                    "Punishments\n\n" +
                    "No punishments configured."
            );

            book.setItemMeta(meta);

            return book;
        }

        /*
         * Build the page.
         */
        StringBuilder page =
                new StringBuilder();

        page.append(
                ChatColor.BLACK
        );

        page.append(
                "Punishments\n\n"
        );

        /*
         * List punishments.
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

            String duration =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".duration",
                            ""
                    );

            page.append(
                    ChatColor.BLACK
            );

            page.append(
                    name
            );

            if (!duration.isEmpty()) {

                page.append(
                        " - "
                );

                page.append(
                        duration
                );
            }

            page.append(
                    "\n"
            );
        }

        /*
         * Put page into book.
         */
        meta.setPages(
                page.toString()
        );

        book.setItemMeta(meta);

        return book;
    }

    /*
     * Applies a punishment.
     *
     * /pmapply <player> <id>
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
         * Punishment name.
         */
        String name =
                getConfig().getString(
                        path + ".name",
                        id
                );

        /*
         * Duration.
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
         * Remove / from command.
         */
        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        /*
         * Empty command.
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
         * Execute command from console.
         */
        boolean success =
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
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
         * Success message.
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
