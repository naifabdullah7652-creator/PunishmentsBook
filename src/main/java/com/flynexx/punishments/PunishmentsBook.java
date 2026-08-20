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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class PunishmentsBook extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
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

        if (!sender.hasPermission("punishmentsbook.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /pm <player>");
            return true;
        }

        Player player = (Player) sender;

        openBook(player, args[0]);

        return true;
    }

    private void openBook(Player player, String target) {

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta = (BookMeta) book.getItemMeta();

        if (meta == null) {
            player.sendMessage(
                    ChatColor.RED + "Could not create punishment book."
            );
            return;
        }

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

        List<String> pages = new ArrayList<String>();

        StringBuilder page = new StringBuilder();

        /*
         * HEADER
         */
        page.append(color("&4&lPUNISHMENTS"));
        page.append("\n\n");

        /*
         * PLAYER
         */
        String playerLine = getConfig().getString(
                "settings.player-line",
                "&7Player: &f%player%"
        );

        page.append(
                color(playerLine.replace("%player%", target))
        );

        page.append("\n\n");

        /*
         * PUNISHMENTS
         */
        ConfigurationSection section =
                getConfig().getConfigurationSection("punishments");

        if (section != null) {

            for (String id : section.getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        "&cPunishment"
                );

                String duration = getConfig().getString(
                        "punishments." + id + ".duration",
                        "Permanent"
                );

                String line =
                        color("&c» " + name)
                                + "\n"
                                + color("&7Duration: &f" + duration)
                                + "\n\n";

                /*
                 * Split pages when they become large.
                 */
                if (page.length() + line.length() > 220) {

                    pages.add(page.toString());

                    page = new StringBuilder();
                }

                page.append(line);
            }
        }

        if (section == null || section.getKeys(false).isEmpty()) {

            page.append(
                    color("&7No punishments configured.")
            );
        }

        if (page.length() > 0) {
            pages.add(page.toString());
        }

        meta.setPages(pages);

        book.setItemMeta(meta);

        /*
         * Save current item.
         */
        final int slot =
                player.getInventory().getHeldItemSlot();

        final ItemStack oldItem =
                player.getInventory().getItem(slot);

        /*
         * Put the written book into the
         * currently selected hotbar slot.
         */
        player.getInventory().setItem(slot, book);

        player.updateInventory();

        /*
         * Give the client a moment to receive
         * the written book.
         */
        Bukkit.getScheduler().runTask(
                this,
                new Runnable() {

                    @Override
                    public void run() {

                        if (!player.isOnline()) {
                            return;
                        }

                        /*
                         * Open the book using
                         * EntityPlayer.openBook(ItemStack)
                         * through Reflection.
                         */
                        if (!openBookNMS(player, book)) {

                            player.sendMessage(
                                    ChatColor.RED +
                                    "Could not open punishment book."
                            );
                        }

                        /*
                         * Restore the original item
                         * after the packet is sent.
                         */
                        Bukkit.getScheduler().runTaskLater(
                                PunishmentsBook.this,
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        if (player.isOnline()) {

                                            player.getInventory()
                                                    .setItem(
                                                            slot,
                                                            oldItem
                                                    );

                                            player.updateInventory();
                                        }
                                    }
                                },
                                2L
                        );
                    }
                }
        );
    }

    /**
     * Opens a written book on Spigot/Paper 1.8.8
     * without importing NMS classes.
     *
     * Internally this calls:
     *
     * EntityPlayer.openBook(ItemStack)
     *
     * which sends MC|BOpen to the client.
     */
    private boolean openBookNMS(
            Player player,
            ItemStack book) {

        try {

            /*
             * Get CraftPlayer implementation.
             */
            Method getHandle =
                    player.getClass().getMethod("getHandle");

            Object entityPlayer =
                    getHandle.invoke(player);

            /*
             * Find openBook(ItemStack)
             * in EntityPlayer / superclass.
             */
            Method openBookMethod = null;

            Class<?> clazz =
                    entityPlayer.getClass();

            while (clazz != null) {

                try {

                    openBookMethod =
                            clazz.getMethod(
                                    "openBook",
                                    getNMSItemStackClass(
                                            book
                                    )
                            );

                    break;

                } catch (NoSuchMethodException ignored) {

                    clazz = clazz.getSuperclass();
                }
            }

            if (openBookMethod == null) {

                getLogger().warning(
                        "NMS openBook(ItemStack) method was not found."
                );

                return false;
            }

            /*
             * Convert Bukkit ItemStack -> NMS ItemStack.
             */
            Object nmsBook =
                    asNMSCopy(book);

            if (nmsBook == null) {

                getLogger().warning(
                        "Could not convert book to NMS ItemStack."
                );

                return false;
            }

            /*
             * Call EntityPlayer.openBook(nmsBook)
             */
            openBookMethod.invoke(
                    entityPlayer,
                    nmsBook
            );

            return true;

        } catch (Exception e) {

            getLogger().warning(
                    "Failed to open book: "
                            + e.getClass().getSimpleName()
                            + ": "
                            + e.getMessage()
            );

            return false;
        }
    }

    /**
     * Finds the NMS ItemStack class.
     */
    private Class<?> getNMSItemStackClass(
            ItemStack book) {

        try {

            String packageName =
                    Bukkit.getServer()
                            .getClass()
                            .getPackage()
                            .getName();

            String version =
                    packageName.substring(
                            packageName.lastIndexOf('.') + 1
                    );

            return Class.forName(
                    "net.minecraft.server."
                            + version
                            + ".ItemStack"
            );

        } catch (Exception e) {

            return null;
        }
    }

    /**
     * Converts Bukkit ItemStack to NMS ItemStack
     * using CraftItemStack.asNMSCopy().
     */
    private Object asNMSCopy(ItemStack item) {

        try {

            String packageName =
                    Bukkit.getServer()
                            .getClass()
                            .getPackage()
                            .getName();

            String version =
                    packageName.substring(
                            packageName.lastIndexOf('.') + 1
                    );

            Class<?> craftItemStack =
                    Class.forName(
                            "org.bukkit.craftbukkit."
                                    + version
                                    + ".inventory.CraftItemStack"
                    );

            Class<?> nmsItemStack =
                    Class.forName(
                            "net.minecraft.server."
                                    + version
                                    + ".ItemStack"
                    );

            Method method =
                    craftItemStack.getMethod(
                            "asNMSCopy",
                            ItemStack.class
                    );

            Object result =
                    method.invoke(
                            null,
                            item
                    );

            if (!nmsItemStack.isInstance(result)) {
                return null;
            }

            return result;

        } catch (Exception e) {

            getLogger().warning(
                    "Could not convert Bukkit item to NMS: "
                            + e.getMessage()
            );

            return null;
        }
    }

    /**
     * Translate Minecraft color codes.
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
