package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
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
         * =====================================================
         * /pm <player>
         * =====================================================
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
         * =====================================================
         * /pmapply <player> <punishment>
         * =====================================================
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

            /*
             * Build the NMS written book directly.
             */
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

            /*
             * Convert NMS -> Bukkit.
             */
            org.bukkit.inventory.ItemStack bukkitBook =
                    CraftItemStack.asBukkitCopy(
                            nmsBook
                    );

            /*
             * Save current held item.
             */
            final org.bukkit.inventory.ItemStack oldItem =
                    player.getItemInHand();

            /*
             * Put the actual written book in hand.
             */
            player.setItemInHand(
                    bukkitBook
            );

            player.updateInventory();

            /*
             * Get NMS player.
             */
            EntityPlayer entityPlayer =
                    ((CraftPlayer) player)
                            .getHandle();

            /*
             * Open the book.
             */
            entityPlayer.openBook(
                    nmsBook
            );

            /*
             * Restore previous item after
             * the client has opened the book.
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
     *
     * The important part:
     *
     * We do NOT use BookMeta.setPages() here.
     *
     * We create the pages directly inside the item's NBT.
     *
     * This prevents the JSON from being displayed as ordinary
     * book text.
     */
    private ItemStack createNMSBook(
            String target) {

        /*
         * Create an ordinary written book.
         */
        org.bukkit.inventory.ItemStack base =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        /*
         * Convert to NMS.
         */
        ItemStack book =
                CraftItemStack.asNMSCopy(
                        base
                );

        /*
         * Create book NBT.
         */
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

        /*
         * Pages list.
         */
        NBTTagList pages =
                new NBTTagList();

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

            pages.appendTag(
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

        /*
         * Build a page at a time.
         */
        List<String> punishmentIds =
                new ArrayList<String>(
                        section.getKeys(false)
                );

        StringBuilder page =
                new StringBuilder();

        /*
         * Page title.
         */
        page.append(
                "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\""
        );

        page.append(
                ",\"extra\":["
        );

        /*
         * Header already exists.
         */
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
             * Command generated by clicking.
             */
            String command =
                    "/pmapply " +
                    target +
                    " " +
                    id;

            if (!first) {

                page.append(
                        ","
                );
            }

            first = false;

            /*
             * Clickable component.
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

            page.append(
                    ",\"clickEvent\":{\"action\":\"run_command\",\"value\":\""
            );

            page.append(
                    escapeJson(command)
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
             * Ten punishments per page.
             */
            if (count >= 10) {

                page.append(
                        "]}"
                );

                /*
                 * Store page.
                 */
                pages.appendTag(
                        new NBTTagString(
                                normalizePage(
                                        page.toString()
                                )
                        )
                );

                /*
                 * Start another page.
                 */
                page =
                        new StringBuilder();

                page.append(
                        "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\""
                );

                page.append(
                        ",\"extra\":["
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

            pages.appendTag(
                    new NBTTagString(
                            normalizePage(
                                    page.toString()
                            )
                    )
            );
        }

        /*
         * Add pages to book.
         */
        tag.set(
                "pages",
                pages
        );

        /*
         * Apply NBT.
         */
        book.setTag(
                tag
        );

        return book;
    }

    /*
     * =========================================================
     * NORMALIZE PAGE
     * =========================================================
     */
    private String normalizePage(
            String page) {

        /*
         * The first component is already the root.
         *
         * Nothing is displayed to the user except the
         * rendered book contents.
         */
        return page;
    }

    /*
     * =========================================================
     * ESCAPE JSON STRING
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

        /*
         * Check punishment.
         */
        if (!getConfig()
                .isConfigurationSection(path)) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Unknown punishment."
            );

            return;
        }

        /*
         * Name.
         */
        String name =
                getConfig().getString(
                        path + ".name",
                        id
                );

        /*
         * Duration.
         *
         * It is used internally only.
         * It is NOT displayed in the book.
         */
        String duration =
                getConfig().getString(
                        path + ".duration",
                        ""
                );

        /*
         * Reason.
         */
        String reason =
                getConfig().getString(
                        path + ".reason",
                        name
                );

        /*
         * Command.
         */
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

        /*
         * Remove slash.
         */
        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        /*
         * No command.
         */
        if (command.trim().isEmpty()) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "No command configured."
            );

            return;
        }

        /*
         * Execute as the staff member.
         *
         * NOT Console.
         */
        boolean success =
                staff.performCommand(
                        command
                );

        /*
         * Failed.
         */
        if (!success) {

            staff.sendMessage(
                    PREFIX +
                    ChatColor.RED +
                    "Punishment command failed."
            );

            return;
        }

        /*
         * Success.
         */
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
