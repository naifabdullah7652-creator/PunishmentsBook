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
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("punishmentsbook.use")) {
            player.sendMessage(PREFIX + ChatColor.RED +
                    "You don't have permission.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("pm")) {

            if (args.length != 1) {
                player.sendMessage(PREFIX + ChatColor.RED +
                        "Usage: /pm <player>");
                return true;
            }

            openPunishmentBook(player, args[0]);
            return true;
        }

        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (args.length != 2) {
                player.sendMessage(PREFIX + ChatColor.RED +
                        "Usage: /pmapply <player> <punishment>");
                return true;
            }

            executePunishment(player, args[0], args[1]);
            return true;
        }

        return false;
    }

    /*
     * Opens the book without leaving it in the inventory.
     */
    private void openPunishmentBook(final Player player, String target) {

        final ItemStack oldItem = player.getItemInHand();

        ItemStack book = createBook(target);

        player.setItemInHand(book);
        player.updateInventory();

        if (!openBookNMS(player, book)) {

            player.sendMessage(PREFIX + ChatColor.RED +
                    "Could not open punishment book.");

            player.setItemInHand(oldItem);
            player.updateInventory();

            return;
        }

        /*
         * Restore the original item after the client opens the book.
         */
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {

            @Override
            public void run() {

                if (!player.isOnline()) {
                    return;
                }

                player.setItemInHand(oldItem);
                player.updateInventory();
            }

        }, 2L);
    }

    /*
     * Minecraft 1.8.8 open-book method.
     *
     * No PacketPlayOutOpenBook.
     * No direct NMS imports.
     */
    private boolean openBookNMS(Player player, ItemStack book) {

        try {

            Object entityPlayer =
                    player.getClass()
                            .getMethod("getHandle")
                            .invoke(player);

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
                    asNMSCopy.invoke(null, book);

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

                method.invoke(entityPlayer, nmsBook);

                return true;
            }

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Could not open 1.8.8 book: "
                            + throwable.getClass().getSimpleName()
                            + ": "
                            + throwable.getMessage()
            );
        }

        return false;
    }

    /*
     * Creates the book.
     *
     * IMPORTANT:
     * The JSON is generated correctly.
     * Only the punishment name is visible.
     * All punishment names are BLACK.
     * Duration/reason are NOT displayed.
     */
    private ItemStack createBook(String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle("Punishments");
        meta.setAuthor("FlyNeXx");

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        StringBuilder json =
                new StringBuilder();

        json.append("{");
        json.append("\"text\":\"\",");
        json.append("\"extra\":[");

        boolean first = true;

        if (section != null) {

            for (String id : section.getKeys(false)) {

                String name =
                        section.getString(
                                id + ".name",
                                id
                        );

                /*
                 * Remove colors.
                 */
                name =
                        ChatColor.stripColor(
                                ChatColor.translateAlternateColorCodes(
                                        '&',
                                        name
                                )
                        );

                if (!first) {
                    json.append(",");
                }

                first = false;

                String command =
                        "/pmapply "
                                + target
                                + " "
                                + id;

                /*
                 * Clickable black punishment.
                 */
                json.append("{");

                json.append("\"text\":\"");
                json.append(
                        escape("• " + name + "\n")
                );
                json.append("\",");

                json.append("\"color\":\"black\",");

                json.append("\"clickEvent\":{");
                json.append("\"action\":\"run_command\",");
                json.append("\"value\":\"");
                json.append(
                        escape(command)
                );
                json.append("\"}");

                json.append("}");
            }
        }

        json.append("]");
        json.append("}");

        List<String> pages =
                new ArrayList<String>();

        pages.add(json.toString());

        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    /*
     * Executes the punishment.
     */
    private void executePunishment(Player staff,
                                   String target,
                                   String id) {

        String path =
                "punishments." + id;

        if (!getConfig().isConfigurationSection(path)) {

            staff.sendMessage(
                    PREFIX + ChatColor.RED +
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

        String command =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        if (command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX + ChatColor.RED +
                            "No command configured."
            );

            return;
        }

        command =
                command
                        .replace("%player%", target)
                        .replace("%target%", target)
                        .replace("%duration%", duration)
                        .replace("%staff%", staff.getName());

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
                    PREFIX + ChatColor.RED +
                            "Failed to execute punishment."
            );

            return;
        }

        staff.sendMessage(
                PREFIX
                        + ChatColor.GREEN
                        + "Punishment executed: "
                        + ChatColor.WHITE
                        + ChatColor.stripColor(
                        ChatColor.translateAlternateColorCodes(
                                '&',
                                name
                        )
                )
                        + ChatColor.GRAY
                        + " → "
                        + ChatColor.WHITE
                        + target
        );
    }

    /*
     * JSON escaping.
     */
    private String escape(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
