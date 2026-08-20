package com.flynexx.punishments;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

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
     * Opens the book for the player.
     */
    private void openBook(
            Player player,
            String target) {

        ItemStack book =
                createBook(target);

        ItemStack oldItem =
                player.getItemInHand();

        try {

            /*
             * The 1.8.8 client opens the
             * written book from the hand.
             */
            player.setItemInHand(book);
            player.updateInventory();

            /*
             * Get NMS EntityPlayer.
             */
            Object handle =
                    player.getClass()
                            .getMethod("getHandle")
                            .invoke(player);

            /*
             * Convert Bukkit ItemStack
             * to NMS ItemStack.
             */
            Class<?> craftItemStack =
                    Class.forName(
                            "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
                    );

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
             * Find EntityPlayer.openBook(ItemStack).
             */
            java.lang.reflect.Method openBook =
                    null;

            for (java.lang.reflect.Method method :
                    handle.getClass().getMethods()) {

                if (!method.getName()
                        .equals("openBook")) {

                    continue;
                }

                Class<?>[] parameters =
                        method.getParameterTypes();

                if (parameters.length != 1) {
                    continue;
                }

                openBook = method;
                break;
            }

            if (openBook == null) {

                throw new NoSuchMethodException(
                        "EntityPlayer.openBook(ItemStack) not found"
                );
            }

            /*
             * Open the written book.
             */
            openBook.invoke(
                    handle,
                    nmsBook
            );

            /*
             * Restore the item that was
             * previously in the player's hand.
             */
            player.setItemInHand(oldItem);
            player.updateInventory();

        } catch (Throwable ex) {

            /*
             * Always restore the old item.
             */
            player.setItemInHand(oldItem);
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
     * Creates the interactive punishment book.
     *
     * IMPORTANT:
     * Only the punishment NAME is shown.
     *
     * Duration is NOT displayed.
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
         * Create clickable components.
         */
        BaseComponent[] components =
                new BaseComponent[
                        section.getKeys(false).size()
                ];

        int index = 0;

        for (String id :
                section.getKeys(false)) {

            /*
             * Punishment name.
             *
             * NO duration here.
             */
            String name =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".name",
                            id
                    );

            /*
             * Text shown in the book.
             */
            TextComponent component =
                    new TextComponent(
                            ChatColor.BLACK +
                            name +
                            "\n"
                    );

            /*
             * Command executed when
             * the player clicks the punishment.
             */
            String command =
                    "/pmapply " +
                    target +
                    " " +
                    id;

            /*
             * Add click action.
             */
            component.setClickEvent(
                    new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            command
                    )
            );

            components[index++] =
                    component;
        }

        /*
         * Add the interactive page.
         *
         * This is the native Bungee/Spigot
         * BookMeta API and avoids the
         * reflection problem from the
         * previous version.
         */
        meta.spigot().addPage(
                components
        );

        book.setItemMeta(meta);

        return book;
    }

    /*
     * Executes the punishment command.
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
         * Punishment name.
         */
        String name =
                getConfig().getString(
                        path + ".name",
                        id
                );

        /*
         * Duration.
         *
         * This is NOT displayed in
         * the book, but remains available
         * to the punishment command.
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
         * Command configured for punishment.
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
         * Execute punishment command
         * as console.
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
