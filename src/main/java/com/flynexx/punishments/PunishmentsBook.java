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

        /*
         * /pmapply <player> <punishment>
         */
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
     * Creates and opens the interactive book.
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

            /*
             * Get NMS EntityPlayer.
             */
            EntityPlayer entityPlayer =
                    ((CraftPlayer) player).getHandle();

            /*
             * Open the written book.
             *
             * This is the native 1.8.8 NMS method.
             */
            entityPlayer.openBook(nmsBook);

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
     * Creates a 1.8.8 NMS written book.
     *
     * The pages contain JSON with clickEvent.
     */
    private ItemStack createNmsBook(
            String target) {

        /*
         * Create Bukkit written book.
         */
        org.bukkit.inventory.ItemStack bukkitBook =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        /*
         * Convert to NMS.
         */
        ItemStack nmsBook =
                CraftItemStack.asNMSCopy(
                        bukkitBook
                );

        /*
         * Root NBT.
         */
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
         * No punishments configured.
         */
        if (section == null ||
                section.getKeys(false).isEmpty()) {

            String page =
                    "{\"text\":\"Punishments\\n\\nNo punishments configured.\",\"color\":\"black\"}";

            pages.add(
                    new NBTTagString(page)
            );

        } else {

            /*
             * Keep punishments on pages.
             *
             * We use several pages if needed.
             */
            List<String> ids =
                    new ArrayList<String>(
                            section.getKeys(false)
                    );

            StringBuilder page =
                    new StringBuilder();

            page.append(
                    "{\"text\":\"Punishments\\n\\n\",\"color\":\"black\"}"
            );

            int lines = 0;

            for (String id : ids) {

                String name =
                        getConfig().getString(
                                "punishments." +
                                id +
                                ".name",
                                id
                        );

                /*
                 * Only the NAME is displayed.
                 *
                 * Duration is intentionally NOT
                 * displayed in the book.
                 */
                String command =
                        "/pmapply " +
                        target +
                        " " +
                        id;

                /*
                 * Add clickable punishment.
                 */
                String json =
                        "{\"text\":\"" +
                        escapeJson(name) +
                        "\",\"color\":\"black\"," +
                        "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" +
                        escapeJson(command) +
                        "\"}}";

                page.append(json);

                page.append(
                        "{\"text\":\"\\n\",\"color\":\"black\"}"
                );

                lines++;

                /*
                 * Minecraft 1.8 book pages are
                 * small. Split after 10 punishments.
                 */
                if (lines >= 10) {

                    pages.add(
                            new NBTTagString(
                                    page.toString()
                            )
                    );

                    page =
                            new StringBuilder();

                    page.append(
                            "{\"text\":\"Punishments\\n\\n\",\"color\":\"black\"}"
                    );

                    lines = 0;
                }
            }

            /*
             * Add remaining page.
             */
            if (lines > 0) {

                pages.add(
                        new NBTTagString(
                                page.toString()
                        )
                );
            }
        }

        /*
         * Add pages to NBT.
         */
        tag.set(
                "pages",
                pages
        );

        /*
         * Apply NBT to the book.
         */
        nmsBook.setTag(tag);

        return nmsBook;
    }

    /*
     * Escapes text for Minecraft JSON.
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
     * Executes the configured punishment.
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
         * Check punishment exists.
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
         * Punishment name.
         */
        String name =
                getConfig().getString(
                        path + ".name",
                        id
                );

        /*
         * Duration.
         *
         * NOT displayed in the book.
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
         * Remove leading slash.
         */
        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        /*
         * No command configured.
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
         * Execute as console.
         */
        boolean success =
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
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
