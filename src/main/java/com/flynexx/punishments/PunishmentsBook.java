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

        getLogger().info("PunishmentsBook enabled.");
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

        openBook(player, args[0]);
        return true;
    }

    private void openBook(final Player player, String target) {

        final ItemStack oldItem = player.getItemInHand();
        final ItemStack book = createBook(target);

        try {
            player.setItemInHand(book);
            player.updateInventory();

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

            Method openBook = null;

            for (Method method :
                    entityPlayer.getClass().getMethods()) {

                if (!method.getName().equals("openBook")) {
                    continue;
                }

                if (method.getParameterTypes().length != 1) {
                    continue;
                }

                openBook = method;
                break;
            }

            if (openBook == null) {
                throw new Exception(
                        "EntityPlayer.openBook not found"
                );
            }

            openBook.invoke(entityPlayer, nmsBook);

        } catch (Throwable e) {

            getLogger().warning(
                    "Could not open book: "
                    + e.getClass().getSimpleName()
                    + ": "
                    + e.getMessage()
            );

            player.sendMessage(
                    PREFIX + ChatColor.RED +
                    "Could not open punishment book."
            );
        }

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
                2L
        );
    }

    private ItemStack createBook(String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle("Punishments");
        meta.setAuthor("FlyNeXx");

        List<String> pages =
                new ArrayList<String>();

        pages.add(createPage(target));

        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    private String createPage(String target) {

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section == null) {
            return "No punishments configured.";
        }

        StringBuilder json =
                new StringBuilder();

        json.append("{\"text\":\"\"");

        for (String id : section.getKeys(false)) {

            String name =
                    section.getString(
                            id + ".name",
                            id
                    );

            name = ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes(
                            '&',
                            name
                    )
            );

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

            json.append(",\"extra\":[{")
                    .append("\"text\":\"")
                    .append("\\u2022 ")
                    .append(escape(name))
                    .append("\\n")
                    .append("\",")

                    .append("\"color\":\"black\",")

                    .append("\"clickEvent\":{")
                    .append("\"action\":\"run_command\",")
                    .append("\"value\":\"/")
                    .append(escape(command))
                    .append("\"}")
                    .append("}]");
        }

        json.append("}");

        return json.toString();
    }

    private String escape(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
    }
}
