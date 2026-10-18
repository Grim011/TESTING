package com.grimesmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum GrimeTeam {
    // Aces uses DARK_GRAY instead of pure black because black text is unreadable in the tab list.
    // Swap NamedTextColor.DARK_GRAY / "&8" for NamedTextColor.BLACK / "&0" if you really want black.
    ACES("Aces", "A", NamedTextColor.DARK_GRAY, "&8", Material.BLACK_CONCRETE, "1_aces"),
    CLUBS("Clubs", "\u2663", NamedTextColor.GREEN, "&a", Material.GREEN_CONCRETE, "2_clubs"),
    SPADES("Spades", "\u2660", NamedTextColor.RED, "&c", Material.RED_CONCRETE, "3_spades"),
    DIAMONDS("Diamonds", "\u2666", NamedTextColor.BLUE, "&9", Material.BLUE_CONCRETE, "4_diamonds");

    public final String display;
    public final String symbol;
    public final NamedTextColor color;
    public final String legacy;
    public final Material icon;
    /** Scoreboard team name; the number prefix keeps the tab list ordered. */
    public final String sbName;

    GrimeTeam(String display, String symbol, NamedTextColor color, String legacy, Material icon, String sbName) {
        this.display = display;
        this.symbol = symbol;
        this.color = color;
        this.legacy = legacy;
        this.icon = icon;
        this.sbName = sbName;
    }

    public static GrimeTeam parse(String s) {
        for (GrimeTeam t : values()) {
            if (t.name().equalsIgnoreCase(s) || t.display.equalsIgnoreCase(s)) return t;
        }
        if (s != null && s.equalsIgnoreCase("ace")) return ACES;
        return null;
    }
}
