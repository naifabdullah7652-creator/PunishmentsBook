package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutOpenBook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
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

        getLogger().info("PunishmentsBook 2.0.0 enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (command.getName().equalsIgnoreCase("pm")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("punishmentsbook.use")) {
                player.sendMessage(
                        PREFIX + ChatColor.RED +
                        "You don't have permission."
                );
                return true;
            }

            if (args.length != 1) {
                player.sendMessage(
                        PREFIX + ChatColor.RED +
                        "Usage: /pm <player>"
                );
                return true;
            }

            openPunishmentBook(player, args[0]);
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
     * Opens the book directly.
     *
     * IMPORTANT:
     * The book is NEVER placed in the player's inventory.
     */
    private void openPunishmentBook(Player player, String target) {

        List<String> pages = new ArrayList<String>();

        /*
         * PAGE 1
         */
        pages.add(createMainPage(target));

        /*
         * PAGE 2+
         */
        ConfigurationSection section =
                getConfig().getConfigurationSection("punishments");

        if (section != null) {

            for (String id : section.getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        id
                );

                String duration = getConfig().getString(
                        "punishments." + id + ".duration",
                        "Permanent"
                );

                String reason = getConfig().getString(
                        "punishments." + id + ".reason",
                        name
                );

                String command = getConfig().getString(
                        "punishments." + id + ".command",
                        ""
                );

                pages.add(
                        createPunishmentPage(
                                target,
                                id,
                                name,
                                duration,
                                reason,
                                command
                        )
                );
            }
        }

        /*
         * Store the book in the player's temporary held item
         * only long enough for the server packet to open it.
         *
         * It is NOT added to inventory.
         */
        openBookWithNMS(player, pages);
    }

    private String createMainPage(String target) {

        StringBuilder page = new StringBuilder();

        page.append("§4§lPUNISHMENTS\n\n");
        page.append("§7Player: §f")
                .append(escape(target))
                .append("\n\n");

        page.append("§8Select a punishment:\n\n");

        ConfigurationSection section =
                getConfig().getConfigurationSection("punishments");

        if (section != null) {

            for (String id : section.getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        id
                );

                String duration = getConfig().getString(
                        "punishments." + id + ".duration",
                        "Permanent"
                );

                page.append("§c» §f")
                        .append(name)
                        .append("\n");

                page.append("§7Duration: §e")
                        .append(duration)
                        .append("\n\n");
            }
        }

        return page.toString();
    }

    /*
     * This page uses Minecraft 1.8 book JSON.
     *
     * Clicking the punishment executes:
     *
     * /pmapply <player> <id>
     */
    private String createPunishmentPage(String target,
                                        String id,
                                        String name,
                                        String duration,
                                        String reason,
                                        String punishmentCommand) {

        StringBuilder json = new StringBuilder();

        json.append("{\"text\":\"\"");

        json.append(",\"extra\":[");

        json.append(component(
                "§4§lPUNISHMENT\n\n",
                null,
                null
        ));

        json.append(",");

        json.append(component(
                "§7Player: §f" + escapeJson(target) + "\n",
                null,
                null
        ));

        json.append(",");

        json.append(component(
                "§7Punishment: §c" +
                escapeJson(name) +
                "\n",
                null,
                null
        ));

        json.append(",");

        json.append(component(
                "§7Duration: §e" +
                escapeJson(duration) +
                "\n",
                null,
                null
        ));

        json.append(",");

        json.append(component(
                "§7Reason: §f" +
                escapeJson(reason) +
                "\n\n",
                null,
                null
        ));

        String command =
                "/pmapply " +
                target +
                " " +
                id;

        json.append(",");

        json.append(component(
                "§a§l[ CLICK TO EXECUTE ]",
                command,
                "§eClick to execute this punishment."
        ));

        json.append("]}");

        return json.toString();
    }

    private String component(String text,
                             String command,
                             String hover) {

        StringBuilder result = new StringBuilder();

        result.append("{");
        result.append("\"text\":\"")
                .append(escapeJson(text))
                .append("\"");

        if (command != null) {

            result.append(
                    ",\"clickEvent\":{" +
                    "\"action\":\"run_command\"," +
                    "\"value\":\"" +
                    escapeJson(command) +
                    "\"}"
            );
        }

        if (hover != null) {

            result.append(
                    ",\"hoverEvent\":{" +
                    "\"action\":\"show_text\"," +
                    "\"value\":\"" +
                    escapeJson(hover) +
                    "\"}"
            );
        }

        result.append("}");

        return result.toString();
    }

    /*
     * Opens the written book directly using the 1.8.8 NMS packet.
     *
     * No BungeeCord Chat.
     * No inventory item.
     */
    private void openBookWithNMS(Player player,
                                 List<String> pages) {

        try {

            EntityPlayer entityPlayer =
                    ((CraftPlayer) player).getHandle();

            /*
             * Save current item.
             */
            org.bukkit.inventory.ItemStack oldItem =
                    player.getItemInHand();

            org.bukkit.inventory.ItemStack book =
                    new org.bukkit.inventory.ItemStack(
                            org.bukkit.Material.WRITTEN_BOOK
                    );

            org.bukkit.inventory.meta.BookMeta meta =
                    (org.bukkit.inventory.meta.BookMeta)
                            book.getItemMeta();

            meta.setTitle(
                    color(
                            getConfig().getString(
                                    "settings.book-title",
                                    "&4Punishments"
                            )
                    )
            );

            meta.setAuthor(
                    getConfig().getString(
                            "settings.book-author",
                            "FlyNeXx"
                    )
            );

            meta.setPages(pages);

            book.setItemMeta(meta);

            /*
             * Put the temporary book in hand.
             *
             * The packet opens it immediately.
             * Afterwards restore the original item.
             */
            player.setItemInHand(book);
            player.updateInventory();

            PacketPlayOutOpenBook packet =
                    new PacketPlayOutOpenBook(
                            player.getInventory().getHeldItemSlot()
                    );

            entityPlayer.playerConnection.sendPacket(packet);

            Bukkit.getScheduler().runTaskLater(
                    this,
                    new Runnable() {
                        @Override
                        public void run() {

                            if (player.isOnline()) {

                                player.setItemInHand(oldItem);
                                player.updateInventory();
                            }
                        }
                    },
                    1L
            );

        } catch (Exception e) {

            getLogger().warning(
                    "Could not open punishment book: " +
                    e.getClass().getSimpleName() +
                    ": " +
                    e.getMessage()
            );

            player.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment book."
            );
        }
    }

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

        String name =
                getConfig().getString(
                        path + ".name",
                        punishmentId
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

        if (command == null ||
                command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured for " +
                    ChatColor.WHITE +
                    name
            );

            return;
        }

        command = command
                .replace("%player%", targetName)
                .replace("%target%", targetName)
                .replace("%duration%", duration)
                .replace("%reason%", reason)
                .replace("%staff%", staff.getName());

        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        boolean success =
                getServer().dispatchCommand(
                        getServer().getConsoleSender(),
                        command
                );

        if (!success) {

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
                " → " +
                ChatColor.WHITE +
                targetName
        );

        String announcement =
                getConfig().getString(
                        "settings.announcement",
                        "&4&lPunishment &8┃ &f%player% " +
                        "&7was punished with &c%punishment% " +
                        "&7(&e%duration%&7)"
                );

        announcement = announcement
                .replace("%player%", targetName)
                .replace("%punishment%", name)
                .replace("%duration%", duration)
                .replace("%reason%", reason)
                .replace("%staff%", staff.getName());

        Bukkit.broadcastMessage(
                color(announcement)
        );
    }

    private String escape(String text) {

        if (text == null) {
            return "";
        }

        return text;
    }

    private String escapeJson(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
