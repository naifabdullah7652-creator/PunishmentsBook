package com.flynexx.punishments;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

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

        getLogger().info("PunishmentsBook enabled.");
        getLogger().info("Punishments loaded: " + getPunishmentCount());

        if (getCommand("pm") != null) {
            getCommand("pm").setExecutor(this);
        }

        if (getCommand("pmapply") != null) {
            getCommand("pmapply").setExecutor(this);
        }

        if (getCommand("pmrevoke") != null) {
            getCommand("pmrevoke").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
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
                staff.sendMessage(prefix + color(
                        getConfig().getString(
                                "messages.errors.no-permission",
                                "&cYou don't have permission."
                        )
                ));
                return true;
            }

            if (args.length != 1) {
                staff.sendMessage(prefix + color(
                        "&cUsage: /pm <player>"
                ));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);

            if (target == null) {
                staff.sendMessage(prefix + color(
                        getConfig().getString(
                                "messages.errors.player-not-online",
                                "&cPlayer must be online."
                        )
                ));
                return true;
            }

            openBook(staff, target.getName());

            return true;
        }

        if (command.getName().equalsIgnoreCase("pmapply")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff = (Player) sender;

            if (args.length != 2) {
                staff.sendMessage(prefix + color(
                        "&cUsage: /pmapply <player> <punishment>"
                ));
                return true;
            }

            applyPunishment(
                    staff,
                    args[0],
                    args[1]
            );

            return true;
        }

        if (command.getName().equalsIgnoreCase("pmrevoke")) {

            if (!(sender instanceof Player)) {
                return true;
            }

            Player staff = (Player) sender;

            if (!staff.hasPermission("punishmentsbook.revoke")) {
                staff.sendMessage(prefix + color(
                        getConfig().getString(
                                "revoke.messages.no-permission",
                                "&cYou don't have permission to revoke punishments."
                        )
                ));
                return true;
            }

            if (args.length != 1) {
                staff.sendMessage(prefix + color(
                        "&cUsage: /pmrevoke <player>"
                ));
                return true;
            }

            revoke(staff, args[0]);

            return true;
        }

        return true;
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

            ItemStack book = new ItemStack(
                    Material.WRITTEN_BOOK
            );

            BookMeta meta =
                    (BookMeta) book.getItemMeta();

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

            List<String> keys =
                    getConfig().getConfigurationSection(
                            "punishments"
                    ).getKeys(false)
                    instanceof List
                    ? null
                    : null;

            /*
             * Build the first page using normal
             * BookMeta text. This is deliberately
             * NOT JSON so the page always renders
             * correctly on 1.8.8.
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

            page.append("\n\n");

            int number = 1;

            for (String id :
                    getConfig()
                            .getConfigurationSection(
                                    "punishments"
                            )
                            .getKeys(false)) {

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

                page.append("\n");

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

                page.append("\n");

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

                page.append("\n");

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

                page.append("\n\n");

                number++;
            }

            meta.addPage(
                    page.toString()
            );

            /*
             * Add one page for every punishment.
             */
            for (String id :
                    getConfig()
                            .getConfigurationSection(
                                    "punishments"
                            )
                            .getKeys(false)) {

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

                details.append("\n\n");

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

                details.append("\n");

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

                details.append("\n");

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

                details.append("\n\n");

                details.append(
                        ChatColor.GREEN
                );

                details.append(
                        "Use:"
                );

                details.append("\n");

                details.append(
                        ChatColor.BLACK
                );

                details.append(
                        "/pmapply "
                );

                details.append(
                        target
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

            book.setItemMeta(meta);

            /*
             * Give the book temporarily to the player,
             * open it, then restore the previous item.
             *
             * This is the reliable 1.8.8 method.
             */

            int slot =
                    player.getInventory()
                            .getHeldItemSlot();

            ItemStack old =
                    player.getInventory()
                            .getItem(slot);

            player.getInventory()
                    .setItem(slot, book);

            player.updateInventory();

            player.openBook(book);

            final ItemStack oldItem = old;
            final int oldSlot = slot;

            Bukkit.getScheduler().runTaskLater(
                    this,
                    new Runnable() {
                        @Override
                        public void run() {

                            Player p =
                                    Bukkit.getPlayer(
                                            player.getUniqueId()
                                    );

                            if (p == null) {
                                return;
                            }

                            p.getInventory()
                                    .setItem(
                                            oldSlot,
                                            oldItem
                                    );

                            p.updateInventory();
                        }
                    },
                    2L
            );

        } catch (Exception e) {

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
     * APPLY
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

        String name =
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
         * IMPORTANT:
         *
         * Execute as the actual staff member.
         * NOT Console.
         *
         * Therefore PunishmentJail sees the
         * real administrator who clicked the book.
         */

        boolean result =
                Bukkit.dispatchCommand(
                        staff,
                        command
                );

        if (!result) {

            staff.sendMessage(
                    prefix + color(
                            "&cPunishment command failed."
                    )
            );

            return;
        }

        sendStaff(
                staff,
                "messages.staff.applied",
                target,
                name,
                type,
                duration,
                reason
        );

        sendStaff(
                staff,
                "messages.staff.player",
                target,
                name,
                type,
                duration,
                reason
        );

        sendStaff(
                staff,
                "messages.staff.punishment",
                target,
                name,
                type,
                duration,
                reason
        );

        sendStaff(
                staff,
                "messages.staff.type",
                target,
                name,
                type,
                duration,
                reason
        );

        sendStaff(
                staff,
                "messages.staff.duration",
                target,
                name,
                type,
                duration,
                reason
        );

        sendStaff(
                staff,
                "messages.staff.reason",
                target,
                name,
                type,
                duration,
                reason
        );

        sendStaff(
                staff,
                "messages.staff.staff",
                target,
                name,
                type,
                duration,
                reason
        );

        sendTarget(
                target,
                "messages.target.applied",
                name,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendTarget(
                target,
                "messages.target.player",
                name,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendTarget(
                target,
                "messages.target.punishment",
                name,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendTarget(
                target,
                "messages.target.type",
                name,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendTarget(
                target,
                "messages.target.duration",
                name,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendTarget(
                target,
                "messages.target.reason",
                name,
                type,
                duration,
                reason,
                staff.getName()
        );

        sendTarget(
                target,
                "messages.target.staff",
                name,
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

    private void revoke(
            Player staff,
            String target) {

        Player player =
                Bukkit.getPlayerExact(target);

        if (player == null) {

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
         * Revoke checks each configured punishment type.
         * The command is executed by the staff member.
         */

        String command =
                getConfig().getString(
                        "revoke.mute-command",
                        "unmute %player%"
                );

        command =
                command.replace(
                        "%player%",
                        player.getName()
                );

        if (command.startsWith("/")) {
            command =
                    command.substring(1);
        }

        Bukkit.dispatchCommand(
                staff,
                command
        );

        staff.sendMessage(
                prefix + color(
                        getConfig().getString(
                                "revoke.messages.success",
                                "&aPunishment revoked."
                        )
                        .replace(
                                "%player%",
                                player.getName()
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        )
                )
        );

        player.sendMessage(
                prefix + color(
                        getConfig().getString(
                                "revoke.messages.target",
                                "&aYour punishment has been revoked by &f%staff%&a."
                        )
                        .replace(
                                "%staff%",
                                staff.getName()
                        )
                )
        );
    }

    /*
     * =========================================================
     * MESSAGES
     * =========================================================
     */

    private void sendStaff(
            Player staff,
            String path,
            Player target,
            String punishment,
            String type,
            String duration,
            String reason) {

        String message =
                getConfig().getString(
                        path,
                        ""
                );

        if (message.isEmpty()) {
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
                        staff.getName()
                );

        staff.sendMessage(
                color(message)
        );
    }

    private void sendTarget(
            Player target,
            String path,
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

        if (message.isEmpty()) {
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

        target.sendMessage(
                color(message)
        );
    }

    /*
     * =========================================================
     * UTILS
     * =========================================================
     */

    private int getPunishmentCount() {

        if (getConfig().getConfigurationSection(
                "punishments"
        ) == null) {

            return 0;
        }

        return getConfig()
                .getConfigurationSection(
                        "punishments"
                )
                .getKeys(false)
                .size();
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
