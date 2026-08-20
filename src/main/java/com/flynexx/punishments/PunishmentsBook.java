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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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

        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff = (Player) sender;

            if (args.length != 2) {
                return true;
            }

            if (!staff.hasPermission("punishmentsbook.use")) {
                return true;
            }

            applyPunishment(
                    staff,
                    args[0],
                    args[1]
            );

            return true;
        }

        return false;
    }

    private void openBook(
            Player player,
            String target) {

        ItemStack book =
                createBook(target);

        ItemStack old =
                player.getItemInHand();

        try {

            player.setItemInHand(book);
            player.updateInventory();

            Object handle =
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
                    asNMSCopy.invoke(
                            null,
                            book
                    );

            Method openBook = null;

            for (Method method :
                    handle.getClass().getMethods()) {

                if (!method.getName()
                        .equals("openBook")) {
                    continue;
                }

                if (method.getParameterTypes()
                        .length != 1) {
                    continue;
                }

                openBook = method;
                break;
            }

            if (openBook == null) {
                throw new Exception(
                        "EntityPlayer.openBook was not found"
                );
            }

            openBook.invoke(
                    handle,
                    nmsBook
            );

            /*
             * The client has received the book-open
             * request. Restore the previous item.
             */
            player.setItemInHand(old);
            player.updateInventory();

        } catch (Throwable ex) {

            player.setItemInHand(old);
            player.updateInventory();

            getLogger().warning(
                    "Unable to open virtual book: " +
                    ex.getClass().getSimpleName() +
                    ": " +
                    ex.getMessage()
            );

            player.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment book."
            );
        }
    }

    private ItemStack createBook(
            String target) {

        ItemStack book =
                new ItemStack(
                        Material.WRITTEN_BOOK
                );

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        meta.setTitle("Punishments");
        meta.setAuthor("FlyNeXx");

        try {

            Class<?> baseComponent =
                    Class.forName(
                            "net.md_5.bungee.api.chat.BaseComponent"
                    );

            Class<?> textComponent =
                    Class.forName(
                            "net.md_5.bungee.api.chat.TextComponent"
                    );

            Class<?> clickEvent =
                    Class.forName(
                            "net.md_5.bungee.api.chat.ClickEvent"
                    );

            Class<?> clickAction =
                    Class.forName(
                            "net.md_5.bungee.api.chat.ClickEvent$Action"
                    );

            Constructor<?> textConstructor =
                    textComponent.getConstructor(
                            String.class
                    );

            Constructor<?> clickConstructor =
                    clickEvent.getConstructor(
                            clickAction,
                            String.class
                    );

            Method setClickEvent =
                    textComponent.getMethod(
                            "setClickEvent",
                            clickEvent
                    );

            Object runCommand =
                    Enum.valueOf(
                            (Class<Enum>) clickAction,
                            "RUN_COMMAND"
                    );

            Object spigotMeta =
                    BookMeta.class
                            .getMethod("spigot")
                            .invoke(meta);

            Method addPage =
                    spigotMeta.getClass()
                            .getMethod(
                                    "addPage",
                                    Array.newInstance(
                                            baseComponent,
                                            0
                                    ).getClass()
                            );

            ConfigurationSection section =
                    getConfig()
                            .getConfigurationSection(
                                    "punishments"
                            );

            if (section == null) {
                meta.setPages(
                        "No punishments configured."
                );

                book.setItemMeta(meta);
                return book;
            }

            List<Object> components =
                    new ArrayList<Object>();

            for (String id :
                    section.getKeys(false)) {

                String name =
                        getConfig().getString(
                                "punishments." +
                                id +
                                ".name",
                                id
                        );

                /*
                 * §0 = BLACK.
                 *
                 * No duration.
                 * No reason.
                 * No JSON.
                 * No visible event text.
                 */
                Object component =
                        textConstructor.newInstance(
                                "\u00a70" +
                                name +
                                "\n"
                        );

                String command =
                        "/pmapply " +
                        target +
                        " " +
                        id;

                Object event =
                        clickConstructor.newInstance(
                                runCommand,
                                command
                        );

                setClickEvent.invoke(
                        component,
                        event
                );

                components.add(component);
            }

            Object page =
                    Array.newInstance(
                            baseComponent,
                            components.size()
                    );

            for (int i = 0;
                 i < components.size();
                 i++) {

                Array.set(
                        page,
                        i,
                        components.get(i)
                );
            }

            /*
             * addPage(BaseComponent[]...)
             */
            Object pages =
                    Array.newInstance(
                            page.getClass(),
                            1
                    );

            Array.set(
                    pages,
                    0,
                    page
            );

            addPage.invoke(
                    spigotMeta,
                    pages
            );

        } catch (Throwable ex) {

            getLogger().warning(
                    "Could not create interactive page: " +
                    ex.getClass().getSimpleName() +
                    ": " +
                    ex.getMessage()
            );

            /*
             * Plain fallback.
             * Still no JSON.
             */
            List<String> pages =
                    new ArrayList<String>();

            pages.add(
                    ChatColor.BLACK +
                    "Punishments\n\n" +
                    "Interactive book unavailable."
            );

            meta.setPages(pages);
        }

        book.setItemMeta(meta);

        return book;
    }

    private void applyPunishment(
            Player staff,
            String target,
            String id) {

        String path =
                "punishments." + id;

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
                        id
                );

        String duration =
                getConfig().getString(
                        path + ".duration",
                        ""
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

        command =
                command
                        .replace("%player%", target)
                        .replace("%target%", target)
                        .replace("%duration%", duration)
                        .replace("%reason%", reason)
                        .replace("%staff%", staff.getName());

        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        if (command.trim().isEmpty()) {
            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured."
            );
            return;
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
                    "Punishment command failed."
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
                " -> " +
                ChatColor.WHITE +
                target
        );
    }
}
