package com.grimesmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class GrimeGui implements Listener {

    private enum Type { MAIN, TEAM, PICK }

    private static final class Holder implements InventoryHolder {
        final Type type;
        final GrimeTeam team;
        final Tier tier;
        final int page;
        final List<UUID> shown = new ArrayList<>();
        Inventory inv;

        Holder(Type type, GrimeTeam team, Tier tier, int page) {
            this.type = type;
            this.team = team;
            this.tier = tier;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int[] MAIN_SLOTS = {10, 12, 14, 16};
    private static final int[] TIER_SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int PAGE_SIZE = 45;

    private final GrimeSMP plugin;

    public GrimeGui(GrimeSMP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ helpers

    private Component c(String s) {
        return GrimeSMP.LEG.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(this::c).toList());
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack head(UUID id, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        meta.displayName(c(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(this::c).toList());
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    // ------------------------------------------------------------------ screens

    public void openMain(Player p) {
        Holder h = new Holder(Type.MAIN, null, null, 0);
        Inventory inv = Bukkit.createInventory(h, 27, Component.text("Grime SMP - Teams"));
        h.inv = inv;
        fill(inv);

        GrimeTeam[] teams = GrimeTeam.values();
        for (int i = 0; i < teams.length; i++) {
            GrimeTeam t = teams[i];
            inv.setItem(MAIN_SLOTS[i], item(t.icon, t.legacy + "&l" + t.display,
                    "&7Players: &f" + plugin.filledCount(t) + "/6",
                    "&7Team Grime: &e" + plugin.teamTotal(t),
                    "",
                    "&eClick to edit this roster"));
        }

        inv.setItem(22, plugin.isEventActive()
                ? item(Material.LIME_DYE, "&aEvent: LIVE", "&7Kills are earning Grime.", "", "&eClick to pause")
                : item(Material.RED_DYE, "&cEvent: PAUSED", "&7Kills do NOT earn Grime.", "", "&eClick to start"));
        p.openInventory(inv);
    }

    public void openTeam(Player p, GrimeTeam team) {
        Holder h = new Holder(Type.TEAM, team, null, 0);
        Inventory inv = Bukkit.createInventory(h, 27,
                GrimeSMP.LEG.deserialize(team.legacy + "&l" + team.display + " &8- Roster"));
        h.inv = inv;
        fill(inv);

        Tier[] tiers = Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            Tier tr = tiers[i];
            int pts = tr.points(plugin);
            UUID id = plugin.getOccupant(team, tr);
            if (id == null) {
                inv.setItem(TIER_SLOTS[i], item(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "&7" + tr + " Tier &8- &cEMPTY",
                        "&7Kill value: &f" + pts + " Grime",
                        "",
                        "&eLeft-click to assign a player"));
            } else {
                inv.setItem(TIER_SLOTS[i], head(id,
                        team.legacy + "&l" + tr + " Tier &8- &f" + plugin.playerName(id),
                        "&7Kill value: &f" + pts + " Grime",
                        "&7Balance: &e" + plugin.getPoints(id) + " Grime",
                        "",
                        "&eLeft-click to replace",
                        "&cRight-click to remove"));
            }
        }

        inv.setItem(4, item(team.icon, team.legacy + "&l" + team.display,
                "&7Team Grime: &e" + plugin.teamTotal(team)));
        inv.setItem(22, item(Material.ARROW, "&7Back"));
        p.openInventory(inv);
    }

    public void openPicker(Player p, GrimeTeam team, Tier tier, int page) {
        Holder h = new Holder(Type.PICK, team, tier, page);
        Inventory inv = Bukkit.createInventory(h, 54,
                GrimeSMP.LEG.deserialize(team.legacy + "&l" + team.display + " &8- Pick " + tier + " Tier"));
        h.inv = inv;

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < online.size(); i++) {
            Player t = online.get(start + i);
            h.shown.add(t.getUniqueId());
            GrimeSMP.Slot cur = plugin.findSlot(t.getUniqueId());
            String where = cur == null
                    ? "&7Currently: unassigned"
                    : "&cCurrently: " + cur.team().display + " " + cur.tier() + " (will be moved)";
            inv.setItem(i, head(t.getUniqueId(), "&f" + t.getName(), where, "", "&aClick to place here"));
        }

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = PAGE_SIZE; i < 54; i++) inv.setItem(i, filler);
        if (page > 0) inv.setItem(45, item(Material.ARROW, "&ePrevious page"));
        inv.setItem(49, item(Material.BARRIER, "&cCancel"));
        if (start + PAGE_SIZE < online.size()) inv.setItem(53, item(Material.ARROW, "&eNext page"));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ click handling

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission("grime.admin")) {
            p.closeInventory();
            return;
        }
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getSlot();
        switch (h.type) {
            case MAIN -> {
                for (int i = 0; i < MAIN_SLOTS.length; i++) {
                    if (slot == MAIN_SLOTS[i]) {
                        openTeam(p, GrimeTeam.values()[i]);
                        return;
                    }
                }
                if (slot == 22) {
                    plugin.setEventActive(!plugin.isEventActive());
                    Bukkit.broadcast(GrimeSMP.LEG.deserialize(GrimeSMP.PREFIX
                            + (plugin.isEventActive() ? "&aThe event has started! Kills now earn Grime."
                                                      : "&cThe event is paused. Kills no longer earn Grime.")));
                    openMain(p);
                }
            }
            case TEAM -> {
                if (slot == 22) {
                    openMain(p);
                    return;
                }
                for (int i = 0; i < TIER_SLOTS.length; i++) {
                    if (slot != TIER_SLOTS[i]) continue;
                    Tier tr = Tier.values()[i];
                    if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                        if (plugin.getOccupant(h.team, tr) != null) plugin.clearSlot(h.team, tr);
                        openTeam(p, h.team);
                    } else {
                        openPicker(p, h.team, tr, 0);
                    }
                    return;
                }
            }
            case PICK -> {
                if (slot == 49) {
                    openTeam(p, h.team);
                } else if (slot == 45 && h.page > 0) {
                    openPicker(p, h.team, h.tier, h.page - 1);
                } else if (slot == 53 && e.getCurrentItem() != null
                        && e.getCurrentItem().getType() == Material.ARROW) {
                    openPicker(p, h.team, h.tier, h.page + 1);
                } else if (slot < PAGE_SIZE && slot < h.shown.size()) {
                    UUID id = h.shown.get(slot);
                    Player target = Bukkit.getPlayer(id);
                    if (target == null) {
                        p.sendMessage(GrimeSMP.LEG.deserialize(GrimeSMP.PREFIX + "&cThat player went offline."));
                        openPicker(p, h.team, h.tier, h.page);
                        return;
                    }
                    plugin.assign(h.team, h.tier, id, target.getName());
                    p.sendMessage(GrimeSMP.LEG.deserialize(GrimeSMP.PREFIX + "&f" + target.getName()
                            + " &7is now &f" + h.tier + " Tier &7on " + h.team.legacy + h.team.display + "&7."));
                    target.sendMessage(GrimeSMP.LEG.deserialize(GrimeSMP.PREFIX + "&7You are on team "
                            + h.team.legacy + "&l" + h.team.display + " &7as &f" + h.tier + " Tier &7("
                            + h.tier.points(plugin) + " Grime per kill against you)."));
                    openTeam(p, h.team);
                }
            }
        }
    }
}
