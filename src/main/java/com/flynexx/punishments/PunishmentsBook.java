package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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

        getLogger().info("PunishmentsBook 2.0.0 enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (command.getName().equalsIgnoreCase("pm")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(
                        ChatColor.RED + "Players only."
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

            openPunishmentBook(
                    player,
                    args[0]
            );

            return true;
        }

        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(
                        ChatColor.RED + "Players only."
                );
                return true;
            }

            Player staff = (Player) sender;

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
     * Creates and opens the punishment book.
     *
     * The book is NEVER permanently added to inventory.
     */
    private void openPunishmentBook(Player player,
                                    String target) {

        List<String> pages =
                new ArrayList<String>();

        /*
         * Page 1
         */
        pages.add(
                createMainPage(target)
        );

        /*
         * Each punishment gets its own page.
         */
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

                String reason =
                        getConfig().getString(
                                "punishments." + id + ".reason",
                                name
                        );

                pages.add(
                        createPunishmentPage(
                                target,
                                id,
                                name,
                                duration,
                                reason
                        )
                );
            }
        }

        openBook(player, pages);
    }

    /**
     * Main page.
     */
    private String createMainPage(String target) {

        StringBuilder page =
                new StringBuilder();

        page.append("§4§lPUNISHMENTS\n\n");

        page.append("§7Player: §f")
                .append(target)
                .append("\n\n");

        page.append("§8Available punishments:\n\n");

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section != null) {

            int number = 1;

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

                page.append("§c")
                        .append(number)
                        .append(". §f")
                        .append(name)
                        .append("\n");

                page.append("§7Duration: §e")
                        .append(duration)
                        .append("\n\n");

                number++;
            }
        }

        return page.toString();
    }

    /**
     * Punishment page.
     *
     * Clicking the button executes:
     *
     * /pmapply <player> <id>
     */
    private String createPunishmentPage(
            String target,
            String id,
            String name,
            String duration,
            String reason) {

        StringBuilder page =
                new StringBuilder();

        page.append("§4§lPUNISHMENT\n\n");

        page.append("§7Player:\n");
        page.append("§f")
                .append(target)
                .append("\n\n");

        page.append("§7Punishment:\n");
        page.append("§c")
                .append(name)
                .append("\n\n");

        page.append("§7Duration:\n");
        page.append("§e")
                .append(duration)
                .append("\n\n");

        page.append("§7Reason:\n");
        page.append("§f")
                .append(reason)
                .append("\n\n");

        /*
         * The command is placed inside the page
         * as a clickable JSON component.
         */
        page.append(
                createClickableButton(
                        target,
                        id
                )
        );

        return page.toString();
    }

    /**
     * Creates a clickable JSON button.
     *
     * NOTE:
     * Minecraft 1.8 written books support
     * clickEvent/run_command.
     */
    private String createClickableButton(
            String target,
            String id) {

        String command =
                "/pmapply " +
                target +
                " " +
                id;

        StringBuilder json =
                new StringBuilder();

        json.append("{\"text\":\"\"");

        json.append(",\"extra\":[");

        json.append("{");

        json.append("\"text\":\"");
        json.append("§a§l[ CLICK TO EXECUTE ]");
        json.append("\"");

        json.append(",");

        json.append("\"clickEvent\":{");
        json.append("\"action\":\"run_command\",");
        json.append("\"value\":\"");
        json.append(escapeJson(command));
        json.append("\"");
        json.append("}");

        json.append(",");

        json.append("\"hoverEvent\":{");
        json.append("\"action\":\"show_text\",");
        json.append("\"value\":\"");
        json.append("§eClick to execute this punishment");
        json.append("\"");
        json.append("}");

        json.append("}");

        json.append("]}");

        return json.toString();
    }

    /**
     * Opens a written book directly in Minecraft 1.8.8.
     *
     * IMPORTANT:
     *
     * EntityPlayer.openBook(ItemStack)
     * exists in NMS 1.8_R3.
     *
     * We temporarily put the book in the player's
     * hand because the 1.8 client expects a written
     * book there, then restore the original item.
     */
    private void openBook(final Player player,
                          List<String> pages) {

        final int slot =
                player.getInventory()
                        .getHeldItemSlot();

        final ItemStack oldItem =
                player.getInventory()
                        .getItem(slot);

        try {

            /*
             * Create written book.
             */
            ItemStack book =
                    new ItemStack(
                            Material.WRITTEN_BOOK
                    );

            BookMeta meta =
                    (BookMeta)
                            book.getItemMeta();

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

            meta.setTitle(
                    color(title)
            );

            meta.setAuthor(
                    author
            );

            meta.setPages(
                    pages
            );

            book.setItemMeta(
                    meta
            );

            /*
             * Temporarily put the book
             * in the currently selected slot.
             */
            player.getInventory()
                    .setItem(
                            slot,
                            book
                    );

            player.updateInventory();

            /*
             * Get NMS player.
             */
            CraftPlayer craftPlayer =
                    (CraftPlayer) player;

            EntityPlayer entityPlayer =
                    craftPlayer.getHandle();

            /*
             * THIS is the correct 1.8.8 method.
             *
             * No PacketPlayOutOpenBook.
             */
            entityPlayer.openBook(
                    net.minecraft.server.v1_8_R3.CraftItemStack
                            .asNMSCopy(book)
            );

            /*
             * Restore original item shortly after
             * opening the book.
             *
             * 1 tick is safer than restoring
             * immediately on old 1.8 clients.
             */
            Bukkit.getScheduler().runTaskLater(
                    this,
                    new Runnable() {

                        @Override
                        public void run() {

                            if (!player.isOnline()) {
                                return;
                            }

                            player.getInventory()
                                    .setItem(
                                            slot,
                                            oldItem
                                    );

                            player.updateInventory();
                        }

                    },
                    2L
            );

        } catch (Exception e) {

            /*
             * If something goes wrong,
             * ALWAYS restore the old item.
             */
            player.getInventory()
                    .setItem(
                            slot,
                            oldItem
                    );

            player.updateInventory();

            getLogger().warning(
                    "Could not open punishment book: "
                    + e.getClass().getSimpleName()
                    + ": "
                    + e.getMessage()
            );

            player.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment book."
            );
        }
    }

    /**
     * Executes the configured punishment
     * from the console.
     */
    private void executePunishment(
            Player staff,
            String targetName,
            String punishmentId) {

        String path =
                "punishments." +
                punishmentId;

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

        String punishmentCommand =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        if (punishmentCommand == null ||
                punishmentCommand.trim().isEmpty()) {

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
         * Replace placeholders.
         */
        punishmentCommand =
                punishmentCommand
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
                                "%reason%",
                                reason
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        );

        /*
         * Bukkit console dispatch does not
         * need the leading slash.
         */
        if (punishmentCommand
                .startsWith("/")) {

            punishmentCommand =
                    punishmentCommand.substring(1);
        }

        boolean success =
                getServer()
                        .dispatchCommand(
                                getServer()
                                        .getConsoleSender(),
                                punishmentCommand
                        );

        if (!success) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Failed to execute punishment."
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
                name +
                ChatColor.GRAY +
                " → " +
                ChatColor.WHITE +
                targetName
        );

        /*
         * Server announcement.
         */
        String announcement =
                getConfig().getString(
                        "settings.announcement",
                        "&4&lPunishment &8┃ &f%player% " +
                        "&7was punished with &c%punishment% " +
                        "&7(&e%duration%&7)"
                );

        announcement =
                announcement
                        .replace(
                                "%player%",
                                targetName
                        )
                        .replace(
                                "%punishment%",
                                name
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
