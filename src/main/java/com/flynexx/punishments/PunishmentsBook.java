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
                        PREFIX + ChatColor.RED +
                        "You don't have permission."
                );
                return true;
            }

            if (args.length != 1) {
                staff.sendMessage(
                        PREFIX + ChatColor.RED +
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
                        PREFIX + ChatColor.RED +
                        "You don't have permission."
                );
                return true;
            }

            if (args.length != 2) {
                staff.sendMessage(
                        PREFIX + ChatColor.RED +
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

    /*
     * Opens the written book without leaving it in the inventory.
     */
    private void openPunishmentBook(
            final Player player,
            String target) {

        final ItemStack previousItem =
                player.getItemInHand();

        final ItemStack book =
                createBook(target);

        /*
         * Put the book temporarily in the hand.
         *
         * This is necessary for Minecraft 1.8.8
         * to display a written book.
         */
        player.setItemInHand(book);
        player.updateInventory();

        boolean opened =
                openBookUsingReflection(player, book);

        if (!opened) {

            player.setItemInHand(previousItem);
            player.updateInventory();

            player.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment book."
            );

            getLogger().warning(
                    "Unable to open written book for " +
                    player.getName()
            );

            return;
        }

        /*
         * Remove the temporary book from the inventory
         * after Minecraft has opened the book GUI.
         */
        Bukkit.getScheduler().runTaskLater(
                this,
                new Runnable() {
                    @Override
                    public void run() {

                        if (!player.isOnline()) {
                            return;
                        }

                        player.setItemInHand(previousItem);
                        player.updateInventory();
                    }
                },
                1L
        );
    }

    /*
     * Uses reflection so the plugin does NOT need
     * CraftBukkit/NMS classes during Maven compilation.
     *
     * This avoids:
     *
     * net.minecraft.server.v1_8_R3 does not exist
     *
     * and:
     *
     * CraftItemStack does not exist
     */
    private boolean openBookUsingReflection(
            Player player,
            ItemStack book) {

        try {

            /*
             * CraftPlayer#getHandle()
             */
            Method getHandle =
                    player.getClass().getMethod("getHandle");

            Object entityPlayer =
                    getHandle.invoke(player);

            /*
             * Load CraftItemStack dynamically.
             */
            Class<?> craftItemStackClass =
                    Class.forName(
                            "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
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
             * Search EntityHuman/EntityPlayer for:
             *
             * openBook(ItemStack)
             */
            Method[] methods =
                    entityPlayer.getClass().getMethods();

            for (Method method : methods) {

                if (!method.getName().equals("openBook")) {
                    continue;
                }

                Class<?>[] parameters =
                        method.getParameterTypes();

                if (parameters.length != 1) {
                    continue;
                }

                if (!parameters[0].getName().equals(
                        "net.minecraft.server.v1_8_R3.ItemStack")) {
                    continue;
                }

                method.invoke(
                        entityPlayer,
                        nmsBook
                );

                return true;
            }

        } catch (Throwable exception) {

            getLogger().warning(
                    "Book opening failed: " +
                    exception.getClass().getSimpleName() +
                    " - " +
                    exception.getMessage()
            );
        }

        return false;
    }

    /*
     * Creates the actual written book.
     */
    private ItemStack createBook(String target) {

        ItemStack book =
                new ItemStack(
                        Material.WRITTEN_BOOK
                );

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        String title =
                getConfig().getString(
                        "settings.book-title",
                        "&4Punishments"
                );

        String author =
                getConfig().getString(
                        "settings.book-author",
                        "FlyNeXx"
                );

        meta.setTitle(color(title));
        meta.setAuthor(author);

        List<String> pages =
                new ArrayList<String>();

        /*
         * PAGE 1
         *
         * Clickable punishments.
         */
        pages.add(
                createClickablePage(target)
        );

        /*
         * PAGE 2
         *
         * Normal information page.
         */
        pages.add(
                createInformationPage(target)
        );

        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    /*
     * Creates the clickable page.
     */
    private String createClickablePage(
            String target) {

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section == null) {

            return "{\"text\":\"No punishments configured.\"}";
        }

        StringBuilder json =
                new StringBuilder();

        json.append(
                "{\"text\":\"§4§lPUNISHMENTS\\n\\n§7Player: §f"
        );

        json.append(
                escapeJson(target)
        );

        json.append(
                "\\n\\n§8Select a punishment:\\n\\n\",\"extra\":["
        );

        boolean first = true;

        for (String id :
                section.getKeys(false)) {

            String name =
                    section.getString(
                            id + ".name",
                            id
                    );

            String duration =
                    section.getString(
                            id + ".duration",
                            "Permanent"
                    );

            /*
             * Remove Bukkit color formatting from
             * JSON display text, then use § manually.
             */
            name =
                    ChatColor.stripColor(
                            color(name)
                    );

            duration =
                    ChatColor.stripColor(
                            color(duration)
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

            String buttonText =
                    "§c» §f" +
                    name +
                    " §7[" +
                    duration +
                    "]§r\\n";

            String hoverText =
                    "§eClick to execute §f" +
                    name;

            json.append("{");

            json.append(
                    "\"text\":\""
            );

            json.append(
                    escapeJson(buttonText)
            );

            json.append(
                    "\","
            );

            /*
             * CLICK EVENT
             */
            json.append(
                    "\"clickEvent\":{"
            );

            json.append(
                    "\"action\":\"run_command\","
            );

            json.append(
                    "\"value\":\""
            );

            json.append(
                    escapeJson(command)
            );

            json.append(
                    "\"},"
            );

            /*
             * HOVER EVENT
             */
            json.append(
                    "\"hoverEvent\":{"
            );

            json.append(
                    "\"action\":\"show_text\","
            );

            json.append(
                    "\"value\":\""
            );

            json.append(
                    escapeJson(hoverText)
            );

            json.append(
                    "\""
            );

            json.append(
                    "}"
            );

            json.append(
                    "}"
            );
        }

        json.append(
                "]}"
        );

        return json.toString();
    }

    /*
     * Information page.
     */
    private String createInformationPage(
            String target) {

        StringBuilder page =
                new StringBuilder();

        page.append(
                color(
                        "&4&lPUNISHMENTS\n\n"
                )
        );

        page.append(
                color(
                        "&7Player: &f" +
                        target +
                        "\n\n"
                )
        );

        page.append(
                color(
                        "&8Click a punishment\n" +
                        "&8to execute it.\n\n"
                )
        );

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section != null) {

            for (String id :
                    section.getKeys(false)) {

                String name =
                        section.getString(
                                id + ".name",
                                id
                        );

                String duration =
                        section.getString(
                                id + ".duration",
                                "Permanent"
                        );

                page.append(
                        color(
                                "&c» &f" +
                                name +
                                "\n"
                        )
                );

                page.append(
                        color(
                                "&7Duration: &e" +
                                duration +
                                "\n\n"
                        )
                );
            }
        }

        return page.toString();
    }

    /*
     * Executes the configured punishment command.
     */
    private void executePunishment(
            Player staff,
            String target,
            String id) {

        String path =
                "punishments." + id;

        if (!getConfig().isConfigurationSection(
                path)) {

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
                        "Permanent"
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
                    "No command configured for " +
                    ChatColor.WHITE +
                    name
            );

            return;
        }

        /*
         * Placeholder replacement.
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
                                "%time%",
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
         * Bukkit commands do not need
         * a starting slash.
         */
        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        boolean success =
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        command
                );

        if (!success) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Failed to execute punishment command."
            );

            return;
        }

        /*
         * Staff confirmation.
         */
        staff.sendMessage(
                PREFIX +
                ChatColor.GREEN +
                "Punishment executed: " +
                ChatColor.WHITE +
                color(name) +
                ChatColor.GRAY +
                " → " +
                ChatColor.WHITE +
                target
        );

        /*
         * Broadcast.
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
                                target
                        )
                        .replace(
                                "%punishment%",
                                ChatColor.stripColor(
                                        color(name)
                                )
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

        Bukkit.broadcastMessage(
                color(announcement)
        );
    }

    /*
     * JSON escaping.
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
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\t",
                        "\\t"
                );
    }

    /*
     * Bukkit color codes.
     */
    private String color(
            String text) {

        if (text == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes(
                '&',
                text
        );
    }
}
