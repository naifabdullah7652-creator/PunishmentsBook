package com.flynexx.punishments;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class PunishmentsBook extends JavaPlugin {

    private final String PREFIX = ChatColor.DARK_RED + "PunishmentsBook " + ChatColor.GRAY + "┃ ";

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getCommand("pm").setExecutor(this);

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
                player.sendMessage(ChatColor.RED + "You don't have permission.");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /pm <player>");
                return true;
            }

            giveBook(player, args[0]);
            return true;
        }

        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.use")) {
                staff.sendMessage(ChatColor.RED + "You don't have permission.");
                return true;
            }

            if (args.length != 2) {
                staff.sendMessage(ChatColor.RED + "Usage: /pmapply <player> <punishment>");
                return true;
            }

            executePunishment(staff, args[0], args[1]);
            return true;
        }

        return false;
    }

    private void giveBook(Player player, String target) {

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta = (BookMeta) book.getItemMeta();

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

        /*
         * PAGE 1
         */
        StringBuilder firstPage = new StringBuilder();

        firstPage.append(
                color("&4&lPUNISHMENTS\n\n")
        );

        firstPage.append(
                color("&7Player: &f" + target + "\n\n")
        );

        firstPage.append(
                color("&8Select a punishment:\n\n")
        );

        /*
         * PUNISHMENTS
         */
        if (getConfig().isConfigurationSection("punishments")) {

            for (String id :
                    getConfig().getConfigurationSection("punishments").getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        id
                );

                String duration = getConfig().getString(
                        "punishments." + id + ".duration",
                        "Permanent"
                );

                String line =
                        color("&c» &f" + name)
                        + "\n"
                        + color("&7Duration: &e" + duration)
                        + "\n\n";

                firstPage.append(line);
            }
        }

        /*
         * IMPORTANT:
         *
         * BookMeta itself does not provide click events in 1.8.8.
         * Therefore we use Minecraft's native book JSON format.
         *
         * The clickable page is created below.
         */

        String jsonPage = createClickablePage(target);

        pages.add(jsonPage);

        /*
         * INFORMATION PAGE
         */
        StringBuilder info = new StringBuilder();

        info.append(color("&4&lPUNISHMENTS\n\n"));
        info.append(color("&7Player: &f" + target + "\n\n"));
        info.append(color("&8Click a punishment\n"));
        info.append(color("&8below to execute it.\n\n"));

        if (getConfig().isConfigurationSection("punishments")) {

            for (String id :
                    getConfig().getConfigurationSection("punishments").getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        id
                );

                String duration = getConfig().getString(
                        "punishments." + id + ".duration",
                        "Permanent"
                );

                info.append(color("&c» &f" + name + "\n"));
                info.append(color("&7Duration: &e" + duration + "\n\n"));
            }
        }

        pages.add(info.toString());

        meta.setPages(pages);

        book.setItemMeta(meta);

        player.getInventory().addItem(book);

        player.sendMessage(
                PREFIX +
                ChatColor.GREEN +
                "Punishment book given for " +
                ChatColor.WHITE +
                target +
                ChatColor.GREEN +
                "."
        );
    }

    /*
     * Creates a Minecraft book page using JSON.
     *
     * Every punishment becomes a clickable button.
     */
    private String createClickablePage(String target) {

        StringBuilder json = new StringBuilder();

        json.append("{\"text\":\"\"");

        if (getConfig().isConfigurationSection("punishments")) {

            for (String id :
                    getConfig().getConfigurationSection("punishments").getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        id
                );

                String duration = getConfig().getString(
                        "punishments." + id + ".duration",
                        "Permanent"
                );

                String safeName = escapeJson(
                        ChatColor.stripColor(color(name))
                );

                String safeDuration = escapeJson(
                        ChatColor.stripColor(color(duration))
                );

                String command =
                        "/pmapply "
                        + target
                        + " "
                        + id;

                json.append(",\"extra\":[");

                json.append(
                        "{\"text\":\"§c» §f"
                        + safeName
                        + " §7["
                        + safeDuration
                        + "]\\n\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\""
                        + escapeJson(command)
                        + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"text\":\"§eClick to execute punishment\"}}}"
                );

                json.append("]");
            }
        }

        json.append("}");

        /*
         * Minecraft expects one JSON object per page.
         */
        return json.toString();
    }

    private void executePunishment(Player staff,
                                   String targetName,
                                   String punishmentId) {

        if (!getConfig().isConfigurationSection(
                "punishments." + punishmentId)) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Unknown punishment."
            );

            return;
        }

        String path = "punishments." + punishmentId;

        String displayName = getConfig().getString(
                path + ".name",
                punishmentId
        );

        String duration = getConfig().getString(
                path + ".duration",
                "Permanent"
        );

        String command = getConfig().getString(
                path + ".command",
                ""
        );

        if (command == null || command.trim().isEmpty()) {

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
         * Replace placeholders.
         */
        command = command
                .replace("%player%", targetName)
                .replace("%target%", targetName)
                .replace("%duration%", duration)
                .replace("%staff%", staff.getName());

        /*
         * Remove starting slash because Bukkit dispatchCommand
         * expects the command without it.
         */
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        boolean success = getServer()
                .dispatchCommand(
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

            /*
             * Announcement
             */
            String announcement = getConfig().getString(
                    "settings.announcement",
                    "&4&lPunishment &8┃ &f%player% &7was punished with &c%punishment% &7(&e%duration%&7)"
            );

            announcement = announcement
                    .replace("%player%", targetName)
                    .replace("%punishment%", ChatColor.stripColor(color(displayName)))
                    .replace("%duration%", duration)
                    .replace("%staff%", staff.getName());

            getServer().broadcastMessage(color(announcement));

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
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String color(String text) {

        if (text == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
