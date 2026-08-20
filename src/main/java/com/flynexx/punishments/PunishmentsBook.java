package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutSetSlot;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("PunishmentsBook 2.0.0 enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender,
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

        if (!sender.hasPermission("punishmentsbook.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /pm <player>");
            return true;
        }

        Player player = (Player) sender;
        openBook(player, args[0]);

        return true;
    }

    private void openBook(Player player, String target) {

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        if (meta == null) {
            player.sendMessage(ChatColor.RED + "Could not create punishment book.");
            return;
        }

        String title = getConfig().getString(
                "settings.book-title",
                "&4Punishments"
        );

        String author = getConfig().getString(
                "settings.book-author",
                "FlyNeXx"
        );

        /*
         * 1.8.8 supports BookMeta title/author,
         * so do not use Player.openBook().
         */
        meta.setTitle(color(title));
        meta.setAuthor(author);

        List<String> pages = new ArrayList<String>();
        StringBuilder page = new StringBuilder();

        page.append(color("&4&lPUNISHMENTS\n\n"));

        String playerLine = getConfig().getString(
                "settings.player-line",
                "&7Player: &f%player%"
        );

        page.append(color(
                playerLine.replace("%player%", target)
        ));

        page.append("\n\n");

        ConfigurationSection section =
                getConfig().getConfigurationSection("punishments");

        if (section != null) {

            for (String id : section.getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        "&cPunishment"
                );

                String duration = getConfig().getString(
                        "punishments." + id + ".duration",
                        "Permanent"
                );

                String line =
                        color("&c» " + name)
                        + "\n"
                        + color("&7Duration: &f" + duration)
                        + "\n\n";

                /*
                 * Minecraft book page limit.
                 */
                if (page.length() + line.length() > 220) {
                    pages.add(page.toString());
                    page = new StringBuilder();
                }

                page.append(line);
            }
        }

        if (page.length() == 0) {
            page.append(color("&7No punishments configured."));
        }

        pages.add(page.toString());

        meta.setPages(pages);
        book.setItemMeta(meta);

        /*
         * Save the player's current item.
         */
        ItemStack oldItem = player.getItemInHand();

        /*
         * Put the book into the player's hand.
         */
        player.setItemInHand(book);
        player.updateInventory();

        /*
         * Open written book using NMS for 1.8.8.
         */
        openBookNMS(player);

        /*
         * Restore previous item after opening.
         */
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

    private void openBookNMS(Player player) {

        EntityPlayer entityPlayer =
                ((CraftPlayer) player).getHandle();

        int slot = player.getInventory().getHeldItemSlot();

        entityPlayer.playerConnection.sendPacket(
                new PacketPlayOutSetSlot(
                        0,
                        slot,
                        entityPlayer.inventory.getItem(slot)
                )
        );

        /*
         * 1.8.8 has no Player.openBook().
         *
         * The client automatically opens a written book
         * when the item is changed while using the proper
         * NMS interaction flow.
         */
        entityPlayer.a(
                entityPlayer.inventory.getItem(slot),
                org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack
                        .asNMSCopy(entityPlayer.inventory.getItem(slot)),
                org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack
                        .asNMSCopy(entityPlayer.inventory.getItem(slot))
        );
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
