package com.grimesmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class GrimeSMP extends JavaPlugin implements Listener {

    public static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacyAmpersand();
    public static final String PREFIX = "&6[Grime] &r";

    public record Slot(GrimeTeam team, Tier tier) {}

    private final Map<GrimeTeam, EnumMap<Tier, UUID>> slots = new EnumMap<>(GrimeTeam.class);
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<GrimeTeam, Integer> bonus = new EnumMap<>(GrimeTeam.class);
    /** victim UUID -> time (ms) they last gave out points. Killing them again within the cooldown awards nothing. */
    private final Map<UUID, Long> victimCooldowns = new HashMap<>();
    private final Map<GrimeTeam, Team> sbTeams = new EnumMap<>(GrimeTeam.class);

    private boolean eventActive = false;
    private File dataFile;
    private Scoreboard board;
    private Objective sidebar;
    private GrimeGui gui;

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (GrimeTeam t : GrimeTeam.values()) {
            slots.put(t, new EnumMap<>(Tier.class));
            bonus.put(t, 0);
        }
        dataFile = new File(getDataFolder(), "data.yml");
        load();
        setupBoard();

        gui = new GrimeGui(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);

        GrimeCommand cmd = new GrimeCommand(this, gui);
        var command = Objects.requireNonNull(getCommand("grime"));
        command.setExecutor(cmd);
        command.setTabCompleter(cmd);

        getServer().getScheduler().runTaskTimer(this, this::refreshDisplay, 20L, 40L);
        getLogger().info("GrimeSMP enabled. Use /grime to open the admin menu.");
    }

    @Override
    public void onDisable() {
        save();
        for (Team t : sbTeams.values()) {
            try { t.unregister(); } catch (IllegalStateException ignored) { }
        }
        if (sidebar != null) {
            try { sidebar.unregister(); } catch (IllegalStateException ignored) { }
        }
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        eventActive = y.getBoolean("eventActive", false);

        ConfigurationSection ns = y.getConfigurationSection("names");
        if (ns != null) for (String k : ns.getKeys(false)) names.put(UUID.fromString(k), ns.getString(k, "Unknown"));

        ConfigurationSection ps = y.getConfigurationSection("points");
        if (ps != null) for (String k : ps.getKeys(false)) points.put(UUID.fromString(k), ps.getInt(k));

        for (GrimeTeam t : GrimeTeam.values()) {
            bonus.put(t, y.getInt("bonus." + t.name(), 0));
            for (Tier tr : Tier.values()) {
                String v = y.getString("slots." + t.name() + "." + tr.name());
                if (v != null) slots.get(t).put(tr, UUID.fromString(v));
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("eventActive", eventActive);
        for (var e : names.entrySet()) y.set("names." + e.getKey(), e.getValue());
        for (var e : points.entrySet()) y.set("points." + e.getKey(), e.getValue());
        for (GrimeTeam t : GrimeTeam.values()) {
            y.set("bonus." + t.name(), bonus.get(t));
            for (var e : slots.get(t).entrySet()) {
                y.set("slots." + t.name() + "." + e.getKey().name(), e.getValue().toString());
            }
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (IOException ex) {
            getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ team / slot API

    public Slot findSlot(UUID id) {
        for (GrimeTeam t : GrimeTeam.values()) {
            for (var e : slots.get(t).entrySet()) {
                if (e.getValue().equals(id)) return new Slot(t, e.getKey());
            }
        }
        return null;
    }

    public UUID getOccupant(GrimeTeam team, Tier tier) {
        return slots.get(team).get(tier);
    }

    public int filledCount(GrimeTeam team) {
        return slots.get(team).size();
    }

    public String playerName(UUID id) {
        return names.getOrDefault(id, "Unknown");
    }

    /** Puts a player into a slot. Moves them if they already had one; replaces whoever was there. */
    public void assign(GrimeTeam team, Tier tier, UUID id, String name) {
        Slot old = findSlot(id);
        if (old != null) slots.get(old.team()).remove(old.tier());
        slots.get(team).put(tier, id);
        names.put(id, name);
        save();
        syncMembers();
        refreshDisplay();
    }

    public void clearSlot(GrimeTeam team, Tier tier) {
        slots.get(team).remove(tier);
        save();
        syncMembers();
        refreshDisplay();
    }

    public boolean clearPlayer(UUID id) {
        Slot s = findSlot(id);
        if (s == null) return false;
        clearSlot(s.team(), s.tier());
        return true;
    }

    // ------------------------------------------------------------------ points API

    public int getPoints(UUID id) {
        return points.getOrDefault(id, 0);
    }

    public void setPoints(UUID id, int amount) {
        points.put(id, Math.max(0, amount));
        save();
        refreshDisplay();
    }

    public void addPoints(UUID id, int amount) {
        setPoints(id, getPoints(id) + amount);
    }

    public int getBonus(GrimeTeam t) {
        return bonus.get(t);
    }

    public void setBonus(GrimeTeam t, int amount) {
        bonus.put(t, amount);
        save();
        refreshDisplay();
    }

    /** Team balance = every member's Grime + any manual team bonus (for team event wins). */
    public int teamTotal(GrimeTeam t) {
        int sum = bonus.get(t);
        for (UUID id : slots.get(t).values()) sum += getPoints(id);
        return sum;
    }

    public List<GrimeTeam> sortedTeams() {
        List<GrimeTeam> list = new ArrayList<>(Arrays.asList(GrimeTeam.values()));
        list.sort(Comparator.comparingInt(this::teamTotal).reversed());
        return list;
    }

    public List<Map.Entry<UUID, Integer>> topPlayers(int limit) {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(points.entrySet());
        list.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }

    public void resetPoints() {
        points.clear();
        for (GrimeTeam t : GrimeTeam.values()) bonus.put(t, 0);
        victimCooldowns.clear();
        save();
        refreshDisplay();
    }

    public boolean isEventActive() {
        return eventActive;
    }

    public void setEventActive(boolean active) {
        eventActive = active;
        save();
        refreshDisplay();
    }

    // ------------------------------------------------------------------ scoreboard / tab list

    private void setupBoard() {
        board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (GrimeTeam t : GrimeTeam.values()) {
            Team old = board.getTeam(t.sbName);
            if (old != null) old.unregister();
            Team st = board.registerNewTeam(t.sbName);
            st.color(t.color);
            st.prefix(Component.text("[" + t.symbol + "] ", t.color));
            sbTeams.put(t, st);
        }
        Objective old = board.getObjective("grime_smp");
        if (old != null) old.unregister();
        sidebar = board.registerNewObjective("grime_smp", Criteria.DUMMY,
                Component.text("GRIME SMP", NamedTextColor.GOLD, TextDecoration.BOLD));
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        syncMembers();
    }

    /** Rebuilds which player names belong to which scoreboard team (tab colour + prefix). */
    private void syncMembers() {
        for (GrimeTeam t : GrimeTeam.values()) {
            Team st = sbTeams.get(t);
            for (String entry : new ArrayList<>(st.getEntries())) st.removeEntry(entry);
            for (UUID id : slots.get(t).values()) {
                String n = names.get(id);
                if (n != null) st.addEntry(n);
            }
        }
    }

    private String sidebarEntry(GrimeTeam t) {
        return t.legacy.replace('&', '\u00a7') + t.symbol + " " + t.display;
    }

    public void refreshDisplay() {
        if (sidebar == null) return;
        for (GrimeTeam t : GrimeTeam.values()) {
            sidebar.getScore(sidebarEntry(t)).setScore(teamTotal(t));
        }

        String status = eventActive ? "&aEVENT LIVE" : "&cEVENT PAUSED";
        Component header = LEG.deserialize("&6&lGRIME SMP\n" + status + "\n");

        StringBuilder standings = new StringBuilder("\n");
        for (GrimeTeam t : sortedTeams()) {
            standings.append(t.legacy).append("&l").append(t.symbol).append(' ').append(t.display)
                    .append("&7: &f").append(teamTotal(t)).append('\n');
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            Slot s = findSlot(p.getUniqueId());
            String you = s == null
                    ? "&7Not on a team"
                    : s.team().legacy + "&l" + s.team().display + " &7(" + s.tier() + " Tier)";
            String footer = standings + you + "\n&7Your Grime: &e" + getPoints(p.getUniqueId()) + "\n";
            p.sendPlayerListHeaderAndFooter(header, LEG.deserialize(footer));
        }
    }

    // ------------------------------------------------------------------ listeners

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Slot s = findSlot(p.getUniqueId());
        if (s != null && !p.getName().equals(names.get(p.getUniqueId()))) {
            names.put(p.getUniqueId(), p.getName()); // handle name changes
            save();
            syncMembers();
        }
        Bukkit.getScheduler().runTaskLater(this, this::refreshDisplay, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        if (!eventActive) return;
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        Slot vs = findSlot(victim.getUniqueId());
        Slot ks = findSlot(killer.getUniqueId());
        if (vs == null || ks == null || vs.team() == ks.team()) return;

        long cooldownMs = getConfig().getLong("kill-cooldown-seconds", 900L) * 1000L;
        long now = System.currentTimeMillis();
        Long last = victimCooldowns.get(victim.getUniqueId());
        if (cooldownMs > 0 && last != null && now - last < cooldownMs) {
            long left = (cooldownMs - (now - last) + 999) / 1000;
            killer.sendMessage(LEG.deserialize(PREFIX + "&c" + victim.getName()
                    + " was killed recently, so no Grime this time (&f" + (left / 60) + "m " + (left % 60) + "s &cleft)."));
            return;
        }
        victimCooldowns.put(victim.getUniqueId(), now);

        int pts = vs.tier().points(this);
        addPoints(killer.getUniqueId(), pts);

        Bukkit.broadcast(LEG.deserialize(PREFIX
                + ks.team().legacy + killer.getName() + " &7(" + ks.team().display + ") killed "
                + vs.team().legacy + victim.getName() + " &7(" + vs.team().display + " " + vs.tier() + " Tier) "
                + "&a+" + pts + " Grime"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (getConfig().getBoolean("friendly-fire", true)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.equals(victim)) return;

        Slot a = findSlot(attacker.getUniqueId());
        Slot v = findSlot(victim.getUniqueId());
        if (a != null && v != null && a.team() == v.team()) e.setCancelled(true);
    }
}
