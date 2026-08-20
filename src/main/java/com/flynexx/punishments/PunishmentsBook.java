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
     * Opens the written book without leaving it in the player's inventory.
     */
    private void openPunishmentBook(final Player player, String target) {

        ItemStack oldItem = player.getItemInHand();

        ItemStack book = createBook(target);

        /*
         * Put the book temporarily in the hand.
         * It is removed immediately after the client opens it.
         */
        player.setItemInHand(book);
        player.updateInventory();

        if (!openBook(player, book)) {

            player.sendMessage(PREFIX + ChatColor.RED +
                    "Could not open punishment book.");

            player.setItemInHand(oldItem);
            player.updateInventory();
            return;
        }

        /*
         * Restore the original item after the book opens.
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
     * Uses Bukkit/CraftBukkit reflection.
     *
     * Important:
     * We do NOT import NMS classes.
     * We do NOT use PacketPlayOutOpenBook.
     */
    private boolean openBook(Player player, ItemStack book) {

        try {

            Object craftPlayer = player;

            Method getHandle =
                    craftPlayer.getClass().getMethod("getHandle");

            Object entityPlayer =
                    getHandle.invoke(craftPlayer);

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
                    asNMSCopy.invoke(null, book);

            /*
             * Find EntityPlayer.openBook(ItemStack)
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

                if (!parameters[0].getName()
                        .equals("net.minecraft.server.v1_8_R3.ItemStack")) {
                    continue;
                }

                method.invoke(entityPlayer, nmsBook);

                return true;
            }

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Could not open book: "
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
     * The visible punishment list contains ONLY the punishment name.
     * No duration.
     * No reason.
     * No JSON code.
     */
    private ItemStack createBook(String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle(
                color(getConfig().getString(
                        "settings.book-title",
                        "&4Punishments"
                ))
        );

        meta.setAuthor(
                getConfig().getString(
                        "settings.book-author",
                        "FlyNeXx"
                )
        );

        List<String> pages =
                new ArrayList<String>();

        /*
         * First page.
         *
         * The lines are deliberately plain black text.
         * The actual clickable behaviour is handled by
         * the Minecraft written-book JSON format.
         */
        StringBuilder page =
                new StringBuilder();

        page.append("{\"text\":\"\"");

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section != null) {

            for (String id : section.getKeys(false)) {

                String name =
                        section.getString(
                                id + ".name",
                                id
                        );

                /*
                 * Remove all color codes.
                 */
                name = ChatColor.stripColor(
                        ChatColor.translateAlternateColorCodes(
                                '&',
                                name
                        )
                );

                /*
                 * Command executed when clicked.
                 */
                String command =
                        "/pmapply "
                                + target
                                + " "
                                + id;

                page.append(",\"extra\":[")
                        .append("{")
                        .append("\"text\":\"")
                        .append(escape("§0• " + name + "\n"))
                        .append("\",")
                        .append("\"clickEvent\":{")
                        .append("\"action\":\"run_command\",")
                        .append("\"value\":\"")
                        .append(escape(command))
                        .append("\"")
                        .append("}")
                        .append("}");
            }

            page.append("]");
        }

        page.append("}");

        pages.add(page.toString());

        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    /*
     * Executes the configured punishment command.
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
                command.replace("%player%", target)
                        .replace("%target%", target)
                        .replace("%duration%", duration)
                        .replace("%staff%", staff.getName());

        if (command.startsWith("/")) {
            command = command.substring(1);
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

    private String escape(String text) {

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
