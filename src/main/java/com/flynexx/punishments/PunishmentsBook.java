package com.flynexx.punishments;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class PunishmentsBook extends JavaPlugin {

    private final String PREFIX =
            ChatColor.DARK_RED + "PunishmentsBook " + ChatColor.GRAY + "┃ ";

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
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        /*
         * /pm <player>
         */
        if (command.getName().equalsIgnoreCase("pm")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("punishmentsbook.use")) {
                player.sendMessage(
                        ChatColor.RED + "You don't have permission."
                );
                return true;
            }

            if (args.length != 1) {
                player.sendMessage(
                        ChatColor.RED + "Usage: /pm <player>"
                );
                return true;
            }

            openPunishmentBook(player, args[0]);
            return true;
        }

        /*
         * /pmapply <player> <punishment>
         *
         * This command is called by clicking a punishment.
         */
        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.use")) {
                staff.sendMessage(
                        ChatColor.RED + "You don't have permission."
                );
                return true;
            }

            if (args.length != 2) {
                staff.sendMessage(
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
     * Creates the punishment book and opens it directly.
     *
     * The book is NOT added to the player's inventory.
     */
    private void openPunishmentBook(Player player, String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        if (meta == null) {
            player.sendMessage(
                    PREFIX + ChatColor.RED +
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

        meta.setTitle(color(title));
        meta.setAuthor(author);

        /*
         * Create clickable page.
         */
        String page = createClickablePage(target);

        List<String> pages =
                new ArrayList<String>();

        pages.add(page);

        meta.setPages(pages);

        book.setItemMeta(meta);

        /*
         * Open book without adding it to inventory.
         */
        openBookReflectively(player, book);
    }

    /**
     * Creates ONE valid JSON page.
     *
     * Every punishment is clickable.
     */
    private String createClickablePage(String target) {

        StringBuilder json =
                new StringBuilder();

        json.append("{");
        json.append("\"text\":\"\",");
        json.append("\"extra\":[");

        /*
         * Header
         */
        json.append(
                jsonText(
                        "&4&lPUNISHMENTS",
                        null,
                        null
                )
        );

        json.append(
                jsonText(
                        "\n\n&7Player: &f" + target,
                        null,
                        null
                )
        );

        json.append(
                jsonText(
                        "\n\n&8Select a punishment:\n\n",
                        null,
                        null
                )
        );

        boolean first = true;

        /*
         * Punishments
         */
        if (getConfig().isConfigurationSection("punishments")) {

            for (String id :
                    getConfig()
                            .getConfigurationSection("punishments")
                            .getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        id
                );

                String duration = getConfig().getString(
                        "punishments." + id + ".duration",
                        "Permanent"
                );

                if (!first) {
                    json.append(",");
                }

                first = false;

                /*
                 * Command executed when clicked.
                 */
                String command =
                        "/pmapply " +
                        target +
                        " " +
                        id;

                /*
                 * Punishment line
                 */
                json.append(
                        jsonText(
                                "&c» &f" +
                                ChatColor.stripColor(
                                        color(name)
                                ) +
                                " &7[" +
                                ChatColor.stripColor(
                                        color(duration)
                                ) +
                                "]\n",
                                command,
                                "§eClick to execute §f" +
                                ChatColor.stripColor(
                                        color(name)
                                )
                        )
                );
            }
        }

        /*
         * Empty punishments
         */
        if (first) {

            json.append(
                    jsonText(
                            "&7No punishments configured.",
                            null,
                            null
                    )
            );
        }

        json.append("]");
        json.append("}");

        return json.toString();
    }

    /**
     * Creates one JSON text component.
     */
    private String jsonText(String text,
                            String command,
                            String hover) {

        StringBuilder json =
                new StringBuilder();

        String colored =
                color(text);

        /*
         * Convert Bukkit color codes to Minecraft JSON § codes.
         */
        colored = colored.replace("\"", "\\\"");
        colored = colored.replace("\\", "\\\\");
        colored = colored.replace("\n", "\\n");
        colored = colored.replace("\r", "\\r");

        json.append("{");
        json.append("\"text\":\"");
        json.append(colored);
        json.append("\"");

        /*
         * Click event
         */
        if (command != null) {

            String safeCommand =
                    escapeJson(command);

            json.append(
                    ",\"clickEvent\":{" +
                    "\"action\":\"run_command\"," +
                    "\"value\":\"" +
                    safeCommand +
                    "\"}"
            );
        }

        /*
         * Hover event
         */
        if (hover != null) {

            String safeHover =
                    escapeJson(hover);

            json.append(
                    ",\"hoverEvent\":{" +
                    "\"action\":\"show_text\"," +
                    "\"value\":\"" +
                    safeHover +
                    "\"}"
            );
        }

        json.append("}");

        return json.toString();
    }

    /**
     * Opens the written book using reflection.
     *
     * This avoids compile-time NMS imports, so the plugin
     * can still build with the Bukkit/Spigot 1.8.8 API.
     */
    private void openBookReflectively(Player player,
                                      ItemStack book) {

        ItemStack oldItem =
                player.getItemInHand();

        int oldSlot =
                player.getInventory().getHeldItemSlot();

        try {

            /*
             * Put book temporarily in the player's hand.
             */
            player.setItemInHand(book);
            player.updateInventory();

            /*
             * CraftPlayer class.
             */
            Object craftPlayer =
                    player;

            Class<?> craftPlayerClass =
                    Class.forName(
                            "org.bukkit.craftbukkit." +
                            getServerVersion() +
                            ".entity.CraftPlayer"
                    );

            /*
             * getHandle()
             */
            Method getHandle =
                    craftPlayerClass.getMethod(
                            "getHandle"
                    );

            Object entityPlayer =
                    getHandle.invoke(
                            craftPlayer
                    );

            /*
             * Find openBook method dynamically.
             */
            Method openBookMethod = null;

            Method[] methods =
                    entityPlayer
                            .getClass()
                            .getMethods();

            for (Method method : methods) {

                if (!method.getName()
                        .equalsIgnoreCase("openBook")) {
                    continue;
                }

                if (method.getParameterTypes().length == 1) {
                    openBookMethod = method;
                    break;
                }
            }

            if (openBookMethod == null) {

                /*
                 * Some 1.8.8 builds don't expose
                 * openBook directly.
                 *
                 * Try Bukkit's player method
                 * reflectively as a fallback.
                 */
                try {

                    Method method =
                            player.getClass()
                                    .getMethod(
                                            "openBook",
                                            ItemStack.class
                                    );

                    method.invoke(
                            player,
                            book
                    );

                    restoreItemLater(
                            player,
                            oldItem,
                            oldSlot
                    );

                    return;

                } catch (Exception ignored) {
                }

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "This server build does not support direct book opening."
                );

                restoreItemLater(
                        player,
                        oldItem,
                        oldSlot
                );

                return;
            }

            /*
             * Convert Bukkit ItemStack to NMS ItemStack.
             */
            Class<?> craftItemStackClass =
                    Class.forName(
                            "org.bukkit.craftbukkit." +
                            getServerVersion() +
                            ".inventory.CraftItemStack"
                    );

            Method asNMSCopy =
                    craftItemStackClass.getMethod(
                            "asNMSCopy",
                            ItemStack.class
                    );

            Object nmsBook =
                    asNMSCopy.invoke(
                            null,
                            book
                    );

            /*
             * Open the book.
             */
            openBookMethod.invoke(
                    entityPlayer,
                    nmsBook
            );

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Could not open punishment book: "
                    + throwable.getClass().getSimpleName()
                    + ": "
                    + throwable.getMessage()
            );

            /*
             * Last fallback:
             * try Bukkit's method reflectively.
             */
            try {

                Method method =
                        player.getClass()
                                .getMethod(
                                        "openBook",
                                        ItemStack.class
                                );

                method.invoke(
                        player,
                        book
                );

            } catch (Throwable ignored) {
            }

        }

        /*
         * Restore the original item.
         */
        restoreItemLater(
                player,
                oldItem,
                oldSlot
        );
    }

    /**
     * Restores the player's previous item after
     * the book has been opened.
     */
    private void restoreItemLater(final Player player,
                                  final ItemStack oldItem,
                                  final int oldSlot) {

        getServer()
                .getScheduler()
                .runTaskLater(
                        this,
                        new Runnable() {

                            @Override
                            public void run() {

                                if (!player.isOnline()) {
                                    return;
                                }

                                player.getInventory()
                                        .setHeldItemSlot(
                                                oldSlot
                                        );

                                player.setItemInHand(
                                        oldItem
                                );

                                player.updateInventory();
                            }
                        },
                        2L
                );
    }

    /**
     * Gets the CraftBukkit version dynamically.
     *
     * Example:
     * v1_8_R3
     */
    private String getServerVersion() {

        String name =
                getServer()
                        .getClass()
                        .getPackage()
                        .getName();

        return name.substring(
                name.lastIndexOf('.') + 1
        );
    }

    /**
     * Executes punishment command from config.
     */
    private void executePunishment(Player staff,
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

        /*
         * Placeholders
         */
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

        /*
         * Remove /
         */
        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        boolean success =
                getServer()
                        .dispatchCommand(
                                getServer()
                                        .getConsoleSender(),
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

            /*
             * Announcement
             */
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

            getServer()
                    .broadcastMessage(
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

    private String escapeJson(String text) {

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
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        "\\r"
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
