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

import java.util.ArrayList;
import java.util.List;

public class PunishmentsBook extends JavaPlugin {
    @Override public void onEnable() { saveDefaultConfig(); getLogger().info("PunishmentsBook 2.0.0 enabled."); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("pm")) return false;
        if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
        if (!sender.hasPermission("punishmentsbook.use")) { sender.sendMessage(ChatColor.RED + "You don't have permission."); return true; }
        if (args.length != 1) { sender.sendMessage(ChatColor.RED + "Usage: /pm <player>"); return true; }
        openBook((Player)sender, args[0]); return true;
    }
    private void openBook(final Player player, String target) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(color(getConfig().getString("settings.book-title", "&4Punishments")));
        meta.setAuthor(getConfig().getString("settings.book-author", "FlyNeXx"));
        List<String> pages = new ArrayList<String>();
        StringBuilder page = new StringBuilder(color("&4&lPUNISHMENTS\n\n"));
        page.append(color(getConfig().getString("settings.player-line", "&7Player: &f%player%").replace("%player%", target))).append("\n\n");
        ConfigurationSection section = getConfig().getConfigurationSection("punishments");
        if (section != null) for (String id : section.getKeys(false)) {
            String name = getConfig().getString("punishments." + id + ".name", "&cPunishment");
            String duration = getConfig().getString("punishments." + id + ".duration", "Permanent");
            String line = color("&c» " + name) + "\n" + color("&7Duration: &f" + duration) + "\n\n";
            if (page.length() + line.length() > 220) { pages.add(page.toString()); page = new StringBuilder(); }
            page.append(line);
        }
        if (page.length() == 0) page.append(color("&7No punishments configured."));
        pages.add(page.toString()); meta.setPages(pages); book.setItemMeta(meta);
        ItemStack oldItem = player.getItemInHand(); player.setItemInHand(book); player.updateInventory(); player.openBook(book);
        final ItemStack restore = oldItem;
        Bukkit.getScheduler().runTaskLater(this, new Runnable() { public void run() { if (player.isOnline()) { player.setItemInHand(restore); player.updateInventory(); } } }, 2L);
    }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}
