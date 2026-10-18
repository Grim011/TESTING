package com.grimesmp;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GrimeCommand implements CommandExecutor, TabCompleter {

    private final GrimeSMP plugin;
    private final GrimeGui gui;

    public GrimeCommand(GrimeSMP plugin, GrimeGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage(GrimeSMP.LEG.deserialize(GrimeSMP.PREFIX + text));
    }

    private void raw(CommandSender s, String text) {
        s.sendMessage(GrimeSMP.LEG.deserialize(text));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) return cached;
        return Bukkit.getOfflinePlayer(name);
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ dispatch

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p && p.hasPermission("grime.admin")) gui.openMain(p);
            else standings(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "standings", "top" -> standings(sender);
            case "balance", "bal" -> balance(sender, args);
            case "roster", "teams" -> roster(sender);
            default -> {
                if (!sender.hasPermission("grime.admin")) {
                    msg(sender, "&cYou don't have permission to do that.");
                    return true;
                }
                admin(sender, sub, args);
            }
        }
        return true;
    }

    private void admin(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "menu" -> {
                if (sender instanceof Player p) gui.openMain(p);
                else msg(sender, "&cPlayers only.");
            }
            case "set" -> cmdSet(sender, args);
            case "clear" -> cmdClear(sender, args);
            case "points" -> cmdPoints(sender, args);
            case "bonus" -> cmdBonus(sender, args);
            case "event" -> cmdEvent(sender, args);
            case "reset" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    msg(sender, "&cThis wipes ALL Grime balances and team bonuses (teams stay). "
                            + "Run &f/grime reset confirm &cto proceed.");
                } else {
                    plugin.resetPoints();
                    msg(sender, "&aAll Grime balances reset to 0.");
                }
            }
            default -> help(sender);
        }
    }

    private void help(CommandSender s) {
        raw(s, "&6&lGrime SMP commands");
        raw(s, "&e/grime &7- open the admin menu (or standings)");
        raw(s, "&e/grime set <team> <tier> <player> &7- place a player in a slot");
        raw(s, "&e/grime clear <player> &7or &e/grime clear <team> <tier>");
        raw(s, "&e/grime points <add|remove|set> <player> <amount>");
        raw(s, "&e/grime bonus <add|remove|set> <team> <amount> &7- team event wins");
        raw(s, "&e/grime event <start|stop>");
        raw(s, "&e/grime reset confirm &7- zero all balances");
        raw(s, "&e/grime standings&7, &e/grime balance [player]&7, &e/grime roster");
    }

    // ------------------------------------------------------------------ admin subcommands

    private void cmdSet(CommandSender s, String[] a) {
        if (a.length < 4) {
            msg(s, "&cUsage: /grime set <aces|clubs|spades|diamonds> <S|A|B|C|D|F> <player>");
            return;
        }
        GrimeTeam team = GrimeTeam.parse(a[1]);
        Tier tier = Tier.parse(a[2]);
        if (team == null || tier == null) {
            msg(s, "&cUnknown team or tier.");
            return;
        }
        OfflinePlayer target = resolve(a[3]);
        String name = target.getName() != null ? target.getName() : a[3];
        plugin.assign(team, tier, target.getUniqueId(), name);
        msg(s, "&f" + name + " &7is now &f" + tier + " Tier &7on " + team.legacy + team.display + "&7.");
    }

    private void cmdClear(CommandSender s, String[] a) {
        if (a.length == 3) {
            GrimeTeam team = GrimeTeam.parse(a[1]);
            Tier tier = Tier.parse(a[2]);
            if (team == null || tier == null) {
                msg(s, "&cUnknown team or tier.");
                return;
            }
            plugin.clearSlot(team, tier);
            msg(s, "&7Cleared " + team.legacy + team.display + " &7" + tier + " Tier slot.");
        } else if (a.length == 2) {
            OfflinePlayer t = resolve(a[1]);
            if (plugin.clearPlayer(t.getUniqueId())) msg(s, "&7Removed &f" + a[1] + " &7from their team.");
            else msg(s, "&c" + a[1] + " isn't on a team.");
        } else {
            msg(s, "&cUsage: /grime clear <player>  or  /grime clear <team> <tier>");
        }
    }

    private void cmdPoints(CommandSender s, String[] a) {
        if (a.length < 4) {
            msg(s, "&cUsage: /grime points <add|remove|set> <player> <amount>");
            return;
        }
        Integer n = parseInt(a[3]);
        if (n == null) {
            msg(s, "&cAmount must be a whole number.");
            return;
        }
        OfflinePlayer t = resolve(a[2]);
        UUID id = t.getUniqueId();
        switch (a[1].toLowerCase()) {
            case "add" -> plugin.addPoints(id, n);
            case "remove" -> plugin.addPoints(id, -n);
            case "set" -> plugin.setPoints(id, n);
            default -> {
                msg(s, "&cUse add, remove or set.");
                return;
            }
        }
        msg(s, "&f" + a[2] + " &7now has &e" + plugin.getPoints(id) + " Grime&7.");
    }

    private void cmdBonus(CommandSender s, String[] a) {
        if (a.length < 4) {
            msg(s, "&cUsage: /grime bonus <add|remove|set> <team> <amount>");
            return;
        }
        GrimeTeam team = GrimeTeam.parse(a[2]);
        Integer n = parseInt(a[3]);
        if (team == null || n == null) {
            msg(s, "&cUnknown team or invalid amount.");
            return;
        }
        switch (a[1].toLowerCase()) {
            case "add" -> plugin.setBonus(team, plugin.getBonus(team) + n);
            case "remove" -> plugin.setBonus(team, plugin.getBonus(team) - n);
            case "set" -> plugin.setBonus(team, n);
            default -> {
                msg(s, "&cUse add, remove or set.");
                return;
            }
        }
        msg(s, team.legacy + team.display + " &7bonus is now &e" + plugin.getBonus(team)
                + " &7(team total &e" + plugin.teamTotal(team) + "&7).");
    }

    private void cmdEvent(CommandSender s, String[] a) {
        if (a.length < 2) {
            msg(s, "&7The event is currently " + (plugin.isEventActive() ? "&aLIVE" : "&cPAUSED") + "&7.");
            return;
        }
        switch (a[1].toLowerCase()) {
            case "start" -> {
                plugin.setEventActive(true);
                Bukkit.broadcast(GrimeSMP.LEG.deserialize(GrimeSMP.PREFIX
                        + "&aThe event has started! Kills now earn Grime."));
            }
            case "stop", "pause" -> {
                plugin.setEventActive(false);
                Bukkit.broadcast(GrimeSMP.LEG.deserialize(GrimeSMP.PREFIX
                        + "&cThe event is paused. Kills no longer earn Grime."));
            }
            default -> msg(s, "&cUsage: /grime event <start|stop>");
        }
    }

    // ------------------------------------------------------------------ public subcommands

    private void standings(CommandSender s) {
        raw(s, "&6&m      &r &6&lGRIME STANDINGS &6&m      ");
        int rank = 1;
        for (GrimeTeam t : plugin.sortedTeams()) {
            raw(s, "&e" + rank++ + ". " + t.legacy + "&l" + t.display + " &7- &f" + plugin.teamTotal(t) + " Grime");
        }
        List<Map.Entry<UUID, Integer>> top = plugin.topPlayers(5);
        if (!top.isEmpty()) {
            raw(s, "&7Top players:");
            for (var e : top) {
                GrimeSMP.Slot sl = plugin.findSlot(e.getKey());
                String team = sl == null ? "" : " " + sl.team().legacy + "(" + sl.team().display + ")";
                raw(s, "&8- &f" + plugin.playerName(e.getKey()) + team + " &7- &e" + e.getValue());
            }
        }
    }

    private void balance(CommandSender s, String[] a) {
        OfflinePlayer target;
        String name;
        if (a.length > 1) {
            target = resolve(a[1]);
            name = a[1];
        } else if (s instanceof Player p) {
            target = p;
            name = p.getName();
        } else {
            msg(s, "&cUsage: /grime balance <player>");
            return;
        }
        GrimeSMP.Slot sl = plugin.findSlot(target.getUniqueId());
        String where = sl == null ? "" : " &8(" + sl.team().legacy + sl.team().display + " &7" + sl.tier() + " Tier&8)";
        msg(s, "&f" + name + " &7has &e" + plugin.getPoints(target.getUniqueId()) + " Grime" + where);
    }

    private void roster(CommandSender s) {
        for (GrimeTeam t : GrimeTeam.values()) {
            raw(s, t.legacy + "&l" + t.display + " &7(" + plugin.teamTotal(t) + " Grime)");
            for (Tier tr : Tier.values()) {
                UUID id = plugin.getOccupant(t, tr);
                String who = id == null ? "&8empty" : "&f" + plugin.playerName(id) + " &7- &e" + plugin.getPoints(id);
                raw(s, "  &7" + tr + " Tier (" + tr.points(plugin) + "): " + who);
            }
        }
    }

    // ------------------------------------------------------------------ tab completion

    private List<String> filter(List<String> options, String typed) {
        String low = typed.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(low)).toList();
    }

    private List<String> teamNames() {
        return Arrays.stream(GrimeTeam.values()).map(t -> t.name().toLowerCase()).toList();
    }

    private List<String> tierNames() {
        return Arrays.stream(Tier.values()).map(Enum::name).toList();
    }

    private List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        boolean admin = s.hasPermission("grime.admin");
        if (a.length == 1) {
            List<String> subs = new ArrayList<>(List.of("standings", "balance", "roster"));
            if (admin) subs.addAll(List.of("menu", "set", "clear", "points", "bonus", "event", "reset"));
            return filter(subs, a[0]);
        }
        String sub = a[0].toLowerCase();
        if (sub.equals("balance") && a.length == 2) return filter(onlineNames(), a[1]);
        if (!admin) return List.of();

        switch (sub) {
            case "set" -> {
                if (a.length == 2) return filter(teamNames(), a[1]);
                if (a.length == 3) return filter(tierNames(), a[2]);
                if (a.length == 4) return filter(onlineNames(), a[3]);
            }
            case "clear" -> {
                if (a.length == 2) {
                    List<String> opts = new ArrayList<>(teamNames());
                    opts.addAll(onlineNames());
                    return filter(opts, a[1]);
                }
                if (a.length == 3) return filter(tierNames(), a[2]);
            }
            case "points" -> {
                if (a.length == 2) return filter(List.of("add", "remove", "set"), a[1]);
                if (a.length == 3) return filter(onlineNames(), a[2]);
            }
            case "bonus" -> {
                if (a.length == 2) return filter(List.of("add", "remove", "set"), a[1]);
                if (a.length == 3) return filter(teamNames(), a[2]);
            }
            case "event" -> {
                if (a.length == 2) return filter(List.of("start", "stop"), a[1]);
            }
            case "reset" -> {
                if (a.length == 2) return filter(List.of("confirm"), a[1]);
            }
            default -> { }
        }
        return List.of();
    }
}
