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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (command.getName().equalsIgnoreCase("pm")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.use")) {
                staff.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "You don't have permission."
                );
                return true;
            }

            if (args.length != 1) {
                staff.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Usage: /pm <player>"
                );
                return true;
            }

            openPunishmentBook(staff, args[0]);

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

            executePunishment(
                    staff,
                    args[0],
                    args[1]
            );

            return true;
        }

        return false;
    }

    /**
     * Creates the book and opens it directly.
     * The book is NEVER placed in the player's inventory.
     */
    private void openPunishmentBook(
            Player player,
            String target) {

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
                createPages(target);

        meta.setPages(pages);

        book.setItemMeta(meta);

        /*
         * Open the book using reflection.
         *
         * This avoids compile-time dependency on
         * net.minecraft.server.v1_8_R3.
         */
        openBookNMS(player, book);
    }

    /**
     * Creates normal readable book pages.
     *
     * Important:
     * We keep the text as normal Minecraft text.
     */
    private List<String> createPages(String target) {

        List<String> pages =
                new ArrayList<String>();

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

                String duration =
                        getConfig().getString(
                                "punishments." + id + ".duration",
                                "Permanent"
                        );

                String line =
                        color("&c» &f" + name) +
                        "\n" +
                        color("&7Duration: &e" + duration) +
                        "\n\n";

                /*
                 * Keep pages at a safe size.
                 */
                if (page.length() + line.length() > 220) {

                    pages.add(page.toString());

                    page =
                            new StringBuilder();
                }

                page.append(line);
            }
        }

        if (page.length() == 0) {

            page.append(
                    color("&7No punishments configured.")
            );
        }

        pages.add(page.toString());

        /*
         * Instruction page.
         */
        StringBuilder info =
                new StringBuilder();

        info.append(
                color("&4&lPunishment System\n\n")
        );

        info.append(
                color("&7Player: &f" + target + "\n\n")
        );

        info.append(
                color("&8Punishments are configured\n")
        );

        info.append(
                color("&8in the plugin configuration.\n\n")
        );

        info.append(
                color("&7Use the configured punishment\n")
        );

        info.append(
                color("&7commands to apply them.")
        );

        pages.add(info.toString());

        return pages;
    }

    /**
     * Opens the written book without giving it to the player.
     *
     * Uses reflection so the project can compile using
     * Spigot API only.
     */
    private void openBookNMS(
            Player player,
            ItemStack book) {

        try {

            String version =
                    Bukkit.getServer()
                            .getClass()
                            .getPackage()
                            .getName()
                            .split("\\.")[3];

            /*
             * CraftPlayer
             */
            Class<?> craftPlayerClass =
                    Class.forName(
                            "org.bukkit.craftbukkit."
                            + version
                            + ".entity.CraftPlayer"
                    );

            Object craftPlayer =
                    craftPlayerClass
                            .cast(player);

            Method getHandle =
                    craftPlayerClass
                            .getMethod("getHandle");

            Object entityPlayer =
                    getHandle.invoke(
                            craftPlayer
                    );

            /*
             * CraftItemStack
             */
            Class<?> craftItemStackClass =
                    Class.forName(
                            "org.bukkit.craftbukkit."
                            + version
                            + ".inventory.CraftItemStack"
                    );

            Method asNMSCopy =
                    craftItemStackClass
                            .getMethod(
                                    "asNMSCopy",
                                    ItemStack.class
                            );

            Object nmsBook =
                    asNMSCopy.invoke(
                            null,
                            book
                    );

            /*
             * PacketPlayOutSetSlot
             *
             * 1.8.8 constructor:
             * int windowId,
             * int slot,
             * ItemStack item
             */
            Class<?> packetClass =
                    Class.forName(
                            "net.minecraft.server."
                            + version
                            + ".PacketPlayOutSetSlot"
                    );

            Constructor<?> packetConstructor =
                    packetClass.getConstructor(
                            int.class,
                            int.class,
                            Class.forName(
                                    "net.minecraft.server."
                                    + version
                                    + ".ItemStack"
                            )
                    );

            /*
             * Use a fake slot so the item is never actually
             * placed permanently in the inventory.
             */
            Object packet =
                    packetConstructor.newInstance(
                            0,
                            player.getInventory()
                                    .getHeldItemSlot() + 36,
                            nmsBook
                    );

            /*
             * PlayerConnection
             */
            Field playerConnectionField =
                    entityPlayer
                            .getClass()
                            .getField(
                                    "playerConnection"
                            );

            Object playerConnection =
                    playerConnectionField
                            .get(entityPlayer);

            Method sendPacket =
                    playerConnection
                            .getClass()
                            .getMethod(
                                    "sendPacket",
                                    Class.forName(
                                            "net.minecraft.server."
                                            + version
                                            + ".Packet"
                                    )
                            );

            sendPacket.invoke(
                    playerConnection,
                    packet
            );

            /*
             * Open book packet.
             *
             * Paper/Spigot 1.8.8 does not expose
             * Player#openBook().
             *
             * PacketPlayOutOpenBook exists in some
             * 1.8.8 server builds, so try it.
             */
            try {

                Class<?> openBookClass =
                        Class.forName(
                                "net.minecraft.server."
                                + version
                                + ".PacketPlayOutOpenBook"
                        );

                Constructor<?> openBookConstructor =
                        openBookClass.getConstructor();

                Object openBookPacket =
                        openBookConstructor.newInstance();

                sendPacket.invoke(
                        playerConnection,
                        openBookPacket
                );

            } catch (Throwable ignored) {

                /*
                 * Some 1.8.8 builds don't have
                 * PacketPlayOutOpenBook.
                 *
                 * Nothing is placed permanently
                 * in the player's inventory.
                 */
            }

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Could not open punishment book: "
                    + throwable.getClass().getSimpleName()
                    + ": "
                    + throwable.getMessage()
            );

            player.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment book."
            );
        }
    }

    /**
     * Executes punishment command from /pmapply.
     */
    private void executePunishment(
            Player staff,
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

        command =
                command
                        .replace(
                                "%player%",
                                targetName
                        )
                        .replace(
                                "%target%",
                                targetName
                        )
                        .replace(
                                "%duration%",
                                duration
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        );

        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        boolean success =
                getServer().dispatchCommand(
                        getServer().getConsoleSender(),
                        command
                );

        if (success) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.GREEN +
                    "Punishment executed: " +
                    ChatColor.WHITE +
                    displayName +
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
                            .replace(
                                    "%player%",
                                    targetName
                            )
                            .replace(
                                    "%punishment%",
                                    ChatColor.stripColor(
                                            color(displayName)
                                    )
                            )
                            .replace(
                                    "%duration%",
                                    duration
                            )
                            .replace(
                                    "%staff%",
                                    staff.getName()
                            );

            getServer().broadcastMessage(
                    color(announcement)
            );

        } else {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Failed to execute punishment command."
            );
        }
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
