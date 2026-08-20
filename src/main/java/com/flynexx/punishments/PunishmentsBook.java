package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;

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
     * Open the interactive book.
     */
    private void openBook(
            Player player,
            String target) {

        try {

            ItemStack nmsBook =
                    createNmsBook(target);

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
             * Paper/Spigot 1.8.8
             */
            entityPlayer.openBook(
                    nmsBook
            );

        } catch (Throwable ex) {

            getLogger().warning(
                    "Unable to open punishment book: " +
                    ex.getClass().getSimpleName() +
                    ": " +
                    ex.getMessage()
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
     * Create the NMS written book.
     *
     * The book contains JSON pages.
     *
     * Only the punishment NAME is shown.
     *
     * Duration is NOT displayed.
     */
    private ItemStack createNmsBook(
            String target) {

        org.bukkit.inventory.ItemStack bukkitBook =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        ItemStack nmsBook =
                CraftItemStack.asNMSCopy(
                        bukkitBook
                );

        NBTTagCompound tag =
                new NBTTagCompound();

        /*
         * Book title.
         */
        tag.setString(
                "title",
                "Punishments"
        );

        /*
         * Book author.
         */
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
                            "{\"text\":\"Punishments\\n\\nNo punishments configured.\",\"color\":\"black\"}"
                    )
            );

            tag.set(
                    "pages",
                    pages
            );

            nmsBook.setTag(tag);

            return nmsBook;
        }

        /*
         * Start JSON page.
         */
        StringBuilder json =
                new StringBuilder();

        json.append("{");

        json.append(
                "\"text\":\"\","
        );

        json.append(
                "\"extra\":["
        );

        /*
         * Header.
         */
        json.append(
                "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\"}"
        );

        int count = 0;

        for (String id :
                section.getKeys(false)) {

            /*
             * Punishment name.
             */
            String name =
                    getConfig().getString(
                            "punishments." +
                            id +
                            ".name",
                            id
                    );

            /*
             * Command executed by clicking.
             */
            String command =
                    "/pmapply " +
                    target +
                    " " +
                    id;

            /*
             * Comma between JSON objects.
             */
            json.append(",");

            /*
             * Clickable punishment.
             */
            json.append("{");

            json.append(
                    "\"text\":\""
            );

            json.append(
                    escapeJson(name)
            );

            json.append(
                    "\","
            );

            json.append(
                    "\"color\":\"black\","
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
                    "\""
            );

            json.append(
                    "}"
            );

            json.append(
                    "}"
            );

            /*
             * New line.
             */
            json.append(",");

            json.append(
                    "{\"text\":\"\\n\"}"
            );

            count++;

            /*
             * Ten punishments per page.
             */
            if (count >= 10) {

                json.append(
                        "]}"
                );

                pages.add(
                        new NBTTagString(
                                json.toString()
                        )
                );

                /*
                 * New page.
                 */
                json =
                        new StringBuilder();

                json.append("{");

                json.append(
                        "\"text\":\"\","
                );

                json.append(
                        "\"extra\":["
                );

                json.append(
                        "{\"text\":\"Punishments\\n\\n\",\"color\":\"dark_red\"}"
                );

                count = 0;
            }
        }

        /*
         * Finish final page.
         */
        if (count > 0) {

            json.append(
                    "]}"
            );

            pages.add(
                    new NBTTagString(
                            json.toString()
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
        nmsBook.setTag(
                tag
        );

        return nmsBook;
    }

    /*
     * Escape text for Minecraft JSON.
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
     * Apply punishment.
     *
     * /pmapply <player> <id>
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
         * It is NOT shown in the book.
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
         * Configured command.
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
         * Remove leading slash.
         */
        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        /*
         * Empty command.
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
         * Execute command as console.
         */
        boolean success =
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        command
                );

        /*
         * Command failed.
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
