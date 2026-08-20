package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;
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

        /*
         * /pm <player>
         */
        if (command.getName().equalsIgnoreCase("pm")) {

            if (!(sender instanceof Player)) {

                sender.sendMessage(
                        ChatColor.RED +
                        "Players only."
                );

                return true;
            }

            Player player =
                    (Player) sender;

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

        /*
         * /pmapply <player> <punishment>
         */
        if (command.getName()
                .equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff =
                    (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.use")) {

                staff.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "You don't have permission."
                );

                return true;
            }

            if (args.length != 2) {

                staff.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Usage: /pmapply <player> <punishment>"
                );

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
            final Player player,
            String target) {

        try {

            ItemStack nmsBook =
                    createNMSBook(target);

            if (nmsBook == null) {

                player.sendMessage(
                        PREFIX +
                        ChatColor.RED +
                        "Could not create punishment book."
                );

                return;
            }

            org.bukkit.inventory.ItemStack bukkitBook =
                    CraftItemStack.asBukkitCopy(
                            nmsBook
                    );

            final org.bukkit.inventory.ItemStack oldItem =
                    player.getItemInHand();

            player.setItemInHand(
                    bukkitBook
            );

            player.updateInventory();

            EntityPlayer entityPlayer =
                    ((CraftPlayer) player)
                            .getHandle();

            entityPlayer.openBook(
                    nmsBook
            );

            /*
             * Restore the old item after the book
             * has been sent to the client.
             */
            Bukkit.getScheduler().runTaskLater(
                    this,
                    new Runnable() {

                        @Override
                        public void run() {

                            player.setItemInHand(
                                    oldItem
                            );

                            player.updateInventory();
                        }

                    },
                    5L
            );

        } catch (Throwable ex) {

            getLogger().severe(
                    "Could not open punishment book."
            );

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
     * CREATE NMS BOOK
     * =========================================================
     */
    private ItemStack createNMSBook(
            String target) {

        org.bukkit.inventory.ItemStack base =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        ItemStack book =
                CraftItemStack.asNMSCopy(
                        base
                );

        NBTTagCompound tag =
                new NBTTagCompound();

        tag.setString(
                "title",
                "Punishments"
        );

        tag.setString(
                "author",
                "FlyNeXx"
        );

        NBTTagList pages =
                new NBTTagList();

        ConfigurationSection section =
                getConfig()
                        .getConfigurationSection(
                                "punishments"
                        );

        /*
         * No punishments configured.
         */
        if (section == null ||
                section.getKeys(false).isEmpty()) {

            pages.add(
                    new NBTTagString(
                            "Punishments\n\n" +
                            "No punishments configured."
                    )
            );

            tag.set(
                    "pages",
                    pages
            );

            book.setTag(
                    tag
            );

            return book;
        }

        List<String> punishmentIds =
                new ArrayList<String>(
                        section.getKeys(false)
                );

        StringBuilder page =
                new StringBuilder();

        /*
         * Page starts with a normal title.
         */
        page.append(
                "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\",\"extra\":["
        );

        boolean first =
                true;

        int count =
                0;

        for (String id :
                punishmentIds) {

            String name =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".name",
                            id
                    );

            /*
             * Command sent when the player clicks
             * the punishment.
             */
            String clickCommand =
                    "/pmapply " +
                    target +
                    " " +
                    id;

            if (!first) {
                page.append(",");
            }

            first = false;

            /*
             * Punishment name.
             *
             * Duration is intentionally NOT included.
             */
            page.append(
                    "{\"text\":\""
            );

            page.append(
                    escapeJson(name)
            );

            page.append(
                    "\",\"color\":\"black\""
            );

            page.append(
                    ",\"underlined\":true"
            );

            /*
             * Click event.
             */
            page.append(
                    ",\"clickEvent\":{"
            );

            page.append(
                    "\"action\":\"run_command\","
            );

            page.append(
                    "\"value\":\""
            );

            page.append(
                    escapeJson(clickCommand)
            );

            page.append(
                    "\"}"
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
             * Maximum 10 punishments per page.
             */
            if (count >= 10) {

                page.append(
                        "]}"
                );

                pages.add(
                        new NBTTagString(
                                page.toString()
                        )
                );

                page =
                        new StringBuilder();

                page.append(
                        "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\",\"extra\":["
                );

                first =
                        true;

                count =
                        0;
            }
        }

        /*
         * Add final page.
         */
        if (count > 0) {

            page.append(
                    "]}"
            );

            pages.add(
                    new NBTTagString(
                            page.toString()
                    )
            );
        }

        tag.set(
                "pages",
                pages
        );

        book.setTag(
                tag
        );

        return book;
    }

    /*
     * =========================================================
     * ESCAPE JSON
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
         * Replace placeholders.
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
         * Execute as the administrator.
         *
         * NOT Console.
         */
        boolean success =
                staff.performCommand(
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
