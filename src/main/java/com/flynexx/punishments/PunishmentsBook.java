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
    private static final String PREFIX = ChatColor.DARK_RED + "PunishmentsBook " + ChatColor.GRAY + "┃ ";

    @Override public void onEnable() {
        saveDefaultConfig();
        if (getCommand("pm") != null) getCommand("pm").setExecutor(this);
        if (getCommand("pmapply") != null) getCommand("pmapply").setExecutor(this);
        getLogger().info("PunishmentsBook 2.0.0 enabled.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("pm")) {
            if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
            Player staff = (Player) sender;
            if (!staff.hasPermission("punishmentsbook.use")) { staff.sendMessage(PREFIX + ChatColor.RED + "You don't have permission."); return true; }
            if (args.length != 1) { staff.sendMessage(PREFIX + ChatColor.RED + "Usage: /pm <player>"); return true; }
            openBook(staff, args[0]);
            return true;
        }
        if (command.getName().equalsIgnoreCase("pmapply")) {
            if (!(sender instanceof Player)) { sender.sendMessage(ChatColor.RED + "Players only."); return true; }
            Player staff = (Player) sender;
            if (!staff.hasPermission("punishmentsbook.use")) { staff.sendMessage(PREFIX + ChatColor.RED + "You don't have permission."); return true; }
            if (args.length != 2) { staff.sendMessage(PREFIX + ChatColor.RED + "Usage: /pmapply <player> <punishment>"); return true; }
            executePunishment(staff, args[0], args[1]);
            return true;
        }
        return false;
    }

    private void openBook(final Player player, String target) {
        final ItemStack old = player.getItemInHand();
        final ItemStack book = createBook(target);
        player.setItemInHand(book);
        player.updateInventory();
        if (!openBookNms(player, book)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not open punishment page. Server must be 1.8.8.");
        }
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override public void run() {
                if (player.isOnline()) { player.setItemInHand(old); player.updateInventory(); }
            }
        }, 2L);
    }

    private boolean openBookNms(Player player, ItemStack book) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Class<?> cis = Class.forName("org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack");
            Object nms = cis.getMethod("asNMSCopy", ItemStack.class).invoke(null, book);
            for (Method m : handle.getClass().getMethods()) {
                if (!m.getName().equals("openBook") || m.getParameterTypes().length != 1) continue;
                if (m.getParameterTypes()[0].getName().equals("net.minecraft.server.v1_8_R3.ItemStack")) {
                    m.invoke(handle, nms);
                    return true;
                }
            }
        } catch (Throwable ex) {
            getLogger().warning("1.8.8 openBook reflection failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        return false;
    }

    private ItemStack createBook(String target) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(color(getConfig().getString("settings.book-title", "&4Punishments")));
        meta.setAuthor(getConfig().getString("settings.book-author", "FlyNeXx"));
        List<String> pages = new ArrayList<String>();
        pages.add(createClickablePage(target));
        StringBuilder info = new StringBuilder();
        info.append(color("&4&lPUNISHMENTS\n\n"));
        info.append(color("&7Player: &f" + target + "\n\n"));
        info.append(color("&8Click a punishment to execute it.\n\n"));
        ConfigurationSection s = getConfig().getConfigurationSection("punishments");
        if (s != null) for (String id : s.getKeys(false)) {
            String name = getConfig().getString("punishments." + id + ".name", id);
            String duration = getConfig().getString("punishments." + id + ".duration", "Permanent");
            info.append(color("&c» &f" + name + "\n"));
            info.append(color("&7Duration: &e" + duration + "\n\n"));
        }
        pages.add(info.toString());
        meta.setPages(pages);
        book.setItemMeta(meta);
        return book;
    }

    private String createClickablePage(String target) {
        ConfigurationSection s = getConfig().getConfigurationSection("punishments");
        if (s == null) return "{\"text\":\"No punishments configured.\"}";
        StringBuilder j = new StringBuilder("{\"text\":\"\",\"extra\":[");
        boolean first = true;
        for (String id : s.getKeys(false)) {
            String name = ChatColor.stripColor(color(s.getString(id + ".name", id)));
            String duration = ChatColor.stripColor(color(s.getString(id + ".duration", "Permanent")));
            String command = "/pmapply " + target + " " + id;
            if (!first) j.append(','); first = false;
            String text = "§c» §f" + name + " §7[" + duration + "]§r\n";
            String hover = "§eClick to execute §f" + name;
            j.append("{\"text\":\"").append(escape(text)).append("\",")
             .append("\"clickEvent\":{\"action\":\"run_command\",\"value\":\"").append(escape(command)).append("\"},")
             .append("\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"").append(escape(hover)).append("\"}}");
        }
        return j.append("]}").toString();
    }

    private void executePunishment(Player staff, String target, String id) {
        String path = "punishments." + id;
        if (!getConfig().isConfigurationSection(path)) { staff.sendMessage(PREFIX + ChatColor.RED + "Unknown punishment."); return; }
        String name = getConfig().getString(path + ".name", id);
        String duration = getConfig().getString(path + ".duration", "Permanent");
        String cmd = getConfig().getString(path + ".command", "");
        if (cmd.trim().isEmpty()) { staff.sendMessage(PREFIX + ChatColor.RED + "No command configured for " + ChatColor.WHITE + name); return; }
        cmd = cmd.replace("%player%", target).replace("%target%", target).replace("%duration%", duration).replace("%staff%", staff.getName());
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)) { staff.sendMessage(PREFIX + ChatColor.RED + "Failed to execute punishment command."); return; }
        staff.sendMessage(PREFIX + ChatColor.GREEN + "Punishment executed: " + ChatColor.WHITE + color(name) + ChatColor.GRAY + " → " + ChatColor.WHITE + target);
        String a = getConfig().getString("settings.announcement", "&4&lPunishment &8┃ &f%player% &7was punished with &c%punishment% &7(&e%duration%&7)");
        Bukkit.broadcastMessage(color(a.replace("%player%", target).replace("%punishment%", ChatColor.stripColor(color(name))).replace("%duration%", duration).replace("%staff%", staff.getName())));
    }

    private String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); }
    private String color(String s) { return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s); }
}
