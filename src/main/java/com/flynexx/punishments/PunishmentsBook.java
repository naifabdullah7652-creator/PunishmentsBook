package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutOpenBook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class PunishmentsBook extends JavaPlugin {

    private final String PREFIX =
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

        getLogger().info("PunishmentsBook enabled.");
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
     * The book is NEVER left inside the player's inventory.
     */
    private void openPunishmentBook(Player player, String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

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

        /*
         * PAGE 1
         * Keep the normal text exactly readable.
         */
        StringBuilder page =
                new StringBuilder();

        page.append(
                color("&4&lPUNISHMENTS\n\n")
        );

        page.append(
                color("&7Player: &f" + target + "\n\n")
        );

        page.append(
                color("&8Select a punishment:\n\n")
        );

        /*
         * Punishments
         */
        if (getConfig().isConfigurationSection("punishments")) {

            for (String id :
                    getConfig()
                            .getConfigurationSection("punishments")
                            .getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        id
                );

                String duration = getConfig().getString(
                        "punishments." + id + ".duration",
                        "Permanent"
                );

                page.append(
                        color("&c» &f" + name)
                );

                page.append("\n");

                page.append(
                        color("&7Duration: &e" + duration)
                );

                page.append("\n\n");
            }
        }

        /*
         * Do NOT use JSON here.
         *
         * This keeps the normal BookMeta text working correctly.
         */
        List<String> pages =
                new ArrayList<String>();

        pages.add(page.toString());

        /*
         * PAGE 2
         */
        StringBuilder info =
                new StringBuilder();

        info.append(
                color("&4&lPUNISHMENTS\n\n")
        );

        info.append(
                color("&7Player: &f" + target + "\n\n")
        );

        info.append(
                color("&8Punishment information\n\n")
        );

        if (getConfig().isConfigurationSection("punishments")) {

            for (String id :
                    getConfig()
                            .getConfigurationSection("punishments")
                            .getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        id
                );

                String duration = getConfig().getString(
                        "punishments." + id + ".duration",
                        "Permanent"
                );

                info.append(
                        color("&c» &f" + name + "\n")
                );

                info.append(
                        color("&7Duration: &e" + duration + "\n\n")
                );
            }
        }

        pages.add(info.toString());

        meta.setPages(pages);

        book.setItemMeta(meta);

        /*
         * Save the item currently held by the player.
         */
        final ItemStack oldItem =
                player.getItemInHand();

        /*
         * Put the book in the hand temporarily.
         * It will NOT remain in the inventory.
         */
        player.setItemInHand(book);
        player.updateInventory();

        /*
         * Open the book using the 1.8.8 NMS packet.
         */
        openBookPacket(player);

        /*
         * Restore the previous item.
         */
        Bukkit.getScheduler().runTaskLater(
                this,
                new Runnable() {

                    @Override
                    public void run() {

                        if (!player.isOnline()) {
                            return;
                        }

                        player.setItemInHand(oldItem);
                        player.updateInventory();
                    }

                },
                2L
        );
    }

    /*
     * Minecraft 1.8.8 does not have Player#openBook().
     *
     * Therefore we use PacketPlayOutOpenBook.
     */
    private void openBookPacket(Player player) {

        try {

            CraftPlayer craftPlayer =
                    (CraftPlayer) player;

            EntityPlayer entityPlayer =
                    craftPlayer.getHandle();

            PacketPlayOutOpenBook packet =
                    new PacketPlayOutOpenBook(
                            entityPlayer.inventory.itemInHandIndex
                    );

            entityPlayer.playerConnection.sendPacket(
                    packet
            );

        } catch (Throwable throwable) {

            getLogger().warning(
                    "Could not open punishment book: "
                            + throwable.getClass().getSimpleName()
                            + ": "
                            + throwable.getMessage()
            );
        }
    }

    /*
     * Executes the configured punishment command.
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

        String displayName =
                getConfig().getString(
                        path + ".name",
                        punishmentId
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

        if (command == null ||
                command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured for " +
                    ChatColor.WHITE +
                    displayName
            );

            return;
        }

        command = command
                .replace("%player%", targetName)
                .replace("%target%", targetName)
                .replace("%duration%", duration)
                .replace("%staff%", staff.getName());

        if (command.startsWith("/")) {
            command =
                    command.substring(1);
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
                    "Failed to execute punishment command."
            );

            return;
        }

        /*
         * Staff message
         */
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
         * Broadcast
         */
        String announcement =
                getConfig().getString(
                        "settings.announcement",
                        "&4&lPunishment &8┃ &f%player% &7was punished with &c%punishment% &7(&e%duration%&7)"
                );

        announcement =
                announcement
                        .replace(
                                "%player%",
                                targetName
                        )
                        .replace(
                                "%punishment%",
                                ChatColor.stripColor(
                                        color(displayName)
                                )
                        )
                        .replace(
                                "%duration%",
                                duration
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        );

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
