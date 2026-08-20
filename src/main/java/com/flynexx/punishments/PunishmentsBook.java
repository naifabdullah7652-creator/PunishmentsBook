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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class PunishmentsBook extends JavaPlugin {

    private static final String PREFIX =
            ChatColor.DARK_RED + "PunishmentsBook " + ChatColor.GRAY + "┃ ";

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
                sender.sendMessage("Players only.");
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.use")) {
                staff.sendMessage(PREFIX + ChatColor.RED +
                        "You don't have permission.");
                return true;
            }

            if (args.length != 1) {
                staff.sendMessage(PREFIX + ChatColor.RED +
                        "Usage: /pm <player>");
                return true;
            }

            openPunishmentBook(staff, args[0]);
            return true;
        }

        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.use")) {
                staff.sendMessage(PREFIX + ChatColor.RED +
                        "You don't have permission.");
                return true;
            }

            if (args.length != 2) {
                staff.sendMessage(PREFIX + ChatColor.RED +
                        "Usage: /pmapply <player> <punishment>");
                return true;
            }

            executePunishment(staff, args[0], args[1]);
            return true;
        }

        return false;
    }

    /*
     * Opens the book without leaving it in the player's inventory.
     *
     * Important:
     * We DO NOT use BookMeta.setPages() with JSON.
     * JSON pages are inserted directly into the NMS
     * NBTTagCompound so Minecraft 1.8.8 interprets
     * the click events instead of displaying the JSON.
     */
    private void openPunishmentBook(final Player player, final String target) {

        ItemStack oldItem = player.getItemInHand();

        ItemStack book = createNormalBook();

        ItemStack nmsBook = createNmsBook(book, target);

        if (nmsBook == null) {
            player.sendMessage(PREFIX + ChatColor.RED +
                    "Could not open punishment book.");
            return;
        }

        if (!openNmsBook(player, nmsBook)) {
            player.sendMessage(PREFIX + ChatColor.RED +
                    "Could not open punishment book on this server.");
            return;
        }

        final ItemStack restore = oldItem;

        /*
         * Restore whatever the player was holding.
         * The book itself never remains in the inventory.
         */
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setItemInHand(restore);
                    player.updateInventory();
                }
            }
        }, 2L);
    }

    /*
     * Creates the physical Bukkit book.
     * The actual clickable JSON pages are added later
     * through NMS NBT.
     */
    private ItemStack createNormalBook() {

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setTitle("Punishments");
        meta.setAuthor("FlyNeXx");

        /*
         * Temporary normal page.
         * This is replaced by NMS pages.
         */
        List<String> pages = new ArrayList<String>();
        pages.add("Punishments");

        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    /*
     * Creates an NMS ItemStack using reflection.
     *
     * This means:
     * - No CraftBukkit imports
     * - No NMS imports
     * - No BungeeCord dependency
     * - Maven only needs Spigot API
     */
    private ItemStack createNmsBook(ItemStack bukkitBook, String target) {

        try {

            String version = getServerVersion();

            Class<?> craftItemStackClass = Class.forName(
                    "org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack"
            );

            Method asNMSCopy = craftItemStackClass.getMethod(
                    "asNMSCopy",
                    ItemStack.class
            );

            Object nmsBook = asNMSCopy.invoke(null, bukkitBook);

            Class<?> nbtCompoundClass = Class.forName(
                    "net.minecraft.server." + version + ".NBTTagCompound"
            );

            Class<?> nbtListClass = Class.forName(
                    "net.minecraft.server." + version + ".NBTTagList"
            );

            Class<?> nbtStringClass = Class.forName(
                    "net.minecraft.server." + version + ".NBTTagString"
            );

            /*
             * Get or create root tag.
             */
            Method getTag = nmsBook.getClass().getMethod("getTag");

            Object tag = getTag.invoke(nmsBook);

            if (tag == null) {
                Constructor<?> compoundConstructor =
                        nbtCompoundClass.getConstructor();

                tag = compoundConstructor.newInstance();

                Method setTag = nmsBook.getClass().getMethod(
                        "setTag",
                        nbtCompoundClass
                );

                setTag.invoke(nmsBook, tag);
            }

            /*
             * Create Pages list.
             */
            Object pages =
                    nbtListClass.getConstructor().newInstance();

            Method addMethod = nbtListClass.getMethod(
                    "add",
                    Class.forName(
                            "net.minecraft.server." + version + ".NBTBase"
                    )
            );

            ConfigurationSection section =
                    getConfig().getConfigurationSection("punishments");

            if (section == null) {
                return null;
            }

            StringBuilder page = new StringBuilder();

            page.append("{\"text\":\"\"");

            page.append(",\"extra\":[");

            boolean first = true;

            for (String id : section.getKeys(false)) {

                String name = getConfig().getString(
                        "punishments." + id + ".name",
                        id
                );

                /*
                 * Completely black/white text.
                 * No colors are used for punishments.
                 */
                String display =
                        "» " + name + "\\n";

                String command =
                        "/pmapply " + target + " " + id;

                String json =
                        "{\"text\":\"" +
                        jsonEscape(display) +
                        "\"," +
                        "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" +
                        jsonEscape(command) +
                        "\"}}";

                if (!first) {
                    page.append(",");
                }

                page.append(json);

                first = false;
            }

            page.append("]}");

            Constructor<?> stringConstructor =
                    nbtStringClass.getConstructor(String.class);

            Object pageTag =
                    stringConstructor.newInstance(page.toString());

            addMethod.invoke(pages, pageTag);

            /*
             * Add pages to the book.
             */
            Method setMethod = nbtCompoundClass.getMethod(
                    "set",
                    String.class,
                    Class.forName(
                            "net.minecraft.server." + version + ".NBTBase"
                    )
            );

            setMethod.invoke(tag, "pages", pages);

            /*
             * Force written book type.
             */
            Method setStringMethod = nbtCompoundClass.getMethod(
                    "setString",
                    String.class,
                    String.class
            );

            setStringMethod.invoke(
                    tag,
                    "title",
                    "Punishments"
            );

            setStringMethod.invoke(
                    tag,
                    "author",
                    "FlyNeXx"
            );

            /*
             * Convert NMS -> Bukkit.
             */
            Method asBukkitCopy =
                    craftItemStackClass.getMethod(
                            "asBukkitCopy",
                            nmsBook.getClass()
                    );

            return (ItemStack) asBukkitCopy.invoke(
                    null,
                    nmsBook
            );

        } catch (Throwable ex) {

            getLogger().warning(
                    "Could not create NMS book: " +
                    ex.getClass().getSimpleName() +
                    ": " +
                    ex.getMessage()
            );

            return null;
        }
    }

    /*
     * Opens the book using EntityHuman.openBook(ItemStack)
     * through reflection.
     *
     * No PacketPlayOutOpenBook is required.
     */
    private boolean openNmsBook(Player player, ItemStack book) {

        try {

            String version = getServerVersion();

            Class<?> craftPlayerClass = Class.forName(
                    "org.bukkit.craftbukkit." +
                    version +
                    ".entity.CraftPlayer"
            );

            Method getHandle =
                    craftPlayerClass.getMethod("getHandle");

            Object craftPlayer =
                    getHandle.invoke(player);

            Object entityHuman = craftPlayer;

            Class<?> craftItemStackClass =
                    Class.forName(
                            "org.bukkit.craftbukkit." +
                            version +
                            ".inventory.CraftItemStack"
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
                    entityHuman.getClass().getMethods()) {

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
                return false;
            }

            openBook.invoke(entityHuman, nmsBook);

            return true;

        } catch (Throwable ex) {

            getLogger().warning(
                    "Could not open NMS book: " +
                    ex.getClass().getSimpleName() +
                    ": " +
                    ex.getMessage()
            );

            return false;
        }
    }

    /*
     * Executes the punishment from config.yml.
     */
    private void executePunishment(
            Player staff,
            String target,
            String id) {

        String path = "punishments." + id;

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
                        id
                );

        String command =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        String time =
                getConfig().getString(
                        path + ".time",
                        ""
                );

        String reason =
                getConfig().getString(
                        path + ".reason",
                        name
                );

        if (command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured."
            );

            return;
        }

        command = command
                .replace("%player%", target)
                .replace("%target%", target)
                .replace("%time%", time)
                .replace("%duration%", time)
                .replace("%reason%", reason)
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
                target
        );
    }

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

    private String jsonEscape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
