package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.ItemStack as NmsItemStack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

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

    private void openPunishmentBook(Player player, String target) {

        org.bukkit.inventory.ItemStack oldItem =
                player.getItemInHand();

        org.bukkit.inventory.ItemStack book =
                createBook(target);

        try {

            /*
             * Put the temporary book in the player's hand.
             * Minecraft 1.8.8 requires the written book to be
             * in the hand when EntityHuman.openBook() is called.
             */
            player.setItemInHand(book);
            player.updateInventory();

            CraftHumanEntity craftPlayer =
                    (CraftHumanEntity) player;

            EntityHuman entityHuman =
                    craftPlayer.getHandle();

            NmsItemStack nmsBook =
                    CraftItemStack.asNMSCopy(book);

            /*
             * Correct 1.8.8 method.
             *
             * DO NOT use PacketPlayOutOpenBook.
             */
            entityHuman.openBook(nmsBook);

        } catch (Throwable throwable) {

            getLogger().severe(
                    "Could not open punishment book: "
                    + throwable.getClass().getSimpleName()
                    + ": "
                    + throwable.getMessage()
            );

            player.sendMessage(
                    PREFIX + ChatColor.RED +
                    "Could not open punishment book."
            );

        } finally {

            /*
             * Remove the book from the inventory immediately.
             * It is only temporary.
             */
            player.setItemInHand(oldItem);
            player.updateInventory();
        }
    }

    private org.bukkit.inventory.ItemStack createBook(
            String target) {

        org.bukkit.inventory.ItemStack book =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle("Punishments");
        meta.setAuthor("FlyNeXx");

        List<String> pages =
                new ArrayList<String>();

        /*
         * The first page contains the actual clickable JSON.
         */
        pages.add(createClickablePage(target));

        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    private String createClickablePage(String target) {

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section == null) {
            return "{\"text\":\"No punishments configured.\"}";
        }

        StringBuilder json =
                new StringBuilder();

        json.append("{\"text\":\"\",\"extra\":[");

        boolean first = true;

        for (String id : section.getKeys(false)) {

            String name =
                    section.getString(
                            id + ".name",
                            id
                    );

            /*
             * Remove ALL Bukkit color codes.
             */
            name = ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes(
                            '&',
                            name
                    )
            );

            /*
             * Remove characters that could break JSON.
             */
            name = escapeJson(name);

            /*
             * The command is configured in config.yml.
             */
            String command =
                    section.getString(
                            id + ".command",
                            ""
                    );

            command = command
                    .replace("%player%", target)
                    .replace("%target%", target);

            if (command.startsWith("/")) {
                command = command.substring(1);
            }

            command = escapeJson(command);

            if (!first) {
                json.append(",");
            }

            first = false;

            /*
             * BLACK text.
             *
             * Click:
             * /jail <player> <time> <reason>
             */
            json.append("{")
                    .append("\"text\":\"")
                    .append("\\u2022 ")
                    .append(name)
                    .append("\\n")
                    .append("\",")

                    .append("\"color\":\"black\",")

                    .append("\"clickEvent\":{")
                    .append("\"action\":\"run_command\",")
                    .append("\"value\":\"/")
                    .append(command)
                    .append("\"}")

                    .append("}");
        }

        json.append("]}");

        return json.toString();
    }

    private String escapeJson(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", " ");
    }
}
