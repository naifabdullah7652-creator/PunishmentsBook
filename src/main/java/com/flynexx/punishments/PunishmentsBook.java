package com.flynexx.punishments;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
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

            if (args.length < 2) {
                staff.sendMessage(
                        PREFIX + ChatColor.RED +
                        "Usage: /pmapply <player> <punishment>"
                );
                return true;
            }

            String target = args[0];
            String punishment = args[1];

            executePunishment(staff, target, punishment);
            return true;
        }

        return false;
    }

    /**
     * Creates the book without putting it permanently
     * into the player's inventory.
     */
    private void openPunishmentBook(Player player, String target) {

        ItemStack book = createBook(target);

        ItemStack oldItem = player.getItemInHand();

        /*
         * Temporarily put the book in the player's hand.
         * This is required by the 1.8.8 client to open
         * the written-book GUI.
         */
        player.setItemInHand(book);
        player.updateInventory();

        Bukkit.getScheduler().runTaskLater(
                this,
                new Runnable() {
                    @Override
                    public void run() {

                        if (!player.isOnline()) {
                            return;
                        }

                        try {
                            openBook(player);
                        } catch (Exception e) {
                            getLogger().warning(
                                    "Could not open virtual book: "
                                    + e.getMessage()
                            );
                        }

                        /*
                         * Restore exactly what the player
                         * had before /pm.
                         */
                        player.setItemInHand(oldItem);
                        player.updateInventory();
                    }
                },
                1L
        );
    }

    /**
     * Creates the actual written book.
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

        /*
         * First page = clickable punishment list.
         */
        BaseComponent[] page =
                createPunishmentPage(target);

        meta.spigot().setPages(page);

        book.setItemMeta(meta);

        return book;
    }

    /**
     * Creates one interactive page.
     */
    private BaseComponent[] createPunishmentPage(
            String target) {

        ComponentBuilder builder =
                new ComponentBuilder(
                        color("&4&lPUNISHMENTS\n\n")
                );

        builder.append(
                color("&7Player: &f" + target + "\n\n")
        );

        builder.append(
                color("&8Click a punishment to execute it.\n\n")
        );

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section == null) {

            builder.append(
                    color("&7No punishments configured.")
            );

            return builder.create();
        }

        for (String id : section.getKeys(false)) {

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

            String line =
                    color("&c» &f" + name) +
                    color(" &7[" + duration + "]\n");

            builder.append(line);

            builder.event(
                    new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            "/pmapply " + target + " " + id
                    )
            );

            /*
             * 1.8-compatible hover text.
             */
            builder.event(
                    new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ComponentBuilder(
                                    color(
                                            "&eClick to execute\n" +
                                            "&7Duration: &f" +
                                            duration +
                                            "\n" +
                                            "&7Reason: &f" +
                                            reason
                                    )
                            ).create()
                    )
            );

            /*
             * Reset events for next line.
             */
            builder.append("\n");
        }

        return builder.create();
    }

    /**
     * Opens a written book using reflection.
     *
     * This avoids direct imports such as:
     * net.minecraft.server.v1_8_R3
     *
     * Therefore the Java source can compile against
     * Spigot API while still supporting the 1.8.8 server.
     */
    private void openBook(Player player)
            throws Exception {

        Object craftPlayer =
                player.getClass()
                        .getMethod("getHandle")
                        .invoke(player);

        Method openBookMethod = null;

        for (Method method :
                craftPlayer.getClass().getMethods()) {

            if (!method.getName().equals("openBook")) {
                continue;
            }

            if (method.getParameterTypes().length == 1 ||
                method.getParameterTypes().length == 2) {

                openBookMethod = method;
                break;
            }
        }

        if (openBookMethod == null) {
            throw new NoSuchMethodException(
                    "EntityPlayer.openBook not found"
            );
        }

        Class<?>[] parameters =
                openBookMethod.getParameterTypes();

        /*
         * Some 1.8.x builds expose:
         *
         * openBook(ItemStack)
         *
         * while other implementations expose:
         *
         * openBook(ItemStack, EnumHand)
         *
         */
        if (parameters.length == 1) {

            openBookMethod.invoke(
                    craftPlayer,
                    getNmsItem(player.getItemInHand())
            );

            return;
        }

        if (parameters.length == 2) {

            Object enumHand = null;

            Class<?> enumClass =
                    parameters[1];

            if (enumClass.isEnum()) {

                Object[] constants =
                        enumClass.getEnumConstants();

                if (constants != null &&
                    constants.length > 0) {

                    enumHand = constants[0];
                }
            }

            openBookMethod.invoke(
                    craftPlayer,
                    getNmsItem(player.getItemInHand()),
                    enumHand
            );
        }
    }

    /**
     * Converts Bukkit ItemStack to NMS ItemStack
     * using reflection.
     */
    private Object getNmsItem(ItemStack item)
            throws Exception {

        Method asNmsCopy = null;

        Object craftItemStackClass =
                Class.forName(
                        "org.bukkit.craftbukkit."
                                + getServerVersion()
                                + ".inventory.CraftItemStack"
                );

        Class<?> clazz =
                (Class<?>) craftItemStackClass;

        for (Method method : clazz.getMethods()) {

            if (method.getName().equals("asNMSCopy") &&
                method.getParameterTypes().length == 1) {

                asNmsCopy = method;
                break;
            }
        }

        if (asNmsCopy == null) {
            throw new NoSuchMethodException(
                    "CraftItemStack.asNMSCopy not found"
            );
        }

        return asNmsCopy.invoke(
                null,
                item
        );
    }

    /**
     * Gets the CraftBukkit version dynamically.
     */
    private String getServerVersion() {

        String packageName =
                Bukkit.getServer()
                        .getClass()
                        .getPackage()
                        .getName();

        return packageName.substring(
                packageName.lastIndexOf('.') + 1
        );
    }

    /**
     * Executes the punishment command.
     */
    private void executePunishment(
            Player staff,
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

        command =
                command
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

        announcement =
                announcement
                        .replace("%player%", targetName)
                        .replace(
                                "%punishment%",
                                ChatColor.stripColor(
                                        color(name)
                                )
                        )
                        .replace("%duration%", duration)
                        .replace("%reason%", reason)
                        .replace("%staff%", staff.getName());

        getServer().broadcastMessage(
                color(announcement)
        );
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
