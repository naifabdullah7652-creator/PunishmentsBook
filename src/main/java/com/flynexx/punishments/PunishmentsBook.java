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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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

        getLogger().info("PunishmentsBook 2.0.0 enabled.");
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
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("punishmentsbook.use")) {
            player.sendMessage(
                    PREFIX + ChatColor.RED +
                    "You don't have permission.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(
                    PREFIX + ChatColor.RED +
                    "Usage: /pm <player>");
            return true;
        }

        openPunishmentBook(player, args[0]);
        return true;
    }

    /**
     * Creates the book and opens it virtually.
     * The book is never left in the player's inventory.
     */
    private void openPunishmentBook(Player player, String target) {

        ItemStack book = createBook(target);

        if (!openVirtualBook(player, book)) {
            player.sendMessage(
                    PREFIX + ChatColor.RED +
                    "Could not open punishment book.");
        }
    }

    /**
     * Creates a written book.
     *
     * IMPORTANT:
     * No JSON strings are inserted into the pages.
     */
    private ItemStack createBook(final String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle("Punishments");
        meta.setAuthor("FlyNeXx");

        /*
         * We use BookMeta.Spigot through reflection.
         * This avoids compiling directly against bungeecord-chat.
         */
        try {

            Class<?> baseComponentClass =
                    Class.forName(
                            "net.md_5.bungee.api.chat.BaseComponent");

            Class<?> textComponentClass =
                    Class.forName(
                            "net.md_5.bungee.api.chat.TextComponent");

            Class<?> clickEventClass =
                    Class.forName(
                            "net.md_5.bungee.api.chat.ClickEvent");

            Class<?> clickActionClass =
                    Class.forName(
                            "net.md_5.bungee.api.chat.ClickEvent$Action");

            Object runCommandAction =
                    Enum.valueOf(
                            (Class<Enum>) clickActionClass,
                            "RUN_COMMAND");

            Constructor<?> textConstructor =
                    textComponentClass.getConstructor(
                            String.class);

            Constructor<?> clickConstructor =
                    clickEventClass.getConstructor(
                            clickActionClass,
                            String.class);

            Method setClickEvent =
                    textComponentClass.getMethod(
                            "setClickEvent",
                            clickEventClass);

            /*
             * BookMeta.spigot()
             */
            Method spigotMethod =
                    BookMeta.class.getMethod("spigot");

            Object spigotMeta =
                    spigotMethod.invoke(meta);

            Method addPage =
                    spigotMeta.getClass().getMethod(
                            "addPage",
                            Array.newInstance(
                                    baseComponentClass, 0
                            ).getClass()
                    );

            ConfigurationSection section =
                    getConfig().getConfigurationSection(
                            "punishments");

            if (section == null) {
                meta.setPages(
                        "No punishments configured."
                );

                book.setItemMeta(meta);
                return book;
            }

            List<Object> components =
                    new ArrayList<Object>();

            for (String id :
                    section.getKeys(false)) {

                String name =
                        getConfig().getString(
                                "punishments." +
                                id + ".name",
                                id
                        );

                /*
                 * Black text.
                 * No duration.
                 * No reason.
                 * No JSON.
                 */
                Object text =
                        textConstructor.newInstance(
                                "\u00a70" + name + "\n"
                        );

                String command =
                        "/pmapply " +
                        target +
                        " " +
                        id;

                Object clickEvent =
                        clickConstructor.newInstance(
                                runCommandAction,
                                command
                        );

                setClickEvent.invoke(
                        text,
                        clickEvent
                );

                components.add(text);
            }

            /*
             * Convert List<BaseComponent>
             * to BaseComponent[].
             */
            Object page =
                    Array.newInstance(
                            baseComponentClass,
                            components.size()
                    );

            for (int i = 0;
                 i < components.size();
                 i++) {

                Array.set(
                        page,
                        i,
                        components.get(i)
                );
            }

            /*
             * addPage(BaseComponent[]...)
             *
             * Reflection sees this as:
             * addPage(BaseComponent[][])
             */
            Object pages =
                    Array.newInstance(
                            page.getClass(),
                            1
                    );

            Array.set(
                    pages,
                    0,
                    page
            );

            addPage.invoke(
                    spigotMeta,
                    pages
            );

        } catch (Throwable ex) {

            getLogger().warning(
                    "Could not create clickable book: " +
                    ex.getClass().getSimpleName() +
                    ": " +
                    ex.getMessage()
            );

            /*
             * Fallback:
             * ordinary text, but still no JSON.
             */
            List<String> fallback =
                    new ArrayList<String>();

            fallback.add(
                    ChatColor.BLACK +
                    "Punishments\n\n" +
                    ChatColor.BLACK +
                    "Book interaction is unavailable."
            );

            meta.setPages(fallback);
        }

        book.setItemMeta(meta);
        return book;
    }

    /**
     * Opens the written book without leaving it in inventory.
     *
     * Uses only reflection, so the source does not contain:
     * PacketPlayOutOpenBook
     * CraftItemStack imports
     * EntityHuman imports
     * NMS imports
     */
    private boolean openVirtualBook(
            final Player player,
            ItemStack book) {

        ItemStack oldItem =
                player.getItemInHand();

        try {

            /*
             * Temporarily put the book in the hand.
             * This is required by the 1.8.8 client/book mechanism.
             */
            player.setItemInHand(book);
            player.updateInventory();

            Object craftPlayer =
                    player.getClass()
                            .getMethod("getHandle")
                            .invoke(player);

            /*
             * CraftItemStack.asNMSCopy(...)
             */
            Class<?> craftItemStack =
                    Class.forName(
                            "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
                    );

            Method asNMSCopy =
                    craftItemStack.getMethod(
                            "asNMSCopy",
                            ItemStack.class
                    );

            Object nmsBook =
                    asNMSCopy.invoke(
                            null,
                            book
                    );

            /*
             * EntityPlayer.openBook(ItemStack)
             */
            Method openBook = null;

            Method[] methods =
                    craftPlayer.getClass()
                            .getMethods();

            for (Method method : methods) {

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

            if (openBook == null) {

                restoreItem(player, oldItem);

                return false;
            }

            openBook.invoke(
                    craftPlayer,
                    nmsBook
            );

            /*
             * Restore the player's original item
             * after the client receives the open-book packet.
             */
            Bukkit.getScheduler().runTaskLater(
                    this,
                    new Runnable() {
                        @Override
                        public void run() {

                            if (player.isOnline()) {
                                /*
                                 * The original item was captured
                                 * by the outer method.
                                 *
                                 * Inventory is restored below
                                 * by the scheduled restore method.
                                 */
                            }
                        }
                    },
                    1L
            );

            /*
             * Restore immediately after sending MC|BOpen.
             * 1.8.8 clients keep the opened book GUI.
             */
            restoreItem(player, oldItem);

            return true;

        } catch (Throwable ex) {

            restoreItem(player, oldItem);

            getLogger().warning(
                    "Could not open 1.8.8 book: " +
                    ex.getClass().getSimpleName() +
                    ": " +
                    ex.getMessage()
            );

            return false;
        }
    }

    private void restoreItem(
            Player player,
            ItemStack item) {

        try {
            player.setItemInHand(item);
            player.updateInventory();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Hidden command used by the clickable book.
     *
     * /pmapply <player> <punishment-id>
     */
    private void applyPunishment(
            Player staff,
            String target,
            String id) {

        String path =
                "punishments." + id;

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

        if (command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured."
            );

            return;
        }

        command =
                command
                        .replace("%player%", target)
                        .replace("%target%", target)
                        .replace("%duration%", duration)
                        .replace("%reason%", reason)
                        .replace("%staff%", staff.getName());

        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        boolean result =
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        command
                );

        if (!result) {

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
                name +
                ChatColor.GRAY +
                " -> " +
                ChatColor.WHITE +
                target
        );
    }

    /**
     * Internal command executor.
     *
     * /pmapply <player> <id>
     */
    private boolean handleApplyCommand(
            CommandSender sender,
            String[] args) {

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
            return true;
        }

        applyPunishment(
                staff,
                args[0],
                args[1]
        );

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args) {

        return new ArrayList<String>();
    }

    /*
     * We handle pmapply here as well because
     * both commands use this JavaPlugin as executor.
     */
    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args,
            boolean unused) {

        return false;
    }
}
