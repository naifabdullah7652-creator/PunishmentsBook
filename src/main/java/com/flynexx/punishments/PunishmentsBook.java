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
import java.lang.reflect.Field;
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

            String target = args[0];

            openPunishmentBook(player, target);

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
     * ============================================================
     * BOOK
     * ============================================================
     */

    private void openPunishmentBook(Player player, String target) {

        ItemStack book =
                new ItemStack(Material.WRITTEN_BOOK);

        BookMeta meta =
                (BookMeta) book.getItemMeta();

        String title =
                getConfig().getString(
                        "settings.book-title",
                        "&4Punishments"
                );

        String author =
                getConfig().getString(
                        "settings.book-author",
                        "FlyNeXx"
                );

        meta.setTitle(color(title));
        meta.setAuthor(author);

        List<String> pages =
                createPages(target);

        meta.setPages(pages);

        book.setItemMeta(meta);

        /*
         * Open the book directly.
         *
         * Nothing is permanently placed into the inventory.
         */
        openBookWithoutInventory(player, book);
    }

    private List<String> createPages(String target) {

        List<String> pages =
                new ArrayList<String>();

        /*
         * Page 1
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
                color("&8Click a punishment below:\n\n")
        );

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section != null) {

            int count = 0;

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

                /*
                 * Keep normal visible text.
                 */
                page.append(
                        color("&c" + (count + 1) +
                                ". &f" + name + "\n")
                );

                page.append(
                        color("&7Duration: &e" +
                                duration + "\n\n")
                );

                count++;

                /*
                 * Keep the first page readable.
                 */
                if (count == 4) {
                    pages.add(page.toString());
                    page = new StringBuilder();

                    page.append(
                            color("&4&lPUNISHMENTS\n\n")
                    );

                    page.append(
                            color("&7Player: &f" +
                                    target + "\n\n")
                    );
                }
            }
        }

        if (page.length() > 0) {
            pages.add(page.toString());
        }

        /*
         * Interactive page.
         *
         * The JSON is kept simple for the 1.8.8 client.
         */
        pages.add(
                createClickablePage(target)
        );

        return pages;
    }

    /*
     * Creates a JSON page.
     *
     * Clicking a punishment executes:
     *
     * /pmapply <player> <id>
     */
    private String createClickablePage(String target) {

        StringBuilder json =
                new StringBuilder();

        json.append("{\"text\":\"");

        json.append(
                escapeJson(
                        ChatColor.stripColor(
                                color("&4&lPUNISHMENTS")
                        )
                )
        );

        json.append(
                "\\n\\n"
        );

        json.append(
                escapeJson(
                        ChatColor.stripColor(
                                color("&7Player: &f" + target)
                        )
                )
        );

        json.append(
                "\\n\\n"
        );

        json.append(
                escapeJson(
                        ChatColor.stripColor(
                                color("&8Click a punishment:")
                        )
                )
        );

        json.append(
                "\",\"extra\":["
        );

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section != null) {

            boolean first = true;

            int number = 1;

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

                if (!first) {
                    json.append(",");
                }

                first = false;

                String command =
                        "/pmapply " +
                        target +
                        " " +
                        id;

                String visible =
                        "&c" + number +
                        ". &f" + name +
                        " &7[" +
                        duration +
                        "]\\n";

                String hover =
                        "&eClick to execute\n" +
                        "&7Duration: &f" +
                        duration;

                json.append("{");

                json.append(
                        "\"text\":\""
                );

                json.append(
                        escapeJson(
                                color(visible)
                        )
                );

                json.append(
                        "\","
                );

                json.append(
                        "\"clickEvent\":{"
                );

                json.append(
                        "\"action\":\"run_command\","
                );

                json.append(
                        "\"value\":\""
                );

                json.append(
                        escapeJson(command)
                );

                json.append(
                        "\"},"
                );

                json.append(
                        "\"hoverEvent\":{"
                );

                json.append(
                        "\"action\":\"show_text\","
                );

                json.append(
                        "\"value\":\""
                );

                json.append(
                        escapeJson(
                                color(hover)
                        )
                );

                json.append(
                        "\"}"
                );

                json.append(
                        "}"
                );

                number++;
            }
        }

        json.append("]}");

        return json.toString();
    }

    /*
     * ============================================================
     * OPEN BOOK - 1.8.8
     * ============================================================
     *
     * Uses reflection so Maven does NOT need CraftBukkit/NMS
     * dependencies.
     *
     * The server is still running on 1.8.8 where these classes
     * exist at runtime.
     */
    private void openBookWithoutInventory(
            final Player player,
            final ItemStack book) {

        try {

            /*
             * Save the current held item.
             */
            final int slot =
                    player.getInventory().getHeldItemSlot();

            final ItemStack old =
                    player.getInventory().getItem(slot);

            /*
             * Temporarily place the book in the held slot.
             *
             * It is immediately restored after sending the
             * opening packet.
             */
            player.getInventory().setItem(
                    slot,
                    book
            );

            /*
             * Convert Bukkit ItemStack -> NMS ItemStack.
             */
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
                    asNMSCopy.invoke(
                            null,
                            book
                    );

            /*
             * CraftPlayer -> EntityPlayer.
             */
            Class<?> craftPlayerClass =
                    Class.forName(
                            "org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer"
                    );

            Method getHandle =
                    craftPlayerClass.getMethod(
                            "getHandle"
                    );

            Object entityPlayer =
                    getHandle.invoke(player);

            /*
             * EntityPlayer.openBook(ItemStack)
             *
             * This method exists in the 1.8.x server source.
             */
            Method openBook =
                    entityPlayer.getClass().getMethod(
                            "openBook",
                            Class.forName(
                                    "net.minecraft.server.v1_8_R3.ItemStack"
                            )
                    );

            openBook.invoke(
                    entityPlayer,
                    nmsBook
            );

            /*
             * Restore the original item immediately.
             */
            player.getInventory().setItem(
                    slot,
                    old
            );

            player.updateInventory();

        } catch (Exception ex) {

            getLogger().severe(
                    "Could not open punishment book."
            );

            getLogger().severe(
                    ex.getClass().getSimpleName() +
                    ": " +
                    ex.getMessage()
            );

            /*
             * Emergency fallback:
             * restore inventory and tell the player.
             */
            player.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Could not open the punishment book."
            );
        }
    }

    /*
     * ============================================================
     * EXECUTION
     * ============================================================
     */

    private void executePunishment(
            Player staff,
            String target,
            String punishmentId) {

        String path =
                "punishments." +
                punishmentId;

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

        if (command == null ||
                command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured for " +
                    ChatColor.WHITE +
                    name
            );

            return;
        }

        /*
         * Placeholder support.
         */
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

        command =
                command.replace(
                        "%time%",
                        duration
                );

        command =
                command.replace(
                        "%duration%",
                        duration
                );

        command =
                command.replace(
                        "%reason%",
                        reason
                );

        command =
                command.replace(
                        "%staff%",
                        staff.getName()
                );

        /*
         * Remove / if present.
         */
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
                    PREFIX +
                    ChatColor.RED +
                    "Failed to execute punishment."
            );

            return;
        }

        /*
         * Staff message.
         */
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

        /*
         * Broadcast.
         */
        String announcement =
                getConfig().getString(
                        "settings.announcement",
                        "&4&lPunishment &8┃ &f%player% &7was punished with &c%punishment% &7(&e%duration%&7)"
                );

        announcement =
                announcement.replace(
                        "%player%",
                        target
                );

        announcement =
                announcement.replace(
                        "%punishment%",
                        ChatColor.stripColor(
                                color(name)
                        )
                );

        announcement =
                announcement.replace(
                        "%duration%",
                        duration
                );

        announcement =
                announcement.replace(
                        "%reason%",
                        reason
                );

        announcement =
                announcement.replace(
                        "%staff%",
                        staff.getName()
                );

        Bukkit.broadcastMessage(
                color(announcement)
        );
    }

    /*
     * ============================================================
     * UTILITIES
     * ============================================================
     */

    private String escapeJson(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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
