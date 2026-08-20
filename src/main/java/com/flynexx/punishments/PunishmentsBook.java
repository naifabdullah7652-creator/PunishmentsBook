package com.flynexx.punishments;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;

import java.util.ArrayList;
import java.util.List;

public class PunishmentsBook extends JavaPlugin {

    private String prefix;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        prefix = color(getConfig().getString(
                "settings.prefix",
                "&cPunishments &7┃ "
        ));

        if (getCommand("pm") != null) {
            getCommand("pm").setExecutor(this);
        }

        if (getCommand("pmapply") != null) {
            getCommand("pmapply").setExecutor(this);
        }

        if (getCommand("pmrevoke") != null) {
            getCommand("pmrevoke").setExecutor(this);
        }

        getLogger().info("PunishmentsBook enabled.");
        getLogger().info(
                "Loaded punishments: " + getPunishmentCount()
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
                sender.sendMessage("Players only.");
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.use")) {

                staff.sendMessage(
                        prefix + color(
                                getConfig().getString(
                                        "messages.errors.no-permission",
                                        "&cYou don't have permission."
                                )
                        )
                );

                return true;
            }

            if (args.length != 1) {

                staff.sendMessage(
                        prefix + color(
                                "&cUsage: /pm <player>"
                        )
                );

                return true;
            }

            Player target =
                    Bukkit.getPlayerExact(args[0]);

            if (target == null) {

                staff.sendMessage(
                        prefix + color(
                                getConfig().getString(
                                        "messages.errors.player-not-online",
                                        "&cPlayer must be online."
                                )
                        )
                );

                return true;
            }

            openPunishmentBook(
                    staff,
                    target.getName()
            );

            return true;
        }

        /*
         * /pmapply <player> <punishment>
         */
        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.use")) {

                staff.sendMessage(
                        prefix + color(
                                getConfig().getString(
                                        "messages.errors.no-permission",
                                        "&cYou don't have permission."
                                )
                        )
                );

                return true;
            }

            if (args.length != 2) {

                staff.sendMessage(
                        prefix + color(
                                "&cUsage: /pmapply <player> <punishment>"
                        )
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

        /*
         * /pmrevoke <player>
         */
        if (command.getName().equalsIgnoreCase("pmrevoke")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission(
                    "punishmentsbook.revoke")) {

                staff.sendMessage(
                        prefix + color(
                                getConfig().getString(
                                        "revoke.messages.no-permission",
                                        "&cYou don't have permission to revoke punishments."
                                )
                        )
                );

                return true;
            }

            if (args.length != 1) {

                staff.sendMessage(
                        prefix + color(
                                "&cUsage: /pmrevoke <player>"
                        )
                );

                return true;
            }

            revokePunishment(
                    staff,
                    args[0]
            );

            return true;
        }

        return true;
    }

    /*
     * =========================================================
     * OPEN PUNISHMENT BOOK
     * =========================================================
     */

    private void openPunishmentBook(
            Player player,
            String targetName) {

        try {

            ItemStack nmsBook =
                    createNMSBook(targetName);

            /*
             * Open directly through NMS.
             * Required for Minecraft 1.8.8.
             */
            EntityPlayer entityPlayer =
                    ((CraftPlayer) player).getHandle();

            entityPlayer.openBook(
                    nmsBook
            );

        } catch (Exception e) {

            getLogger().severe(
                    "Could not open punishment book."
            );

            e.printStackTrace();

            player.sendMessage(
                    prefix + color(
                            "&cFailed to open punishment book."
                    )
            );
        }
    }

    /*
     * =========================================================
     * CREATE BOOK
     * =========================================================
     */

    private ItemStack createNMSBook(
            String targetName) {

        org.bukkit.inventory.ItemStack bukkitBook =
                new org.bukkit.inventory.ItemStack(
                        Material.WRITTEN_BOOK
                );

        BookMeta meta =
                (BookMeta) bukkitBook.getItemMeta();

        meta.setTitle(
                getConfig().getString(
                        "book.title",
                        "Punishments"
                )
        );

        meta.setAuthor(
                getConfig().getString(
                        "book.author",
                        "PunishmentsBook"
                )
        );

        ConfigurationSection punishments =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        /*
         * If the section does not exist.
         */
        if (punishments == null) {

            meta.addPage(
                    ChatColor.RED +
                    "No Punishments configured."
            );

            bukkitBook.setItemMeta(meta);

            return CraftItemStack.asNMSCopy(
                    bukkitBook
            );
        }

        /*
         * =====================================================
         * PAGE 1
         * =====================================================
         */

        StringBuilder page =
                new StringBuilder();

        page.append(
                ChatColor.DARK_RED
        );

        page.append(
                ChatColor.BOLD
        );

        page.append(
                "Punishments"
        );

        page.append(
                "\n\n"
        );

        int number = 1;

        for (String id :
                punishments.getKeys(false)) {

            String path =
                    "punishments." + id;

            String name =
                    getConfig().getString(
                            path + ".name",
                            id
                    );

            String type =
                    getConfig().getString(
                            path + ".type",
                            ""
                    );

            String duration =
                    getConfig().getString(
                            path + ".duration",
                            ""
                    );

            String reason =
                    getConfig().getString(
                            path + ".reason",
                            ""
                    );

            page.append(
                    ChatColor.BLACK
            );

            page.append(
                    number
            );

            page.append(
                    ". "
            );

            page.append(
                    ChatColor.DARK_RED
            );

            page.append(
                    ChatColor.BOLD
            );

            page.append(
                    name
            );

            page.append(
                    "\n"
            );

            page.append(
                    ChatColor.GRAY
            );

            page.append(
                    "Type: "
            );

            page.append(
                    ChatColor.BLACK
            );

            page.append(
                    type
            );

            page.append(
                    "\n"
            );

            page.append(
                    ChatColor.GRAY
            );

            page.append(
                    "Duration: "
            );

            page.append(
                    ChatColor.BLACK
            );

            page.append(
                    duration
            );

            page.append(
                    "\n"
            );

            page.append(
                    ChatColor.GRAY
            );

            page.append(
                    "Reason: "
            );

            page.append(
                    ChatColor.BLACK
            );

            page.append(
                    reason
            );

            page.append(
                    "\n\n"
            );

            number++;
        }

        meta.addPage(
                page.toString()
        );

        /*
         * =====================================================
         * DETAILS PAGES
         * =====================================================
         */

        for (String id :
                punishments.getKeys(false)) {

            String path =
                    "punishments." + id;

            String name =
                    getConfig().getString(
                            path + ".name",
                            id
                    );

            String type =
                    getConfig().getString(
                            path + ".type",
                            ""
                    );

            String duration =
                    getConfig().getString(
                            path + ".duration",
                            ""
                    );

            String reason =
                    getConfig().getString(
                            path + ".reason",
                            ""
                    );

            StringBuilder details =
                    new StringBuilder();

            details.append(
                    ChatColor.DARK_RED
            );

            details.append(
                    ChatColor.BOLD
            );

            details.append(
                    name
            );

            details.append(
                    "\n\n"
            );

            details.append(
                    ChatColor.GRAY
            );

            details.append(
                    "Type: "
            );

            details.append(
                    ChatColor.BLACK
            );

            details.append(
                    type
            );

            details.append(
                    "\n"
            );

            details.append(
                    ChatColor.GRAY
            );

            details.append(
                    "Duration: "
            );

            details.append(
                    ChatColor.BLACK
            );

            details.append(
                    duration
            );

            details.append(
                    "\n"
            );

            details.append(
                    ChatColor.GRAY
            );

            details.append(
                    "Reason: "
            );

            details.append(
                    ChatColor.BLACK
            );

            details.append(
                    reason
            );

            details.append(
                    "\n\n"
            );

            details.append(
                    ChatColor.GREEN
            );

            details.append(
                    "Apply:"
            );

            details.append(
                    "\n"
            );

            details.append(
                    ChatColor.BLACK
            );

            details.append(
                    "/pmapply "
            );

            details.append(
                    targetName
            );

            details.append(
                    " "
            );

            details.append(
                    id
            );

            meta.addPage(
                    details.toString()
            );
        }

        bukkitBook.setItemMeta(
                meta
        );

        return CraftItemStack.asNMSCopy(
                bukkitBook
        );
    }

    /*
     * =========================================================
     * APPLY PUNISHMENT
     * =========================================================
     */

    private void applyPunishment(
            Player staff,
            String targetName,
            String punishmentId) {

        String path =
                "punishments." +
                punishmentId;

        if (!getConfig().isConfigurationSection(
                path)) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "messages.errors.unknown-punishment",
                                    "&cUnknown punishment."
                            )
                    )
            );

            return;
        }

        Player target =
                Bukkit.getPlayerExact(
                        targetName
                );

        if (target == null) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "messages.errors.player-not-online",
                                    "&cPlayer must be online."
                            )
                    )
            );

            return;
        }

        String punishment =
                getConfig().getString(
                        path + ".name",
                        punishmentId
                );

        String type =
                getConfig().getString(
                        path + ".type",
                        ""
                );

        String duration =
                getConfig().getString(
                        path + ".duration",
                        ""
                );

        String reason =
                getConfig().getString(
                        path + ".reason",
                        ""
                );

        String command =
                getConfig().getString(
                        path + ".command",
                        ""
                );

        /*
         * Replace variables.
         */

        command =
                command.replace(
                        "%player%",
                        target.getName()
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

        if (command.startsWith("/")) {

            command =
                    command.substring(1);
        }

        if (command.trim().isEmpty()) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "messages.errors.command-failed",
                                    "&cPunishment command failed."
                            )
                    )
            );

            return;
        }

        /*
         * Execute as the staff member.
         *
         * This is important for PunishmentJail.
         */

        boolean success =
                Bukkit.dispatchCommand(
                        staff,
                        command
                );

        if (!success) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "messages.errors.command-failed",
                                    "&cPunishment command failed."
                            )
                    )
            );

            return;
        }

        /*
         * STAFF CHAT
         */

        sendMessage(
                staff,
                "messages.staff.applied",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                staff,
                "messages.staff.player",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                staff,
                "messages.staff.punishment",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                staff,
                "messages.staff.type",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                staff,
                "messages.staff.duration",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                staff,
                "messages.staff.reason",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                staff,
                "messages.staff.staff",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        /*
         * TARGET CHAT
         */

        sendMessage(
                target,
                "messages.target.applied",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                target,
                "messages.target.player",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                target,
                "messages.target.punishment",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                target,
                "messages.target.type",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                target,
                "messages.target.duration",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                target,
                "messages.target.reason",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendMessage(
                target,
                "messages.target.staff",
                target,
                punishment,
                type,
                duration,
                reason,
                staff.getName()
        );
    }

    /*
     * =========================================================
     * REVOKE
     * =========================================================
     */

    private void revokePunishment(
            Player staff,
            String targetName) {

        Player target =
                Bukkit.getPlayerExact(
                        targetName
                );

        if (target == null) {

            staff.sendMessage(
                    prefix + color(
                            getConfig().getString(
                                    "messages.errors.player-not-online",
                                    "&cPlayer must be online."
                            )
                    )
            );

            return;
        }

        /*
         * We don't use Bukkit BanList here.
         *
         * Revoke commands are configured in config.yml.
         */

        String command =
                getConfig().getString(
                        "revoke.mute-command",
                        "unmute %player%"
                );

        command =
                command.replace(
                        "%player%",
                        target.getName()
                );

        command =
                command.replace(
                        "%staff%",
                        staff.getName()
                );

        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        if (!command.trim().isEmpty()) {

            Bukkit.dispatchCommand(
                    staff,
                    command
            );
        }

        String message =
                getConfig().getString(
                        "revoke.messages.success",
                        "&aPunishment for &f%player% &ahas been revoked."
                );

        message =
                message.replace(
                        "%player%",
                        target.getName()
                );

        message =
                message.replace(
                        "%staff%",
                        staff.getName()
                );

        staff.sendMessage(
                prefix + color(message)
        );

        String targetMessage =
                getConfig().getString(
                        "revoke.messages.target",
                        "&aYour punishment has been revoked by &f%staff%&a."
                );

        targetMessage =
                targetMessage.replace(
                        "%staff%",
                        staff.getName()
                );

        target.sendMessage(
                prefix + color(targetMessage)
        );
    }

    /*
     * =========================================================
     * MESSAGES
     * =========================================================
     */

    private void sendMessage(
            Player player,
            String path,
            Player target,
            String punishment,
            String type,
            String duration,
            String reason,
            String staff) {

        String message =
                getConfig().getString(
                        path,
                        ""
                );

        if (message == null ||
                message.isEmpty()) {

            return;
        }

        message =
                message.replace(
                        "%player%",
                        target.getName()
                );

        message =
                message.replace(
                        "%punishment%",
                        punishment
                );

        message =
                message.replace(
                        "%type%",
                        type
                );

        message =
                message.replace(
                        "%duration%",
                        duration
                );

        message =
                message.replace(
                        "%reason%",
                        reason
                );

        message =
                message.replace(
                        "%staff%",
                        staff
                );

        player.sendMessage(
                color(message)
        );
    }

    /*
     * =========================================================
     * UTILITIES
     * =========================================================
     */

    private int getPunishmentCount() {

        ConfigurationSection section =
                getConfig().getConfigurationSection(
                        "punishments"
                );

        if (section == null) {
            return 0;
        }

        return section.getKeys(false).size();
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
