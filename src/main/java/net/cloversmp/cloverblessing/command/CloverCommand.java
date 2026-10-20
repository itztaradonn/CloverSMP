package net.cloversmp.cloverblessing.command;

import net.cloversmp.cloverblessing.CloverPlugin;
import net.cloversmp.cloverblessing.blessing.BlessingManager;
import net.cloversmp.cloverblessing.blessing.BlessingRegistry;
import net.cloversmp.cloverblessing.charge.ChargeManager;
import net.cloversmp.cloverblessing.model.*;
import net.cloversmp.cloverblessing.party.PartyManager;
import net.cloversmp.cloverblessing.storage.DataManager;
import net.cloversmp.cloverblessing.ui.Menus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

/**
 * {@code /clover} — opens the Clover menu; subcommands: inspect, party, admin.
 */
public class CloverCommand implements CommandExecutor, TabCompleter {

    private final CloverPlugin plugin;
    private final DataManager data;
    private final BlessingRegistry registry;
    private final BlessingManager blessings;
    private final ChargeManager charge;
    private final PartyManager parties;
    private final Menus menus;

    public CloverCommand(CloverPlugin plugin, DataManager data, BlessingRegistry registry, BlessingManager blessings,
                         ChargeManager charge, PartyManager parties, Menus menus) {
        this.plugin = plugin;
        this.data = data;
        this.registry = registry;
        this.blessings = blessings;
        this.charge = charge;
        this.parties = parties;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (sender instanceof Player p) menus.openClover(p);
                else help(sender);
            }
            case "inspect" -> inspect(sender, args);
            case "party" -> party(sender, args);
            case "admin" -> admin(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        msg(s, "/clover — open your clover", NamedTextColor.GREEN);
        msg(s, "/clover inspect <player> — see someone's leaves", NamedTextColor.GRAY);
        msg(s, "/clover party invite|accept|leave|kick|list", NamedTextColor.GRAY);
        if (s.hasPermission("clover.admin")) {
            msg(s, "/clover admin give|setcharge|reset|reload ...", NamedTextColor.GRAY);
        }
    }

    // --- inspect: leaf count and category icons only; a curse is shown in full ---

    private void inspect(CommandSender s, String[] args) {
        if (args.length < 2) {
            msg(s, "Usage: /clover inspect <player>", NamedTextColor.RED);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        Clover clover = target == null ? null : data.getClover(target.getUniqueId());
        if (clover == null) {
            msg(s, "That player is not online.", NamedTextColor.RED);
            return;
        }
        TextComponent.Builder line = Component.text()
                .append(Component.text(target.getName() + " · " + charge.getLeafCount(clover) + " leaves · ", NamedTextColor.GREEN));
        for (LeafSlot slot : LeafSlot.values()) {
            if (slot.isCurse()) continue;
            Blessing b = clover.getEquippedBlessing(slot);
            BlessingCore core = b == null ? null : registry.getCore(b.getCoreId());
            boolean open = charge.isLeafActive(clover, slot);
            line.append(core != null && open
                    ? Component.text(core.getCategory().getIcon() + " ", Menus.categoryColor(core.getCategory()))
                    : Component.text(open ? "· " : "🍂 ", NamedTextColor.DARK_GRAY));
        }
        s.sendMessage(line.build());

        Blessing curse = clover.getEquippedBlessing(LeafSlot.CURSE);
        BlessingCore cc = curse == null ? null : registry.getCore(curse.getCoreId());
        if (cc != null && charge.isLeafActive(clover, LeafSlot.CURSE)) {
            s.sendMessage(Component.text("☠ " + cc.getDisplayName() + ": ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text(cc.getWeaknessText(), NamedTextColor.RED)));
        }
    }

    // --- party ---

    private void party(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            msg(s, "Only players can use parties.", NamedTextColor.RED);
            return;
        }
        int max = plugin.getConfig().getInt("party.max-size", 4);
        String action = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "invite" -> {
                Player target = args.length < 3 ? null : Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    msg(p, "Usage: /clover party invite <online player>", NamedTextColor.RED);
                    return;
                }
                switch (parties.invite(p.getUniqueId(), target.getUniqueId(), max)) {
                    case OK -> {
                        msg(p, "Invited " + target.getName() + ".", NamedTextColor.GREEN);
                        target.sendMessage(Component.text(p.getName() + " invited you to a party. ", NamedTextColor.GREEN)
                                .append(Component.text("[Accept]", NamedTextColor.YELLOW)
                                        .clickEvent(ClickEvent.runCommand("/clover party accept"))));
                    }
                    case NOT_LEADER -> msg(p, "Only the party leader can invite.", NamedTextColor.RED);
                    case TARGET_IN_PARTY -> msg(p, target.getName() + " is already in a party.", NamedTextColor.RED);
                    case FULL -> msg(p, "Your party is full (" + max + ").", NamedTextColor.RED);
                    case SELF -> msg(p, "You can't invite yourself.", NamedTextColor.RED);
                }
            }
            case "accept" -> {
                UUID leader = parties.accept(p.getUniqueId(), max);
                if (leader == null) {
                    msg(p, "You have no valid party invite.", NamedTextColor.RED);
                    return;
                }
                tellParty(p.getUniqueId(), p.getName() + " joined the party.");
            }
            case "leave" -> {
                if (parties.getParty(p.getUniqueId()) == null) {
                    msg(p, "You're not in a party.", NamedTextColor.RED);
                    return;
                }
                tellParty(p.getUniqueId(), p.getName() + " left the party.");
                parties.leave(p.getUniqueId());
            }
            case "kick" -> {
                OfflinePlayer target = args.length < 3 ? null : Bukkit.getOfflinePlayerIfCached(args[2]);
                if (target == null || !parties.kick(p.getUniqueId(), target.getUniqueId())) {
                    msg(p, "Only the leader can kick, and only party members.", NamedTextColor.RED);
                    return;
                }
                msg(p, "Kicked " + target.getName() + ".", NamedTextColor.YELLOW);
            }
            default -> {
                var party = parties.getParty(p.getUniqueId());
                if (party == null) {
                    msg(p, "You're not in a party. /clover party invite <player>", NamedTextColor.GRAY);
                    return;
                }
                List<String> names = new ArrayList<>();
                for (UUID id : party.getMembers()) {
                    String name = Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString());
                    names.add(id.equals(party.getLeader()) ? name + " (leader)" : name);
                }
                msg(p, "Party: " + String.join(", ", names), NamedTextColor.GREEN);
            }
        }
    }

    private void tellParty(UUID member, String text) {
        var party = parties.getParty(member);
        if (party == null) return;
        for (UUID id : party.getMembers()) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) msg(m, text, NamedTextColor.GREEN);
        }
    }

    // --- admin ---

    private void admin(CommandSender s, String[] args) {
        if (!s.hasPermission("clover.admin")) {
            msg(s, "No permission.", NamedTextColor.RED);
            return;
        }
        String action = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        if (action.equals("reload")) {
            if (!require(s, "clover.admin.reload")) return;
            plugin.reloadConfig();
            msg(s, "Clover config reloaded.", NamedTextColor.GREEN);
            return;
        }
        Player target = args.length < 3 ? null : Bukkit.getPlayerExact(args[2]);
        Clover clover = target == null ? null : data.getClover(target.getUniqueId());
        if (clover == null) {
            msg(s, "Usage: /clover admin <give|setcharge|reset> <online player> ...", NamedTextColor.RED);
            return;
        }
        switch (action) {
            case "give" -> {
                if (args.length < 4) {
                    msg(s, "Usage: /clover admin give <player> <core> [graft]", NamedTextColor.RED);
                    return;
                }
                Graft graft = null;
                if (args.length >= 5) {
                    try {
                        graft = Graft.valueOf(args[4].toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        msg(s, "Unknown graft. Use: quick, deep, hunter, vengeful, ward.", NamedTextColor.RED);
                        return;
                    }
                }
                Blessing b = blessings.give(clover, args[3].toLowerCase(Locale.ROOT), graft);
                if (b == null) {
                    msg(s, "Unknown core, or curses can't take grafts.", NamedTextColor.RED);
                    return;
                }
                BlessingCore core = registry.getCore(b.getCoreId());
                msg(s, "Gave " + target.getName() + " " + core.getDisplayName()
                        + (graft != null ? " (" + graft.getDisplayName() + ")" : "") + ".", NamedTextColor.GREEN);
                if (s != target) msg(target, "You received " + core.getDisplayName() + ". Open /clover to equip it.", NamedTextColor.GREEN);
            }
            case "setcharge" -> {
                if (!require(s, "clover.admin.setcharge")) return;
                try {
                    charge.setCharge(clover, Integer.parseInt(args[3]));
                    msg(s, target.getName() + " now has " + clover.getCharge() + " charge.", NamedTextColor.GREEN);
                } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
                    msg(s, "Usage: /clover admin setcharge <player> <amount>", NamedTextColor.RED);
                }
            }
            case "reset" -> {
                if (!require(s, "clover.admin.reset")) return;
                clover.clearBlessings();
                clover.setCharge(0);
                clover.setLeaf2Permanent(false);
                clover.setPollen(0);
                registry.getAllHandlers().values().forEach(h -> h.clear(target.getUniqueId()));
                plugin.getLogger().info(s.getName() + " reset the clover of " + target.getName());
                msg(s, "Reset " + target.getName() + "'s clover.", NamedTextColor.YELLOW);
            }
            default -> msg(s, "Usage: /clover admin <give|setcharge|reset|reload>", NamedTextColor.RED);
        }
    }

    private static boolean require(CommandSender s, String permission) {
        if (s.hasPermission(permission)) return true;
        msg(s, "No permission.", NamedTextColor.RED);
        return false;
    }

    private static void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    // --- tab completion ---

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> options = switch (args.length) {
            case 1 -> sender.hasPermission("clover.admin") ? List.of("inspect", "party", "admin") : List.of("inspect", "party");
            case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "inspect" -> onlineNames();
                case "party" -> List.of("invite", "accept", "leave", "kick", "list");
                case "admin" -> sender.hasPermission("clover.admin") ? List.of("give", "setcharge", "reset", "reload") : List.of();
                default -> List.of();
            };
            case 3 -> args[0].equalsIgnoreCase("admin") || args[1].equalsIgnoreCase("invite")
                    || args[1].equalsIgnoreCase("kick") ? onlineNames() : List.of();
            case 4 -> args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("give")
                    ? registry.getAllCores().stream().map(BlessingCore::getId).toList() : List.of();
            case 5 -> args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("give")
                    ? Stream.of(Graft.values()).map(g -> g.name().toLowerCase(Locale.ROOT)).toList() : List.of();
            default -> List.of();
        };
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
