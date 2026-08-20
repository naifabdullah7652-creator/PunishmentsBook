package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

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

        getLogger().info(
                "PunishmentsBook 2.0.0 enabled."
        );
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (command.getName().equalsIgnoreCase("pm")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(
                        ChatColor.RED +
                        "Players only."
                );
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission(
                    "punishmentsbook.use")) {

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "You don't have permission."
                );

                return true;
            }

            if (args.length != 1) {

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Usage: /pm <player>"
                );

                return true;
            }

            openBook(
                    player,
                    args[0]
            );

            return true;
        }

        if (command.getName()
                .equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff = (Player) sender;

            if (args.length != 2) {
                return true;
            }

            if (!staff.hasPermission(
                    "punishmentsbook.use")) {
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

    /*
     * =========================================================
     * OPEN BOOK
     * =========================================================
     */
    private void openBook(
            Player player,
            String target) {

        try {

            ItemStack nmsBook =
                    createBook(target);

            if (nmsBook == null) {

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Could not create punishment book."
                );

                return;
            }

            EntityPlayer entityPlayer =
                    ((CraftPlayer) player)
                            .getHandle();

            /*
             * Open the book directly.
             */
            entityPlayer.openBook(
                    nmsBook
            );

        } catch (Throwable ex) {

            getLogger().severe(
                    "Failed to open punishment book."
            );

            getLogger().severe(
                    ex.getClass().getName()
            );

            if (ex.getMessage() != null) {
                getLogger().severe(
                        ex.getMessage()
                );
            }

            ex.printStackTrace();

            player.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Could not open punishment book."
            );
        }
    }

    /*
     * =========================================================
     * CREATE BOOK
     * =========================================================
     */
    private ItemStack createBook(
            String target) {

        /*
         * Create normal Bukkit written book.
         */
        org.bukkit.inventory.ItemStack bukkitBook =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        BookMeta meta =
                (BookMeta) bukkitBook.getItemMeta();

        meta.setTitle(
                "Punishments"
        );

        meta.setAuthor(
                "FlyNeXx"
        );

        ConfigurationSection section =
                getConfig()
                        .getConfigurationSection(
                                "punishments"
                        );

        /*
         * No punishments.
         */
        if (section == null ||
                section.getKeys(false).isEmpty()) {

            meta.setPages(
                    "{\"text\":\"Punishments\\n\\nNo punishments configured.\",\"color\":\"black\"}"
            );

            bukkitBook.setItemMeta(meta);

            return CraftItemStack.asNMSCopy(
                    bukkitBook
            );
        }

        /*
         * Build pages.
         */
        List<String> pages =
                new ArrayList<String>();

        StringBuilder page =
                createPageStart();

        int count = 0;

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
             * Command executed when clicked.
             */
            String command =
                    "/pmapply " +
                    target +
                    " " +
                    id;

            /*
             * Add clickable punishment.
             */
            page.append(",");

            page.append("{");

            page.append(
                    "\"text\":\""
            );

            page.append(
                    escapeJson(name)
            );

            page.append(
                    "\","
            );

            page.append(
                    "\"color\":\"black\","
            );

            page.append(
                    "\"clickEvent\":{"
            );

            page.append(
                    "\"action\":\"run_command\","
            );

            page.append(
                    "\"value\":\""
            );

            page.append(
                    escapeJson(command)
            );

            page.append(
                    "\""
            );

            page.append(
                    "}"
            );

            page.append(
                    "}"
            );

            /*
             * New line.
             */
            page.append(
                    ",{\"text\":\"\\n\"}"
            );

            count++;

            /*
             * Ten punishments per page.
             */
            if (count >= 10) {

                page.append(
                        "]}"
                );

                pages.add(
                        page.toString()
                );

                page =
                        createPageStart();

                count = 0;
            }
        }

        /*
         * Add remaining page.
         */
        if (count > 0) {

            page.append(
                    "]}"
            );

            pages.add(
                    page.toString()
            );
        }

        /*
         * IMPORTANT:
         * BookMeta.setPages() receives the
         * JSON page strings directly.
         */
        meta.setPages(
                pages
        );

        bukkitBook.setItemMeta(
                meta
        );

        /*
         * Convert Bukkit book to NMS book.
         */
        return CraftItemStack.asNMSCopy(
                bukkitBook
        );
    }

    /*
     * =========================================================
     * PAGE START
     * =========================================================
     */
    private StringBuilder createPageStart() {

        StringBuilder page =
                new StringBuilder();

        page.append("{");

        page.append(
                "\"text\":\"\","
        );

        page.append(
                "\"extra\":["
        );

        /*
         * Header.
         */
        page.append(
                "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\"}"
        );

        return page;
    }

    /*
     * =========================================================
     * JSON ESCAPE
     * =========================================================
     */
    private String escapeJson(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                );
    }

    /*
     * =========================================================
     * APPLY PUNISHMENT
     * =========================================================
     */
    private void applyPunishment(
            Player staff,
            String target,
            String id) {

        String path =
                "punishments." +
                id;

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

        /*
         * Duration is used here,
         * but NEVER displayed in the book.
         */
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

        /*
         * Placeholders.
         */
        command =
                command
                        .replace(
                                "%player%",
                                target
                        )
                        .replace(
                                "%target%",
                                target
                        )
                        .replace(
                                "%duration%",
                                duration
                        )
                        .replace(
                                "%reason%",
                                reason
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        );

        /*
         * Remove /.
         */
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

        /*
         * Execute command as console.
         */
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
