package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityHuman;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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

        Player player = (Player) sender;

        if (!player.hasPermission("punishmentsbook.use")) {
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

        openPunishmentBook(player, args[0]);

        return true;
    }

    /*
     * Opens the written book without leaving it
     * permanently inside the player's inventory.
     */
    private void openPunishmentBook(
            Player player,
            String target) {

        ItemStack oldItem = player.getItemInHand();

        ItemStack book = createBook(target);

        try {

            /*
             * 1.8.8 requires the book to be in the hand.
             */
            player.setItemInHand(book);
            player.updateInventory();

            /*
             * Get the NMS player.
             */
            CraftHumanEntity craftPlayer =
                    (CraftHumanEntity) player;

            EntityHuman entityHuman =
                    craftPlayer.getHandle();

            /*
             * Convert Bukkit ItemStack -> NMS ItemStack.
             */
            net.minecraft.server.v1_8_R3.ItemStack nmsBook =
                    CraftItemStack.asNMSCopy(book);

            /*
             * Correct 1.8.8 method.
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
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment book."
            );

        } finally {

            /*
             * Remove temporary book from inventory.
             */
            player.setItemInHand(oldItem);
            player.updateInventory();
        }
    }

    /*
     * Creates the written book.
     */
    private ItemStack createBook(String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle("Punishments");
        meta.setAuthor("FlyNeXx");

        List<String> pages =
                new ArrayList<String>();

        /*
         * Page 1 = clickable punishments.
         */
        pages.add(createClickablePage(target));

        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    /*
     * Creates the punishment page.
     *
     * The names are:
     * - Black
     * - Short
     * - No duration
     * - No reason
     *
     * Clicking executes the configured command.
     */
    private String createClickablePage(String target) {

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section == null) {
            return "No punishments configured.";
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
             * Remove Bukkit color codes.
             */
            name = ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes(
                            '&',
                            name
                    )
            );

            /*
             * Command from config.
             */
            String command =
                    section.getString(
                            id + ".command",
                            ""
                    );

            command =
                    command.replace(
                            "%player%",
                            target
                    );

            command =
                    command.replace(
                            "%target%",
                            target
                    );

            if (command.startsWith("/")) {
                command =
                        command.substring(1);
            }

            if (!first) {
                json.append(",");
            }

            first = false;

            /*
             * Escape JSON values.
             */
            String safeName =
                    escapeJson(name);

            String safeCommand =
                    escapeJson(command);

            /*
             * Black clickable line.
             */
            json.append("{")
                    .append("\"text\":\"")
                    .append("\\u2022 ")
                    .append(safeName)
                    .append("\\n")
                    .append("\",")

                    .append("\"color\":\"black\",")

                    .append("\"clickEvent\":{")
                    .append("\"action\":\"run_command\",")
                    .append("\"value\":\"/")
                    .append(safeCommand)
                    .append("\"}")

                    .append("}");
        }

        json.append("]}");

        return json.toString();
    }

    /*
     * Escape characters that can break JSON.
     */
    private String escapeJson(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", " ")
                .replace("\t", " ");
    }
}
